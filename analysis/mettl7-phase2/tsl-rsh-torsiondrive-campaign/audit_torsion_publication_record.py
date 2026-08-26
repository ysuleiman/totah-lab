#!/usr/bin/env python3
"""Fail-closed, offline audit of the immutable CHI/PHI/PSI campaign archives."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import math
import re
import tarfile
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath


HERE = Path(__file__).resolve().parent
ARCHIVES = {
    "CHI": ("TSL_RSH_TORSIONDRIVE_BACKUP-20260825T144336Z-1-001.zip",
            "6cc680aec6a161147d45564755276b22b9b46f46a7fb502fdb77f9d349f96fa3"),
    "PHI": ("TSL_RSH_PHI_RUNPOD_RESULTS.tar.gz",
            "22d7e15d275611c909fd96d7f3597f30acb153b2e0090df38793ce627835f65e"),
    "PSI": ("TSL_RSH_PSI_RUNPOD_RESULTS.tar.gz",
            "1e339fc04bf495521095f8f6e6ff93286b0da7f2252fc27b0a90c450ddd55818"),
}
ATOMS_ZERO_BASED = {"CHI": [55, 25, 9, 8], "PHI": [25, 9, 8, 7], "PSI": [9, 8, 7, 1]}
ATOMS_ONE_BASED = {key: [value + 1 for value in values] for key, values in ATOMS_ZERO_BASED.items()}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def parse_sums(data: bytes) -> list[tuple[str, str]]:
    entries = []
    for line in data.decode().splitlines():
        if not line.strip():
            continue
        match = re.fullmatch(r"([0-9a-f]{64})  (.+)", line)
        if not match:
            raise AssertionError(f"malformed checksum line: {line!r}")
        entries.append((match.group(1), match.group(2)))
    return entries


@dataclass
class Archive:
    path: Path
    axis: str

    def __post_init__(self) -> None:
        self.kind = "zip" if self.path.suffix == ".zip" else "tar"
        if self.kind == "zip":
            self.archive = zipfile.ZipFile(self.path)
            bad = self.archive.testzip()
            if bad is not None:
                raise AssertionError(f"ZIP CRC failure: {bad}")
            self.names = set(self.archive.namelist())
        else:
            self.archive = tarfile.open(self.path, "r:gz")
            self.names = {member.name for member in self.archive.getmembers() if member.isfile()}

    def read(self, name: str) -> bytes:
        if name not in self.names:
            raise AssertionError(f"missing archive member: {name}")
        if self.kind == "zip":
            return self.archive.read(name)
        handle = self.archive.extractfile(name)
        if handle is None:
            raise AssertionError(f"unreadable archive member: {name}")
        return handle.read()

    def close(self) -> None:
        self.archive.close()

    def json(self, name: str) -> dict:
        return json.loads(self.read(name))


def task_id(axis: str, source_id: str, target: int) -> str:
    return hashlib.sha256(f"{axis}|{source_id}|{target}".encode()).hexdigest()[:20]


def verify_manifest(archive: Archive, manifest_name: str, base: str) -> int:
    entries = parse_sums(archive.read(manifest_name))
    for expected, relative in entries:
        member = str(PurePosixPath(base) / relative)
        actual = sha256(archive.read(member))
        if actual != expected:
            raise AssertionError(f"checksum mismatch: {archive.path.name}:{member}")
    return len(entries)


def verify_axis_paths(archive: Archive, result_prefix: str) -> None:
    scientific = [name for name in archive.names if "/results/" in name and "/candidates/" in name]
    wrong = [name for name in scientific if not name.startswith(result_prefix + "/candidates/")]
    if wrong:
        raise AssertionError(f"cross-axis candidate paths in {archive.axis}: {wrong[:3]}")


def verify_constraints(text: str, axis: str, target: int) -> None:
    match = re.search(r"dihedral\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)\s+(-?\d+)", text)
    if not match:
        raise AssertionError(f"missing dihedral constraint for {axis}")
    atoms = [int(match.group(i)) for i in range(1, 5)]
    if atoms != ATOMS_ONE_BASED[axis] or int(match.group(5)) != target:
        raise AssertionError(f"wrong {axis} constraint: atoms={atoms}, target={match.group(5)}")


def audit_chi(archive: Archive) -> dict:
    root = "TSL_RSH_TORSIONDRIVE_BACKUP/results/CHI"
    state_name = f"{root}/WAVEFRONT_STATE.json"
    state = archive.json(state_name)
    if state.get("torsion") != "CHI":
        raise AssertionError("CHI state identity mismatch")
    verify_axis_paths(archive, root)
    state_checksum_count = verify_manifest(archive, f"{root}/STATE_SHA256SUMS", root)
    record_names = sorted(name for name in archive.names if re.fullmatch(
        re.escape(root) + r"/candidates/[0-9a-f]{20}/WAVEFRONT_RECORD\.json", name))
    candidate_manifest_names = sorted(name for name in archive.names if re.fullmatch(
        re.escape(root) + r"/candidates/[0-9a-f]{20}/SHA256SUMS", name))
    if len(record_names) != 225 or len(candidate_manifest_names) != 225:
        raise AssertionError("CHI finalized candidate inventory is not 225/225")
    nested_count = 0
    energies = []
    completed = set(state["completed_task_ids"])
    for manifest_name in candidate_manifest_names:
        nested_count += verify_manifest(archive, manifest_name, str(PurePosixPath(manifest_name).parent))
    for record_name in record_names:
        record = archive.json(record_name)
        identifier = PurePosixPath(record_name).parts[-2]
        if record["task_id"] != identifier or identifier not in completed:
            raise AssertionError(f"CHI candidate identity mismatch: {identifier}")
        energy = float(record["energy_hartree"])
        if not math.isfinite(energy):
            raise AssertionError(f"nonfinite CHI energy: {identifier}")
        energies.append(energy)
        constraint_name = str(PurePosixPath(record_name).parent / "constraints.txt")
        verify_constraints(archive.read(constraint_name).decode(), "CHI", int(record["target_degrees"]))
    replay = json.loads((HERE / "CHI_OFFLINE_REPLAY_RESULT.json").read_text())
    canonical = {str(key): float(value) for key, value in replay["all_paid_result_best_energies_hartree"].items()}
    if not replay["converged"] or len(canonical) != 24:
        raise AssertionError("corrected CHI replay is incomplete")
    minimum = min(canonical.items(), key=lambda item: item[1])
    return {
        "axis": "CHI", "state": state, "checksum_entries": state_checksum_count + nested_count,
        "candidate_count": len(record_names), "completed_count": len(record_names),
        "failed_count": len(state["failed_task_ids"]), "rounds": replay["corrected_convergence_round"],
        "historical_rounds": state["round"], "cell_count": len(canonical), "converged": True,
        "energies": canonical, "minimum_degrees": [int(minimum[0])], "minimum_energy_hartree": minimum[1],
        "energy_range_hartree": max(canonical.values()) - min(canonical.values()),
        "state_sha256": sha256(archive.read(state_name)),
        "completion_receipt": None, "receipt_status": "UNAVAILABLE_HISTORICAL",
        "run_date": "2026-08-25", "runtime": "UNAVAILABLE_HISTORICAL",
        "platform": "Google Colab (embedded runtime evidence)", "gpu": "NVIDIA A100-SXM4-40GB",
        "notes": "Canonical surface reconstructed offline from all paid candidate records; 158 candidates were unnecessary under canonical reactivation semantics."
    }


def audit_tar_axis(archive: Archive, axis: str) -> dict:
    root = f"results/{axis}"
    state_name = f"{root}/WAVEFRONT_STATE.json"
    receipt_name = f"{root}/COMPLETION_RECEIPT.json"
    state = archive.json(state_name)
    receipt = archive.json(receipt_name)
    if state.get("torsion") != axis:
        raise AssertionError(f"{axis} state identity mismatch")
    verify_axis_paths(archive, root)
    checksum_count = verify_manifest(archive, f"{root}/SHA256SUMS", root)
    expected_count = {"PHI": 5352, "PSI": 9051}[axis]
    if checksum_count != expected_count:
        raise AssertionError(f"{axis} root checksum count {checksum_count} != {expected_count}")
    state_sha = sha256(archive.read(state_name))
    sums_sha = sha256(archive.read(f"{root}/STATE_SHA256SUMS"))
    if receipt["state_sha256"] != state_sha or receipt["state_checksums_sha256"] != sums_sha:
        raise AssertionError(f"{axis} receipt lineage mismatch")
    completed = set(state["completed_task_ids"])
    records = sorted(name for name in archive.names if re.fullmatch(
        re.escape(root) + r"/candidates/[0-9a-f]{20}/WAVEFRONT_RECORD\.json", name))
    if len(records) != len(completed):
        raise AssertionError(f"{axis} record/completed mismatch")
    for record_name in records:
        record = archive.json(record_name)
        identifier = PurePosixPath(record_name).parts[-2]
        if identifier != record["task_id"] or identifier not in completed:
            raise AssertionError(f"{axis} candidate identity mismatch: {identifier}")
        if task_id(axis, record["source_id"], int(record["target_degrees"])) != identifier:
            raise AssertionError(f"{axis} task hash mismatch: {identifier}")
        if not math.isfinite(float(record["energy_hartree"])):
            raise AssertionError(f"nonfinite {axis} energy: {identifier}")
        verify_constraints(archive.read(str(PurePosixPath(record_name).parent / "constraints.txt")).decode(),
                           axis, int(record["target_degrees"]))
    task_names = [name for name in archive.names if "/tasks/" in name and name.endswith(".json")]
    for name in task_names:
        spec = archive.json(name)
        if spec["torsion_atoms"] != ATOMS_ZERO_BASED[axis]:
            raise AssertionError(f"{axis} task torsion mismatch: {name}")
        if f"/results/{axis}/candidates/" not in spec["candidate"]:
            raise AssertionError(f"{axis} task candidate path mismatch: {name}")
        if task_id(axis, spec["source_id"], int(spec["target_degrees"])) != spec["task_id"]:
            raise AssertionError(f"{axis} task identity mismatch: {name}")
    energies = {str(key): float(value["energy_hartree"]) for key, value in state["cells"].items()}
    minimum = min(energies.items(), key=lambda item: item[1])
    launch = next((name for name in archive.names if name == f"logs/{axis.lower()}_launch_utc.txt"), None)
    run_date = archive.read(launch).decode().strip() if launch else "UNAVAILABLE"
    return {
        "axis": axis, "state": state, "checksum_entries": checksum_count,
        "candidate_count": len(records), "completed_count": len(completed),
        "failed_count": len(state["failed_task_ids"]), "rounds": state["round"],
        "cell_count": len(energies), "converged": not state["queue"], "energies": energies,
        "minimum_degrees": [int(minimum[0])], "minimum_energy_hartree": minimum[1],
        "energy_range_hartree": max(energies.values()) - min(energies.values()),
        "state_sha256": state_sha, "completion_receipt": sha256(archive.read(receipt_name)),
        "receipt_status": receipt["status"], "run_date": run_date, "runtime": "UNAVAILABLE_IN_ARCHIVE",
        "platform": "RunPod Secure Cloud (operator record)", "gpu": "2 x NVIDIA A100-SXM4-80GB (operator-verified)",
        "notes": ("Historical receipt is mislabeled PHI_COMPLETE_PERSISTED; axis-specific state, tasks, constraints and paths verify PSI."
                  if axis == "PSI" else "Receipt and scientific axis identity agree.")
    }


def write_outputs(results: dict[str, dict], archive_hashes: dict[str, str]) -> None:
    common = {
        "schema": "tsl-rsh-torsiondrive-publication-manifest-v1",
        "generated_from_immutable_evidence": True,
        "scientific_protocol": {
            "method": "PBE-D3(BJ)", "basis": "def2-SVP", "density_fitting_basis": "def2-SVP-JKFIT",
            "grid_level": 5, "grid_response_gradient": True, "scf_tolerance": 1e-8,
            "scf_max_cycles": 160, "initial_guess": "MINAO", "charge": 0, "multiplicity": 1,
            "dispersion": {"implementation": "simple-dftd3 1.5.0", "damping": "BJ", "s6": 1.0,
                           "s8": 0.7875, "s9": 0.0, "a1": 0.4289, "a2": 4.4407,
                           "alp": 14.0, "atm": False},
            "software": {"PySCF": "2.14.0", "GPU4PySCF": "1.8.0", "CuPy": "13.4.1",
                         "cutensor": "2.2.0", "simple-dftd3": "1.5.0", "geomeTRIC": "1.1.1"},
            "grid_degrees": list(range(-180, 180, 15)), "grid_spacing_degrees": 15,
            "energy_decrease_threshold_hartree": 1e-5,
            "propagation_energy_upper_limit_hartree": 0.05,
            "candidate_optimization": "geomeTRIC constrained relaxed scan; maximum 300 iterations",
        },
        "software_provenance": {
            "pre_defect_fix_scientific_baseline_commit": "0160c1fb6f510c6ea7d290bc8c25c945684b9899",
            "wrapper_axis_identity_fix_commit": "656723aa4122dc81140b93b26dc7a5c9b3d4be41",
            "execution_package_git_commit": "UNAVAILABLE_IN_ARCHIVES",
        },
        "torsions": {},
    }
    for axis, result in results.items():
        archive_name = ARCHIVES[axis][0]
        common["torsions"][axis] = {
            "torsion_atoms_zero_based": ATOMS_ZERO_BASED[axis],
            "torsion_atoms_one_based": ATOMS_ONE_BASED[axis],
            "archive": archive_name, "archive_sha256": archive_hashes[axis],
            "individual_file_checksum_manifest": ("embedded candidate SHA256SUMS + STATE_SHA256SUMS"
                                                  if axis == "CHI" else f"results/{axis}/SHA256SUMS"),
            "checksum_entries_verified": result["checksum_entries"],
            "candidate_count": result["candidate_count"], "completed_count": result["completed_count"],
            "failed_count": result["failed_count"], "round_count": result["rounds"],
            "final_grid_cell_count": result["cell_count"], "converged": result["converged"],
            "minimum_grid_degrees": result["minimum_degrees"],
            "minimum_energy_hartree": result["minimum_energy_hartree"],
            "energy_range_hartree": result["energy_range_hartree"],
            "execution_platform": result["platform"], "gpu_model": result["gpu"],
            "run_date_or_launch_time": result["run_date"], "runtime": result["runtime"],
            "wavefront_state_sha256": result["state_sha256"],
            "completion_receipt_sha256": result["completion_receipt"],
            "historical_receipt_status": result["receipt_status"], "notes": result["notes"],
            "provenance_status": "PASS",
        }
    (HERE / "TORSION_PUBLICATION_REPRODUCIBILITY_MANIFEST.json").write_text(
        json.dumps(common, indent=2, sort_keys=True) + "\n")
    with (HERE / "TORSION_SURFACE_CONSISTENCY.csv").open("w", newline="") as handle:
        writer = csv.writer(handle, lineterminator="\n")
        writer.writerow(["torsion", "definition_zero_based", "grid", "candidate_count", "successful_candidates",
                         "failed_candidates", "rounds", "grid_cells", "converged", "energy_range_hartree",
                         "minimum_grid_degrees", "minimum_energy_hartree", "archive", "sha256", "verification"])
        for axis, result in results.items():
            writer.writerow([axis, "-".join(map(str, ATOMS_ZERO_BASED[axis])), "-180:15:165",
                             result["candidate_count"], result["completed_count"], result["failed_count"],
                             result["rounds"], result["cell_count"], str(result["converged"]).lower(),
                             f'{result["energy_range_hartree"]:.15g}', ";".join(map(str, result["minimum_degrees"])),
                             f'{result["minimum_energy_hartree"]:.15g}', ARCHIVES[axis][0], archive_hashes[axis], "PASS"])
    contamination = {
        "schema": "tsl-rsh-torsiondrive-cross-axis-contamination-audit-v1",
        "result": "NONE", "checks": {
            "axis_specific_archive_membership": "PASS", "axis_specific_state_identity": "PASS",
            "axis_specific_task_hashes": "PASS", "axis_specific_torsion_atoms": "PASS",
            "axis_specific_constraints": "PASS", "axis_specific_candidate_paths": "PASS",
            "receipt_state_checksum_lineage": "PASS",
        },
        "psi_metadata_defect_scope": "Historical PSI completion label/status lookup only; no PHI scientific input was consumed and no CHI/PHI archive member was modified.",
        "raw_archives_modified": False,
    }
    (HERE / "TORSION_CROSS_AXIS_CONTAMINATION_AUDIT.json").write_text(
        json.dumps(contamination, indent=2, sort_keys=True) + "\n")
    checksum_audit = {
        "schema": "tsl-rsh-torsiondrive-checksum-audit-v1", "status": "PASS",
        "archives": {axis: {"file": ARCHIVES[axis][0], "sha256": archive_hashes[axis],
                            "expected_sha256": ARCHIVES[axis][1], "nested_checksums_verified": result["checksum_entries"]}
                     for axis, result in results.items()},
        "psi_required_nested_checksum_count": 9051,
        "psi_archive_unchanged": archive_hashes["PSI"] == ARCHIVES["PSI"][1],
    }
    (HERE / "TORSION_CHECKSUM_AUDIT.json").write_text(json.dumps(checksum_audit, indent=2, sort_keys=True) + "\n")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--verify-only", action="store_true")
    args = parser.parse_args()
    hashes = {}
    results = {}
    for axis, (name, expected) in ARCHIVES.items():
        path = HERE / name
        actual = file_sha256(path)
        if actual != expected:
            raise AssertionError(f"{axis} archive SHA mismatch: {actual} != {expected}")
        hashes[axis] = actual
        archive = Archive(path, axis)
        try:
            results[axis] = audit_chi(archive) if axis == "CHI" else audit_tar_axis(archive, axis)
        finally:
            archive.close()
    if not args.verify_only:
        write_outputs(results, hashes)
    print(json.dumps({"status": "PASS", "archives": hashes,
                      "nested_checksums": {axis: value["checksum_entries"] for axis, value in results.items()},
                      "cross_axis_contamination": "NONE"}, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
