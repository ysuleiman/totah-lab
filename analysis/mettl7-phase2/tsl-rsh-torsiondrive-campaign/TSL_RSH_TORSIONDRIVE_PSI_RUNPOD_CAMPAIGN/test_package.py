#!/usr/bin/env python3
import hashlib, importlib.util, json, tempfile
from pathlib import Path
import numpy as np

HERE = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("runner", HERE / "run_torsiondrive_a100.py")
runner = importlib.util.module_from_spec(spec); spec.loader.exec_module(runner)


def expect_failure(callable_):
    try: callable_()
    except (RuntimeError, runner.CandidateRejected): return
    raise AssertionError("operation did not fail closed")


manifest = runner.verify_package()
dependency_audit = runner.audit_runtime_dependencies()
assert dependency_audit["missing"] == [] and dependency_audit["uncovered"] == []
assert manifest["total_possible_cells"] == 72
assert set(manifest["torsions"]) == {"CHI", "PHI", "PSI"}
assert all(len(v) == 4 for v in manifest["torsions"].values())
assert manifest["grid_degrees"] == list(range(-180, 180, 15))
assert manifest["energy_upper_limit_hartree"] == 0.05
assert manifest["energy_decrease_threshold_hartree"] == 1e-5
assert manifest["multistart_seeds"] == ["MIN01", "MIN02", "MIN04"]
assert manifest["execution_torsions"] == ["PHI", "PSI"]
assert manifest["execution_possible_cells"] == 48
assert manifest["chi_execution_authorized"] is False
seal = json.loads((HERE / "PACKAGE_SEAL.json").read_text())
assert seal["status"] == "SEALED_NOT_EXECUTED"
assert seal["execution_torsions"] == ["PHI", "PSI"] and seal["chi_execution_authorized"] is False

fake_runtime = {"gpu_name": "NVIDIA A100-SXM4-40GB", "cuda_device_count": 1,
                "campaign_manifest_sha256": runner.sha256(runner.MANIFEST),
                "runner_sha256": runner.sha256(Path(runner.__file__))}
smoke = runner.pre_qm_production_smoke(lambda: fake_runtime)
assert smoke["status"] == "PRE_QM_SMOKE_PASS"
assert smoke["mass_vector_sha256"] == "97aeb1dfa06c145e9c7b2712959968acbcc31c987493f6c4d855811f3f019e05"

for seed in manifest["multistart_seeds"]:
    elements, coordinates = runner.read_xyz(HERE / "input" / f"{seed}_verified.xyz")
    assert len(elements) == 56 and coordinates.shape == (56, 3)

# Genuine wavefront: only neighbors of improved cells spawn, and the energy
# upper limit suppresses propagation without deleting the populated cell.
grid = manifest["grid_degrees"]
cells, tasks = runner.wavefront_transition({}, [{"task_id": "a", "target_degrees": 0,
    "energy_hartree": -10.0, "geometry": "g"}], grid, 0.05)
assert {task["target_degrees"] for task in tasks} == {-15, 15}
cells, tasks = runner.wavefront_transition(cells, [{"task_id": "b", "target_degrees": 15,
    "energy_hartree": -9.9, "geometry": "g"}], grid, 0.05)
assert tasks == [] and "15" in cells

# Canonical 1e-5 Eh activation threshold is independent of exact best-state
# retention. A microscopic improvement updates the authority but does not spawn.
base = {"0": {"task_id": "old", "target_degrees": 0,
              "energy_hartree": -10.0, "geometry": "old"}}
cells, tasks = runner.wavefront_transition(base, [{"task_id": "tiny", "target_degrees": 0,
    "energy_hartree": -10.000009, "geometry": "tiny"}], grid, 0.05, 1e-5)
assert cells["0"]["task_id"] == "tiny" and tasks == []
cells, tasks = runner.wavefront_transition(cells, [{"task_id": "large", "target_degrees": 0,
    "energy_hartree": -10.000020, "geometry": "large"}], grid, 0.05, 1e-5)
assert cells["0"]["task_id"] == "large"
assert {task["target_degrees"] for task in tasks} == {-15, 15}
assert set(runner.neighbor_cells(-180, grid)) == {-165, 165}

# Same-round candidates are reduced to the best result before activation. Two
# individually sub-threshold refinements whose total exceeds the threshold must
# reactivate exactly once from the best geometry.
base = {"0": {"task_id": "old", "target_degrees": 0,
              "energy_hartree": -10.0, "geometry": "old"}}
same_round = [{"task_id": "first", "target_degrees": 0,
               "energy_hartree": -10.000006, "geometry": "first"},
              {"task_id": "best", "target_degrees": 0,
               "energy_hartree": -10.000012, "geometry": "best"}]
base, tasks = runner.wavefront_transition(base, same_round, grid, 0.05, 1e-5)
assert base["0"]["task_id"] == "best" and len(tasks) == 2

# A fully populated surface with only sub-threshold refinements terminates.
stable = {str(angle): {"task_id": f"old-{angle}", "target_degrees": angle,
          "energy_hartree": -10.0, "geometry": "g"} for angle in grid}
refinements = [{"task_id": f"new-{angle}", "target_degrees": angle,
                "energy_hartree": -10.000001, "geometry": "g"} for angle in grid]
stable, tasks = runner.wavefront_transition(stable, refinements, grid, 0.05, 1e-5)
assert len(stable) == 24 and tasks == []

# Scientific geometry gate passes an unchanged structure and fails a broken bond.
elements, geometry = runner.read_xyz(HERE / "input/MIN01_verified.xyz")
target = runner.dihedral_degrees(geometry, manifest["torsions"]["CHI"])
assert runner.validate_scientific_geometry(elements, geometry, geometry.copy(),
    manifest["torsions"]["CHI"], target)["connectivity_pass"]
broken = geometry.copy(); broken[55] += np.asarray([20.0, 0.0, 0.0])
expect_failure(lambda: runner.validate_scientific_geometry(elements, geometry, broken,
    manifest["torsions"]["CHI"], target))

# Interruption/resume: a finalized task is checksum verified and reloaded; a
# modified task fails closed. This exercises the same nested-manifest reader.
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp); candidate = root / "candidate"; candidate.mkdir()
    runner.atomic_json(candidate / "WAVEFRONT_RECORD.json", {"task_id": "resume", "energy_hartree": -1.0})
    runner.write_checksums(candidate)
    runner.verify_checksums(candidate, candidate / "SHA256SUMS")
    assert json.loads((candidate / "WAVEFRONT_RECORD.json").read_text())["task_id"] == "resume"
    (candidate / "WAVEFRONT_RECORD.json").write_text("{}\n")
    expect_failure(lambda: runner.verify_checksums(candidate, candidate / "SHA256SUMS"))

# Mid-candidate recovery consumes the latest checksum-valid persisted gradient
# step, archives the interrupted segment, and returns its exact geometry.
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp); partial = root / "task.in_progress"
    step = partial / "steps/step_0000"; step.mkdir(parents=True)
    source_xyz = HERE / "input/MIN01_verified.xyz"
    (step / "geometry.xyz").write_bytes(source_xyz.read_bytes())
    runner.write_checksums(step)
    _, expected_coordinates = runner.read_xyz(source_xyz)
    recovered, lineage = runner.recover_interrupted_candidate(
        partial, root / "recovery", np.zeros((56, 3)))
    assert np.array_equal(recovered, expected_coordinates)
    assert Path(lineage).is_dir() and (Path(lineage) / "RECOVERY_RECEIPT.json").is_file()

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp); partial = root / "task.in_progress"
    step = partial / "steps/step_0000"; step.mkdir(parents=True)
    (step / "geometry.xyz").write_bytes((HERE / "input/MIN01_verified.xyz").read_bytes())
    runner.write_checksums(step); (step / "geometry.xyz").write_text("corrupt\n")
    recovered, lineage = runner.recover_interrupted_candidate(
        partial, root / "recovery", np.zeros((56, 3)))
    receipt = json.loads((Path(lineage) / "RECOVERY_RECEIPT.json").read_text())
    assert receipt["partial_trailing_step_excluded"] is True
    assert receipt["complete_steps_recovered"] == 0
    assert np.array_equal(recovered, np.zeros((56, 3)))

with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp); partial = root / "task.in_progress"
    for index in range(2):
        step = partial / f"steps/step_{index:04d}"; step.mkdir(parents=True)
        (step / "geometry.xyz").write_bytes((HERE / "input/MIN01_verified.xyz").read_bytes())
        runner.write_checksums(step)
    (partial / "steps/step_0000/geometry.xyz").write_text("corrupt\n")
    expect_failure(lambda: runner.recover_interrupted_candidate(
        partial, root / "recovery", np.zeros((56, 3))))

# Expected scientific candidate failures are isolated with evidence; unrelated
# exceptions are not accepted by this boundary and therefore still propagate.
with tempfile.TemporaryDirectory() as tmp:
    root = Path(tmp); partial = root / "candidate.in_progress"; partial.mkdir()
    receipt = runner.isolate_candidate_failure(partial, root / "failed", "task-x",
                                                runner.CandidateRejected("connectivity"))
    assert json.loads((receipt / "CANDIDATE_FAILURE.json").read_text())["status"] == "ISOLATED_CANDIDATE_FAILURE"
    assert not partial.exists()

assert runner.expected_candidate_failure(RuntimeError("SCF did not converge")) is True
wrapped = RuntimeError("optimizer wrapper")
wrapped.__cause__ = RuntimeError("SCF did not converge")
assert runner.expected_candidate_failure(wrapped) is True
assert runner.expected_candidate_failure(TypeError("bad tuple contract")) is False
assert runner.expected_candidate_failure(AttributeError("missing shape")) is False

# A deployed zero-threshold state cannot resume into paid work until an offline
# replay receipt has been generated and independently reviewed.
with tempfile.TemporaryDirectory() as tmp:
    original_results = runner.RESULTS; runner.RESULTS = Path(tmp) / "results"
    root = runner.RESULTS / "CHI"; root.mkdir(parents=True)
    runner.atomic_json(root / "WAVEFRONT_STATE.json", {
        "protocol_sha256": runner.LEGACY_ZERO_THRESHOLD_MANIFEST_SHA256,
        "queue": [{"source_id": "legacy", "target_degrees": 0}], "cells": {},
        "round": 13, "completed_task_ids": [], "failed_task_ids": []})
    runner.write_state_checksums(root)
    seeds = {seed: runner.read_xyz(HERE / "input" / f"{seed}_verified.xyz")
             for seed in manifest["multistart_seeds"]}
    expect_failure(lambda: runner.execute_wavefront(object(), manifest, "CHI",
        manifest["torsions"]["CHI"], seeds, runner.sha256(runner.MANIFEST)))
    runner.RESULTS = original_results

# End-to-end interruption/resume of the production wavefront orchestration,
# with the expensive constrained optimizer replaced by a deterministic stub.
with tempfile.TemporaryDirectory() as tmp:
    original_results = runner.RESULTS
    original_optimize = runner.optimize_candidate
    original_gate = runner.validate_scientific_geometry
    runner.RESULTS = Path(tmp) / "results"
    calls = {"count": 0, "first_task_calls": 0, "interrupt": True}
    def stub_optimize(core, elements, start, torsion, target, output):
        calls["count"] += 1
        if calls["count"] == 1: calls["first_task_calls"] += 1
        if calls["interrupt"] and calls["count"] == 2:
            raise RuntimeError("simulated interruption")
        output.mkdir(parents=True)
        runner.atomic_text(output / "final.xyz", Path(HERE / "input/MIN01_verified.xyz").read_text())
        # A shallow surface lets propagation complete while still exercising
        # the actual frontier and state transitions.
        return start.copy(), {"total_energy_hartree": -100.0 + abs(target) * 1e-6}
    runner.optimize_candidate = stub_optimize
    runner.validate_scientific_geometry = lambda *args: {"connectivity_pass": True,
        "chirality_pass": True, "dihedral_error_degrees": 0.0}
    seeds = {seed: runner.read_xyz(HERE / "input" / f"{seed}_verified.xyz")
             for seed in manifest["multistart_seeds"]}
    expect_failure(lambda: runner.execute_wavefront(object(), manifest, "CHI",
        manifest["torsions"]["CHI"], seeds, "protocol"))
    calls["interrupt"] = False
    gate_calls = {"count": 0}
    def reject_one_candidate(*args):
        gate_calls["count"] += 1
        if gate_calls["count"] == 1:
            raise runner.CandidateRejected("synthetic connectivity failure")
        return {"connectivity_pass": True, "chirality_pass": True, "dihedral_error_degrees": 0.0}
    runner.validate_scientific_geometry = reject_one_candidate
    state = runner.execute_wavefront(object(), manifest, "CHI",
        manifest["torsions"]["CHI"], seeds, "protocol")
    assert not state["queue"] and len(state["cells"]) > 0
    assert len(state["failed_task_ids"]) == 1
    assert calls["first_task_calls"] == 1
    runner.RESULTS = original_results
    runner.optimize_candidate = original_optimize
    runner.validate_scientific_geometry = original_gate

# The production boundary must not turn a programming exception into an
# ordinary failed candidate.
with tempfile.TemporaryDirectory() as tmp:
    original_results = runner.RESULTS; original_optimize = runner.optimize_candidate
    runner.RESULTS = Path(tmp) / "results"
    runner.optimize_candidate = lambda *args: (_ for _ in ()).throw(TypeError("programming defect"))
    seeds = {seed: runner.read_xyz(HERE / "input" / f"{seed}_verified.xyz")
             for seed in manifest["multistart_seeds"]}
    try:
        runner.execute_wavefront(object(), manifest, "CHI", manifest["torsions"]["CHI"], seeds, "protocol")
    except TypeError as error:
        assert str(error) == "programming defect"
    else:
        raise AssertionError("programming exception was swallowed")
    assert not (runner.RESULTS / "CHI/failed_candidates").exists()
    runner.RESULTS = original_results; runner.optimize_candidate = original_optimize

# Publication regression: populated count derives from states, and the frozen
# coupling diagnostic consumes the actual endpoint geometries.
states = {"CHI": {"cells": {"0": {"geometry": str(HERE / "input/MIN01_verified.xyz")}}},
          "PHI": {"cells": {}}, "PSI": {"cells": {}}}
diagnostic = runner.coupling_diagnostic(states, manifest["torsions"])
assert len(diagnostic["cells"]) == 1 and diagnostic["frozen_before_qm"] is True

print("PACKAGE_TESTS_PASS=58")
