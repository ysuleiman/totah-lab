#!/usr/bin/env python3
"""Deterministic recovery only. This module contains no electronic-structure execution path."""
from __future__ import annotations

import hashlib
import io
import json
import os
import platform
import zipfile
from pathlib import Path

import numpy as np
from pyscf import gto
from pyscf.hessian import thermo

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "immutable-evidence/TSL_RSH_MIN01_STATIONARY_POINT_OPTIMIZATION_RESULTS.zip"
EXECUTED_PACKAGE = ROOT / "immutable-evidence/EXECUTED_PACKAGE.zip"
MASS_FILE = ROOT / "input/MASS_VECTOR.json"
MANIFEST = ROOT / "RECOVERY_MANIFEST.json"
OUTPUT = ROOT / "recovered-results"
EXPECTED_SOURCE_SHA256 = "da2e98b4bbc7ec2664aaa0d55294c1a61e62e112a4612d4fcfa2d5f7d5e67b51"
N_ATOMS, N_CART = 56, 168


def sha256_bytes(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def sha256(path: Path) -> str:
    return sha256_bytes(path.read_bytes())


def atomic_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(text)
    os.replace(temporary, path)


def atomic_json(path: Path, value) -> None:
    atomic_text(path, json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def atomic_array(path: Path, value) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("wb") as target:
        np.save(target, np.asarray(value))
    os.replace(temporary, path)


def atomic_matrix(path: Path, value) -> None:
    temporary = path.with_name(path.name + ".tmp")
    np.savetxt(temporary, np.asarray(value), fmt="%.17e")
    os.replace(temporary, path)


def write_checksums(directory: Path) -> None:
    paths = sorted(path for path in directory.rglob("*")
                   if path.is_file() and path.name not in {
                       "SHA256SUMS", "PUBLICATION_RECEIPT.json"})
    atomic_text(directory / "SHA256SUMS", "".join(
        f"{sha256(path)}  {path.relative_to(directory)}\n" for path in paths))


def verify_checksum_file(directory: Path, manifest: Path) -> int:
    checked = 0
    for row in manifest.read_text().splitlines():
        expected, relative = row.split(maxsplit=1)
        target = directory / relative.strip()
        if not target.is_file():
            raise RuntimeError(f"recovered artifact missing: {target}")
        if sha256(target) != expected:
            raise RuntimeError(f"recovered artifact checksum mismatch: {target}")
        checked += 1
    return checked


def verify_archive_checksums(archive: zipfile.ZipFile) -> int:
    manifests = sorted(name for name in archive.namelist() if name.endswith("SHA256SUMS"))
    if "results/SHA256SUMS" not in manifests:
        raise RuntimeError("source archive lacks root checksum manifest")
    checked = 0
    for manifest_name in manifests:
        directory = manifest_name.rsplit("/", 1)[0] + "/"
        for row in archive.read(manifest_name).decode().splitlines():
            expected, relative = row.split(maxsplit=1)
            target = directory + relative.strip()
            try:
                payload = archive.read(target)
            except KeyError as error:
                raise RuntimeError(f"checksum target missing: {target}") from error
            if sha256_bytes(payload) != expected:
                raise RuntimeError(f"checksum mismatch: {target}")
            checked += 1
    return checked


def npy(archive: zipfile.ZipFile, relative: str) -> np.ndarray:
    return np.load(io.BytesIO(archive.read("results/" + relative)), allow_pickle=False)


def parse_xyz(payload: bytes):
    lines = payload.decode().splitlines()
    rows = [line.split() for line in lines[2:]]
    if int(lines[0]) != N_ATOMS or len(rows) != N_ATOMS:
        raise RuntimeError("endpoint geometry is not 56 atoms")
    elements = [row[0] for row in rows]
    coordinates = np.asarray([[float(x) for x in row[1:4]] for row in rows])
    return elements, coordinates


def rigid_body_matrix(masses: np.ndarray, coordinates_bohr: np.ndarray) -> np.ndarray:
    contract = thermo._get_TR(masses, coordinates_bohr)
    if not isinstance(contract, tuple) or len(contract) != 6:
        raise RuntimeError("unexpected PySCF _get_TR return contract")
    vectors = [np.asarray(vector) for vector in contract]
    if any(vector.shape != (N_CART,) for vector in vectors):
        raise RuntimeError("invalid rigid-body vector dimension")
    return np.stack(vectors, axis=0)


def sign_invariant_mode_error(left: np.ndarray, right: np.ndarray) -> float:
    if left.shape != right.shape:
        raise RuntimeError("stored/recomputed mode shape mismatch")
    errors = []
    for stored, recomputed in zip(left, right):
        errors.append(min(np.max(np.abs(stored - recomputed)),
                          np.max(np.abs(stored + recomputed))))
    return float(max(errors))


def verify_executed_runner(runtime: dict) -> str:
    with zipfile.ZipFile(EXECUTED_PACKAGE) as package:
        candidates = [name for name in package.namelist()
                      if name.endswith("run_min01_stationary_optimization_a100.py")]
        if len(candidates) != 1:
            raise RuntimeError("executed package runner identity is ambiguous")
        runner_sha = sha256_bytes(package.read(candidates[0]))
    if runner_sha != runtime["runner_sha256"]:
        raise RuntimeError("runtime runner SHA does not match executed package")
    return runner_sha


def recover() -> dict:
    if sha256(SOURCE) != EXPECTED_SOURCE_SHA256:
        raise RuntimeError("source A100 results archive SHA-256 mismatch")
    manifest = json.loads(MANIFEST.read_text())
    tolerance = manifest["numerical_tolerances"]
    masses_record = json.loads(MASS_FILE.read_text())
    masses = np.asarray(masses_record["mass_vector_amu"], dtype=float)
    if masses.shape != (N_ATOMS,) or not np.isfinite(masses).all():
        raise RuntimeError("invalid frozen mass vector")
    with zipfile.ZipFile(SOURCE) as archive:
        checksum_count = verify_archive_checksums(archive)
        runtime = json.loads(archive.read("results/RUNTIME_ENVIRONMENT.json"))
        runner_sha = verify_executed_runner(runtime)
        optimization = json.loads(archive.read("results/optimization/OPTIMIZATION_RESULT.json"))
        audit = json.loads(archive.read(
            "results/endpoint_gradient_audit/ENDPOINT_GRADIENT_AUDIT.json"))
        failure = json.loads(archive.read("results/FAILURE.json"))
        geometry_payload = archive.read("results/stationary_point_qualification/geometry.xyz")
        elements, coordinates = parse_xyz(geometry_payload)
        hessian_members = {
            "electronic": "results/stationary_point_qualification/electronic_hessian_hartree_per_bohr2.npy",
            "dispersion": "results/stationary_point_qualification/dispersion_hessian_hartree_per_bohr2.npy",
            "total": "results/stationary_point_qualification/total_hessian_hartree_per_bohr2.npy",
        }
        hessian_payloads = {name: archive.read(member)
                            for name, member in hessian_members.items()}
        electronic = np.load(io.BytesIO(hessian_payloads["electronic"]), allow_pickle=False)
        dispersion = np.load(io.BytesIO(hessian_payloads["dispersion"]), allow_pickle=False)
        total = np.load(io.BytesIO(hessian_payloads["total"]), allow_pickle=False)
        stored_eigenvalues = np.loadtxt(io.BytesIO(archive.read(
            "results/stationary_point_qualification/signed_mass_weighted_eigenvalues_atomic_units.txt")))
        stored_frequencies = np.loadtxt(io.BytesIO(archive.read(
            "results/stationary_point_qualification/signed_frequencies_cm-1.txt")))
        stored_modes = npy(archive,
            "stationary_point_qualification/normal_modes_cartesian_per_sqrt_amu.npy")
        stored_mass_weighted_modes = npy(archive,
            "stationary_point_qualification/normal_modes_mass_weighted.npy")
    if failure.get("exception_type") != "AttributeError" or "tuple" not in failure.get("message", ""):
        raise RuntimeError("source failure is not the preregistered reporting defect")
    if optimization.get("status") != "CONVERGED" or audit.get("pass") is not True:
        raise RuntimeError("optimization/audit prerequisite did not complete")
    for name, matrix in (("electronic", electronic), ("dispersion", dispersion), ("total", total)):
        if matrix.shape != (N_CART, N_CART) or not np.isfinite(matrix).all():
            raise RuntimeError(f"invalid persisted {name} Hessian")
    composition_error = float(np.max(np.abs(total - (electronic + dispersion))))
    if composition_error > tolerance["Hessian_component_sum_absolute"]:
        raise RuntimeError("persisted Hessian components do not compose")
    mol = gto.M(atom=list(zip(elements, coordinates.tolist())), basis="def2-svp",
                charge=0, spin=0, unit="Angstrom", verbose=0)
    runtime_masses = np.asarray(mol.atom_mass_list(isotope_avg=True))
    if not np.array_equal(runtime_masses, masses):
        raise RuntimeError("endpoint runtime masses differ from frozen mass vector")
    total4 = total.reshape(N_ATOMS, 3, N_ATOMS, 3).transpose(0, 2, 1, 3)
    modes = thermo.harmonic_analysis(mol, total4, exclude_trans=True,
                                    exclude_rot=True, imaginary_freq=True, mass=masses)
    complex_frequencies = np.asarray(modes["freq_wavenumber"])
    frequencies = np.asarray([-abs(x.imag) if abs(x.imag) > 1e-12 else x.real
                              for x in complex_frequencies])
    eigenvalues = np.asarray(modes["force_const_au"])
    cartesian_modes = np.asarray(modes["norm_mode"])
    mass_weighted_modes = cartesian_modes * np.sqrt(masses)[None, :, None]
    if frequencies.shape != (N_CART - 6,) or cartesian_modes.shape != (N_CART - 6, N_ATOMS, 3):
        raise RuntimeError("recomputed projected mode dimensions are invalid")
    if not np.isfinite(frequencies).all() or np.any(frequencies == 0.0):
        raise RuntimeError("frequency/mode integrity failed")
    eigenvalue_error = float(np.max(np.abs(stored_eigenvalues - eigenvalues)))
    frequency_error = float(np.max(np.abs(stored_frequencies - frequencies)))
    mode_error = sign_invariant_mode_error(stored_modes, cartesian_modes)
    mass_weighted_mode_error = sign_invariant_mode_error(
        stored_mass_weighted_modes, mass_weighted_modes)
    if eigenvalue_error > tolerance["stored_recomputed_eigenvalue_absolute"]:
        raise RuntimeError("stored/recomputed eigenvalues disagree")
    if frequency_error > tolerance["stored_recomputed_frequency_absolute_cm-1"]:
        raise RuntimeError("stored/recomputed frequencies disagree")
    if max(mode_error, mass_weighted_mode_error) > tolerance[
            "stored_recomputed_mode_sign_invariant_max_absolute"]:
        raise RuntimeError("stored/recomputed modes disagree")
    rigid = rigid_body_matrix(masses, mol.atom_coords())
    rigid_rank = int(np.linalg.matrix_rank(rigid))
    if rigid.shape != (6, N_CART) or rigid_rank != 6:
        raise RuntimeError("rigid-body projection integrity failed")
    negative_count = int(np.sum(frequencies < 0.0))
    frequency_integrity = True
    hessian_complete = True
    publication_complete = True
    verified = (optimization["status"] == "CONVERGED" and audit["pass"]
                and hessian_complete and negative_count == 0 and frequency_integrity
                and publication_complete)
    OUTPUT.mkdir(parents=True, exist_ok=False)
    atomic_text(OUTPUT / "endpoint_geometry.xyz", geometry_payload.decode())
    atomic_array(OUTPUT / "total_hessian_hartree_per_bohr2.npy", total)
    atomic_matrix(OUTPUT / "signed_mass_weighted_eigenvalues_atomic_units.txt",
                  eigenvalues.reshape(-1, 1))
    atomic_matrix(OUTPUT / "signed_frequencies_cm-1.txt", frequencies.reshape(-1, 1))
    atomic_array(OUTPUT / "normal_modes_cartesian_per_sqrt_amu.npy", cartesian_modes)
    atomic_array(OUTPUT / "normal_modes_mass_weighted.npy", mass_weighted_modes)
    atomic_json(OUTPUT / "RIGID_BODY_PROJECTION_DIAGNOSTICS.json", {
        "api": "pyscf.hessian.thermo._get_TR", "return_contract": "tuple of six flattened 3N vectors",
        "stacked_shape": list(rigid.shape), "rank": rigid_rank,
        "singular_values": np.linalg.svd(rigid, compute_uv=False).tolist(),
        "projected_vibrational_dimension": len(frequencies),
        "mass_vector_sha256": sha256(MASS_FILE)})
    result = {
        "schema": "tsl-rsh-min01-stationary-recovery-result-v1",
        "optimization_converged": True, "endpoint_derivative_audit_pass": True,
        "hessian_components_complete": hessian_complete,
        "lowest_signed_frequency_cm-1": float(np.min(frequencies)),
        "negative_vibrational_mode_count": negative_count,
        "frequency_mode_integrity_pass": frequency_integrity,
        "min01_verified_minimum": verified,
        "publication_evidence_complete": publication_complete,
        "source_archive_sha256": sha256(SOURCE), "source_nested_checksums_verified": checksum_count,
        "executed_runner_sha256": runner_sha,
        "optimization_endpoint_geometry_sha256": optimization["endpoint_geometry_sha256"],
        "qualification_geometry_artifact_sha256": sha256_bytes(geometry_payload),
        "electronic_hessian_source_file_sha256": sha256_bytes(hessian_payloads["electronic"]),
        "dispersion_hessian_source_file_sha256": sha256_bytes(hessian_payloads["dispersion"]),
        "total_hessian_source_file_sha256": sha256_bytes(hessian_payloads["total"]),
        "Hessian_composition_max_abs": composition_error,
        "stored_recomputed_eigenvalue_max_abs": eigenvalue_error,
        "stored_recomputed_frequency_max_abs_cm-1": frequency_error,
        "stored_recomputed_mode_sign_invariant_max_abs": mode_error,
        "stored_recomputed_mass_weighted_mode_sign_invariant_max_abs": mass_weighted_mode_error,
        "mass_convention": masses_record["mass_convention"],
        "mass_vector_amu": masses.tolist(), "mass_vector_sha256": sha256(MASS_FILE),
        "expensive_qm_rerun": False, "SCF_run": False, "gradient_run": False,
        "endpoint_finite_difference_run": False, "electronic_hessian_run": False,
        "D3_hessian_run": False, "optimization_run": False,
        "MIN02_run": False, "MIN04_run": False, "model_fit_run": False,
        "runtime": platform.platform()}
    atomic_json(OUTPUT / "RECOVERY_RESULT.json", result)
    write_checksums(OUTPUT)
    recovered_count = verify_checksum_file(OUTPUT, OUTPUT / "SHA256SUMS")
    receipt = {
        "schema": "tsl-rsh-min01-stationary-recovery-receipt-v1",
        "status": "VERIFIED_AND_SEALED",
        "source_archive_sha256": sha256(SOURCE),
        "source_nested_checksums_verified": checksum_count,
        "executed_package_sha256": sha256(EXECUTED_PACKAGE),
        "executed_runner_sha256": runner_sha,
        "recovery_manifest_sha256": sha256(MANIFEST),
        "recovery_code_sha256": sha256(Path(__file__)),
        "recovered_artifact_manifest_sha256": sha256(OUTPUT / "SHA256SUMS"),
        "recovered_artifacts_verified": recovered_count,
        "recovery_result_sha256": sha256(OUTPUT / "RECOVERY_RESULT.json"),
        "publication_evidence_complete": verified,
        "expensive_qm_rerun": False,
        "optimization_rerun": False,
        "hessian_rerun": False,
    }
    atomic_json(OUTPUT / "PUBLICATION_RECEIPT.json", receipt)
    # Read back and independently reverify every binding before returning success.
    persisted_receipt = json.loads((OUTPUT / "PUBLICATION_RECEIPT.json").read_text())
    if persisted_receipt != receipt:
        raise RuntimeError("publication receipt read-back mismatch")
    if verify_checksum_file(OUTPUT, OUTPUT / "SHA256SUMS") != recovered_count:
        raise RuntimeError("publication receipt artifact verification mismatch")
    return result


if __name__ == "__main__":
    recover()
