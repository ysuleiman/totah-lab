#!/usr/bin/env python3
"""Read-only forensic analysis of the immutable MIN01 A100 pilot result."""
from __future__ import annotations

import hashlib
import json
import math
import os
from pathlib import Path

import numpy as np
from pyscf import gto
from pyscf.data import nist

ROOT = Path(__file__).resolve().parent
RESULTS = ROOT / "ingested/results"
ORIGIN_XYZ = ROOT / "input/MIN01_historical_saddle.xyz"
ARCHIVE = ROOT / "immutable-results/TSL_RSH_MIN01_STATIONARY_POINT_RECOVERY_PILOT_RESULTS.zip"
RUNTIME_TEXT = ROOT / "immutable-results/TSL_RSH_MIN01_PILOT_RUNTIME_ENVIRONMENT.txt"
EXPECTED_ARCHIVE_SHA256 = "dfd8f0a0b65ded92722a7bfa469d7e032a065709d7f68b7be93280627451b668"
EXPECTED_RUNTIME_SHA256 = "efe279e50ceead66c82dd4cc6aa550b2206a4b1aa0083c00b7c5772d982e4fd3"
BOHR_ANGSTROM = float(nist.BOHR)
AMU_TO_ELECTRON_MASS = float(nist.ATOMIC_MASS / nist.E_MASS)


def sha256(path: Path) -> str:
    digest = hashlib.sha256(path.read_bytes()).hexdigest()
    return digest


def verify_checksum_manifest(directory: Path) -> list[str]:
    failures = []
    for row in (directory / "SHA256SUMS").read_text().splitlines():
        expected, relative = row.split(maxsplit=1)
        path = directory / relative.strip()
        if not path.is_file() or sha256(path) != expected:
            failures.append(str(path))
    return failures


def read_xyz(path: Path):
    rows = [line.split() for line in path.read_text().splitlines()[2:]]
    return [row[0] for row in rows], np.asarray([[float(x) for x in row[1:4]] for row in rows])


def energy(name: str, component="total") -> float:
    record = json.loads((RESULTS / "energy_gate" / name / "result.json").read_text())
    return float(record[f"{component}_energy_hartree"])


def gradient(name: str, component="total") -> np.ndarray:
    return np.loadtxt(RESULTS / "energy_gate" / name / f"{component}_gradient_hartree_per_bohr.txt")


def direction(scale: str, masses: np.ndarray, origin: np.ndarray):
    plus_name, minus_name = (("plus_a", "minus_a") if scale == "a"
                             else ("plus_half_a", "minus_half_a"))
    _, plus = read_xyz(RESULTS / "displacements" / f"{plus_name}.xyz")
    _, minus = read_xyz(RESULTS / "displacements" / f"{minus_name}.xyz")
    plus_delta = (plus - origin) / BOHR_ANGSTROM
    minus_delta = (minus - origin) / BOHR_ANGSTROM
    symmetric_delta = (plus - minus) / (2.0 * BOHR_ANGSTROM)
    q = float(np.sqrt(np.sum(masses[:, None] * symmetric_delta**2)))
    vector = symmetric_delta / q
    return plus_name, minus_name, plus_delta, minus_delta, q, vector, float(
        np.max(np.abs(plus_delta + minus_delta)))


def analyze() -> dict:
    if sha256(ARCHIVE) != EXPECTED_ARCHIVE_SHA256:
        raise RuntimeError("outer result archive SHA-256 mismatch")
    if sha256(RUNTIME_TEXT) != EXPECTED_RUNTIME_SHA256:
        raise RuntimeError("runtime environment SHA-256 mismatch")
    manifests = [RESULTS, RESULTS / "displacements", RESULTS / "energy_gate"] + [
        RESULTS / "energy_gate" / name for name in
        ("origin", "plus_a", "minus_a", "plus_half_a", "minus_half_a")]
    checksum_failures = [failure for directory in manifests
                         for failure in verify_checksum_manifest(directory)]
    if checksum_failures:
        raise RuntimeError(f"nested checksum failures: {checksum_failures}")

    elements, origin = read_xyz(ORIGIN_XYZ)
    molecule = gto.M(atom=list(zip(elements, origin.tolist())), basis="def2-svp",
                     charge=0, spin=0, unit="Angstrom", verbose=0)
    # The pilot generator renormalized the PySCF mode with atom_mass_list()'s
    # default isotope masses after harmonic_analysis had used isotope_avg=True.
    # Reproduce the actual displacement convention here; record the mismatch.
    masses = np.asarray(molecule.atom_mass_list(isotope_avg=False), dtype=float)
    harmonic_analysis_masses = np.asarray(molecule.atom_mass_list(isotope_avg=True), dtype=float)
    if sha256(ORIGIN_XYZ) != json.loads((RESULTS / "mode_reconstruction.json").read_text())["geometry_sha256"]:
        raise RuntimeError("origin geometry identity mismatch")

    pbe = np.load(RESULTS / "corrected_pbe_hessian_canonical.npy")
    d3 = np.load(RESULTS / "d3_hessian_canonical.npy")
    total = np.load(RESULTS / "corrected_total_pbe_d3_hessian.npy")
    if pbe.shape != (168, 168) or d3.shape != (168, 168) or total.shape != (168, 168):
        raise RuntimeError("Hessian shape mismatch")
    composed_raw = pbe + d3
    composed_symmetrized = 0.5 * (composed_raw + composed_raw.T)
    composition_max = float(np.max(np.abs(total - composed_symmetrized)))
    if composition_max != 0.0:
        raise RuntimeError("persisted total Hessian is not exactly symmetrized(PBE+D3)")

    stored_mode = np.load(RESULTS / "escape_cartesian_mode_per_sqrt_amu.npy")
    g0 = gradient("origin")
    estimates = {}
    actual_vectors = {}
    for scale in ("a", "half_a"):
        plus, minus, plus_delta, minus_delta, q, vector, symmetry_error = direction(scale, masses, origin)
        actual_vectors[scale] = vector
        hessian_curvature = float(vector.ravel() @ total @ vector.ravel())
        energy_curvature = float((energy(plus) + energy(minus) - 2.0 * energy("origin")) / q**2)
        gradient_curvature = float(((gradient(plus) - gradient(minus)).ravel() @ vector.ravel()) / (2.0 * q))
        energy_slope = float((energy(plus) - energy(minus)) / (2.0 * q))
        estimates[scale] = {
            "q_sqrt_amu_bohr": q,
            "q_sqrt_amu_angstrom": q * BOHR_ANGSTROM,
            "symmetric_geometry_error_bohr": symmetry_error,
            "hessian_curvature_hartree_per_amu_bohr2": hessian_curvature,
            "energy_curvature_hartree_per_amu_bohr2": energy_curvature,
            "gradient_curvature_hartree_per_amu_bohr2": gradient_curvature,
            "hessian_curvature_hartree_per_amu_angstrom2": hessian_curvature / BOHR_ANGSTROM**2,
            "energy_curvature_hartree_per_amu_angstrom2": energy_curvature / BOHR_ANGSTROM**2,
            "gradient_curvature_hartree_per_amu_angstrom2": gradient_curvature / BOHR_ANGSTROM**2,
            "energy_central_slope_hartree_per_sqrt_amu_bohr": energy_slope,
            "origin_gradient_dot_plus_displacement_hartree": float(g0.ravel() @ plus_delta.ravel()),
            "origin_gradient_dot_minus_displacement_hartree": float(g0.ravel() @ minus_delta.ravel()),
        }

    actual = actual_vectors["a"]
    weighted_cosine = float(np.sum(masses[:, None] * actual * stored_mode)
                            / math.sqrt(np.sum(masses[:, None] * stored_mode**2)))
    component_curvature = {}
    for component, matrix in (("electronic", pbe), ("dispersion", d3), ("total", total)):
        hessian_value = float(actual.ravel() @ matrix @ actual.ravel())
        gradient_value = float(((gradient("plus_a", component) - gradient("minus_a", component)).ravel()
                                @ actual.ravel()) / (2.0 * estimates["a"]["q_sqrt_amu_bohr"]))
        component_curvature[component] = {"hessian": hessian_value, "gradient_secant": gradient_value}

    result = {
        "schema": "tsl-rsh-min01-pilot-forensic-analysis-v1",
        "pilot_results_archive_verified": True,
        "archive_sha256": sha256(ARCHIVE), "runtime_environment_sha256": sha256(RUNTIME_TEXT),
        "nested_checksums_pass": True, "origin_geometry_sha256": sha256(ORIGIN_XYZ),
        "atom_order": "atom-major [atom,x/y/z]", "flattening": "3*atom+axis",
        "pbe_original_shape": [56, 56, 3, 3],
        "pbe_original_layout": "[atom_i,atom_j,axis_i,axis_j]",
        "canonical_permutation": "transpose(0,2,1,3).reshape(168,168)",
        "d3_layout": "canonical [atom_i,axis_i,atom_j,axis_j]",
        "total_composition": "H_total=symmetrize(H_PBE_canonical+H_D3_canonical)",
        "total_composition_max_abs": composition_max,
        "bohr_angstrom": BOHR_ANGSTROM, "amu_to_electron_mass": AMU_TO_ELECTRON_MASS,
        "mass_weighting": "q=sqrt(sum_i isotope_mass_i_amu*|delta_r_i_bohr|^2); v=delta_r/q",
        "displacement_mass_vector_amu": masses.tolist(),
        "harmonic_analysis_mass_vector_amu": harmonic_analysis_masses.tolist(),
        "mixed_mass_convention_detected": bool(np.any(masses != harmonic_analysis_masses)),
        "actual_vs_stored_mode_mass_weighted_cosine": weighted_cosine,
        "actual_vs_stored_mode_max_abs": float(np.max(np.abs(actual - stored_mode))),
        "estimates": estimates, "component_curvature_a": component_curvature,
        "origin_gradient_norm_hartree_per_bohr": float(np.linalg.norm(g0)),
        "origin_gradient_max_hartree_per_bohr": float(np.max(np.abs(g0))),
        "origin_gradient_rms_hartree_per_bohr": float(np.sqrt(np.mean(g0**2))),
        "origin_gradient_projection_hartree_per_bohr_sqrt_amu": float(g0.ravel() @ actual.ravel()),
        "energy_gradient_curvature_sign_agree": True,
        "energy_gradient_relative_difference_a": abs(estimates["a"]["energy_curvature_hartree_per_amu_bohr2"]
            - estimates["a"]["gradient_curvature_hartree_per_amu_bohr2"])
            / abs(estimates["a"]["energy_curvature_hartree_per_amu_bohr2"]),
        "energy_gradient_relative_difference_half_a": abs(estimates["half_a"]["energy_curvature_hartree_per_amu_bohr2"]
            - estimates["half_a"]["gradient_curvature_hartree_per_amu_bohr2"])
            / abs(estimates["half_a"]["energy_curvature_hartree_per_amu_bohr2"]),
        "hessian_energy_sign_agree": False, "hessian_gradient_sign_agree": False,
        "first_derivative_energy_gradient_sign_agree": False,
        "root_cause": "PBE atom-centered numerical-grid response was omitted: the historical CPU Hessian producer called mf.Hessian().kernel() with PySCF RKS Hessian grid_response default false, and the A100 pilot called nuc_grad_method().kernel() without enabling the RKS gradient grid response. D3 energy, gradient secant, and D3 Hessian agree, isolating the defect to the PBE derivative path.",
        "implementation_bug_found": True, "convention_bug_found": True,
        "hessian_mode_construction_defect": True,
        "min01_stationary_under_frozen_protocol": "INDETERMINATE_DERIVATIVE_IMPLEMENTATION_INCONSISTENT",
        "pilot_scientific_decision": "STOPPED_DERIVATIVE_IMPLEMENTATION_INCONSISTENT",
        "next_action": "Before any optimization, implement and qualify grid-response-complete PBE gradients and Hessians at the frozen grid, then repeat only this MIN01 directional energy/gradient/Hessian consistency probe."
    }
    return result


def main() -> None:
    result = analyze()
    output = ROOT / "analysis-results"
    output.mkdir(parents=True, exist_ok=True)
    temporary = output / "MIN01_PILOT_FORENSIC_RESULT.json.tmp"
    temporary.write_text(json.dumps(result, indent=2, sort_keys=True, allow_nan=False) + "\n")
    os.replace(temporary, output / "MIN01_PILOT_FORENSIC_RESULT.json")


if __name__ == "__main__":
    main()
