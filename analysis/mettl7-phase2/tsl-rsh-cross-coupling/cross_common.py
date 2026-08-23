#!/usr/bin/env python3
"""Invariant internal coordinates and conservative cross-term basis for GPU-60."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
REPRESENTABILITY = ROOT / "analysis/mettl7-phase2/tsl-rsh-representability"
sys.path.insert(0, str(REPRESENTABILITY))
from classical_common import (  # noqa: E402
    LOCAL_ATOMS, PARAMETER_NAMES as ADDITIVE_NAMES, energy_features as additive_energy_features,
    force_features as additive_force_features, load_qm, split,
)

COORDINATES = (
    {"id": "R_SC", "kind": "distance", "atoms_zero_based": [9, 25], "atoms_one_based": [10, 26], "unit": "angstrom"},
    {"id": "R_SH", "kind": "distance", "atoms_zero_based": [25, 55], "atoms_one_based": [26, 56], "unit": "angstrom"},
    {"id": "THETA_CSH", "kind": "angle", "atoms_zero_based": [9, 25, 55], "atoms_one_based": [10, 26, 56], "unit": "radian"},
    {"id": "THETA_C9_C10_S", "kind": "angle", "atoms_zero_based": [8, 9, 25], "atoms_one_based": [9, 10, 26], "unit": "radian"},
    {"id": "THETA_C11_C10_S", "kind": "angle", "atoms_zero_based": [10, 9, 25], "atoms_one_based": [11, 10, 26], "unit": "radian"},
    {"id": "THETA_C8_C9_C10", "kind": "angle", "atoms_zero_based": [7, 8, 9], "atoms_one_based": [8, 9, 10], "unit": "radian"},
    {"id": "CHI", "kind": "torsion", "atoms_zero_based": [55, 25, 9, 8], "atoms_one_based": [56, 26, 10, 9], "unit": "radian", "embedding": ["sin", "cos"]},
    {"id": "PHI", "kind": "torsion", "atoms_zero_based": [25, 9, 8, 7], "atoms_one_based": [26, 10, 9, 8], "unit": "radian", "embedding": ["sin", "cos"]},
    {"id": "PSI", "kind": "torsion", "atoms_zero_based": [9, 8, 7, 1], "atoms_one_based": [10, 9, 8, 2], "unit": "radian", "embedding": ["sin", "cos"]},
    {"id": "ETA1", "kind": "torsion", "atoms_zero_based": [25, 9, 8, 13], "atoms_one_based": [26, 10, 9, 14], "unit": "radian", "embedding": ["sin", "cos"]},
    {"id": "ETA2", "kind": "torsion", "atoms_zero_based": [25, 9, 8, 24], "atoms_one_based": [26, 10, 9, 25], "unit": "radian", "embedding": ["sin", "cos"]},
    {"id": "D_S_O6", "kind": "distance", "atoms_zero_based": [25, 5], "atoms_one_based": [26, 6], "unit": "angstrom"},
    {"id": "D_S_O23", "kind": "distance", "atoms_zero_based": [25, 22], "atoms_one_based": [26, 23], "unit": "angstrom"},
    {"id": "D_S_O24", "kind": "distance", "atoms_zero_based": [25, 23], "atoms_one_based": [26, 24], "unit": "angstrom"},
)
COORDINATE_BY_ID = {item["id"]: item for item in COORDINATES}
MANDATORY_PAIRS = (
    ("R_SC", "THETA_CSH"), ("R_SH", "THETA_CSH"), ("R_SC", "CHI"),
    ("R_SC", "PHI"), ("R_SH", "CHI"), ("THETA_CSH", "CHI"), ("PHI", "PSI"),
)


def wrapped_difference(a: float, b: float) -> float:
    return math.atan2(math.sin(a - b), math.cos(a - b))


def coordinate_value(xyz: np.ndarray, definition: dict[str, object]) -> float:
    atoms = [xyz[i] for i in definition["atoms_zero_based"]]
    if definition["kind"] == "distance":
        return float(np.linalg.norm(atoms[0] - atoms[1]))
    if definition["kind"] == "angle":
        left, right = atoms[0] - atoms[1], atoms[2] - atoms[1]
        cosine = np.dot(left, right) / np.linalg.norm(left) / np.linalg.norm(right)
        return float(math.acos(np.clip(cosine, -1.0, 1.0)))
    p0, p1, p2, p3 = atoms
    b0, b1, b2 = -(p1 - p0), p2 - p1, p3 - p2
    b1 = b1 / np.linalg.norm(b1)
    v, w = b0 - np.dot(b0, b1) * b1, b2 - np.dot(b2, b1) * b1
    return float(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def coordinate_gradient(xyz: np.ndarray, definition: dict[str, object], step: float = 1.0e-5) -> np.ndarray:
    gradient = np.zeros_like(xyz)
    for atom in definition["atoms_zero_based"]:
        for axis in range(3):
            plus, minus = xyz.copy(), xyz.copy()
            plus[atom, axis] += step
            minus[atom, axis] -= step
            p, m = coordinate_value(plus, definition), coordinate_value(minus, definition)
            delta = wrapped_difference(p, m) if definition["kind"] == "torsion" else p - m
            gradient[atom, axis] = delta / (2.0 * step)
    return gradient


def coordinate_vector(xyz: np.ndarray) -> np.ndarray:
    return np.asarray([coordinate_value(xyz, item) for item in COORDINATES])


def coordinate_jacobian(xyz: np.ndarray) -> np.ndarray:
    return np.stack([coordinate_gradient(xyz, item).reshape(-1) for item in COORDINATES])


def generalized_force(force: np.ndarray, jacobian: np.ndarray) -> tuple[np.ndarray, float, int]:
    """Least-norm generalized force Q solving J.T Q ~= F in the coordinate span."""
    q, _, rank, singular = np.linalg.lstsq(jacobian.T, force.reshape(-1), rcond=1.0e-10)
    reconstruction = jacobian.T @ q
    explained = 1.0 - float(np.sum((force.reshape(-1) - reconstruction) ** 2) / np.sum(force ** 2))
    condition = float(singular[0] / singular[-1]) if len(singular) and singular[-1] > 0 else float("inf")
    return q, explained, int(rank)


def additive_parameters() -> np.ndarray:
    artifact = json.loads((REPRESENTABILITY / "FIT_ARTIFACT/fit-artifact.json").read_text())
    if artifact["parameterNames"] != ADDITIVE_NAMES:
        raise ValueError("frozen additive parameter ordering mismatch")
    return np.asarray(artifact["finalParameterVector"], dtype=float)


def additive_prediction(artifact_id: str, baseline: dict[str, object], parameters: np.ndarray):
    qm_energy, qm_force, xyz = load_qm(artifact_id)
    base = baseline[artifact_id]
    energy = float(base["energy_kcal_mol"]) + float(additive_energy_features(xyz) @ parameters)
    force = np.asarray(base["force_kcal_mol_angstrom"]) + np.tensordot(additive_force_features(xyz), parameters, axes=(2, 0))
    return qm_energy, qm_force, xyz, energy, force


def circular_center(values: np.ndarray) -> float:
    return float(math.atan2(np.mean(np.sin(values)), np.mean(np.cos(values))))


def centered_value(value: float, coordinate_id: str, centers: dict[str, float]) -> float:
    definition = COORDINATE_BY_ID[coordinate_id]
    return wrapped_difference(value, centers[coordinate_id]) if definition["kind"] == "torsion" else value - centers[coordinate_id]


def pair_term_definitions(pair: tuple[str, str], centers: dict[str, float]) -> list[dict[str, object]]:
    left, right = pair
    left_torsion = COORDINATE_BY_ID[left]["kind"] == "torsion"
    right_torsion = COORDINATE_BY_ID[right]["kind"] == "torsion"
    if not left_torsion and not right_torsion:
        return [{"name": f"{left}__{right}__BILINEAR", "pair": [left, right], "form": "bilinear"}]
    if left_torsion and right_torsion:
        return [{"name": f"{left}__{right}__{a.upper()}_{b.upper()}", "pair": [left, right], "form": "torsion_product", "left_embedding": a, "right_embedding": b} for a in ("sin", "cos") for b in ("sin", "cos")]
    linear, torsion = (right, left) if left_torsion else (left, right)
    return [{"name": f"{linear}__{torsion}__CENTERED_{embedding.upper()}_N{n}", "pair": [linear, torsion], "form": "linear_torsion", "embedding": embedding, "periodicity": n} for n in (1, 2) for embedding in ("cos", "sin")]


def term_energy(xyz: np.ndarray, term: dict[str, object], centers: dict[str, float]) -> float:
    left, right = term["pair"]
    ql = coordinate_value(xyz, COORDINATE_BY_ID[left])
    qr = coordinate_value(xyz, COORDINATE_BY_ID[right])
    if term["form"] == "bilinear":
        return centered_value(ql, left, centers) * centered_value(qr, right, centers)
    if term["form"] == "torsion_product":
        a = math.sin(ql) if term["left_embedding"] == "sin" else math.cos(ql)
        b = math.sin(qr) if term["right_embedding"] == "sin" else math.cos(qr)
        return a * b
    linear, torsion = left, right
    q_linear, q_torsion = ql, qr
    embedding = math.cos(term["periodicity"] * q_torsion) if term["embedding"] == "cos" else math.sin(term["periodicity"] * q_torsion)
    return centered_value(q_linear, linear, centers) * embedding


def cross_energy_features(xyz: np.ndarray, terms: list[dict[str, object]], centers: dict[str, float]) -> np.ndarray:
    return np.asarray([1.0] + [term_energy(xyz, term, centers) for term in terms])


def cross_force_features(xyz: np.ndarray, terms: list[dict[str, object]], centers: dict[str, float], step: float = 1.0e-5) -> np.ndarray:
    result = np.zeros((len(xyz), 3, 1 + len(terms)))
    active = sorted({atom for term in terms for coordinate_id in term["pair"] for atom in COORDINATE_BY_ID[coordinate_id]["atoms_zero_based"]})
    for atom in active:
        for axis in range(3):
            plus, minus = xyz.copy(), xyz.copy()
            plus[atom, axis] += step
            minus[atom, axis] -= step
            result[atom, axis] = -(cross_energy_features(plus, terms, centers) - cross_energy_features(minus, terms, centers)) / (2.0 * step)
    return result


def rms(values: np.ndarray) -> float:
    return float(np.sqrt(np.mean(np.asarray(values, dtype=float) ** 2)))
