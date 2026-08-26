#!/usr/bin/env python3
import importlib.util, json, random, tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
def load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module); return module

controller = load("mg_controller", HERE / "run_torsiondrive_a100.py")
scheduler = load("mg_scheduler", HERE / "run_multigpu_aws.py")
manifest = json.loads((HERE / "CAMPAIGN_MANIFEST.json").read_text())
grid = manifest["grid_degrees"]

records = [
    {"task_id": "z", "source_id": "s3", "target_degrees": 0, "energy_hartree": -10.000020, "geometry": "z"},
    {"task_id": "a", "source_id": "s1", "target_degrees": 0, "energy_hartree": -10.000006, "geometry": "a"},
    {"task_id": "b", "source_id": "s2", "target_degrees": 15, "energy_hartree": -10.0, "geometry": "b"},
]
initial = {"0": {"task_id": "old", "source_id": "old", "target_degrees": 0,
                  "energy_hartree": -10.0, "geometry": "old"}}
expected_cells, expected_queue = scheduler.deterministic_reduce(controller, initial, records, manifest)
assert expected_cells["0"]["task_id"] == "z"
assert len([task for task in expected_queue if task["from_degrees"] == 0]) == 2

for seed in range(100):
    shuffled = list(records); random.Random(seed).shuffle(shuffled)
    cells, queue = scheduler.deterministic_reduce(controller, initial, shuffled, manifest)
    assert json.dumps(cells, sort_keys=True) == json.dumps(expected_cells, sort_keys=True)
    assert json.dumps(queue, sort_keys=True) == json.dumps(expected_queue, sort_keys=True)

sub_cells, sub_queue = scheduler.deterministic_reduce(controller, initial, [
    {"task_id": "tiny", "source_id": "s", "target_degrees": 0,
     "energy_hartree": -10.000009, "geometry": "tiny"}], manifest)
assert sub_cells["0"]["task_id"] == "tiny" and sub_queue == []

assert scheduler.gpu_assignments(8) == list(range(8))
assert scheduler.gpu_assignments(10) == [0,1,2,3,4,5,6,7,0,1]
assert manifest["execution_torsions"] == ["PHI", "PSI"]
assert manifest["chi_execution_authorized"] is False
assert "run_phi" in scheduler.run_phi.__name__ and not hasattr(scheduler, "run_chi")

class BrokenPersistence:
    @staticmethod
    def atomic_json(path, value): raise OSError("EBS write failure")
with tempfile.TemporaryDirectory() as tmp:
    try: scheduler.atomic_json(BrokenPersistence, Path(tmp) / "state.json", {})
    except OSError: pass
    else: raise AssertionError("persistence failure did not fail closed")

# Controller restart after worker finalization but before state commit discovers
# and reuses the durable candidate without invoking a worker.
with tempfile.TemporaryDirectory() as tmp:
    candidate = Path(tmp) / "candidate"; candidate.mkdir()
    controller.atomic_json(candidate / "WAVEFRONT_RECORD.json", {
        "task_id": "durable", "target_degrees": 0, "energy_hartree": -10.0})
    controller.write_checksums(candidate)
    recovered = scheduler.recover_finalized_candidate(controller, candidate, "durable")
    assert recovered["task_id"] == "durable"
    try: scheduler.recover_finalized_candidate(controller, candidate, "wrong")
    except RuntimeError: pass
    else: raise AssertionError("candidate identity mismatch did not fail closed")

source = (HERE / "run_multigpu_aws.py").read_text()
assert 'env["CUDA_VISIBLE_DEVICES"] = str(gpu_id)' in source
assert 'outputs = [future.result() for future in futures]' in source
assert source.index('outputs = [future.result() for future in futures]') < source.index(
    'cells, queue = deterministic_reduce', source.index('def run_phi'))

print("MULTIGPU_TESTS_PASS=115")
