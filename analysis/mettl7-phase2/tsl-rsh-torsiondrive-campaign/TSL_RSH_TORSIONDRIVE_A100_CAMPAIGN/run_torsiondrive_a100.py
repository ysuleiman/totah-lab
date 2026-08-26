#!/usr/bin/env python3
"""Checksum-safe multistart relaxed torsion scans; intended for A100 only."""
from __future__ import annotations

import hashlib
import ast
import csv
import importlib.util
import json
import os
import platform
import shutil
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
MANIFEST = HERE / "CAMPAIGN_MANIFEST.json"
CHECKSUMS = HERE / "PACKAGE_SHA256SUMS"
CORE = HERE / "qualified_level5_derivative_core.py"
RESULTS = HERE / "results"
LEGACY_ZERO_THRESHOLD_MANIFEST_SHA256 = "00fcbd5d31dd742409e8c87e66da1caa2aeaa8857323d2ceaa508a84c9d0b896"
class CandidateRejected(Exception):
    """Expected scientific/numerical candidate failure; safe to isolate."""


EXPECTED_QM_FAILURE_MESSAGES = {
    "SCF did not converge",
    "geomeTRIC candidate failure",
}


def expected_candidate_failure(error: BaseException) -> bool:
    """Classify narrowly; programming and protocol errors remain fatal."""
    visited = set()
    current = error
    while current is not None and id(current) not in visited:
        visited.add(id(current))
        if isinstance(current, CandidateRejected):
            return True
        message = str(current)
        if any(message == token or message.startswith(token + ":")
               for token in EXPECTED_QM_FAILURE_MESSAGES):
            return True
        current = current.__cause__ or current.__context__
    return False


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def atomic_text(path: Path, value: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(value)
    os.replace(temporary, path)


def atomic_json(path: Path, value) -> None:
    atomic_text(path, json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def write_checksums(root: Path) -> None:
    files = sorted(p for p in root.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
    atomic_text(root / "SHA256SUMS", "".join(
        f"{sha256(path)}  {path.relative_to(root)}\n" for path in files))


def verify_checksums(root: Path, manifest: Path) -> None:
    if not manifest.is_file():
        raise RuntimeError(f"missing checksum manifest: {manifest}")
    listed = set()
    for line in manifest.read_text().splitlines():
        expected, relative = line.split(maxsplit=1)
        path = root / relative.strip()
        listed.add(relative.strip())
        if not path.is_file() or sha256(path) != expected:
            raise RuntimeError(f"missing or modified artifact: {path}")
    if not listed:
        raise RuntimeError(f"empty checksum manifest: {manifest}")


def verify_package() -> dict:
    verify_checksums(HERE, CHECKSUMS)
    manifest = json.loads(MANIFEST.read_text())
    if manifest["grid_spacing_degrees"] != 15 or len(manifest["grid_degrees"]) != 24:
        raise RuntimeError("grid contract changed")
    if manifest["total_possible_cells"] != 72 or manifest["energy_upper_limit_hartree"] != 0.05:
        raise RuntimeError("campaign size or energy limit changed")
    if (manifest["execution_torsions"] != ["PHI", "PSI"]
            or manifest["execution_possible_cells"] != 48
            or manifest["chi_execution_authorized"] is not False):
        raise RuntimeError("PHI/PSI-only execution scope changed")
    if manifest["energy_decrease_threshold_hartree"] != 1e-5:
        raise RuntimeError("canonical energy-decrease threshold changed")
    if manifest["multistart_seeds"] != ["MIN01", "MIN02", "MIN04"]:
        raise RuntimeError("multistart identity changed")
    return manifest


def required_runtime_files():
    """Enumerate literal package-relative dependencies in the included core."""
    tree = ast.parse(CORE.read_text())
    required = {MANIFEST, CHECKSUMS, CORE}
    for node in ast.walk(tree):
        if (isinstance(node, ast.BinOp) and isinstance(node.op, ast.Div)
                and isinstance(node.right, ast.Constant) and isinstance(node.right.value, str)
                and isinstance(node.left, ast.Name)):
            if node.left.id == "INPUT":
                required.add(HERE / "input" / node.right.value)
            elif node.left.id == "ROOT" and node.right.value not in {"input", "results"}:
                required.add(HERE / node.right.value)
    required.update(HERE / "input" / f"{seed}_verified.xyz" for seed in ("MIN01", "MIN02", "MIN04"))
    return sorted(required)


def audit_runtime_dependencies():
    missing = [str(path.relative_to(HERE)) for path in required_runtime_files() if not path.is_file()]
    if missing:
        raise RuntimeError(f"missing packaged runtime dependencies: {missing}")
    checksummed = {line.split(maxsplit=1)[1].strip() for line in CHECKSUMS.read_text().splitlines()}
    uncovered = [str(path.relative_to(HERE)) for path in required_runtime_files()
                 if path not in (CHECKSUMS,) and str(path.relative_to(HERE)) not in checksummed]
    if uncovered:
        raise RuntimeError(f"runtime dependencies absent from package checksums: {uncovered}")
    return {"required_file_count": len(required_runtime_files()), "missing": [], "uncovered": []}


def pre_qm_production_smoke(runtime_loader= None):
    """Load the production package contract and immutable inputs without QM."""
    manifest = verify_package()
    dependency_audit = audit_runtime_dependencies()
    core = load_core(); core.INPUT = HERE / "input"
    mass_record = json.loads((HERE / "input/MASS_VECTOR.json").read_text())
    masses = np.asarray(mass_record["mass_vector_amu"], dtype=float)
    if masses.shape != (56,) or not np.isfinite(masses).all() or np.any(masses <= 0):
        raise RuntimeError("invalid packaged mass vector")
    class PersistedMassMolecule:
        def atom_mass_list(self, isotope_avg=True):
            if isotope_avg is not True:
                raise RuntimeError("mass convention changed")
            return masses.copy()
    if not np.array_equal(core.mass_vector(PersistedMassMolecule()), masses):
        raise RuntimeError("production core did not consume the packaged mass vector")
    with (HERE / "input/ATOM_ORDER.csv").open(newline="") as source:
        atom_order = list(csv.DictReader(source))
    if len(atom_order) != 56:
        raise RuntimeError("invalid packaged atom order")
    geometry_hashes = {}
    for seed in manifest["multistart_seeds"]:
        elements, coordinates = read_xyz(HERE / "input" / f"{seed}_verified.xyz")
        if elements != [row["element"] for row in atom_order] or coordinates.shape != (56, 3):
            raise RuntimeError(f"geometry/atom-order mismatch: {seed}")
        geometry_hashes[seed] = sha256(HERE / "input" / f"{seed}_verified.xyz")
    if core.D3_PARAMETERS != {"s6": 1.0, "s8": 0.7875, "s9": 0.0,
                              "a1": 0.4289, "a2": 4.4407, "alp": 14.0}:
        raise RuntimeError("D3 configuration identity changed")
    runtime = (runtime_loader or runtime_receipt)()
    if runtime.get("campaign_manifest_sha256") != sha256(MANIFEST):
        raise RuntimeError("runtime/campaign identity mismatch")
    return {"status": "PRE_QM_SMOKE_PASS", "dependency_audit": dependency_audit,
            "mass_vector_sha256": sha256(HERE / "input/MASS_VECTOR.json"),
            "atom_order_sha256": sha256(HERE / "input/ATOM_ORDER.csv"),
            "geometry_sha256": geometry_hashes, "d3_parameters": core.D3_PARAMETERS,
            "runtime_identity": runtime}


def runtime_receipt() -> dict:
    import cupy as cp
    if cp.cuda.runtime.getDeviceCount() != 1:
        raise RuntimeError("exactly one CUDA device is required")
    props = cp.cuda.runtime.getDeviceProperties(0)
    name = props.get("name", props.get(b"name", b""))
    if isinstance(name, bytes):
        name = name.decode("utf-8")
    if "A100" not in str(name):
        raise RuntimeError(f"A100 required, found {name!r}")
    return {"gpu_name": str(name), "cuda_device_count": 1,
            "python": platform.python_version(), "platform": platform.platform(),
            "campaign_manifest_sha256": sha256(MANIFEST), "runner_sha256": sha256(Path(__file__))}


def load_core():
    spec = importlib.util.spec_from_file_location("qualified_level5_core", CORE)
    module = importlib.util.module_from_spec(spec)
    sys.modules[spec.name] = module
    spec.loader.exec_module(module)
    return module


def read_xyz(path: Path):
    lines = path.read_text().splitlines(); count = int(lines[0])
    rows = [line.split() for line in lines[2:2 + count]]
    if count != 56 or len(rows) != 56:
        raise RuntimeError(f"invalid atom count: {path}")
    return [r[0] for r in rows], np.asarray([[float(x) for x in r[1:4]] for r in rows])


def dihedral_degrees(coordinates, indices):
    p0, p1, p2, p3 = (coordinates[index] for index in indices)
    b0, b1, b2 = p0 - p1, p2 - p1, p3 - p2
    b1 /= np.linalg.norm(b1)
    v = b0 - np.dot(b0, b1) * b1
    w = b2 - np.dot(b2, b1) * b1
    return float(np.degrees(np.arctan2(np.dot(np.cross(b1, v), w), np.dot(v, w))))


def nearest_grid(angle, grid):
    return min(grid, key=lambda value: abs((angle - value + 180.0) % 360.0 - 180.0))


def neighbor_cells(cell, grid):
    position = grid.index(cell)
    return [grid[(position - 1) % len(grid)], grid[(position + 1) % len(grid)]]


def wavefront_transition(cells, finished, grid, energy_limit, energy_decrease_threshold=1e-5):
    """Pure TorsionDrive wavefront transition, suitable for adversarial testing."""
    improved = set()
    current_best = {}
    for record in finished:
        key = str(record["target_degrees"])
        if key not in current_best or record["energy_hartree"] < current_best[key]["energy_hartree"]:
            current_best[key] = record
    for record in current_best.values():
        cell = str(record["target_degrees"])
        previous = cells.get(cell)
        if previous is None:
            cells[cell] = record
            improved.add(int(cell))
        elif record["energy_hartree"] < previous["energy_hartree"]:
            decrease = previous["energy_hartree"] - record["energy_hartree"]
            cells[cell] = record
            if decrease > energy_decrease_threshold:
                improved.add(int(cell))
    if not cells:
        return cells, []
    floor = min(record["energy_hartree"] for record in cells.values())
    tasks = []
    for cell in sorted(improved):
        source = cells[str(cell)]
        if source["energy_hartree"] - floor > energy_limit:
            continue
        for target in neighbor_cells(cell, grid):
            tasks.append({"from_degrees": cell, "target_degrees": target,
                          "source_geometry": source["geometry"], "source_id": source["task_id"]})
    return cells, tasks


COVALENT_RADII = {"H": 0.31, "C": 0.76, "O": 0.66, "S": 1.05}


def bond_graph(elements, coordinates):
    graph = set()
    for i in range(len(elements)):
        for j in range(i + 1, len(elements)):
            cutoff = 1.25 * (COVALENT_RADII[elements[i]] + COVALENT_RADII[elements[j]])
            if np.linalg.norm(coordinates[i] - coordinates[j]) <= cutoff:
                graph.add((i, j))
    return graph


def chirality_signatures(coordinates, graph):
    neighbors = {index: [] for index in range(len(coordinates))}
    for a, b in graph:
        neighbors[a].append(b); neighbors[b].append(a)
    values = {}
    for center, linked in neighbors.items():
        if len(linked) == 4:
            linked = sorted(linked)
            vectors = [coordinates[index] - coordinates[center] for index in linked[:3]]
            volume = float(np.linalg.det(np.stack(vectors)))
            if abs(volume) > 1e-4:
                values[str(center)] = 1 if volume > 0 else -1
    return values


def validate_scientific_geometry(elements, reference, candidate, torsion, target):
    if candidate.shape != (56, 3) or not np.isfinite(candidate).all():
        raise CandidateRejected("candidate geometry is malformed or nonfinite")
    reference_graph = bond_graph(elements, reference)
    candidate_graph = bond_graph(elements, candidate)
    if candidate_graph != reference_graph:
        raise CandidateRejected("connectivity changed during constrained optimization")
    if chirality_signatures(candidate, candidate_graph) != chirality_signatures(reference, reference_graph):
        raise CandidateRejected("chirality changed during constrained optimization")
    achieved = dihedral_degrees(candidate, torsion)
    error = abs((achieved - target + 180.0) % 360.0 - 180.0)
    if error > 0.1:
        raise CandidateRejected(f"target dihedral realization failed: {error} degrees")
    return {"connectivity_pass": True, "chirality_pass": True,
            "requested_dihedral_degrees": target, "achieved_dihedral_degrees": achieved,
            "dihedral_error_degrees": error}


def optimize_candidate(core, elements, start, torsion, target, output: Path):
    import geometric.engine, geometric.errors, geometric.molecule, geometric.optimize
    from pyscf.data.nist import BOHR
    initial = output / "initial.xyz"
    constraint = output / "constraints.txt"
    atomic_text(initial, core.xyz_text(elements, start, "sealed minimum multistart seed"))
    one_based = [index + 1 for index in torsion]
    atomic_text(constraint, "$set\n" + "dihedral " + " ".join(map(str, one_based)) + f" {target}\n$end\n")
    counter = {"value": 0}

    class Engine(geometric.engine.Engine):
        def calc_new(self, coords, dirname):
            step = output / "steps" / f"step_{counter['value']:04d}"; counter["value"] += 1
            result, gradient, _ = core.calculate(elements, np.asarray(coords).reshape(56, 3), step)
            return {"energy": result["total_energy_hartree"], "gradient": gradient.reshape(-1)}

    molecule = geometric.molecule.Molecule(str(initial))
    try:
        optimized = geometric.optimize.run_optimizer(
            input=str(initial), constraints=str(constraint), customengine=Engine(molecule),
            prefix=str(output / "geometric"), maxiter=300, **core.GEOMETRIC_CONVERGENCE)
    except tuple(getattr(geometric.errors, name) for name in
                 ("GeomOptNotConvergedError", "GeomOptStructureError")
                 if hasattr(geometric.errors, name)) as error:
        raise CandidateRejected(f"geomeTRIC candidate failure: {error}") from error
    final = np.asarray(optimized.xyzs[-1])
    result, _, _ = core.calculate(elements, final / BOHR, output / "final_single_point")
    atomic_text(output / "final.xyz", core.xyz_text(elements, final, f"target dihedral {target}"))
    atomic_json(output / "CANDIDATE_RESULT.json", {
        "status": "CONVERGED", "target_degrees": target, "steps": counter["value"],
        "total_energy_hartree": result["total_energy_hartree"],
        "final_geometry_sha256": sha256(output / "final.xyz")})
    write_checksums(output)
    return final, result


def task_id(torsion_name, source_id, target):
    return hashlib.sha256(f"{torsion_name}|{source_id}|{target}".encode()).hexdigest()[:20]


def write_state_checksums(root):
    state = root / "WAVEFRONT_STATE.json"
    atomic_text(root / "STATE_SHA256SUMS", f"{sha256(state)}  WAVEFRONT_STATE.json\n")


def recover_interrupted_candidate(temporary: Path, recovery_root: Path, fallback_geometry):
    """Archive a checksum-valid interrupted segment and resume at its last geometry."""
    steps = sorted((temporary / "steps").glob("step_*")) if (temporary / "steps").is_dir() else []
    latest_geometry = np.asarray(fallback_geometry)
    valid_steps = []
    trailing_partial = False
    for index, step in enumerate(steps):
        try:
            verify_checksums(step, step / "SHA256SUMS")
            _, latest_geometry = read_xyz(step / "geometry.xyz")
            valid_steps.append(step)
        except (RuntimeError, ValueError):
            if index != len(steps) - 1:
                raise RuntimeError(f"non-trailing candidate step is corrupt: {step}")
            trailing_partial = True
    recovery_root.mkdir(parents=True, exist_ok=True)
    segment = recovery_root / f"segment_{len(list(recovery_root.glob('segment_*'))):04d}"
    os.replace(temporary, segment)
    atomic_json(segment / "RECOVERY_RECEIPT.json", {
        "status": "INTERRUPTED_SEGMENT_PRESERVED", "complete_steps_recovered": len(valid_steps),
        "partial_trailing_step_excluded": trailing_partial,
        "resume_geometry_source": (str(valid_steps[-1] / "geometry.xyz")
                                   if valid_steps else "original_task_geometry")})
    write_checksums(segment)
    return latest_geometry, str(segment)


def isolate_candidate_failure(temporary: Path, failure_root: Path, identifier: str, error: Exception):
    failure_root.mkdir(parents=True, exist_ok=True)
    destination = failure_root / identifier
    if destination.exists():
        destination = failure_root / f"{identifier}_{len(list(failure_root.glob(identifier + '*'))):04d}"
    if temporary.exists():
        os.replace(temporary, destination)
    else:
        destination.mkdir()
    atomic_json(destination / "CANDIDATE_FAILURE.json", {
        "status": "ISOLATED_CANDIDATE_FAILURE", "task_id": identifier,
        "failure_type": type(error).__name__, "message": str(error)})
    write_checksums(destination)
    return destination


def authoritative_cell_manifest(root: Path, state: dict, protocol_sha: str):
    records = []
    all_candidates = []
    for record_path in (root / "candidates").glob("*/WAVEFRONT_RECORD.json"):
        candidate = record_path.parent
        verify_checksums(candidate, candidate / "SHA256SUMS")
        all_candidates.append(json.loads(record_path.read_text()))
    for target, selected in sorted(state["cells"].items(), key=lambda item: int(item[0])):
        alternatives = [record for record in all_candidates if record["target_degrees"] == int(target)]
        if not alternatives or selected["energy_hartree"] != min(r["energy_hartree"] for r in alternatives):
            raise RuntimeError(f"authoritative lowest-energy semantics violated at {target}")
        gates = selected.get("geometry_gates", {})
        if gates.get("connectivity_pass") is not True or gates.get("chirality_pass") is not True:
            raise RuntimeError(f"authoritative cell lacks scientific geometry gates: {target}")
        geometry = Path(selected["geometry"])
        records.append({"target_degrees": int(target), "energy_hartree": selected["energy_hartree"],
                        "candidate_task_id": selected["task_id"], "geometry_sha256": sha256(geometry),
                        "candidate_manifest_sha256": sha256(geometry.parent / "SHA256SUMS")})
    result = {"schema": "authoritative-torsiondrive-cells-v1", "protocol_sha256": protocol_sha,
              "semantics": "lowest-energy checksum-verified geometry-gated candidate per populated cell",
              "populated_cell_count": len(records), "cells": records}
    atomic_json(root / "AUTHORITATIVE_CELLS.json", result)
    return result


def execute_wavefront(core, manifest, torsion_name, torsion, seeds, protocol_sha):
    root = RESULTS / torsion_name
    state_path = root / "WAVEFRONT_STATE.json"
    if state_path.is_file():
        verify_checksums(root, root / "STATE_SHA256SUMS")
        state = json.loads(state_path.read_text())
        if state["protocol_sha256"] not in {protocol_sha, LEGACY_ZERO_THRESHOLD_MANIFEST_SHA256}:
            raise RuntimeError("resume protocol identity mismatch")
        if state["protocol_sha256"] == LEGACY_ZERO_THRESHOLD_MANIFEST_SHA256:
            recovery = root / "OFFLINE_REPLAY_RECOVERY.json"
            if not recovery.is_file():
                raise RuntimeError("legacy zero-threshold state requires reviewed offline replay; QM prohibited")
            receipt = json.loads(recovery.read_text())
            if (receipt.get("source_state_sha256") != sha256(state_path)
                    or receipt.get("energy_decrease_threshold_hartree") != 1e-5
                    or receipt.get("reviewed") is not True):
                raise RuntimeError("offline replay recovery receipt is invalid or unreviewed")
            state["controller_migration"] = receipt
            state["protocol_sha256"] = protocol_sha
            state["queue"] = receipt["canonical_queue"]
        state.setdefault("energy_decrease_threshold_hartree",
                         manifest["energy_decrease_threshold_hartree"])
    elif root.exists():
        raise RuntimeError(f"partial wavefront state exists: {root}")
    else:
        root.mkdir(parents=True)
        queue = []
        for seed, (_, geometry) in seeds.items():
            target = nearest_grid(dihedral_degrees(geometry, torsion), manifest["grid_degrees"])
            queue.append({"from_degrees": None, "target_degrees": target,
                          "source_geometry": str(HERE / "input" / f"{seed}_verified.xyz"),
                          "source_id": seed})
        state = {"schema": "torsiondrive-wavefront-state-v1", "torsion": torsion_name,
                 "protocol_sha256": protocol_sha, "round": 0, "cells": {}, "queue": queue,
                 "completed_task_ids": [], "failed_task_ids": [],
                 "energy_decrease_threshold_hartree": manifest["energy_decrease_threshold_hartree"],
                 "propagation_energy_upper_limit_hartree": 0.05}
        atomic_json(state_path, state); write_state_checksums(root)
    while state["queue"]:
        finished = []
        for task in state["queue"]:
            identifier = task_id(torsion_name, task["source_id"], task["target_degrees"])
            candidate = root / "candidates" / identifier
            if identifier in state["failed_task_ids"]:
                continue
            if identifier in state["completed_task_ids"]:
                verify_checksums(candidate, candidate / "SHA256SUMS")
                finished.append(json.loads((candidate / "WAVEFRONT_RECORD.json").read_text()))
                continue
            if candidate.exists():
                raise RuntimeError(f"incomplete candidate exists: {candidate}")
            elements, source_geometry = read_xyz(Path(task["source_geometry"]))
            temporary = candidate.with_name(candidate.name + ".in_progress")
            recovery_lineage = []
            if temporary.exists():
                source_geometry, segment = recover_interrupted_candidate(
                    temporary, root / "recovery" / identifier, source_geometry)
                recovery_lineage.append({"path": segment,
                    "manifest_sha256": sha256(Path(segment) / "SHA256SUMS")})
            try:
                geometry, result = optimize_candidate(core, elements, source_geometry, torsion,
                                                       task["target_degrees"], temporary)
                gates = validate_scientific_geometry(elements, source_geometry, geometry, torsion,
                                                     task["target_degrees"])
            except Exception as error:
                if not expected_candidate_failure(error):
                    raise
                isolate_candidate_failure(temporary, root / "failed_candidates", identifier, error)
                state["failed_task_ids"].append(identifier)
                atomic_json(state_path, state); write_state_checksums(root)
                continue
            record = {"task_id": identifier, "from_degrees": task["from_degrees"],
                      "source_id": task["source_id"], "wavefront_round": state["round"],
                      "target_degrees": task["target_degrees"],
                      "energy_hartree": result["total_energy_hartree"],
                      "geometry": str(candidate / "final.xyz"), "geometry_gates": gates,
                      "recovery_lineage": recovery_lineage}
            atomic_json(temporary / "WAVEFRONT_RECORD.json", record); write_checksums(temporary)
            os.replace(temporary, candidate)
            state["completed_task_ids"].append(identifier)
            atomic_json(state_path, state); write_state_checksums(root)
            finished.append(record)
        cells, queue = wavefront_transition(state["cells"], finished, manifest["grid_degrees"],
                                            manifest["energy_upper_limit_hartree"],
                                            state["energy_decrease_threshold_hartree"])
        unique = {}
        for task in queue:
            unique[task_id(torsion_name, task["source_id"], task["target_degrees"])] = task
        state.update({"cells": cells, "queue": list(unique.values()), "round": state["round"] + 1})
        atomic_json(state_path, state); write_state_checksums(root)
    authoritative_cell_manifest(root, state, protocol_sha)
    return state


def coupling_diagnostic(states, torsions):
    rows = []
    for scan_name, state in states.items():
        for target, record in sorted(state["cells"].items(), key=lambda item: int(item[0])):
            _, geometry = read_xyz(Path(record["geometry"]))
            rows.append({"scan": scan_name, "target_degrees": int(target),
                         "phi_degrees": dihedral_degrees(geometry, torsions["PHI"]),
                         "psi_degrees": dihedral_degrees(geometry, torsions["PSI"]),
                         "geometry_sha256": sha256(Path(record["geometry"]))})
    return {"schema": "phi-psi-coupling-diagnostic-v1", "frozen_before_qm": True,
            "interpretation": "diagnostic_only_not_parameter_selection", "cells": rows}


def main() -> None:
    smoke = pre_qm_production_smoke(); manifest = verify_package()
    runtime = smoke["runtime_identity"]; core = load_core()
    core.INPUT = HERE / "input"; core.OUTPUT = RESULTS / "_core"
    RESULTS.mkdir(parents=True, exist_ok=True)
    atomic_json(RESULTS / "RUNTIME_ENVIRONMENT.json", runtime)
    protocol_sha = sha256(MANIFEST)
    seeds = {seed: read_xyz(HERE / "input" / f"{seed}_verified.xyz") for seed in manifest["multistart_seeds"]}
    states = {name: execute_wavefront(core, manifest, name, manifest["torsions"][name], seeds, protocol_sha)
              for name in manifest["execution_torsions"]}
    populated = sum(len(state["cells"]) for state in states.values())
    atomic_json(RESULTS / "PHI_PSI_COUPLING_DIAGNOSTIC.json",
                coupling_diagnostic(states, manifest["torsions"]))
    atomic_json(RESULTS / "CAMPAIGN_RESULT.json", {"status": "COMPLETE",
        "possible_grid_cells": manifest["execution_possible_cells"],
        "execution_torsions": manifest["execution_torsions"],
        "chi_run": False, "populated_cell_count": populated,
        "populated_cell_count_hardcoded": False, "actual_wavefront_implemented": True,
        "brute_force_all_cells": False, "energy_limit_applied_during_propagation": True,
        "qm_run": bool(sum(len(state["completed_task_ids"]) for state in states.values()))})
    write_checksums(RESULTS)


if __name__ == "__main__":
    main()
