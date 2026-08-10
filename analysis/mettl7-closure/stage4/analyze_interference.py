#!/usr/bin/env python3
"""Cross accepted DCMB family medoids with all accepted Stage 3 TSL states."""

from __future__ import annotations

import csv
import json
import math
from pathlib import Path

import numpy as np

import analyze_dcmb_campaign as dcmb


ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
STAGE2 = ROOT / "analysis/mettl7-closure/stage2/prepared"
STAGE3 = ROOT / "analysis/mettl7-closure/stage3"
BACKBONE = {"N", "CA", "C", "O", "OXT"}


def atom_records(path: Path) -> list[dict]:
    atoms = []
    for line in path.read_text().splitlines():
        if not line.startswith(("ATOM  ", "HETATM")):
            continue
        element = line[76:78].strip() or line[12:16].strip()[0]
        if element.upper() == "H":
            continue
        atoms.append({
            "name": line[12:16].strip(), "residue": line[17:20].strip(),
            "chain": line[21:22].strip(), "number": int(line[22:26]),
            "element": element.upper(), "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])]),
        })
    return atoms


def protein_atoms(atoms: list[dict]) -> list[dict]:
    return [atom for atom in atoms if atom["residue"] not in {"SAM", "TSL", "SAH", "MTS"}]


def ligand_xyz(atoms: list[dict], residue: str) -> np.ndarray:
    return np.array([atom["xyz"] for atom in atoms if atom["residue"] == residue])


def point_segment(points: np.ndarray, start: np.ndarray, end: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    vector = end - start
    projection = ((points - start) @ vector) / float(vector @ vector)
    closest = start + np.clip(projection, 0.0, 1.0)[:, None] * vector
    return np.linalg.norm(points - closest, axis=1), projection


def corridor_occupied_fraction(ligand: np.ndarray, start: np.ndarray, end: np.ndarray, radius: float) -> float:
    spacing = 0.5
    low, high = np.minimum(start, end) - radius, np.maximum(start, end) + radius
    axes = [np.arange(low[i], high[i] + spacing / 2, spacing) for i in range(3)]
    grid = np.array(np.meshgrid(*axes, indexing="ij")).reshape(3, -1).T
    distance, projection = point_segment(grid, start, end)
    corridor = (projection >= 0.0) & (projection <= 1.0) & (distance <= radius)
    occupied = dcmb.pair_distances(grid, ligand).min(1) <= 1.7
    return float(np.sum(corridor & occupied) / max(1, np.sum(corridor)))


def moved_paths(original: list[dict], state: list[dict]) -> list[dict]:
    reference = {(atom["chain"], atom["number"], atom["name"]): atom for atom in original}
    paths = []
    for atom in state:
        key = (atom["chain"], atom["number"], atom["name"])
        if key not in reference:
            continue
        start, end = reference[key]["xyz"], atom["xyz"]
        displacement = float(np.linalg.norm(end - start))
        if displacement > 0.001:
            paths.append({"start": start, "end": end, "displacement": displacement, "kind": "backbone" if atom["name"] in BACKBONE else "sidechain"})
    return paths


def swept_metrics(ligand: np.ndarray, paths: list[dict]) -> dict:
    if not paths:
        return {"minimum_path_distance_A": "", "paths_lt_3p4A": 0, "paths_lt_2A": 0, "shared_swept_occupied_volume_A3": 0.0, "movement_mechanistic_gate": False}
    distances, samples = [], []
    for path in paths:
        distance, _ = point_segment(ligand, path["start"], path["end"])
        distances.append(float(distance.min()))
        count = max(2, int(math.ceil(path["displacement"] / 0.2)) + 1)
        samples.extend(np.linspace(path["start"], path["end"], count))
    overlap = dcmb.shared_volume(ligand, np.array(samples), 1.7)
    return {
        "minimum_path_distance_A": min(distances), "paths_lt_3p4A": sum(value < 3.4 for value in distances),
        "paths_lt_2A": sum(value < 2.0 for value in distances), "shared_swept_occupied_volume_A3": overlap,
        "movement_mechanistic_gate": overlap >= 0.5,
    }


def family_coordinates(row: dict) -> np.ndarray:
    raw = HERE / "raw" / f'{row["system"]}_{row["enantiomer"]}_s{row["representative_seed"]}.pdbqt'
    return dcmb.heavy_models(raw)[int(row["representative_mode"]) - 1]["xyz"]


def main() -> None:
    with (HERE / "family_results.csv").open() as handle:
        families = list(csv.DictReader(handle))
    manifest = json.loads((STAGE3 / "manifest.json").read_text())
    states_by_system = {item["system"]: [ROOT / artifact["path"] for artifact in item["artifacts"]] for item in manifest["systems"]}
    state_cache = {}
    for system, paths in states_by_system.items():
        original = protein_atoms(atom_records(STAGE2 / f"{system}_SAM_BOUND.pdb"))
        state_cache[system] = []
        for index, path in enumerate(paths, 1):
            atoms = atom_records(path)
            sam = [atom for atom in atoms if atom["residue"] == "SAM"]
            sam_by_name = {atom["name"]: atom["xyz"] for atom in sam}
            tsl_atoms = [atom for atom in atoms if atom["residue"] == "TSL"]
            sulfur = next(atom["xyz"] for atom in tsl_atoms if atom["element"] == "S")
            state_cache[system].append({
                "index": index, "path": path, "tsl": np.array([atom["xyz"] for atom in tsl_atoms]),
                "sulfur": sulfur, "sam_ce": sam_by_name["CE"],
                "paths": moved_paths(original, protein_atoms(atoms)), "response_required": system.startswith("7A_"),
            })

    rows, matrix = [], []
    for family in families:
        system = family["system"]
        ligand = family_coordinates(family)
        family_state_rows = []
        for state in state_cache[system]:
            distances = dcmb.pair_distances(ligand, state["tsl"])
            segment_distance, projection = point_segment(ligand, state["sulfur"], state["sam_ce"])
            between = (projection >= 0.0) & (projection <= 1.0)
            direct = float(distances.min()) < 2.0
            blockade = bool(np.any(between & (segment_distance <= 2.0)))
            shared_occupied = dcmb.shared_volume(ligand, state["tsl"], 1.7)
            movement = swept_metrics(ligand, state["paths"])
            row = {
                "system": system, "enantiomer": family["enantiomer"], "family": family["family"],
                "population": family["population"], "tsl_state": state["index"], "tsl_response_required": state["response_required"],
                "minimum_dcmb_tsl_distance_A": float(distances.min()), "atom_pairs_lt_2A": int(np.sum(distances < 2.0)),
                "atom_pairs_lt_2p5A": int(np.sum(distances < 2.5)), "shared_core_volume_A3": dcmb.shared_volume(ligand, state["tsl"], 1.0),
                "shared_occupied_volume_A3": shared_occupied, "dcmb_atoms_in_tsl_1p7A_envelope": int(np.sum(distances.min(1) <= 1.7)),
                "tsl_atoms_in_dcmb_1p7A_envelope": int(np.sum(distances.min(0) <= 1.7)),
                "transfer_segment_length_A": float(np.linalg.norm(state["sam_ce"] - state["sulfur"])),
                "minimum_distance_to_transfer_segment_A": float(segment_distance.min()), "dcmb_atoms_projected_within_segment": int(np.sum(between)),
                "between_within_1p5A": int(np.sum(between & (segment_distance <= 1.5))), "between_within_2A": int(np.sum(between & (segment_distance <= 2.0))),
                "between_within_2p5A": int(np.sum(between & (segment_distance <= 2.5))),
                "corridor_occupied_fraction_1p5A": corridor_occupied_fraction(ligand, state["sulfur"], state["sam_ce"], 1.5),
                "corridor_occupied_fraction_2A": corridor_occupied_fraction(ligand, state["sulfur"], state["sam_ce"], 2.0),
                "corridor_occupied_fraction_2p5A": corridor_occupied_fraction(ligand, state["sulfur"], state["sam_ce"], 2.5),
                "direct_lt_2A": direct, "transfer_corridor_blockade": blockade,
                **movement,
            }
            row["core_or_corridor_interference"] = direct or blockade
            row["clean_noninterfering_escape"] = not direct and not blockade and shared_occupied == 0.0
            rows.append(row)
            family_state_rows.append(row)

        interfering = sum(row["core_or_corridor_interference"] for row in family_state_rows)
        clean = sum(row["clean_noninterfering_escape"] for row in family_state_rows)
        if interfering == len(family_state_rows):
            classification = "BROADLY_INTERFERING"
        elif interfering > 0:
            classification = "STATE_DEPENDENT_INTERFERING"
        elif clean > 0:
            classification = "NON_INTERFERING_ESCAPE"
        else:
            classification = "NOT_CLASSIFIED_SHARED_VOLUME_ONLY"
        matrix.append({
            "system": system, "enantiomer": family["enantiomer"], "family": family["family"], "population": family["population"],
            "tsl_states": len(family_state_rows), "states_core_or_corridor_interfered": interfering,
            "states_clean_escape": clean, "states_movement_gate_interfered": sum(row["movement_mechanistic_gate"] for row in family_state_rows),
            "classification": classification,
        })

    dcmb.write_csv(HERE / "interference_state_matrix.csv", rows)
    dcmb.write_csv(HERE / "interference_family_matrix.csv", matrix)
    system_rows = []
    for system in states_by_system:
        subset = [row for row in matrix if row["system"] == system]
        system_rows.append({
            "system": system, "tsl_states": len(states_by_system[system]), "dcmb_families": len(subset),
            "broadly_interfering_families": sum(row["classification"] == "BROADLY_INTERFERING" for row in subset),
            "state_dependent_families": sum(row["classification"] == "STATE_DEPENDENT_INTERFERING" for row in subset),
            "non_interfering_escape_families": sum(row["classification"] == "NON_INTERFERING_ESCAPE" for row in subset),
            "shared_volume_only_unclassified_families": sum(row["classification"] == "NOT_CLASSIFIED_SHARED_VOLUME_ONLY" for row in subset),
            "families_with_movement_gate_interference": sum(row["states_movement_gate_interfered"] > 0 for row in subset),
        })
    dcmb.write_csv(HERE / "eight_system_interference_matrix.csv", system_rows)
    summary = {"status": "PASS", "systems": len(system_rows), "dcmb_families": len(matrix), "family_state_comparisons": len(rows), "tsl_states": sum(len(value) for value in states_by_system.values())}
    (HERE / "interference_summary.json").write_text(json.dumps(summary, indent=2) + "\n")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
