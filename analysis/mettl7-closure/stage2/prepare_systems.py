#!/usr/bin/env python3
"""Prepare and validate the locked eight-system SAM-bound coordinate matrix."""
from __future__ import annotations

import csv
import hashlib
import json
import shutil
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
DCMB = ROOT / "analysis/dcmb"
sys.path[:0] = [str(DCMB), str(DCMB / "reciprocal_mutation")]
import same_site_pose_analysis as base  # noqa: E402
import cooperative_wall_analysis as cooperative  # noqa: E402

BACKBONE = {"N", "CA", "C", "O", "OXT"}
PHE_NAMES = cooperative.PHE_NAMES


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def pdb_lines(path: Path) -> list[str]:
    return [line for line in path.read_text().splitlines() if line not in {"END", "ENDMDL"}]


def atom_records(path: Path) -> list[dict]:
    result = []
    for index, line in enumerate(path.read_text().splitlines()):
        if not line.startswith(("ATOM  ", "HETATM")):
            continue
        result.append({
            "index": index, "record": line[:6].strip(), "name": line[12:16].strip(),
            "res": line[17:20].strip(), "chain": line[21].strip() or "A",
            "num": int(line[22:26]), "element": line[76:78].strip().upper(),
            "xyz": np.array([float(line[30:38]), float(line[38:46]), float(line[46:54])]),
        })
    return result


def replace_coordinates(source: Path, replacements: dict[tuple[int, str], np.ndarray], destination: Path) -> None:
    lines = []
    for line in pdb_lines(source):
        if line.startswith(("ATOM  ", "HETATM")):
            key = (int(line[22:26]), line[12:16].strip())
            if key in replacements:
                x, y, z = replacements[key]
                line = f"{line[:30]}{x:8.3f}{y:8.3f}{z:8.3f}{line[54:]}"
        lines.append(line)
    destination.write_text("\n".join(lines + ["END", ""]) )


def state_coordinates(path: Path, residue: int) -> dict[str, np.ndarray]:
    return {atom["name"]: atom["xyz"].copy() for atom in atom_records(path) if atom["num"] == residue}


def build_selected_7b(source: Path, destination: Path, selection: dict) -> None:
    atoms = base.pdb_atoms(source)
    c43 = cooperative.coords(atoms, 43)
    c199 = cooperative.coords(atoms, 199)
    c229 = cooperative.coords(atoms, 229)
    replacements = {}
    if "F43_chi1" in selection:
        state43 = cooperative.phe_state(c43, selection["F43_chi1"], selection["F43_chi2"])
        replacements.update({(43, name): xyz for name, xyz in state43.items()})
    if "F199_chi1" in selection:
        state199 = cooperative.phe_state(c199, selection["F199_chi1"], selection["F199_chi2"])
        replacements.update({(199, name): xyz for name, xyz in state199.items()})
    state229 = cooperative.repack229(c229, selection["L229_offset"])
    replacements.update({(229, name): xyz for name, xyz in state229.items()})
    replace_coordinates(source, replacements, destination)


def append_sam(receptor: Path, bound_wt: Path, destination: Path) -> None:
    receptor_lines = [line for line in pdb_lines(receptor) if not line.startswith("HETATM")]
    sam_lines = [line for line in pdb_lines(bound_wt)
                 if line.startswith("HETATM") and line[17:20].strip() == "SAM"
                 and line[76:78].strip().upper() != "H"]
    destination.write_text("\n".join(receptor_lines + ["TER"] + sam_lines + ["TER", "END", ""]))


def ca_rmsd(first: Path, second: Path) -> float:
    def ca(path):
        return {(a["chain"], a["num"]): a["xyz"] for a in atom_records(path)
                if a["record"] == "ATOM" and a["name"] == "CA"}
    a, b = ca(first), ca(second)
    keys = sorted(set(a) & set(b))
    return float(np.sqrt(np.mean([np.sum((a[key] - b[key]) ** 2) for key in keys])))


def validate(system: dict, receptor: Path, complex_path: Path) -> dict:
    target = system["target"]
    parent = ROOT / ("resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb"
                     if target == "METTL7A" else "experiments/METTL7B-v6_diffdock/target_protein.pdb")
    pocket = ROOT / ("analysis/mettl7-closure/stage0/METTL7A_homologous_197_sphere_SAM_superpocket.pqr"
                     if target == "METTL7A" else "resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets/pocket2_vert.pqr")
    receptor_atoms, complex_atoms = atom_records(receptor), atom_records(complex_path)
    protein = [a for a in complex_atoms if a["record"] == "ATOM" and a["element"] != "H"]
    sam = [a for a in complex_atoms if a["record"] == "HETATM" and a["res"] == "SAM" and a["element"] != "H"]
    residues = {(a["chain"], a["num"]) for a in protein}
    names = {a["num"]: a["res"] for a in protein if a["name"] == "CA"}
    expected = {43: "PHE" if target == "METTL7A" else "LEU", 199: "PHE" if target == "METTL7A" else "GLY"}
    for mutation in system["mutations"]:
        expected[int("".join(filter(str.isdigit, mutation)))] = mutation[-1]
    one_to_three = {"F": "PHE", "L": "LEU", "G": "GLY"}
    expected = {number: one_to_three.get(name, name) for number, name in expected.items()}
    identity_ok = all(names.get(number) == name for number, name in expected.items())
    protein_xyz = np.array([a["xyz"] for a in protein])
    sam_xyz = np.array([a["xyz"] for a in sam])
    receptor_sam = np.linalg.norm(protein_xyz[:, None, :] - sam_xyz[None, :, :], axis=2)
    spheres = np.array([[float(line[30:38]), float(line[38:46]), float(line[46:54])]
                        for line in pocket.read_text().splitlines() if line.startswith(("ATOM  ", "HETATM"))])
    sam_fraction = float(np.mean(np.min(np.linalg.norm(sam_xyz[:, None, :] - spheres[None, :, :], axis=2), axis=1) <= 4.0))
    parent_atoms = atom_records(parent)
    allowed = set(system["mutation_positions"])
    if target == "METTL7B" and 43 in allowed:
        allowed.add(229)
    unchanged_parent = {(a["num"], a["name"]): a["xyz"] for a in parent_atoms if a["num"] not in allowed}
    unchanged_now = {(a["num"], a["name"]): a["xyz"] for a in receptor_atoms if a["num"] not in allowed}
    common = sorted(set(unchanged_parent) & set(unchanged_now))
    unchanged_max = max(float(np.linalg.norm(unchanged_parent[key] - unchanged_now[key])) for key in common)
    checks = {
        "chain_ok": {a["chain"] for a in protein} == {"A"},
        "residues_ok": residues == {("A", number) for number in range(1, 245)},
        "mutation_identity_ok": identity_ok,
        "sam_heavy_atoms_ok": len(sam) == 27,
        "sam_superpocket_fraction": sam_fraction,
        "sam_in_superpocket_ok": sam_fraction == 1.0,
        "receptor_sam_pairs_lt_2A": int(np.sum(receptor_sam < 2.0)),
        "receptor_sam_ok": int(np.sum(receptor_sam < 2.0)) == 0,
        "backbone_rmsd_A": ca_rmsd(parent, receptor),
        "backbone_ok": ca_rmsd(parent, receptor) <= 0.001,
        "unchanged_atom_max_displacement_A": unchanged_max,
        "unchanged_atoms_ok": unchanged_max <= 0.001,
    }
    passed = all(value for key, value in checks.items() if key.endswith("_ok"))
    return {"system": system["id"], "status": "PASS" if passed else "FAIL", **checks,
            "receptor": str(receptor.relative_to(ROOT)), "receptor_sha256": sha256(receptor),
            "complex": str(complex_path.relative_to(ROOT)), "complex_sha256": sha256(complex_path)}


def select_rows(path: Path, field: str) -> list[dict]:
    required = field.split(";")
    rows = [row for row in csv.DictReader(path.open())
            if row["clashes_below_2A"] == "0" and row["minimum_distance_A"]
            and all(row[name] for name in required)]
    rows.sort(key=lambda row: (float(row["steric_score"]), -float(row["minimum_distance_A"]),
                               *(int(row[name]) for name in required)))
    return rows


def select_l43f_state(source: Path) -> tuple[dict, list[dict]]:
    atoms = base.pdb_atoms(source)
    c43, c229 = cooperative.coords(atoms, 43), cooperative.coords(atoms, 229)
    fixed = np.array([a[5] for a in atoms if a[4] != "H" and a[3] not in {43, 229}])
    rows = []
    for chi1 in cooperative.CHI1:
        for chi2 in cooperative.CHI2:
            p43 = cooperative.phe_state(c43, chi1, chi2)
            a43 = np.array([p43[name] for name in PHE_NAMES])
            for offset in cooperative.L229_OFFSETS:
                p229 = cooperative.repack229(c229, offset)
                a229 = np.array([p229[name] for name in p229 if name not in BACKBONE | {"CB"}])
                s1, m1, c1 = cooperative.rot.pair_score(a43, fixed)
                s2, m2, c2 = cooperative.rot.pair_score(a229, fixed)
                s3, m3, c3 = cooperative.rot.pair_score(a43, a229)
                rows.append({"F43_chi1": chi1, "F43_chi2": chi2, "L229_offset": offset,
                             "steric_score": s1 + s2 + s3,
                             "minimum_distance_A": min(m1, m2, m3),
                             "clashes_below_2A": c1 + c2 + c3})
    viable = [row for row in rows if row["clashes_below_2A"] == 0]
    viable.sort(key=lambda row: (row["steric_score"], -row["minimum_distance_A"],
                                 row["F43_chi1"], row["F43_chi2"], row["L229_offset"]))
    return viable[0], rows


def main() -> None:
    prepared = HERE / "prepared"
    prepared.mkdir(parents=True, exist_ok=True)
    parent_a = ROOT / "resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb"
    parent_b = ROOT / "experiments/METTL7B-v6_diffdock/target_protein.pdb"
    reciprocal = DCMB / "reciprocal_mutation"
    single_rows = select_rows(reciprocal / "cooperative_wall/single_199_controls.csv", "chi1;chi2;L229_offset")
    double_rows = select_rows(reciprocal / "cooperative_wall/mettl7b_joint_rotamer_repacking.csv",
                              "F43_chi1;F43_chi2;F199_chi1;F199_chi2;L229_offset")
    single_selection = {"F199_chi1": int(single_rows[0]["chi1"]), "F199_chi2": int(single_rows[0]["chi2"]),
                        "L229_offset": int(single_rows[0]["L229_offset"])}
    double_selection = {name: int(double_rows[0][name]) for name in
                        ("F43_chi1", "F43_chi2", "F199_chi1", "F199_chi2", "L229_offset")}
    l43f_best, l43f_rows = select_l43f_state(reciprocal / "METTL7B_L43F_fixed_backbone.pdb")
    l43f_selection = {name: int(l43f_best[name]) for name in ("F43_chi1", "F43_chi2", "L229_offset")}
    receptor_sources = {
        "7A_WT": parent_a,
        "7A_F43L": reciprocal / "METTL7A_F43L_fixed_backbone.pdb",
        "7A_F199G": reciprocal / "METTL7A_F199G_fixed_backbone.pdb",
        "7A_F43L_F199G": reciprocal / "METTL7A_F43L_F199G_fixed_backbone.pdb",
        "7B_WT": parent_b,
    }
    selected_l43f = prepared / "7B_L43F_receptor.pdb"
    selected_g = prepared / "7B_G199F_receptor.pdb"
    selected_double = prepared / "7B_L43F_G199F_receptor.pdb"
    build_selected_7b(reciprocal / "METTL7B_L43F_fixed_backbone.pdb", selected_l43f, l43f_selection)
    build_selected_7b(reciprocal / "METTL7B_G199F_fixed_backbone.pdb", selected_g, single_selection)
    build_selected_7b(reciprocal / "METTL7B_L43F_G199F_fixed_backbone.pdb", selected_double, double_selection)
    receptor_sources.update({"7B_L43F": selected_l43f, "7B_G199F": selected_g, "7B_L43F_G199F": selected_double})
    systems = [
        {"id": "7A_WT", "target": "METTL7A", "mutations": [], "mutation_positions": []},
        {"id": "7B_WT", "target": "METTL7B", "mutations": [], "mutation_positions": []},
        {"id": "7A_F43L", "target": "METTL7A", "mutations": ["F43L"], "mutation_positions": [43]},
        {"id": "7A_F199G", "target": "METTL7A", "mutations": ["F199G"], "mutation_positions": [199]},
        {"id": "7A_F43L_F199G", "target": "METTL7A", "mutations": ["F43L", "F199G"], "mutation_positions": [43, 199]},
        {"id": "7B_L43F", "target": "METTL7B", "mutations": ["L43F"], "mutation_positions": [43]},
        {"id": "7B_G199F", "target": "METTL7B", "mutations": ["G199F"], "mutation_positions": [199]},
        {"id": "7B_L43F_G199F", "target": "METTL7B", "mutations": ["L43F", "G199F"], "mutation_positions": [43, 199]},
    ]
    validation = []
    provenance = {"protocol": "analysis/mettl7-closure/stage1/protocol.json",
                  "single_7B_L43F_selection": l43f_selection,
                  "single_7B_L43F_selection_metrics": l43f_best,
                  "single_7B_G199F_selection": single_selection,
                  "single_7B_G199F_selection_metrics": single_rows[0],
                  "double_7B_L43F_G199F_selection": double_selection,
                  "double_7B_L43F_G199F_selection_metrics": double_rows[0],
                  "selection_rule": "zero clashes, minimum steric score, maximum minimum distance, deterministic torsion order",
                  "sam_policy": "27 validated WT SAM heavy atoms appended unchanged; no relaxation",
                  "systems": {}}
    with (HERE / "l43f_joint_enumeration.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(l43f_rows[0]))
        writer.writeheader(); writer.writerows(l43f_rows)
    for system in systems:
        receptor = prepared / f'{system["id"]}_receptor.pdb'
        source = receptor_sources[system["id"]]
        if source.resolve() != receptor.resolve():
            shutil.copyfile(source, receptor)
        bound = ROOT / f'analysis/dcmb/sam_state/validated/WT_METTL{system["target"][-2:]}_SAM_BOUND.pdb'
        complex_path = prepared / f'{system["id"]}_SAM_BOUND.pdb'
        append_sam(receptor, bound, complex_path)
        row = validate(system, receptor, complex_path)
        validation.append(row)
        provenance["systems"][system["id"]] = {"source": str(source.relative_to(ROOT)),
                                                   "source_sha256": sha256(source),
                                                   "receptor_sha256": row["receptor_sha256"],
                                                   "complex_sha256": row["complex_sha256"]}
    with (HERE / "validation.csv").open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(validation[0]))
        writer.writeheader(); writer.writerows(validation)
    (HERE / "provenance.json").write_text(json.dumps(provenance, indent=2) + "\n")
    if any(row["status"] != "PASS" for row in validation):
        raise SystemExit("Stage 2 preparation validation failed")
    print(json.dumps({"systems": len(validation), "passed": len(validation),
                      "l43f_selection": l43f_selection, "single_selection": single_selection,
                      "double_selection": double_selection}, sort_keys=True))


if __name__ == "__main__":
    main()
