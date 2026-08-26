#!/usr/bin/env python3
"""Deterministic no-QM replay regression using synthetic persisted records."""
import importlib.util
import json
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent

def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module

runner = load("replay_runner", HERE / "run_torsiondrive_a100.py")
replay = load("offline_replay", HERE / "offline_replay_chi.py")
manifest = json.loads((HERE / "CAMPAIGN_MANIFEST.json").read_text())
grid = manifest["grid_degrees"]
seeds = {}
for seed in manifest["multistart_seeds"]:
    _, geometry = runner.read_xyz(HERE / "input" / f"{seed}_verified.xyz")
    seeds[seed] = runner.nearest_grid(runner.dihedral_degrees(
        geometry, manifest["torsions"]["CHI"]), grid)

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp); queue = [{"source_id": seed, "target_degrees": target}
                              for seed, target in seeds.items()]
    cells = {}; completed = set(); round_number = 0
    while queue:
        finished = []
        for task in queue:
            identifier = runner.task_id("CHI", task["source_id"], task["target_degrees"])
            if identifier in completed: continue
            completed.add(identifier)
            record = {"task_id": identifier, "source_id": task["source_id"],
                      "from_degrees": task.get("from_degrees"),
                      "target_degrees": task["target_degrees"], "wavefront_round": round_number,
                      "energy_hartree": -10.0, "geometry": "synthetic"}
            directory = root / identifier; directory.mkdir()
            runner.atomic_json(directory / "WAVEFRONT_RECORD.json", record)
            runner.write_checksums(directory); finished.append(record)
        cells, spawned = runner.wavefront_transition(cells, finished, grid, 0.05, 1e-5)
        unique = {}
        for task in spawned:
            identifier = runner.task_id("CHI", task["source_id"], task["target_degrees"])
            if identifier not in completed: unique[identifier] = task
        queue = list(unique.values()); round_number += 1
    first = replay.replay(root); second = replay.replay(root)
    assert first == second
    assert first["converged"] is True and first["populated_cells"] == 24
    assert first["unnecessary_candidate_count"] == 0

print("OFFLINE_REPLAY_TESTS_PASS=4")
