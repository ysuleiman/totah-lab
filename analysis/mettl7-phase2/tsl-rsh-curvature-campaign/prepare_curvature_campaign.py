#!/usr/bin/env python3
"""Construct and audit the frozen TSL-RSH mixed-curvature geometry panel."""

from __future__ import annotations

import csv
import hashlib
import json
import math
import shutil
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
ANCHOR_ROOT = ROOT / "analysis/mettl7-phase2/execution-unit-05O/qm-native-minima"
EARLIER_COORDINATES = ROOT / "analysis/mettl7-phase2/tsl-rsh-cross-coupling/INTERNAL_COORDINATE_DEFINITIONS.json"
EARLIER_ANALYSIS = ROOT / "analysis/mettl7-phase2/tsl-rsh-cross-coupling/TRAIN_COUPLING_ANALYSIS.json"
GEOMETRIES = HERE / "geometries"
ANCHORS = ("MIN01", "MIN02", "MIN04")
COORDINATES = (
    {"coordinate_id": "S_C", "type": "DISTANCE", "atom_indices_zero_based": [9, 25], "atom_indices_one_based": [10, 26], "periodic": False, "unit": "angstrom"},
    {"coordinate_id": "S_H", "type": "DISTANCE", "atom_indices_zero_based": [25, 55], "atom_indices_one_based": [26, 56], "periodic": False, "unit": "angstrom"},
    {"coordinate_id": "C_S_H", "type": "ANGLE", "atom_indices_zero_based": [9, 25, 55], "atom_indices_one_based": [10, 26, 56], "periodic": False, "unit": "radian"},
    {"coordinate_id": "CHI", "type": "TORSION", "atom_indices_zero_based": [55, 25, 9, 8], "atom_indices_one_based": [56, 26, 10, 9], "periodic": True, "unit": "radian"},
    {"coordinate_id": "PHI", "type": "TORSION", "atom_indices_zero_based": [25, 9, 8, 7], "atom_indices_one_based": [26, 10, 9, 8], "periodic": True, "unit": "radian"},
    {"coordinate_id": "PSI", "type": "TORSION", "atom_indices_zero_based": [9, 8, 7, 1], "atom_indices_one_based": [10, 9, 8, 2], "periodic": True, "unit": "radian"},
)
BY_ID = {item["coordinate_id"]: item for item in COORDINATES}
PAIRS = (("S_C", "C_S_H"), ("S_H", "C_S_H"), ("S_C", "CHI"), ("S_H", "CHI"), ("C_S_H", "CHI"), ("PHI", "PSI"))
STEPS = {"S_C": 0.015, "S_H": 0.010, "C_S_H": math.radians(1.5), "CHI": math.radians(3.0), "PHI": math.radians(3.0), "PSI": math.radians(3.0)}
EXPECTED_ELEMENTS = ["C"] * 5 + ["O"] + ["C"] * 16 + ["O", "O", "C", "S"] + ["H"] * 30
COVALENT = {"H": 0.31, "C": 0.76, "O": 0.66, "S": 1.05}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read_xyz(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines(); count = int(lines[0]); rows = [line.split() for line in lines[2:2 + count]]
    elements = [row[0] for row in rows]; xyz = np.asarray([[float(v) for v in row[1:4]] for row in rows])
    if count != 56 or elements != EXPECTED_ELEMENTS or xyz.shape != (56, 3) or not np.isfinite(xyz).all():
        raise ValueError(f"anchor identity/order failure: {path}")
    return elements, xyz


def write_xyz(path: Path, elements: list[str], xyz: np.ndarray, comment: str) -> None:
    text = f"{len(elements)}\n{comment}\n" + "".join(f"{element:2s} {point[0]: .12f} {point[1]: .12f} {point[2]: .12f}\n" for element, point in zip(elements, xyz))
    path.write_text(text)


def wrapped(value: float) -> float:
    return math.atan2(math.sin(value), math.cos(value))


def value(xyz: np.ndarray, definition: dict[str, object]) -> float:
    atoms = [xyz[index] for index in definition["atom_indices_zero_based"]]
    if definition["type"] == "DISTANCE": return float(np.linalg.norm(atoms[0] - atoms[1]))
    if definition["type"] == "ANGLE":
        left, right = atoms[0] - atoms[1], atoms[2] - atoms[1]
        return float(math.acos(np.clip(np.dot(left, right) / np.linalg.norm(left) / np.linalg.norm(right), -1.0, 1.0)))
    p0, p1, p2, p3 = atoms; b0, b1, b2 = -(p1 - p0), p2 - p1, p3 - p2; b1 /= np.linalg.norm(b1)
    v, w = b0 - np.dot(b0, b1) * b1, b2 - np.dot(b2, b1) * b1
    return float(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def vector(xyz: np.ndarray) -> np.ndarray:
    return np.asarray([value(xyz, definition) for definition in COORDINATES])


def difference(actual: np.ndarray, target: np.ndarray) -> np.ndarray:
    result = actual - target
    for index, definition in enumerate(COORDINATES):
        if definition["periodic"]: result[index] = wrapped(result[index])
    return result


def jacobian(xyz: np.ndarray, step: float = 1.0e-5) -> np.ndarray:
    result = np.zeros((len(COORDINATES), xyz.size))
    active = sorted({atom for definition in COORDINATES for atom in definition["atom_indices_zero_based"]})
    for atom in active:
        for axis in range(3):
            plus, minus = xyz.copy(), xyz.copy(); plus[atom, axis] += step; minus[atom, axis] -= step
            delta = difference(vector(plus), vector(minus)) / (2.0 * step)
            result[:, 3 * atom + axis] = delta
    return result


def construct(anchor: np.ndarray, target: np.ndarray) -> tuple[np.ndarray, int, float]:
    xyz = anchor.copy()
    for iteration in range(25):
        residual = difference(vector(xyz), target)
        maximum = float(np.max(np.abs(residual)))
        if maximum <= 2.0e-9: return xyz, iteration, maximum
        j = jacobian(xyz)
        displacement = np.linalg.lstsq(j, -residual, rcond=1.0e-11)[0].reshape(56, 3)
        if np.max(np.linalg.norm(displacement, axis=1)) > 0.15:
            raise ValueError("Newton internal-coordinate step exceeded 0.15 A")
        xyz += displacement
    raise ValueError(f"internal-coordinate construction did not converge: {np.max(np.abs(difference(vector(xyz), target)))}")


def connectivity(elements: list[str], xyz: np.ndarray) -> set[tuple[int, int]]:
    return {(i, j) for i in range(len(xyz)) for j in range(i + 1, len(xyz)) if np.linalg.norm(xyz[i] - xyz[j]) <= 1.25 * (COVALENT[elements[i]] + COVALENT[elements[j]])}


def chiral_signatures(elements: list[str], xyz: np.ndarray, bonds: set[tuple[int, int]]) -> dict[int, float]:
    neighbors = {i: [] for i in range(len(xyz))}
    for i, j in bonds: neighbors[i].append(j); neighbors[j].append(i)
    result = {}
    for center, linked in neighbors.items():
        if elements[center] == "C" and len(linked) == 4:
            a, b, c = sorted(linked)[:3]
            volume = float(np.dot(np.cross(xyz[a] - xyz[center], xyz[b] - xyz[center]), xyz[c] - xyz[center]))
            if abs(volume) > 1.0e-5: result[center] = volume
    return result


def minimum_distance(xyz: np.ndarray) -> float:
    return min(float(np.linalg.norm(xyz[i] - xyz[j])) for i in range(len(xyz)) for j in range(i + 1, len(xyz)))


def verify_frozen_coordinate_identity() -> None:
    old = {item["id"]: item for item in json.loads(EARLIER_COORDINATES.read_text())["coordinates"]}
    mapping = {"S_C": "R_SC", "S_H": "R_SH", "C_S_H": "THETA_CSH", "CHI": "CHI", "PHI": "PHI", "PSI": "PSI"}
    for current, historical in mapping.items():
        if BY_ID[current]["atom_indices_zero_based"] != old[historical]["atoms_zero_based"]:
            raise ValueError(f"frozen coordinate identity mismatch: {current}")


def main() -> None:
    verify_frozen_coordinate_identity()
    previous = json.loads(EARLIER_ANALYSIS.read_text())
    if previous["strong_pair_count"] != 0:
        raise ValueError("unexpected prior strong pair result")
    if GEOMETRIES.exists(): shutil.rmtree(GEOMETRIES)
    GEOMETRIES.mkdir(parents=True)
    anchor_data = {}; definitions = [dict(item) for item in COORDINATES]
    for anchor in ANCHORS:
        source = ANCHOR_ROOT / anchor / "final.xyz"; input_data = json.loads((ANCHOR_ROOT / anchor / "input.json").read_text()); result = json.loads((ANCHOR_ROOT / anchor / "result.json").read_text())
        elements, xyz = read_xyz(source)
        if input_data["charge"] != 0 or input_data["multiplicity"] != 1 or input_data["minimum_id"] != anchor or result["final_xyz_sha256"] != sha256(source):
            raise ValueError(f"anchor provenance failure: {anchor}")
        anchor_data[anchor] = {"elements": elements, "xyz": xyz, "source": str(source.relative_to(ROOT)), "sha256": sha256(source), "atom_order_sha256": hashlib.sha256("\n".join(elements).encode()).hexdigest(), "charge": 0, "multiplicity": 1, "values": vector(xyz)}
    for index, definition in enumerate(definitions):
        for anchor in ANCHORS: definition["anchor_value_" + anchor.lower()] = float(anchor_data[anchor]["values"][index])
    coordinate_document = {"schema": "tsl-rsh-curvature-coordinate-definitions-v1", "source_identity": str(EARLIER_COORDINATES.relative_to(ROOT)), "source_sha256": sha256(EARLIER_COORDINATES), "coordinate_count": len(definitions), "coordinates": definitions, "anchors": {anchor: {key: value for key, value in data.items() if key not in {"elements", "xyz", "values"}} for anchor, data in anchor_data.items()}}
    (HERE / "CURVATURE_COORDINATE_DEFINITIONS.json").write_text(json.dumps(coordinate_document, indent=2, sort_keys=True) + "\n")

    panel = {"schema": "tsl-rsh-curvature-pair-panel-v1", "selection_source": "core pair set specified before new QM; prior training-only analysis had zero strong pairs, so no additions", "selected_pair_count": len(PAIRS), "pairs": [{"pair_id": f"{a}__{b}", "coordinates": [a, b], "anchors": list(ANCHORS), "full_scale": 1.0, "second_scale": 0.5 if (a, b) == ("S_C", "C_S_H") else None, "second_scale_anchors": ["MIN01"] if (a, b) == ("S_C", "C_S_H") else []} for a, b in PAIRS], "validation_labels_used": False}
    (HERE / "CURVATURE_PAIR_PANEL.json").write_text(json.dumps(panel, indent=2, sort_keys=True) + "\n")
    displacement = {"schema": "tsl-rsh-curvature-displacement-protocol-v1", "steps": {key: {"full_scale": value, "half_scale": value / 2.0, "unit": BY_ID[key]["unit"]} for key, value in STEPS.items()}, "justification": {"bond_steps": "0.010-0.015 A are <1% of the 1.35-1.86 A equilibrium bonds; conventional 60-300 kcal/mol/A^2 curvature predicts 0.006-0.07 kcal/mol signals, over 10^4 times the 5.17e-10 Ha CPU/GPU energy discrepancy", "angle_step": "1.5 degrees is locally small; conventional angle curvature predicts ~0.02-0.1 kcal/mol signals", "torsion_step": "3 degrees is locally small relative to the observed conformational spread while producing gradient secants well above ~1e-12 Ha/bohr GPU reproducibility", "second_scale": "0.5 scale for S_C/C_S_H at MIN01 is frozen now to diagnose finite-difference scale dependence"}, "construction": "iterative minimum-Cartesian-norm Newton solve over all six monitored internal coordinates; selected coordinates displaced, other five/six constrained to anchor values", "acceptance": {"target_displacement_error_max": 2e-7, "non_target_coordinate_drift_max": 2e-6, "minimum_interatomic_distance_angstrom": 0.70, "connectivity": "same covalent-radius graph as anchor", "chirality": "sign of every nondegenerate tetrahedral carbon volume preserved"}, "raw_factorial_count": 76, "single_coordinate_points": 0, "single_coordinate_reason": "mixed energy curvature uses four corners; gradient/secant estimator uses corner gradients and no additional single-coordinate labels are mathematically required", "anchor_points": 0, "anchor_reason": "trusted anchor energies/gradients already exist; this preparation creates only new displaced geometries"}
    (HERE / "DISPLACEMENT_PROTOCOL.json").write_text(json.dumps(displacement, indent=2, sort_keys=True) + "\n")

    rows = []; audit_rows = []; rejected = []; geometries = []
    plans = [(anchor, pair, 1.0) for anchor in ANCHORS for pair in PAIRS]
    plans.append(("MIN01", ("S_C", "C_S_H"), 0.5))
    raw_count = 0
    for anchor, pair, scale in plans:
        data = anchor_data[anchor]; base = data["xyz"]; base_values = data["values"]; base_bonds = connectivity(data["elements"], base); base_chiral = chiral_signatures(data["elements"], base, base_bonds)
        for first_sign, second_sign in ((1, 1), (1, -1), (-1, 1), (-1, -1)):
            raw_count += 1
            target = base_values.copy()
            indices = [next(i for i, d in enumerate(COORDINATES)
                            if d["coordinate_id"] == coordinate)
                       for coordinate in pair]
            target[indices] += [first_sign * scale * STEPS[pair[0]],
                                second_sign * scale * STEPS[pair[1]]]
            try:
                xyz, iterations, solver_error = construct(base, target); realized = difference(vector(xyz), base_values)
                target_error = max(abs(realized[indices[0]] - first_sign * scale * STEPS[pair[0]]), abs(realized[indices[1]] - second_sign * scale * STEPS[pair[1]]))
                non_target = max((abs(realized[i]) for i in range(len(COORDINATES)) if i not in indices), default=0.0)
                bonds = connectivity(data["elements"], xyz); chiral = chiral_signatures(data["elements"], xyz, bonds)
                chirality = set(chiral) == set(base_chiral) and all(chiral[key] * base_chiral[key] > 0 for key in chiral)
                min_distance = minimum_distance(xyz); connectivity_ok = bonds == base_bonds
                accepted = target_error <= 2e-7 and non_target <= 2e-6 and min_distance >= 0.70 and connectivity_ok and chirality
                if not accepted: raise ValueError(f"acceptance failed target={target_error} drift={non_target} min={min_distance} connectivity={connectivity_ok} chirality={chirality}")
                campaign_id = f"CURV-{anchor}-{pair[0]}-{pair[1]}-S{str(scale).replace('.', 'P')}-{('P' if first_sign > 0 else 'M')}{('P' if second_sign > 0 else 'M')}"
                path = GEOMETRIES / f"{campaign_id}.xyz"; write_xyz(path, data["elements"], xyz, f"{campaign_id}; anchor={anchor}; pair={pair}; scale={scale}")
                digest = sha256(path)
                if any(np.sqrt(np.mean((xyz - previous_xyz) ** 2)) <= 1e-8 for previous_xyz in geometries): raise ValueError("duplicate geometry detected")
                geometries.append(xyz)
                row = {"campaign_id": campaign_id, "anchor_id": anchor, "pair_id": f"{pair[0]}__{pair[1]}", "scale": scale, "first_sign": first_sign, "second_sign": second_sign, "first_target_delta": first_sign * scale * STEPS[pair[0]], "second_target_delta": second_sign * scale * STEPS[pair[1]], "geometry_path": str(path.relative_to(HERE)), "geometry_sha256": digest, "anchor_geometry_sha256": data["sha256"], "atom_order_sha256": data["atom_order_sha256"], "charge": 0, "multiplicity": 1, "target_displacement_error": target_error, "non_target_coordinate_drift": non_target, "minimum_interatomic_distance_angstrom": min_distance, "connectivity_preserved": True, "chirality_preserved": True}
                rows.append(row); audit_rows.append({**row, "newton_iterations": iterations, "solver_final_error": solver_error, "realized_coordinate_changes": {definition["coordinate_id"]: float(realized[i]) for i, definition in enumerate(COORDINATES)}, "max_atom_displacement_angstrom": float(np.max(np.linalg.norm(xyz - base, axis=1))), "rms_atom_displacement_angstrom": float(np.sqrt(np.mean((xyz - base) ** 2)))})
            except Exception as error:
                rejected.append({"anchor": anchor, "pair": pair, "scale": scale, "signs": [first_sign, second_sign], "reason": str(error)})
    if raw_count != 76 or len(rows) != 76 or rejected:
        raise ValueError(f"curvature panel incomplete: raw={raw_count}, accepted={len(rows)}, rejected={rejected}")
    with (HERE / "CURVATURE_GEOMETRY_MANIFEST.csv").open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0].keys()), lineterminator="\n"); writer.writeheader(); writer.writerows(rows)
    audit = {"schema": "tsl-rsh-curvature-geometry-construction-audit-v1", "raw_factorial_geometry_count": raw_count, "unique_geometry_count": len(rows), "rejected_geometry_count": len(rejected), "rejected": rejected, "connectivity_check_pass": all(row["connectivity_preserved"] for row in rows), "chirality_check_pass": all(row["chirality_preserved"] for row in rows), "non_target_drift_check_pass": all(row["non_target_coordinate_drift"] <= 2e-6 for row in rows), "duplicate_check_pass": len(rows) == len({row["geometry_sha256"] for row in rows}), "maximum_target_displacement_error": max(row["target_displacement_error"] for row in rows), "maximum_non_target_coordinate_drift": max(row["non_target_coordinate_drift"] for row in rows), "minimum_interatomic_distance_angstrom": min(row["minimum_interatomic_distance_angstrom"] for row in rows), "count_note": "76 is the irreducible 72 core corners plus four preregistered half-scale MIN01 S_C/C_S_H corners; one point above the approximate 75 target avoids dropping a core anchor/pair or the required scale diagnostic", "points": audit_rows}
    (HERE / "GEOMETRY_CONSTRUCTION_AUDIT.json").write_text(json.dumps(audit, indent=2, sort_keys=True) + "\n")
    print(json.dumps({key: audit[key] for key in ("raw_factorial_geometry_count", "unique_geometry_count", "rejected_geometry_count", "connectivity_check_pass", "chirality_check_pass", "non_target_drift_check_pass", "duplicate_check_pass", "maximum_target_displacement_error", "maximum_non_target_coordinate_drift", "minimum_interatomic_distance_angstrom")}, indent=2))


if __name__ == "__main__": main()
