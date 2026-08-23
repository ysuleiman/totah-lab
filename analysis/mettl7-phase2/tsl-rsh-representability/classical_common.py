#!/usr/bin/env python3
"""Shared geometry and conservative additive-basis definitions."""

from __future__ import annotations

import hashlib
import json
import math
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
EVIDENCE = ROOT / "analysis/mettl7-phase2/tsl-rsh-trusted-evidence"
CAMPAIGN = ROOT / "analysis/mettl7-phase2/execution-unit-05O/gpu-qm-campaign"
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
TOPOLOGY = ROOT / "analysis/mettl7-phase2/execution-unit-05O/model-form-analysis/baseline/baseline.parm7"
BASELINE_IDENTITY = ROOT / "analysis/mettl7-phase2/execution-unit-05O/model-form-analysis/BASELINE_IDENTITY.json"
HARTREE_TO_KCAL_MOL = 627.5094740631
BOHR_TO_ANGSTROM = 0.529177210903
LOCAL_ATOMS = np.array([1, 7, 8, 9, 10, 25, 36, 55], dtype=int)
BONDS = ((9, 25, "S_C"), (25, 55, "S_H"))
ANGLES = ((8, 9, 25, "ANGLE_9_10_26"), (10, 9, 25, "ANGLE_11_10_26"), (9, 25, 55, "ANGLE_10_26_56"))
TORSIONS = ((55, 25, 9, 8, "CHI_SHSC"), (25, 9, 8, 7, "PHI_SC"), (9, 8, 7, 1, "PSI_BACKBONE"))
PARAMETER_NAMES = ["ENERGY_REFERENCE_OFFSET"]
PARAMETER_UNITS = ["kcal/mol"]
for _, _, name in BONDS:
    PARAMETER_NAMES.extend((name + "_LINEAR", name + "_QUADRATIC"))
    PARAMETER_UNITS.extend(("kcal/mol/angstrom", "kcal/mol/angstrom^2"))
for *_, name in ANGLES:
    PARAMETER_NAMES.extend((name + "_LINEAR", name + "_QUADRATIC"))
    PARAMETER_UNITS.extend(("kcal/mol/radian", "kcal/mol/radian^2"))
for *_, name in TORSIONS:
    for periodicity in range(1, 4):
        PARAMETER_NAMES.extend((f"{name}_COS_N{periodicity}", f"{name}_SIN_N{periodicity}"))
        PARAMETER_UNITS.extend(("kcal/mol", "kcal/mol"))


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def geometry(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines()
    count = int(lines[0])
    rows = [line.split() for line in lines[2:] if line.strip()]
    if len(rows) != count:
        raise ValueError(f"atom-count mismatch in {path}")
    return [row[0] for row in rows], np.array([[float(x) for x in row[1:4]] for row in rows])


def angle(coordinates: np.ndarray, atoms: tuple[int, int, int]) -> float:
    a, b, c = (coordinates[index] for index in atoms)
    left, right = a - b, c - b
    cosine = np.dot(left, right) / np.linalg.norm(left) / np.linalg.norm(right)
    return float(math.acos(np.clip(cosine, -1.0, 1.0)))


def dihedral(coordinates: np.ndarray, atoms: tuple[int, int, int, int]) -> float:
    p0, p1, p2, p3 = (coordinates[index] for index in atoms)
    b0, b1, b2 = -(p1 - p0), p2 - p1, p3 - p2
    b1 = b1 / np.linalg.norm(b1)
    v = b0 - np.dot(b0, b1) * b1
    w = b2 - np.dot(b2, b1) * b1
    return float(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def energy_features(coordinates: np.ndarray) -> np.ndarray:
    values = [1.0]
    for first, second, _ in BONDS:
        coordinate = float(np.linalg.norm(coordinates[first] - coordinates[second]))
        values.extend((coordinate, coordinate * coordinate))
    for first, center, last, _ in ANGLES:
        coordinate = angle(coordinates, (first, center, last))
        values.extend((coordinate, coordinate * coordinate))
    for first, second, third, fourth, _ in TORSIONS:
        coordinate = dihedral(coordinates, (first, second, third, fourth))
        for periodicity in range(1, 4):
            values.extend((math.cos(periodicity * coordinate), math.sin(periodicity * coordinate)))
    result = np.asarray(values)
    if result.shape != (len(PARAMETER_NAMES),) or not np.isfinite(result).all():
        raise ValueError("invalid additive energy feature vector")
    return result


def force_features(coordinates: np.ndarray, step: float = 1.0e-5) -> np.ndarray:
    """Return -d(feature energy)/dx in 1/angstrom using central differences."""
    result = np.zeros((len(coordinates), 3, len(PARAMETER_NAMES)))
    for atom in LOCAL_ATOMS:
        for axis in range(3):
            plus, minus = coordinates.copy(), coordinates.copy()
            plus[atom, axis] += step
            minus[atom, axis] -= step
            result[atom, axis] = -(energy_features(plus) - energy_features(minus)) / (2.0 * step)
    if not np.isfinite(result).all():
        raise ValueError("invalid additive force feature tensor")
    return result


def load_qm(artifact_id: str) -> tuple[float, np.ndarray, np.ndarray]:
    directory = RESULTS / artifact_id
    result = json.loads((directory / "result.json").read_text())
    _, coordinates = geometry(directory / "geometry.xyz")
    energy = float(result["total_energy_hartree"]) * HARTREE_TO_KCAL_MOL
    force = np.asarray(result["force_hartree_per_bohr"], dtype=float) * HARTREE_TO_KCAL_MOL / BOHR_TO_ANGSTROM
    if force.shape != (56, 3) or not np.isfinite(force).all():
        raise ValueError(f"invalid QM force for {artifact_id}")
    return energy, force, coordinates


def split() -> dict[str, object]:
    return json.loads((EVIDENCE / "FROZEN_TRAIN_VALIDATION_SPLIT.json").read_text())
