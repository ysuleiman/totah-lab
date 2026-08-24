#!/usr/bin/env python3
"""Seal completed MIN02/MIN04 evidence. Contains no QM or model execution path."""
from __future__ import annotations

import hashlib
import io
import json
import os
import shutil
import zipfile
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
SOURCE_RESULTS = ROOT / "TSL_RSH_MIN02_MIN04_STATIONARY_POINT_RESULTS.zip"
SOURCE_RUNTIME = ROOT / "TSL_RSH_MIN02_MIN04_STATIONARY_POINT_RUNTIME_ENVIRONMENT.txt"
SOURCE_PACKAGE = ROOT / "TSL_RSH_MIN02_MIN04_STATIONARY_POINT_OPTIMIZATION_AND_QUALIFICATION.zip"
CAMPAIGN = ROOT / "analysis/mettl7-phase2/tsl-rsh-min02-min04-stationary-package"
MIN01 = ROOT / "analysis/mettl7-phase2/tsl-rsh-min01-stationary-recovery"
OUTPUT = HERE / "sealed-evidence"
EXPECTED_RESULTS_SHA = "e557f370f1f03767538cbf83ab0644992bce073bba9d402266e3be045a815a7b"
EXPECTED_RUNTIME_SHA = "77c5a61c8bbf3760c5fcf093569d5781047acbd7a0da166ee22d96170533caa1"
EXPECTED_PACKAGE_SHA = "168e22040babc19bca8b23b86b04862b6541ee72e9b575606788a50aab001b96"
EXPECTED_CAMPAIGN_SHA = "52fdb925a28720c37e105295f188199f766ed368ec9b7f8ba84dca4758ef9086"
EXPECTED_WRAPPER_SHA = "91c65503a49c07cad381223a4a3d669dfebca3e94f5139a883eb223ca5ccf052"
EXPECTED_CORE_SHA = "1152a47df222b7431c2f1cbe7d30a69d081e547ba71811395bfadcb1dc97f73a"
MIN01_BUNDLE_SHA = "815fd3b59376008f09543a44e9fd60be17d5a8160211ec89855a7d761f04c430"
N_CART = 168


def sha256_bytes(payload: bytes) -> str:
    return hashlib.sha256(payload).hexdigest()


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(value)
    os.replace(temporary, path)


def atomic_json(path: Path, value) -> None:
    atomic_text(path, json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def verify_nested_checksums(archive: zipfile.ZipFile) -> tuple[int, int]:
    manifests = sorted(name for name in archive.namelist() if name.endswith("SHA256SUMS"))
    if "results/SHA256SUMS" not in manifests:
        raise RuntimeError("root results checksum manifest missing")
    checked = 0
    for manifest in manifests:
        directory = manifest.rsplit("/", 1)[0] + "/"
        for row in archive.read(manifest).decode().splitlines():
            expected, relative = row.split(maxsplit=1)
            target = directory + relative.strip()
            try:
                payload = archive.read(target)
            except KeyError as error:
                raise RuntimeError(f"nested checksum target missing: {target}") from error
            if sha256_bytes(payload) != expected:
                raise RuntimeError(f"nested checksum mismatch: {target}")
            checked += 1
    return len(manifests), checked


def array(archive: zipfile.ZipFile, name: str) -> np.ndarray:
    return np.load(io.BytesIO(archive.read(name)), allow_pickle=False)


def verify_structure(archive: zipfile.ZipFile, minimum_id: str) -> dict:
    base = f"results/{minimum_id}/"
    final = json.loads(archive.read(base + "FINAL_RESULT.json"))
    stationary = json.loads(archive.read(
        base + "stationary_point_qualification/STATIONARY_POINT_RESULT.json"))
    optimization = json.loads(archive.read(base + "optimization/OPTIMIZATION_RESULT.json"))
    audit = json.loads(archive.read(base + "endpoint_gradient_audit/ENDPOINT_GRADIENT_AUDIT.json"))
    receipt = json.loads(archive.read(base + "PUBLICATION_RECEIPT.json"))
    runtime = json.loads(archive.read(base + "RUNTIME_ENVIRONMENT.json"))
    if sha256_bytes(archive.read(base + "SCIENTIFIC_ARTIFACT_SHA256SUMS")) != \
            receipt["scientific_artifact_manifest_sha256"]:
        raise RuntimeError(f"{minimum_id} publication receipt manifest mismatch")
    if sha256_bytes(archive.read(base + "FINAL_RESULT.json")) != receipt["final_result_sha256"]:
        raise RuntimeError(f"{minimum_id} publication receipt result mismatch")
    gates = (final["optimization_converged"], final["endpoint_derivative_audit_pass"],
             final["hessian_components_complete"], final["frequency_mode_integrity_pass"],
             final["publication_evidence_complete"],
             final["negative_vibrational_mode_count"] == 0,
             final["stationary_point_classification"] == "VERIFIED_LOCAL_MINIMUM",
             optimization["status"] == "CONVERGED", audit["pass"] is True,
             stationary["classification"] == "VERIFIED_LOCAL_MINIMUM")
    if not all(gates):
        raise RuntimeError(f"{minimum_id} qualification gate failed")
    q = base + "stationary_point_qualification/"
    electronic = array(archive, q + "electronic_hessian_hartree_per_bohr2.npy")
    dispersion = array(archive, q + "dispersion_hessian_hartree_per_bohr2.npy")
    total = array(archive, q + "total_hessian_hartree_per_bohr2.npy")
    for name, value in (("electronic", electronic), ("dispersion", dispersion), ("total", total)):
        if value.shape != (N_CART, N_CART) or not np.isfinite(value).all():
            raise RuntimeError(f"{minimum_id} invalid {name} Hessian")
    composition_error = float(np.max(np.abs(total - (electronic + dispersion))))
    if composition_error > 1e-12:
        raise RuntimeError(f"{minimum_id} Hessian composition mismatch")
    frequencies = np.loadtxt(io.BytesIO(archive.read(q + "signed_frequencies_cm-1.txt")))
    modes = array(archive, q + "normal_modes_cartesian_per_sqrt_amu.npy")
    mass_modes = array(archive, q + "normal_modes_mass_weighted.npy")
    if frequencies.shape != (162,) or modes.shape != (162, 56, 3) \
            or mass_modes.shape != (162, 56, 3) or not np.isfinite(frequencies).all() \
            or not np.isfinite(modes).all() or not np.isfinite(mass_modes).all() \
            or np.any(frequencies <= 0):
        raise RuntimeError(f"{minimum_id} signed frequency/mode integrity failure")
    if runtime["campaign_manifest_sha256"] != EXPECTED_CAMPAIGN_SHA \
            or runtime["wrapper_sha256"] != EXPECTED_WRAPPER_SHA \
            or runtime["protocol_core_sha256"] != EXPECTED_CORE_SHA \
            or runtime["pyscf"] != "2.14.0" or runtime["gpu4pyscf"] != "1.8.0" \
            or runtime["dftd3"] != "1.5.0" or runtime["geometric"] != "1.1.1" \
            or runtime["cupy"] != "13.4.1" or "A100-SXM4-40GB" not in runtime["gpu"]:
        raise RuntimeError(f"{minimum_id} runtime/protocol identity mismatch")
    return {
        "minimum_id": minimum_id,
        "verified_local_minimum": True,
        "endpoint_energy_hartree": final["endpoint_energy_hartree"],
        "endpoint_geometry_sha256": optimization["endpoint_geometry_sha256"],
        "lowest_signed_frequency_cm-1": stationary["lowest_signed_frequency_cm-1"],
        "negative_vibrational_mode_count": 0,
        "optimization_steps": optimization["steps_persisted"],
        "endpoint_audit_rms_hartree_per_bohr": audit["rms_difference_hartree_per_bohr"],
        "endpoint_audit_max_hartree_per_bohr": audit["max_difference_hartree_per_bohr"],
        "hessian_composition_max_abs": composition_error,
        "publication_receipt_sha256": sha256_bytes(archive.read(base + "PUBLICATION_RECEIPT.json")),
        "publication_evidence_complete": True,
    }


def deterministic_zip(path: Path, entries: dict[str, bytes]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with zipfile.ZipFile(temporary, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as target:
        for name in sorted(entries):
            info = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            target.writestr(info, entries[name])
    os.replace(temporary, path)


def archive_prefix(archive: zipfile.ZipFile, prefix: str) -> dict[str, bytes]:
    return {name[len(prefix):]: archive.read(name) for name in archive.namelist()
            if name.startswith(prefix) and not name.endswith("/")}


def bundle_entries(archive: zipfile.ZipFile, minimum_id: str, lineage: dict) -> dict[str, bytes]:
    entries = {f"evidence/{name}": payload for name, payload in
               archive_prefix(archive, f"results/{minimum_id}/").items()}
    entries["provenance/LINEAGE.json"] = (json.dumps(lineage, indent=2, sort_keys=True) + "\n").encode()
    entries["provenance/RUNTIME_ENVIRONMENT.txt"] = SOURCE_RUNTIME.read_bytes()
    for name in ("CAMPAIGN_MANIFEST.json", "PACKAGE_SHA256SUMS",
                 "run_min02_min04_stationary_a100.py"):
        entries[f"protocol/{name}"] = (CAMPAIGN / name).read_bytes()
    entries["protocol/sealed_min01_RECOVERY_RESULT.json"] = (
        MIN01 / "recovered-results/RECOVERY_RESULT.json").read_bytes()
    return entries


def entries_checksums(entries: dict[str, bytes]) -> bytes:
    return "".join(f"{sha256_bytes(entries[name])}  {name}\n" for name in sorted(entries)).encode()


def seal() -> dict:
    if sha256(SOURCE_RESULTS) != EXPECTED_RESULTS_SHA:
        raise RuntimeError("source results archive SHA-256 mismatch")
    if sha256(SOURCE_RUNTIME) != EXPECTED_RUNTIME_SHA:
        raise RuntimeError("runtime environment SHA-256 mismatch")
    if sha256(SOURCE_PACKAGE) != EXPECTED_PACKAGE_SHA:
        raise RuntimeError("executed A100 package SHA-256 mismatch")
    if sha256(CAMPAIGN / "CAMPAIGN_MANIFEST.json") != EXPECTED_CAMPAIGN_SHA \
            or sha256(CAMPAIGN / "run_min02_min04_stationary_a100.py") != EXPECTED_WRAPPER_SHA \
            or sha256(ROOT / "analysis/mettl7-phase2/tsl-rsh-min01-stationary-optimization/run_min01_stationary_optimization_a100.py") != EXPECTED_CORE_SHA:
        raise RuntimeError("frozen campaign-definition identity mismatch")
    if OUTPUT.exists():
        raise RuntimeError(f"sealed output already exists: {OUTPUT}")
    with zipfile.ZipFile(SOURCE_RESULTS) as archive:
        manifest_count, checksum_count = verify_nested_checksums(archive)
        verified = {minimum_id: verify_structure(archive, minimum_id)
                    for minimum_id in ("MIN02", "MIN04")}
        dedup = json.loads(archive.read("results/BASIN_DEDUPLICATION_RESULT.json"))
        final = json.loads(archive.read("results/CAMPAIGN_FINAL_RESULT.json"))
        expected_pairs = ("MIN01_MIN02", "MIN01_MIN04", "MIN02_MIN04")
        if dedup["status"] != "COMPLETE" or dedup["unique_verified_minimum_count"] != 3 \
                or dedup["unique_minimum_ids"] != ["MIN01", "MIN02", "MIN04"] \
                or any(dedup["pairwise"][pair]["same_basin"] for pair in expected_pairs):
            raise RuntimeError("three-minimum basin-deduplication result mismatch")
        immutable = OUTPUT / "immutable-source"
        immutable.mkdir(parents=True)
        shutil.copyfile(SOURCE_RESULTS, immutable / SOURCE_RESULTS.name)
        shutil.copyfile(SOURCE_RUNTIME, immutable / SOURCE_RUNTIME.name)
        shutil.copyfile(SOURCE_PACKAGE, immutable / SOURCE_PACKAGE.name)
        bundle_hashes = {}
        for minimum_id in ("MIN02", "MIN04"):
            lineage = {
                "schema": "tsl-rsh-stationary-evidence-lineage-v1",
                "minimum_id": minimum_id,
                "source_results_archive_sha256": EXPECTED_RESULTS_SHA,
                "runtime_environment_sha256": EXPECTED_RUNTIME_SHA,
                "executed_package_sha256": EXPECTED_PACKAGE_SHA,
                "campaign_manifest_sha256": EXPECTED_CAMPAIGN_SHA,
                "execution_wrapper_sha256": EXPECTED_WRAPPER_SHA,
                "frozen_protocol_core_sha256": EXPECTED_CORE_SHA,
                "sealed_min01_bundle_sha256": MIN01_BUNDLE_SHA,
                "scientific_result": verified[minimum_id],
                "QM_rerun": False,
            }
            entries = bundle_entries(archive, minimum_id, lineage)
            entries["SHA256SUMS"] = entries_checksums(entries)
            path = OUTPUT / "bundles" / f"TSL_RSH_{minimum_id}_VERIFIED_MINIMUM.zip"
            deterministic_zip(path, entries)
            bundle_hashes[minimum_id] = sha256(path)
        summary = {
            "schema": "tsl-rsh-three-verified-minima-summary-v1",
            "source_results_archive_sha256": EXPECTED_RESULTS_SHA,
            "runtime_environment_sha256": EXPECTED_RUNTIME_SHA,
            "executed_package_sha256": EXPECTED_PACKAGE_SHA,
            "frozen_protocol": "PBE-D3(BJ)/def2-SVP/def2-SVP-JKFIT; grid level 5; grid-response gradient and Hessian; SCF 1e-8; simple-dftd3 1.5.0; PySCF isotope-average masses",
            "frozen_protocol_core_sha256": EXPECTED_CORE_SHA,
            "MIN01": {"verified_local_minimum": True, "bundle_sha256": MIN01_BUNDLE_SHA,
                      "energy_hartree": -1477.943793207099,
                      "lowest_signed_frequency_cm-1": 29.796495395505854},
            "MIN02": verified["MIN02"], "MIN04": verified["MIN04"],
            "basin_deduplication": dedup,
            "unique_verified_minimum_count": 3,
            "unique_minimum_ids": ["MIN01", "MIN02", "MIN04"],
            "QM_rerun": False, "model_fit_run": False, "force_field_fit_run": False,
            "thresholds_changed": False,
        }
        atomic_json(OUTPUT / "THREE_MINIMUM_SUMMARY.json", summary)
        report = (
            "# TSL-RSH three verified stationary minima\n\n"
            "MIN01, MIN02 and MIN04 independently satisfy the frozen component-complete "
            "stationary-point gates. All three are distinct under the locked complete-linkage "
            "basin identity conjunction.\n\n"
            f"- MIN01: -1477.943793207099 Ha; lowest signed frequency +29.7964953955 cm^-1\n"
            f"- MIN02: {verified['MIN02']['endpoint_energy_hartree']:.13f} Ha; lowest signed frequency +{verified['MIN02']['lowest_signed_frequency_cm-1']:.10f} cm^-1\n"
            f"- MIN04: {verified['MIN04']['endpoint_energy_hartree']:.13f} Ha; lowest signed frequency +{verified['MIN04']['lowest_signed_frequency_cm-1']:.10f} cm^-1\n\n"
            "No QM calculation, model fit, force-field fit, threshold change, GPU60 recomputation, "
            "or CURVATURE76 recomputation was performed during sealing.\n")
        atomic_text(OUTPUT / "THREE_MINIMUM_SUMMARY.md", report)
        summary_entries = {
            "THREE_MINIMUM_SUMMARY.json": (OUTPUT / "THREE_MINIMUM_SUMMARY.json").read_bytes(),
            "THREE_MINIMUM_SUMMARY.md": (OUTPUT / "THREE_MINIMUM_SUMMARY.md").read_bytes(),
            "BASIN_DEDUPLICATION_RESULT.json": archive.read("results/BASIN_DEDUPLICATION_RESULT.json"),
            "CAMPAIGN_FINAL_RESULT.json": archive.read("results/CAMPAIGN_FINAL_RESULT.json"),
            "MIN02_FINAL_RESULT.json": archive.read("results/MIN02/FINAL_RESULT.json"),
            "MIN04_FINAL_RESULT.json": archive.read("results/MIN04/FINAL_RESULT.json"),
            "MIN02_PUBLICATION_RECEIPT.json": archive.read("results/MIN02/PUBLICATION_RECEIPT.json"),
            "MIN04_PUBLICATION_RECEIPT.json": archive.read("results/MIN04/PUBLICATION_RECEIPT.json"),
            "MIN01_RECOVERY_RESULT.json": (MIN01 / "recovered-results/RECOVERY_RESULT.json").read_bytes(),
            "MIN01_PUBLICATION_RECEIPT.json": (MIN01 / "recovered-results/PUBLICATION_RECEIPT.json").read_bytes(),
            "RUNTIME_ENVIRONMENT.txt": SOURCE_RUNTIME.read_bytes(),
            "CAMPAIGN_MANIFEST.json": (CAMPAIGN / "CAMPAIGN_MANIFEST.json").read_bytes(),
        }
        summary_entries["SHA256SUMS"] = entries_checksums(summary_entries)
        summary_path = OUTPUT / "bundles/TSL_RSH_THREE_VERIFIED_MINIMA_SUMMARY.zip"
        deterministic_zip(summary_path, summary_entries)
    result = {
        "schema": "tsl-rsh-min02-min04-stationary-ingestion-v1",
        "source_results_archive_verified": True,
        "runtime_environment_verified": True,
        "executed_package_verified": True,
        "nested_checksums_pass": True,
        "nested_checksum_manifests": manifest_count,
        "nested_checksum_entries_verified": checksum_count,
        "MIN02": verified["MIN02"], "MIN04": verified["MIN04"],
        "MIN01_MIN02_same_basin": False, "MIN01_MIN04_same_basin": False,
        "MIN02_MIN04_same_basin": False, "unique_verified_minimum_count": 3,
        "MIN02_bundle_sha256": bundle_hashes["MIN02"],
        "MIN04_bundle_sha256": bundle_hashes["MIN04"],
        "three_minimum_summary_sha256": sha256(summary_path),
        "QM_rerun": False, "MIN01_rerun": False, "model_fit_run": False,
        "force_field_fit_run": False, "thresholds_changed": False,
    }
    atomic_json(OUTPUT / "INGESTION_RESULT.json", result)
    paths = sorted(path for path in OUTPUT.rglob("*") if path.is_file() and path.name != "SEAL_SHA256SUMS")
    atomic_text(OUTPUT / "SEAL_SHA256SUMS", "".join(
        f"{sha256(path)}  {path.relative_to(OUTPUT)}\n" for path in paths))
    return result


if __name__ == "__main__":
    seal()
