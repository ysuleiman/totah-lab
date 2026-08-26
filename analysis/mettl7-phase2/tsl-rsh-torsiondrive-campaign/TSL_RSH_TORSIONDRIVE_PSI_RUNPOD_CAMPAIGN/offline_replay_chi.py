#!/usr/bin/env python3
"""Offline canonical replay of persisted CHI candidate records. Never imports QM."""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACKAGE = HERE if (HERE / "CAMPAIGN_MANIFEST.json").is_file() else HERE / "TSL_RSH_TORSIONDRIVE_A100_CAMPAIGN"
RUNNER = PACKAGE / "run_torsiondrive_a100.py"

spec = importlib.util.spec_from_file_location("torsion_controller", RUNNER)
controller = importlib.util.module_from_spec(spec); spec.loader.exec_module(controller)


def infer_source_id(record, all_ids, seeds):
    if "source_id" in record:
        return record["source_id"]
    matches = [source for source in list(seeds) + list(all_ids)
               if controller.task_id("CHI", source, record["target_degrees"]) == record["task_id"]]
    if len(matches) != 1:
        raise RuntimeError(f"cannot uniquely reconstruct source for {record['task_id']}: {matches}")
    return matches[0]


def replay_records(records):
    records = list(records)
    by_id = {record["task_id"]: record for record in records}
    if len(by_id) != len(records):
        raise RuntimeError("candidate task identity collision")
    manifest = json.loads((PACKAGE / "CAMPAIGN_MANIFEST.json").read_text())
    seeds = {}
    for seed in manifest["multistart_seeds"]:
        _, geometry = controller.read_xyz(PACKAGE / "input" / f"{seed}_verified.xyz")
        target = controller.nearest_grid(
            controller.dihedral_degrees(geometry, manifest["torsions"]["CHI"]), manifest["grid_degrees"])
        seeds[seed] = target
    sources = {identifier: infer_source_id(record, by_id, seeds) for identifier, record in by_id.items()}
    queue = [{"source_id": seed, "target_degrees": target} for seed, target in seeds.items()]
    cells = {}; used = set(); events = []; round_number = 0; missing = []
    while queue:
        finished = []
        for task in queue:
            identifier = controller.task_id("CHI", task["source_id"], task["target_degrees"])
            record = by_id.get(identifier)
            if record is None:
                missing.append(identifier); continue
            used.add(identifier); finished.append(record)
        if missing:
            break
        before = {key: value["energy_hartree"] for key, value in cells.items()}
        cells, spawned = controller.wavefront_transition(
            cells, finished, manifest["grid_degrees"], manifest["energy_upper_limit_hartree"],
            manifest["energy_decrease_threshold_hartree"])
        for record in finished:
            key = str(record["target_degrees"])
            old = before.get(key)
            if old is not None and record["energy_hartree"] < old:
                decrease = old - record["energy_hartree"]
                events.append({"round": round_number, "cell": int(key), "old_energy_hartree": old,
                    "new_energy_hartree": record["energy_hartree"], "decrease_hartree": decrease,
                    "reactivated": decrease > manifest["energy_decrease_threshold_hartree"]})
        unique = {}
        for task in spawned:
            identifier = controller.task_id("CHI", task["source_id"], task["target_degrees"])
            if identifier not in used:
                unique[identifier] = task
        queue = list(unique.values()); round_number += 1
    return {"candidate_record_count": len(records), "used_candidate_count": len(used),
            "unnecessary_candidate_count": len(records) - len(used), "populated_cells": len(cells),
            "subthreshold_reactivation_count": sum(not event["reactivated"] for event in events),
            "energy_improvement_events": events, "converged": not queue and not missing,
            "corrected_convergence_round": round_number if not queue and not missing else None,
            "missing_required_task_ids": missing}


def reconstruct_historical_reactivations(records):
    records = list(records)
    by_id = {record["task_id"]: record for record in records}
    seeds = {"MIN01", "MIN02", "MIN04"}
    sources = {identifier: infer_source_id(record, by_id, seeds) for identifier, record in by_id.items()}
    memo = {}
    def depth(identifier, trail=()):
        if identifier in memo: return memo[identifier]
        if identifier in trail: raise RuntimeError("candidate source cycle")
        source = sources[identifier]
        value = 0 if source in seeds else depth(source, trail + (identifier,)) + 1
        memo[identifier] = value
        return value
    rounds = {}
    for identifier, record in by_id.items():
        rounds.setdefault(depth(identifier), []).append(record)
    cells = {}; events = []
    for round_number in sorted(rounds):
        best = {}
        for record in rounds[round_number]:
            key = str(record["target_degrees"])
            if key not in best or record["energy_hartree"] < best[key]["energy_hartree"]:
                best[key] = record
        before = {key: record["energy_hartree"] for key, record in cells.items()}
        for key, record in best.items():
            old = before.get(key)
            if old is not None and record["energy_hartree"] < old:
                decrease = old - record["energy_hartree"]
                events.append({"round": round_number, "cell": int(key),
                    "decrease_hartree": decrease, "below_canonical_threshold": decrease <= 1e-5})
        cells, _ = controller.wavefront_transition(cells, list(best.values()),
            list(range(-180, 180, 15)), 0.05, 0.0)
    return {"historical_round_count": max(rounds) + 1 if rounds else 0,
            "historical_energy_decrease_reactivations": len(events),
            "historical_subthreshold_reactivations": sum(
                event["below_canonical_threshold"] for event in events),
            "historical_reactivation_events": events}


def replay(candidate_root: Path):
    paths = sorted(candidate_root.glob("*/WAVEFRONT_RECORD.json"))
    for path in paths:
        controller.verify_checksums(path.parent, path.parent / "SHA256SUMS")
    records = [json.loads(path.read_text()) for path in paths]
    return replay_records(records)


def replay_export(export_path: Path, local_state_path: Path):
    export = json.loads(export_path.read_text())
    if export.get("all_record_checksums_pass") is not True:
        raise RuntimeError("export contains checksum-invalid candidate records")
    if controller.sha256(local_state_path) != export["state_sha256"]:
        raise RuntimeError("export state identity does not match preserved local state")
    rows = export["candidate_records"]
    if len(rows) != export["candidate_record_count"]:
        raise RuntimeError("candidate export count mismatch")
    records = [row["record"] for row in rows]
    result = replay_records(records)
    result.update(reconstruct_historical_reactivations(records))
    state = export["state"]
    all_best = {}
    for record in records:
        key = str(record["target_degrees"])
        if key not in all_best or record["energy_hartree"] < all_best[key]:
            all_best[key] = record["energy_hartree"]
    if set(all_best) != set(state["cells"]):
        raise RuntimeError("final state cell set differs from all-record authority")
    differences = {key: state["cells"][key]["energy_hartree"] - all_best[key] for key in all_best}
    result["all_paid_results_authoritative_best_reconstructed"] = True
    result["persisted_state_stale_cell_count"] = sum(value != 0.0 for value in differences.values())
    result["persisted_state_max_stale_energy_hartree"] = max(abs(value) for value in differences.values())
    result["all_paid_result_best_energies_hartree"] = all_best
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("candidate_root", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    arguments = parser.parse_args()
    result = replay(arguments.candidate_root)
    arguments.output.write_text(json.dumps(result, indent=2, sort_keys=True) + "\n")
    print(json.dumps(result, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
