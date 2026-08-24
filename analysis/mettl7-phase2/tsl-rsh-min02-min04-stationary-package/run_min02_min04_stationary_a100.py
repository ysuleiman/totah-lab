#!/usr/bin/env python3
"""MIN02/MIN04 execution wrapper around the exact sealed MIN01 protocol core."""
from __future__ import annotations

import csv
import hashlib
import importlib.util
import json
import math
import os
import platform
import subprocess
import sys
import zipfile
from collections import Counter
from importlib.metadata import version as package_version
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ARCHIVE_ROOT = HERE.parents[2]
MANIFEST_PATH = HERE / "CAMPAIGN_MANIFEST.json"
CORE_PATH = ARCHIVE_ROOT / "analysis/mettl7-phase2/tsl-rsh-min01-stationary-optimization/run_min01_stationary_optimization_a100.py"
CORE_DIR = CORE_PATH.parent
MIN01_RECOVERY = ARCHIVE_ROOT / "analysis/mettl7-phase2/tsl-rsh-min01-stationary-recovery"
RESULTS = HERE / "results"
PACKAGE_CHECKSUMS = HERE / "PACKAGE_SHA256SUMS"
HARTREE_TO_KCAL_MOL = 627.5094740631
DIHEDRALS = {"phi_deg": (56, 26, 10, 9), "psi_deg": (26, 10, 9, 8),
             "chi_deg": (10, 9, 8, 2)}
ANGLES = {"angle_9_10_26_deg": (9, 10, 26), "angle_11_10_26_deg": (11, 10, 26),
          "angle_56_26_10_deg": (56, 26, 10)}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(text)
    os.replace(temporary, path)


def atomic_json(path: Path, value) -> None:
    atomic_text(path, json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def write_checksums(directory: Path) -> None:
    paths = sorted(p for p in directory.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
    atomic_text(directory / "SHA256SUMS", "".join(
        f"{sha256(path)}  {path.relative_to(directory)}\n" for path in paths))


def verify_package_integrity() -> int:
    if not PACKAGE_CHECKSUMS.is_file():
        raise RuntimeError("package checksum manifest is missing")
    checked = 0
    for row in PACKAGE_CHECKSUMS.read_text().splitlines():
        expected, relative = row.split(maxsplit=1)
        target = ARCHIVE_ROOT / relative.strip()
        if not target.is_file() or sha256(target) != expected:
            raise RuntimeError(f"package artifact missing or modified: {relative.strip()}")
        checked += 1
    return checked


def load_core(expected_sha: str):
    if not CORE_PATH.is_file() or sha256(CORE_PATH) != expected_sha:
        raise RuntimeError("sealed stationary protocol core missing or modified")
    spec = importlib.util.spec_from_file_location("sealed_stationary_core", CORE_PATH)
    core = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = core
    spec.loader.exec_module(core)
    return core


def read_xyz(path: Path):
    lines = path.read_text().splitlines()
    count = int(lines[0])
    rows = [line.split() for line in lines[2:2 + count]]
    if count != 56 or len(rows) != 56:
        raise RuntimeError(f"expected 56 atoms: {path}")
    return [row[0] for row in rows], np.asarray([[float(x) for x in row[1:4]] for row in rows])


def configure_structure(core, minimum_id: str, output: Path) -> None:
    core.INPUT = CORE_DIR / "input"
    core.OUTPUT = output
    core.EVIDENCE = CORE_DIR / "immutable-evidence/TSL_RSH_MIN01_LEVEL5_GRID_CONVERGENCE_CLOSURE_RESULTS.zip"
    core.MANIFEST = CORE_DIR / "OPTIMIZATION_MANIFEST.json"
    if not hasattr(core, "_campaign_base_xyz_text"):
        core._campaign_base_xyz_text = core.xyz_text
    core.xyz_text = lambda elements, coordinates, comment: core._campaign_base_xyz_text(
        elements, coordinates, comment.replace("MIN01", minimum_id))


def seal_structure(output: Path, minimum_id: str, input_path: Path) -> None:
    scientific_manifest = output / "SCIENTIFIC_ARTIFACT_SHA256SUMS"
    os.replace(output / "SHA256SUMS", scientific_manifest)
    receipt = {
        "schema": "tsl-rsh-stationary-qualification-publication-receipt-v1",
        "status": "SEALED",
        "minimum_id": minimum_id,
        "input_geometry_sha256": sha256(input_path),
        "protocol_core_sha256": sha256(CORE_PATH),
        "campaign_manifest_sha256": sha256(MANIFEST_PATH),
        "scientific_artifact_manifest_sha256": sha256(scientific_manifest),
        "final_result_sha256": sha256(output / "FINAL_RESULT.json"),
        "MIN01_rerun": False,
        "model_fit_run": False,
        "force_field_fit_run": False,
        "thresholds_changed": False,
    }
    atomic_json(output / "PUBLICATION_RECEIPT.json", receipt)
    write_checksums(output)
    core_manifest = output / "SHA256SUMS"
    if not core_manifest.is_file() or json.loads((output / "PUBLICATION_RECEIPT.json").read_text()) != receipt:
        raise RuntimeError(f"publication receipt read-back failed for {minimum_id}")


def execute_structure(core, minimum_id: str, input_path: Path, campaign: dict, runtime: dict) -> dict:
    output = RESULTS / minimum_id
    configure_structure(core, minimum_id, output)
    elements, coordinates = read_xyz(input_path)
    if dict(Counter(elements)) != core.EXPECTED_COMPOSITION:
        raise RuntimeError(f"composition mismatch for {minimum_id}")
    with (CORE_DIR / "input/ATOM_ORDER.csv").open(newline="") as source:
        atom_order = list(csv.DictReader(source))
    if [row["element"] for row in atom_order] != elements:
        raise RuntimeError(f"atom order mismatch for {minimum_id}")
    core.mass_vector(core.molecule(elements, coordinates))
    output.mkdir(parents=True, exist_ok=False)
    atomic_json(output / "RUNTIME_ENVIRONMENT.json", {
        **runtime, "minimum_id": minimum_id, "input_geometry_sha256": sha256(input_path),
        "protocol_core_sha256": sha256(CORE_PATH), "campaign_manifest_sha256": sha256(MANIFEST_PATH)})
    endpoint, endpoint_result, endpoint_gradient = core.optimize(elements, coordinates)
    audit = core.endpoint_gradient_audit(elements, endpoint, endpoint_gradient,
                                         json.loads(core.MANIFEST.read_text()))
    if not audit["pass"]:
        core.validate_stopped_evidence()
        result = {"minimum_id": minimum_id, "status": "STOPPED_ENDPOINT_GRADIENT_AUDIT_FAILED",
                  "optimization_converged": True, "endpoint_derivative_audit_pass": False,
                  "Hessian_run": False, "publication_evidence_complete": True}
        atomic_json(output / "FINAL_RESULT.json", result)
        write_checksums(output)
        seal_structure(output, minimum_id, input_path)
        return result
    qualification = core.qualify_hessian(elements, endpoint, json.loads(core.MANIFEST.read_text()))
    publication_complete = core.validate_publication_evidence(audit, qualification)
    result = {"minimum_id": minimum_id, "status": "COMPLETE",
              "optimization_converged": True, "endpoint_derivative_audit_pass": True,
              "hessian_components_complete": qualification["hessian_components_complete"],
              "negative_vibrational_mode_count": qualification["negative_vibrational_mode_count"],
              "frequency_mode_integrity_pass": qualification["frequency_mode_integrity_pass"],
              "publication_evidence_complete": publication_complete,
              "stationary_point_classification": qualification["classification"],
              "endpoint_energy_hartree": endpoint_result["total_energy_hartree"]}
    atomic_json(output / "FINAL_RESULT.json", result)
    write_checksums(output)
    seal_structure(output, minimum_id, input_path)
    return result


def unit(vector):
    norm = np.linalg.norm(vector)
    if not np.isfinite(norm) or norm == 0:
        raise RuntimeError("invalid geometry vector")
    return vector / norm


def angle(coords, atoms):
    a, b, c = (coords[index - 1] for index in atoms)
    return math.degrees(math.acos(np.clip(np.dot(unit(a - b), unit(c - b)), -1.0, 1.0)))


def dihedral(coords, atoms):
    p0, p1, p2, p3 = (coords[index - 1] for index in atoms)
    b0, b1, b2 = p0 - p1, p2 - p1, p3 - p2
    b1u = unit(b1)
    v, w = b0 - np.dot(b0, b1u) * b1u, b2 - np.dot(b2, b1u) * b1u
    return math.degrees(math.atan2(np.dot(np.cross(b1u, v), w), np.dot(v, w)))


def circular_difference(a, b):
    return abs((a - b + 180.0) % 360.0 - 180.0)


def aligned_rmsd(a, b):
    ac, bc = a - a.mean(axis=0), b - b.mean(axis=0)
    u, _, vt = np.linalg.svd(ac.T @ bc)
    rotation = u @ np.diag([1.0, 1.0, np.linalg.det(u @ vt)]) @ vt
    return float(np.sqrt(np.mean(np.sum((ac @ rotation - bc) ** 2, axis=1))))


def endpoint_record(minimum_id, geometry_path, energy):
    elements, coords = read_xyz(geometry_path)
    record = {"minimum_id": minimum_id, "elements": elements, "coords": coords,
              "energy_hartree": float(energy), "geometry_sha256": sha256(geometry_path),
              "s_h_angstrom": float(np.linalg.norm(coords[25] - coords[55])),
              "s_c_angstrom": float(np.linalg.norm(coords[25] - coords[9]))}
    record.update({name: dihedral(coords, atoms) for name, atoms in DIHEDRALS.items()})
    record.update({name: angle(coords, atoms) for name, atoms in ANGLES.items()})
    return record


def pair_identity(a, b, gates):
    if a["elements"] != b["elements"]:
        raise RuntimeError("canonical atom-order mismatch during basin comparison")
    heavy = np.asarray([element != "H" for element in a["elements"]])
    values = {"heavy_atom_rmsd_angstrom": aligned_rmsd(a["coords"][heavy], b["coords"][heavy]),
              "phi_difference_deg": circular_difference(a["phi_deg"], b["phi_deg"]),
              "psi_difference_deg": circular_difference(a["psi_deg"], b["psi_deg"]),
              "chi_difference_deg": circular_difference(a["chi_deg"], b["chi_deg"]),
              "s_h_difference_angstrom": abs(a["s_h_angstrom"] - b["s_h_angstrom"]),
              "s_c_difference_angstrom": abs(a["s_c_angstrom"] - b["s_c_angstrom"]),
              "angle_9_10_26_difference_deg": abs(a["angle_9_10_26_deg"] - b["angle_9_10_26_deg"]),
              "angle_11_10_26_difference_deg": abs(a["angle_11_10_26_deg"] - b["angle_11_10_26_deg"]),
              "angle_56_26_10_difference_deg": abs(a["angle_56_26_10_deg"] - b["angle_56_26_10_deg"]),
              "energy_difference_kcal_per_mol": abs(a["energy_hartree"] - b["energy_hartree"]) * HARTREE_TO_KCAL_MOL}
    same = (values["heavy_atom_rmsd_angstrom"] <= gates["heavy_atom_rmsd_angstrom_max"]
            and max(values[k] for k in ("phi_difference_deg", "psi_difference_deg", "chi_difference_deg")) <= gates["phi_psi_chi_difference_deg_max"]
            and max(values[k] for k in ("s_h_difference_angstrom", "s_c_difference_angstrom")) <= gates["s_h_s_c_difference_angstrom_max"]
            and max(values[k] for k in values if k.startswith("angle_")) <= gates["local_angle_difference_deg_max"]
            and values["energy_difference_kcal_per_mol"] <= gates["energy_difference_kcal_per_mol_max"])
    return same, values


def deduplicate(campaign, results):
    if not all(value.get("stationary_point_classification") == "VERIFIED_LOCAL_MINIMUM"
               for value in results.values()):
        return {"status": "WITHHELD_UNTIL_BOTH_QUALIFY", "lineage": results}
    source_zip = MIN01_RECOVERY / "immutable-evidence/TSL_RSH_MIN01_STATIONARY_POINT_OPTIMIZATION_RESULTS.zip"
    with zipfile.ZipFile(source_zip) as archive:
        min01_geometry = RESULTS / "deduplication/MIN01_sealed_endpoint.xyz"
        atomic_text(min01_geometry, archive.read("results/optimization/final.xyz").decode())
        min01_result = json.loads(archive.read("results/optimization/OPTIMIZATION_RESULT.json"))
    endpoints = {"MIN01": endpoint_record("MIN01", min01_geometry,
                 min01_result["endpoint_total_energy_hartree"])}
    for minimum_id in ("MIN02", "MIN04"):
        endpoints[minimum_id] = endpoint_record(minimum_id,
            RESULTS / minimum_id / "optimization/final.xyz", results[minimum_id]["endpoint_energy_hartree"])
    pairs, equivalent = {}, {}
    ids = ("MIN01", "MIN02", "MIN04")
    for index, left in enumerate(ids):
        for right in ids[index + 1:]:
            same, values = pair_identity(endpoints[left], endpoints[right], campaign["basin_identity"])
            key = f"{left}_{right}"
            pairs[key] = {"same_basin": same, **values}
            equivalent[(left, right)] = same
    clusters = [[minimum_id] for minimum_id in ids]
    while True:
        merged = False
        for i in range(len(clusters)):
            for j in range(i + 1, len(clusters)):
                if all(equivalent.get(tuple(sorted((a, b))), a == b)
                       for a in clusters[i] for b in clusters[j]):
                    clusters[i] += clusters[j]
                    del clusters[j]
                    merged = True
                    break
            if merged:
                break
        if not merged:
            break
    return {"status": "COMPLETE", "pairwise": pairs, "clusters": clusters,
            "unique_verified_minimum_count": len(clusters),
            "unique_minimum_ids": [min(cluster, key=lambda item: endpoints[item]["energy_hartree"])
                                   for cluster in clusters],
            "convergence_lineage": {key: {"geometry_sha256": value["geometry_sha256"],
                "energy_hartree": value["energy_hartree"]} for key, value in endpoints.items()}}


def main():
    package_files_verified = verify_package_integrity()
    campaign = json.loads(MANIFEST_PATH.read_text())
    core = load_core(campaign["protocol_core_sha256"])
    core.install_environment()
    import cupy as cp
    import pyscf
    closure = core.verify_closure()
    if pyscf.__version__ != "2.14.0" or package_version("gpu4pyscf-cuda12x") != "1.8.0" \
            or package_version("dftd3") != "1.5.0" or package_version("geometric") != "1.1.1" \
            or sha256(core.d3_database_path()) != core.D3_DATABASE_SHA256:
        raise RuntimeError("locked software identity mismatch")
    gpu_name = cp.cuda.runtime.getDeviceProperties(0)["name"]
    gpu_name = gpu_name.decode() if isinstance(gpu_name, bytes) else str(gpu_name)
    if "A100" not in gpu_name.upper():
        raise RuntimeError(f"A100 required, found {gpu_name}")
    RESULTS.mkdir(parents=True, exist_ok=False)
    runtime = {"python": platform.python_version(), "platform": platform.platform(),
               "pyscf": pyscf.__version__, "gpu4pyscf": package_version("gpu4pyscf-cuda12x"),
               "dftd3": package_version("dftd3"), "geometric": package_version("geometric"),
               "cupy": cp.__version__, "gpu": gpu_name, "closure_decision": closure["decision"],
               "wrapper_sha256": sha256(Path(__file__)),
               "package_files_verified": package_files_verified,
               "pip_freeze": subprocess.check_output([sys.executable, "-m", "pip", "freeze"], text=True).splitlines()}
    results = {}
    for minimum_id in ("MIN02", "MIN04"):
        entry = campaign["inputs"][minimum_id]
        input_path = ARCHIVE_ROOT / entry["path"]
        if not input_path.is_file() or sha256(input_path) != entry["sha256"]:
            raise RuntimeError(f"missing or modified historical geometry: {minimum_id}")
        results[minimum_id] = execute_structure(core, minimum_id, input_path, campaign, runtime)
    deduplication = deduplicate(campaign, results)
    atomic_json(RESULTS / "BASIN_DEDUPLICATION_RESULT.json", deduplication)
    atomic_json(RESULTS / "CAMPAIGN_FINAL_RESULT.json", {
        "status": "COMPLETE", "structures": results, "deduplication": deduplication,
        "MIN01_rerun": False, "model_fit_run": False, "force_field_fit_run": False,
        "GPU60_recomputed": False, "CURVATURE76_recomputed": False, "thresholds_changed": False})
    write_checksums(RESULTS)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        if RESULTS.is_dir():
            atomic_json(RESULTS / "FAILURE.json", {"status": "FAILED_PRESERVED",
                "exception_type": type(error).__name__, "message": str(error),
                "MIN01_rerun": False, "model_fit_run": False, "thresholds_changed": False})
            write_checksums(RESULTS)
        raise
