#!/usr/bin/env python3
"""Numerical-only worker for one immutable Prometheus PBE-D3(BJ) force target."""

import argparse
import contextlib
import hashlib
import json
import os
import platform
import sys
import tempfile
import time
import traceback
from pathlib import Path


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def scientific_specification_checksum(spec):
    # Java owns this complete, human-auditable identity payload.  Avoid a
    # cross-language canonical-JSON convention (notably exponent formatting).
    return hashlib.sha256(spec["specification_identity_payload"].encode()).hexdigest()


def require(spec, key, expected):
    actual = spec[key]
    if actual != expected:
        raise ValueError(f"{key} mismatch: expected {expected!r}, got {actual!r}")


def configure_threads(count):
    value = str(count)
    for name in ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS",
                 "VECLIB_MAXIMUM_THREADS", "NUMEXPR_NUM_THREADS", "BLIS_NUM_THREADS"):
        os.environ[name] = value


def read_xyz(path, atom_count, expected_elements):
    lines = Path(path).read_text().splitlines()
    if int(lines[0]) != atom_count:
        raise ValueError("XYZ atom count mismatch")
    rows = [line.split() for line in lines[2:2 + atom_count]]
    if len(rows) != atom_count or [row[0].upper() for row in rows] != expected_elements:
        raise ValueError("XYZ atom ordering mismatch")
    return [(row[0], tuple(float(value) for value in row[1:4])) for row in rows]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", required=True)
    parser.add_argument("--geometry", required=True)
    parser.add_argument("--output-directory", required=True)
    args = parser.parse_args()
    spec_path = Path(args.spec).resolve()
    geometry_path = Path(args.geometry).resolve()
    output = Path(args.output_directory).resolve()
    output.mkdir(parents=True, exist_ok=True)
    raw_path = output / "raw_backend_output.log"
    failure_path = output / "failure.json"
    result_path = output / "result.json"
    started_utc = __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat()
    started = time.time()
    try:
        spec = json.loads(spec_path.read_text())
        require(spec, "schema_version", 1)
        require(spec, "formal_charge", 0)
        require(spec, "multiplicity", 1)
        require(spec, "method", "PBE")
        require(spec, "basis", "def2-SVP")
        require(spec, "dispersion", "D3(BJ)")
        require(spec, "density_fitting", True)
        require(spec, "auxiliary_basis", "def2-SVP-JKFIT")
        require(spec, "grid_level", 2)
        require(spec, "grid_pruning", "NWCHEM_PRUNE")
        require(spec, "grid_partition", "ORIGINAL_BECKE")
        require(spec, "radial_grid", "TREUTLER_AHLRICHS")
        require(spec, "radii_adjust", "TREUTLER_ATOMIC_RADII_ADJUST")
        require(spec, "scf_convergence_tolerance", 1e-8)
        require(spec, "maximum_scf_cycles", 160)
        require(spec, "initial_guess_policy", "MINAO_ONLY_NO_CHECKPOINT")
        require(spec, "checkpoint_policy", "DISABLED")
        require(spec, "backend_id", "PYSCF_NUMERICAL_WORKER")
        require(spec, "backend_version", "2.14.0")
        require(spec, "d3_implementation", "simple-dftd3-python")
        require(spec, "d3_version", "1.5.0")
        require(spec, "d3_generation", "D3")
        require(spec, "d3_damping", "BJ_RATIONAL")
        require(spec, "d3_functional_mapping", "pbe")
        require(spec, "d3_atm_enabled", False)
        require(spec, "d3_parameter_source", "s-dftd3 parameters.toml")
        require(spec, "d3_parameter_database_sha256",
                "b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645")
        require(spec, "d3_parameters", {"a1": 0.4289, "a2": 4.4407, "alp": 14.0,
                                          "s6": 1.0, "s8": 0.7875, "s9": 0.0})
        if digest(geometry_path) != spec["geometry_checksum"]:
            raise ValueError("geometry checksum mismatch")
        if scientific_specification_checksum(spec) != spec["specification_file_checksum"]:
            raise ValueError("specification file checksum mismatch")
        configure_threads(int(spec["thread_count"]))

        import numpy as np
        import pyscf
        import dftd3
        from pyscf import dft, gto, lib
        from pyscf.dft import gen_grid, radi
        from dftd3.pyscf import energy as d3_energy

        if pyscf.__version__ != spec["backend_version"] or dftd3.__version__ != spec["d3_version"]:
            raise ValueError("backend version mismatch")
        lib.num_threads(int(spec["thread_count"]))
        atoms = read_xyz(geometry_path, int(spec["atom_count"]), spec["atom_elements"])
        with raw_path.open("w", buffering=1) as raw, contextlib.redirect_stdout(raw), contextlib.redirect_stderr(raw):
            print(json.dumps({"event": "WORKER_START", "specification_identity": spec["result_identity"],
                              "started_utc": started_utc}, sort_keys=True), flush=True)
            mol = gto.M(atom=atoms, basis="def2-svp", charge=0, spin=0,
                        unit="Angstrom", verbose=4, max_memory=int(spec["memory_limit_mb"]))
            mf = dft.RKS(mol).density_fit(auxbasis="def2-svp-jkfit")
            mf.xc = "pbe"
            mf.grids.level = 2
            mf.grids.prune = gen_grid.nwchem_prune
            mf.grids.becke_scheme = gen_grid.original_becke
            mf.grids.radi_method = radi.treutler_ahlrichs
            mf.grids.radii_adjust = radi.treutler_atomic_radii_adjust
            mf.conv_tol = 1e-8
            mf.max_cycle = 160
            mf.max_memory = int(spec["memory_limit_mb"])
            mf.init_guess = "minao"
            mf.chkfile = None
            cycle_counter = {"count": 0}
            mf.callback = lambda environment: cycle_counter.update(count=max(
                cycle_counter["count"], int(environment.get("cycle", -1)) + 1))
            mf = d3_energy(mf, method="pbe", version="d3bj", atm=False,
                    param={"s6": 1.0, "s8": 0.7875, "s9": 0.0,
                           "a1": 0.4289, "a2": 4.4407, "alp": 14.0})
            energy = float(mf.kernel())
            converged = bool(mf.converged)
            iterations = cycle_counter["count"]
            if not converged:
                raise RuntimeError("SCF_NOT_CONVERGED_UNDER_FROZEN_PROTOCOL")
            gradient = np.asarray(mf.nuc_grad_method().kernel(), dtype=float)
            if gradient.shape != (int(spec["atom_count"]), 3) or not np.isfinite(gradient).all():
                raise RuntimeError("INCOMPLETE_OR_NONFINITE_ANALYTIC_GRADIENT")
            force = -gradient
            np.savetxt(output / "gradient_hartree_per_bohr.txt", gradient, fmt="%.17e")
            np.savetxt(output / "force_hartree_per_bohr.txt", force, fmt="%.17e")
            ended_utc = __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat()
            result = {
                "status": "CONVERGED_NUMERICAL_RESULT",
                "result_identity": spec["result_identity"],
                "snapshot_id": spec["snapshot_id"],
                "dataset_role": spec["dataset_role"],
                "geometry_checksum": digest(geometry_path),
                "atom_order_checksum": spec["atom_order_checksum"],
                "protocol_checksum": spec["protocol_checksum"],
                "specification_file_checksum": spec["specification_file_checksum"],
                "specification_artifact_checksum": digest(spec_path),
                "backend_id": "PYSCF_NUMERICAL_WORKER",
                "backend_version": pyscf.__version__,
                "d3_implementation": "simple-dftd3-python",
                "d3_version": dftd3.__version__,
                "d3_generation": spec["d3_generation"],
                "d3_damping": spec["d3_damping"],
                "d3_functional_mapping": spec["d3_functional_mapping"],
                "d3_atm_enabled": spec["d3_atm_enabled"],
                "d3_parameter_source": spec["d3_parameter_source"],
                "d3_parameter_database_sha256": spec["d3_parameter_database_sha256"],
                "d3_parameters": spec["d3_parameters"],
                "energy_hartree": energy,
                "gradient_hartree_per_bohr": gradient.tolist(),
                "force_hartree_per_bohr": force.tolist(),
                "force_definition": "force = -gradient",
                "gradient_force_identity_max_abs": float(np.max(np.abs(gradient + force))),
                "scf_converged": converged,
                "scf_iteration_evidence": {"cycle_count_from_callback": iterations,
                    "converged_attribute": converged},
                "started_utc": started_utc,
                "ended_utc": ended_utc,
                "wall_time_seconds": time.time() - started,
                "thread_configuration": {name: os.environ[name] for name in
                    ("OMP_NUM_THREADS", "OPENBLAS_NUM_THREADS", "MKL_NUM_THREADS",
                     "VECLIB_MAXIMUM_THREADS", "NUMEXPR_NUM_THREADS", "BLIS_NUM_THREADS")},
                "memory_limit_mb": int(spec["memory_limit_mb"]),
                "environment": {"python": platform.python_version(), "platform": platform.platform(),
                    "numpy": np.__version__},
                "frozen_protocol": {key: spec[key] for key in (
                    "method", "basis", "dispersion", "density_fitting", "auxiliary_basis",
                    "grid_level", "grid_pruning", "grid_partition", "radial_grid", "radii_adjust",
                    "scf_convergence_tolerance", "maximum_scf_cycles", "initial_guess_policy",
                    "checkpoint_policy", "d3_parameters")},
            }
            payload = json.dumps(result, indent=2, sort_keys=True) + "\n"
            with tempfile.NamedTemporaryFile("w", dir=output, delete=False) as handle:
                handle.write(payload); handle.flush(); os.fsync(handle.fileno()); temporary = Path(handle.name)
            temporary.replace(result_path)
            print(json.dumps({"event": "WORKER_COMPLETE", "scf_converged": True}, sort_keys=True), flush=True)
    except Exception as error:
        failure = {"status": "FAILED", "started_utc": started_utc,
                   "ended_utc": __import__("datetime").datetime.now(__import__("datetime").timezone.utc).isoformat(),
                   "wall_time_seconds": time.time() - started, "error_type": type(error).__name__,
                   "error": str(error), "traceback": traceback.format_exc(),
                   "specification_path": str(spec_path), "geometry_path": str(geometry_path)}
        failure_path.write_text(json.dumps(failure, indent=2, sort_keys=True) + "\n")
        print(json.dumps(failure, sort_keys=True), file=sys.stderr)
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
