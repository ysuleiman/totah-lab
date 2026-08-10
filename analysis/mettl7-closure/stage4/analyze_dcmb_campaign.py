#!/usr/bin/env python3
"""Analyze the locked eight-system DCMB campaign without score-based selection."""

from __future__ import annotations

import csv
import json
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
PROTOCOL = json.loads((ROOT / "analysis/mettl7-closure/stage1/protocol.json").read_text())
TRANSFER = json.loads((ROOT / "analysis/mettl7-closure/stage0/superpocket_transfer.json").read_text())
SPHERE_A = ROOT / "analysis/mettl7-closure/stage0/METTL7A_homologous_197_sphere_SAM_superpocket.pqr"
STAGE2 = ROOT / "analysis/mettl7-closure/stage2/prepared"


def write_csv(path: Path, rows: list[dict]) -> None:
    if not rows:
        raise RuntimeError(f"No rows for {path.name}")
    with path.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)


def heavy_models(path: Path) -> list[dict]:
    models, atoms, score = [], [], None
    for line in path.read_text().splitlines():
        if line.startswith("MODEL"):
            atoms, score = [], None
        elif line.startswith("REMARK VINA RESULT:"):
            score = float(line.split()[3])
        elif line.startswith(("ATOM  ", "HETATM")):
            atom_type = line.split()[-1]
            if not atom_type.upper().startswith("H"):
                atoms.append([float(line[30:38]), float(line[38:46]), float(line[46:54])])
        elif line.startswith("ENDMDL") and atoms:
            models.append({"score": score, "xyz": np.array(atoms)})
    return models


def pdb_atoms(path: Path, include_sam: bool = True) -> list[dict]:
    atoms = []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM  ", "HETATM")):
            continue
        element = line[76:78].strip() or line[12:16].strip()[0]
        residue = line[17:20].strip()
        if element.upper() == "H" or (not include_sam and residue == "SAM"):
            continue
        atoms.append({
            "name": line[12:16].strip(), "residue": residue, "chain": line[21:22].strip(),
            "number": int(line[22:26]), "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])]),
        })
    return atoms


def spheres(path: Path) -> np.ndarray:
    points = []
    for line in path.read_text().splitlines():
        if line.startswith(("ATOM  ", "HETATM")):
            points.append([float(line[30:38]), float(line[38:46]), float(line[46:54])])
    return np.array(points)


def pair_distances(a: np.ndarray, b: np.ndarray) -> np.ndarray:
    return np.linalg.norm(a[:, None, :] - b[None, :, :], axis=2)


def rmsd(a: np.ndarray, b: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.sum((a - b) ** 2, axis=1))))


def complete_linkage(poses: list[np.ndarray], cutoff: float = 2.0) -> list[list[int]]:
    groups = [[index] for index in range(len(poses))]
    while True:
        choices = []
        for left in range(len(groups)):
            for right in range(left + 1, len(groups)):
                distance = max(rmsd(poses[a], poses[b]) for a in groups[left] for b in groups[right])
                if distance <= cutoff:
                    choices.append((distance, left, right))
        if not choices:
            break
        _, left, right = min(choices)
        groups[left].extend(groups[right])
        groups.pop(right)
    return sorted(groups, key=lambda group: (-len(group), min(group)))


def medoid(members: list[dict]) -> dict:
    ranked = []
    for index, candidate in enumerate(members):
        mean = float(np.mean([rmsd(candidate["xyz"], other["xyz"]) for other in members]))
        ranked.append((mean, candidate["seed"], candidate["mode"], index))
    mean, _, _, index = min(ranked)
    result = members[index]
    result["mean_family_rmsd_A"] = mean
    return result


def shared_volume(a: np.ndarray, b: np.ndarray, radius: float = 1.7, spacing: float = 0.5) -> float:
    low = np.maximum(a.min(0), b.min(0)) - radius
    high = np.minimum(a.max(0), b.max(0)) + radius
    if np.any(low > high):
        return 0.0
    axes = [np.arange(low[i], high[i] + spacing / 2, spacing) for i in range(3)]
    grid = np.array(np.meshgrid(*axes, indexing="ij")).reshape(3, -1).T
    occupied_a = pair_distances(grid, a).min(1) <= radius
    occupied_b = pair_distances(grid, b).min(1) <= radius
    return float(np.sum(occupied_a & occupied_b) * spacing**3)


def accessible_volume(ligand: np.ndarray, protein: np.ndarray, spacing: float = 0.5) -> float:
    low, high = ligand.min(0) - 3.0, ligand.max(0) + 3.0
    axes = [np.arange(low[i], high[i] + spacing / 2, spacing) for i in range(3)]
    grid = np.array(np.meshgrid(*axes, indexing="ij")).reshape(3, -1).T
    return float(np.sum((pair_distances(grid, ligand).min(1) <= 3.0) & (pair_distances(grid, protein).min(1) >= 2.0)) * spacing**3)


def principal_axis(xyz: np.ndarray) -> np.ndarray:
    _, _, vectors = np.linalg.svd(xyz - xyz.mean(0), full_matrices=False)
    axis = vectors[0]
    return axis if axis[np.argmax(np.abs(axis))] >= 0 else -axis


def main() -> None:
    manifest = json.loads((HERE / "campaign_manifest.json").read_text())
    rotation = np.array(TRANSFER["rotation"])
    translation = np.array(TRANSFER["translation"])
    cloud_a = spheres(SPHERE_A)
    cloud_b = (cloud_a - translation) @ rotation.T
    threshold = PROTOCOL["dcmb_docking"]["canonical_site_assignment"]
    assert "70%" in threshold and "4.0 A" in threshold
    poses_rows, family_rows, contact_rows, validation_rows = [], [], [], []

    for condition in manifest["conditions"]:
        system, paralog = condition["system"], condition["paralog"]
        cloud = cloud_a if paralog == "7A" else cloud_b
        complex_atoms = pdb_atoms(STAGE2 / f"{system}_SAM_BOUND.pdb")
        protein_atoms = [atom for atom in complex_atoms if atom["residue"] != "SAM"]
        sam_atoms = [atom for atom in complex_atoms if atom["residue"] == "SAM"]
        protein = np.array([atom["xyz"] for atom in protein_atoms])
        sam = np.array([atom["xyz"] for atom in sam_atoms])
        for enantiomer in ("R", "S"):
            records = []
            failed = False
            for seed in PROTOCOL["dcmb_docking"]["seeds"]:
                path = HERE / "raw" / f"{system}_{enantiomer}_s{seed}.pdbqt"
                models = heavy_models(path)
                passed = len(models) >= PROTOCOL["dcmb_docking"]["minimum_returned_modes_per_seed"]
                failed |= not passed
                validation_rows.append({"system": system, "enantiomer": enantiomer, "seed": seed, "returned_modes": len(models), "minimum_required": 8, "status": "PASS" if passed else "FAIL"})
                for mode, model in enumerate(models, 1):
                    model.update({"seed": seed, "mode": mode})
                    records.append(model)
            if failed:
                for record in records:
                    poses_rows.append({"system": system, "enantiomer": enantiomer, "seed": record["seed"], "mode": record["mode"], "engine_score": record["score"], "superpocket_atom_fraction": "", "site_status": "NOT_EVALUABLE_DOCKING_FAILURE", "family": ""})
                continue

            for record in records:
                record["site_fraction"] = float(np.mean(pair_distances(record["xyz"], cloud).min(1) <= 4.0))
                record["on_site"] = record["site_fraction"] >= 0.70
            accepted = [record for record in records if record["on_site"]]
            groups = complete_linkage([record["xyz"] for record in accepted])
            family_by_pose = {}
            for family_index, group in enumerate(groups, 1):
                members = [accepted[index] for index in group]
                representative = medoid(members)
                ligand = representative["xyz"]
                centroid = ligand.mean(0)
                axis = principal_axis(ligand)
                if paralog == "7B":
                    common_centroid = centroid @ rotation + translation
                    common_axis = axis @ rotation
                else:
                    common_centroid, common_axis = centroid, axis
                distances_sam = pair_distances(ligand, sam)
                pairs_2 = int(np.sum(distances_sam < 2.0))
                pairs_25 = int(np.sum(distances_sam < 2.5))
                compatibility = "sterically_incompatible" if pairs_2 else ("close_nonoverlapping" if pairs_25 else "compatible")
                for member in members:
                    family_by_pose[(member["seed"], member["mode"])] = family_index
                family_rows.append({
                    "system": system, "enantiomer": enantiomer, "family": family_index, "population": len(members),
                    "representative_seed": representative["seed"], "representative_mode": representative["mode"],
                    "medoid_mean_direct_rmsd_A": representative["mean_family_rmsd_A"], "representative_engine_score": representative["score"],
                    "score_min": min(item["score"] for item in members), "score_mean": float(np.mean([item["score"] for item in members])), "score_max": max(item["score"] for item in members),
                    "superpocket_atom_fraction": representative["site_fraction"], "sam_min_distance_A": float(distances_sam.min()),
                    "sam_pairs_lt_2A": pairs_2, "sam_pairs_lt_2p5A": pairs_25, "sam_shared_occupied_volume_A3": shared_volume(ligand, sam),
                    "sam_compatibility": compatibility, "local_accessible_volume_A3": accessible_volume(ligand, protein),
                    "centroid_x": centroid[0], "centroid_y": centroid[1], "centroid_z": centroid[2],
                    "axis_x": axis[0], "axis_y": axis[1], "axis_z": axis[2],
                    "common_7A_centroid_x": common_centroid[0], "common_7A_centroid_y": common_centroid[1], "common_7A_centroid_z": common_centroid[2],
                    "common_7A_axis_x": common_axis[0], "common_7A_axis_y": common_axis[1], "common_7A_axis_z": common_axis[2],
                })
                residue_groups = {}
                for atom in protein_atoms:
                    key = (atom["chain"], atom["number"], atom["residue"])
                    residue_groups.setdefault(key, []).append(atom["xyz"])
                for (chain, number, residue), coordinates in sorted(residue_groups.items()):
                    distance = float(pair_distances(ligand, np.array(coordinates)).min())
                    if distance <= 4.5:
                        contact_rows.append({"system": system, "enantiomer": enantiomer, "family": family_index, "chain": chain, "residue_number": number, "residue_name": residue, "minimum_distance_A": distance})
            for record in records:
                poses_rows.append({
                    "system": system, "enantiomer": enantiomer, "seed": record["seed"], "mode": record["mode"],
                    "engine_score": record["score"], "superpocket_atom_fraction": record["site_fraction"],
                    "site_status": "ACCEPTED_CANONICAL_SITE" if record["on_site"] else "REJECTED_OUTSIDE_CANONICAL_SITE",
                    "family": family_by_pose.get((record["seed"], record["mode"]), ""),
                })

    write_csv(HERE / "seed_validation.csv", validation_rows)
    write_csv(HERE / "pose_results.csv", poses_rows)
    write_csv(HERE / "family_results.csv", family_rows)
    write_csv(HERE / "family_contacts.csv", contact_rows)
    summary = {
        "status": "PASS" if all(row["status"] == "PASS" for row in validation_rows) else "FAIL",
        "systems": len(manifest["conditions"]), "raw_poses": len(poses_rows),
        "accepted_poses": sum(row["site_status"] == "ACCEPTED_CANONICAL_SITE" for row in poses_rows),
        "rejected_off_site_poses": sum(row["site_status"] == "REJECTED_OUTSIDE_CANONICAL_SITE" for row in poses_rows),
        "families": len(family_rows), "sam_present_in_all_receptors": all(item["sam_atom_records"] == 49 for item in manifest["conditions"]),
        "representative_policy": "geometric medoid; score unused",
    }
    (HERE / "summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
