#!/usr/bin/env python3
"""RunPod PSI-only execution wrapper; sealed science remains unchanged."""
from __future__ import annotations

import argparse
import concurrent.futures
import hashlib
import importlib.util
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from torsion_axis_identity import (
    TorsionAxis,
    completion_receipt,
    persisted_status,
)

HERE = Path(__file__).resolve().parent
SERIAL = HERE / "run_torsiondrive_a100.py"
DEFAULT_PERSISTENT_ROOT = Path("/workspace/tsl-rsh/torsiondrive/results")
MAX_GPU_WORKERS = 8
EXECUTION_AXIS = TorsionAxis.PSI


def load_serial():
    spec = importlib.util.spec_from_file_location("sealed_serial_controller", SERIAL)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module


def deterministic_reduce(controller, cells, records, manifest):
    ordered = sorted(records, key=lambda row: (row["target_degrees"], row["energy_hartree"], row["task_id"]))
    updated, queue = controller.wavefront_transition(
        dict(cells), ordered, manifest["grid_degrees"], manifest["energy_upper_limit_hartree"],
        manifest["energy_decrease_threshold_hartree"])
    unique = {}
    for task in queue:
        identifier = controller.task_id(
            task.get("torsion", EXECUTION_AXIS.value),
            task["source_id"],
            task["target_degrees"])
        unique[identifier] = task
    return updated, [unique[key] for key in sorted(unique)]


def gpu_assignments(task_count, worker_count=MAX_GPU_WORKERS):
    if worker_count < 1 or worker_count > 8:
        raise RuntimeError("worker count must be 1..8")
    return [index % worker_count for index in range(task_count)]


def atomic_json(controller, path, value):
    controller.atomic_json(path, value)
    if json.loads(path.read_text()) != value:
        raise RuntimeError(f"durable JSON read-back failed: {path}")


def verify_persistent_storage(root: Path):
    root.mkdir(parents=True, exist_ok=True)
    result = subprocess.run(["findmnt", "-n", "-o", "TARGET,SOURCE,FSTYPE", "-T", str(root)],
                            text=True, capture_output=True, check=True)
    target = result.stdout.split()[0]
    if target == "/":
        raise RuntimeError("results root resolves to root/ephemeral filesystem; persistent RunPod storage required")
    probe = root / ".persistence_probe"
    probe.write_text("RUNPOD_PERSISTENCE_OK\n")
    descriptor = os.open(probe, os.O_RDONLY)
    try: os.fsync(descriptor)
    finally: os.close(descriptor)
    probe.unlink()
    return result.stdout.strip()


def worker(task_path: Path):
    task = json.loads(task_path.read_text())
    expected_gpu = str(task["gpu_id"])
    if os.environ.get("CUDA_VISIBLE_DEVICES") != expected_gpu:
        raise RuntimeError("worker CUDA_VISIBLE_DEVICES identity mismatch")
    controller = load_serial(); core = controller.load_core()
    controller.RESULTS = Path(task["results_root"])
    core.INPUT = HERE / "input"; core.OUTPUT = controller.RESULTS / "_core"
    candidate = Path(task["candidate"]); temporary = candidate.with_name(candidate.name + ".in_progress")
    elements, source_geometry = controller.read_xyz(Path(task["source_geometry"]))
    recovery = []
    if temporary.exists():
        source_geometry, segment = controller.recover_interrupted_candidate(
            temporary, Path(task["recovery_root"]), source_geometry)
        recovery.append({"path": segment, "manifest_sha256": controller.sha256(Path(segment) / "SHA256SUMS")})
    try:
        geometry, result = controller.optimize_candidate(
            core, elements, source_geometry, task["torsion_atoms"], task["target_degrees"], temporary)
        gates = controller.validate_scientific_geometry(
            elements, source_geometry, geometry, task["torsion_atoms"], task["target_degrees"])
    except Exception as error:
        if not controller.expected_candidate_failure(error):
            raise
        destination = controller.isolate_candidate_failure(
            temporary, Path(task["failure_root"]), task["task_id"], error)
        return {"status": "EXPECTED_CANDIDATE_FAILURE", "task_id": task["task_id"],
                "failure_receipt": str(destination / "CANDIDATE_FAILURE.json")}
    record = {"task_id": task["task_id"], "source_id": task["source_id"],
              "from_degrees": task["from_degrees"], "target_degrees": task["target_degrees"],
              "wavefront_round": task["round"], "energy_hartree": result["total_energy_hartree"],
              "geometry": str(candidate / "final.xyz"), "geometry_gates": gates,
              "recovery_lineage": recovery, "gpu_id": task["gpu_id"]}
    controller.atomic_json(temporary / "WAVEFRONT_RECORD.json", record)
    controller.write_checksums(temporary); os.replace(temporary, candidate)
    controller.verify_checksums(candidate, candidate / "SHA256SUMS")
    return {"status": "COMPLETE", "task_id": task["task_id"],
            "record": str(candidate / "WAVEFRONT_RECORD.json")}


def launch_worker(task_path: Path, gpu_id: int):
    env = os.environ.copy(); env["CUDA_VISIBLE_DEVICES"] = str(gpu_id)
    result = subprocess.run([sys.executable, str(Path(__file__)), "--worker", str(task_path)],
                            env=env, text=True, capture_output=True)
    if result.returncode != 0:
        raise RuntimeError(f"worker programming/controller failure on GPU {gpu_id}: {result.stderr}")
    return json.loads(result.stdout.strip().splitlines()[-1])


def state_checksums(controller, root):
    controller.write_state_checksums(root)
    controller.verify_checksums(root, root / "STATE_SHA256SUMS")


def recover_finalized_candidate(controller, candidate: Path, identifier: str):
    if not candidate.exists(): return None
    if not candidate.is_dir(): raise RuntimeError(f"candidate path is not a directory: {candidate}")
    controller.verify_checksums(candidate, candidate / "SHA256SUMS")
    record = json.loads((candidate / "WAVEFRONT_RECORD.json").read_text())
    if record.get("task_id") != identifier:
        raise RuntimeError(f"finalized candidate identity mismatch: {candidate}")
    return record


def s3_checkpoint(root: Path, s3_uri: str | None, round_number: int):
    if not s3_uri: return None
    destination = f"{s3_uri.rstrip('/')}/round_{round_number:04d}/"
    subprocess.run(["aws", "s3", "sync", str(root), destination, "--only-show-errors"], check=True)
    return destination


def run_psi(results_root: Path, workers: int, s3_uri: str | None):
    controller = load_serial(); manifest = controller.verify_package()
    if (manifest["execution_torsions"][-1] != EXECUTION_AXIS.value
            or manifest["chi_execution_authorized"]):
        raise RuntimeError(f"{EXECUTION_AXIS.value}-only execution contract violated")
    ebs = verify_persistent_storage(results_root); controller.RESULTS = results_root
    root = EXECUTION_AXIS.result_directory(results_root)
    state_path = root / "WAVEFRONT_STATE.json"
    seeds = {seed: controller.read_xyz(HERE / "input" / f"{seed}_verified.xyz")
             for seed in manifest["multistart_seeds"]}
    if state_path.is_file():
        controller.verify_checksums(root, root / "STATE_SHA256SUMS")
        state = json.loads(state_path.read_text())
    else:
        root.mkdir(parents=True, exist_ok=False); queue = []
        torsion = manifest["torsions"][EXECUTION_AXIS.value]
        for seed, (_, geometry) in seeds.items():
            target = controller.nearest_grid(controller.dihedral_degrees(geometry, torsion), manifest["grid_degrees"])
            queue.append({"from_degrees": None, "target_degrees": target,
                          "source_geometry": str(HERE / "input" / f"{seed}_verified.xyz"), "source_id": seed})
        state = {"schema": "torsiondrive-wavefront-state-v1",
                 "torsion": EXECUTION_AXIS.value, "round": 0,
                 "protocol_sha256": controller.sha256(controller.MANIFEST), "cells": {}, "queue": queue,
                 "completed_task_ids": [], "failed_task_ids": [],
                 "energy_decrease_threshold_hartree": 1e-5,
                 "propagation_energy_upper_limit_hartree": 0.05,
                 "runpod_execution": {"max_gpu_workers": workers, "persistent_mount": ebs,
                                      "execution_axis": EXECUTION_AXIS.value}}
        atomic_json(controller, state_path, state); state_checksums(controller, root)
    while state["queue"]:
        round_number = state["round"]; round_dir = root / "round_work" / f"round_{round_number:04d}"
        task_paths = []; existing_records = []; failed = set(state["failed_task_ids"])
        ordered_queue = sorted(state["queue"], key=lambda t: (t["target_degrees"], t["source_id"]))
        assignments = gpu_assignments(len(ordered_queue), workers)
        for task, gpu_id in zip(ordered_queue, assignments):
            identifier = controller.task_id(
                EXECUTION_AXIS.value, task["source_id"], task["target_degrees"])
            candidate = root / "candidates" / identifier
            if identifier in failed: continue
            recovered = recover_finalized_candidate(controller, candidate, identifier)
            if recovered is not None:
                existing_records.append(recovered)
                state["completed_task_ids"].append(identifier)
                continue
            spec = {**task, "task_id": identifier, "round": round_number, "gpu_id": gpu_id,
                    "torsion_atoms": manifest["torsions"][EXECUTION_AXIS.value],
                    "results_root": str(results_root),
                    "candidate": str(candidate), "recovery_root": str(root / "recovery" / identifier),
                    "failure_root": str(root / "failed_candidates")}
            path = round_dir / "tasks" / f"{identifier}.json"; atomic_json(controller, path, spec); task_paths.append((path, gpu_id))
        with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool:
            futures = [pool.submit(launch_worker, path, gpu) for path, gpu in task_paths]
            outputs = [future.result() for future in futures]
        records = list(existing_records)
        for output in outputs:
            if output["status"] == "COMPLETE":
                record_path = Path(output["record"]); records.append(json.loads(record_path.read_text()))
                state["completed_task_ids"].append(output["task_id"])
            else:
                state["failed_task_ids"].append(output["task_id"])
        cells, queue = deterministic_reduce(controller, state["cells"], records, manifest)
        state.update({"cells": cells, "queue": queue, "round": round_number + 1,
                      "completed_task_ids": sorted(set(state["completed_task_ids"])),
                      "failed_task_ids": sorted(set(state["failed_task_ids"]))})
        atomic_json(controller, state_path, state); state_checksums(controller, root)
        mirror = s3_checkpoint(root, s3_uri, state["round"])
        atomic_json(controller, round_dir / "ROUND_RECEIPT.json", {
            "round": round_number, "worker_count": workers, "active_workers_after_barrier": 0,
            "state_sha256": controller.sha256(state_path), "s3_checkpoint": mirror})
    controller.authoritative_cell_manifest(root, state, controller.sha256(controller.MANIFEST))
    receipt = completion_receipt(
        EXECUTION_AXIS,
        state_sha256=controller.sha256(state_path),
        state_checksums_sha256=controller.sha256(root / "STATE_SHA256SUMS"))
    atomic_json(controller, root / "COMPLETION_RECEIPT.json", receipt)
    controller.write_checksums(root); return receipt


def status(root: Path):
    return persisted_status(root, EXECUTION_AXIS)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument("--worker", type=Path)
    parser.add_argument("--run-psi", action="store_true"); parser.add_argument("--status", action="store_true")
    parser.add_argument("--verify-shutdown", action="store_true"); parser.add_argument("--workers", type=int, default=8)
    parser.add_argument("--results-root", type=Path, default=DEFAULT_PERSISTENT_ROOT); parser.add_argument("--s3-uri")
    args = parser.parse_args()
    if args.worker: print(json.dumps(worker(args.worker), sort_keys=True)); return
    if args.status: print(json.dumps(status(args.results_root), indent=2)); return
    if args.verify_shutdown:
        value = status(args.results_root)
        if value.get("status") != "COMPLETE": raise RuntimeError(f"shutdown prohibited: {value}")
        print("SHUTDOWN_SAFE=true"); return
    if args.run_psi: print(json.dumps(run_psi(args.results_root, args.workers, args.s3_uri), indent=2)); return
    parser.error("choose --run-psi, --status, --verify-shutdown, or --worker")


if __name__ == "__main__": main()
