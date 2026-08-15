#!/usr/bin/env python3
"""Locked PBE-D3(BJ)/def2-SVP fixed-geometry energy+gradient pilot.

The script intentionally has no optimization or campaign mode. It consumes the
immutable Prometheus calculation specification, emits raw vectors in atomic
order, and performs a central finite-difference sign/unit check along the
canonical S26->H56 axis.
"""
import argparse
import hashlib
import json
import os
import platform
import tempfile
import time
from pathlib import Path

import numpy as np
import pyscf
from pyscf import dft, gto, lib
import dftd3
from dftd3.pyscf import energy as d3_energy


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_xyz(path):
    lines = Path(path).read_text().splitlines()
    count = int(lines[0])
    atoms = []
    coordinates = []
    for line in lines[2:2 + count]:
        fields = line.split()
        atoms.append(fields[0])
        coordinates.append([float(value) for value in fields[1:4]])
    if len(atoms) != count:
        raise ValueError("XYZ atom count mismatch")
    return atoms, np.asarray(coordinates, dtype=float)


def write_xyz(path, atoms, coordinates, comment):
    Path(path).write_text(
        f"{len(atoms)}\n{comment}\n" + "".join(
            f"{symbol:2} {x: .12f} {y: .12f} {z: .12f}\n"
            for symbol, (x, y, z) in zip(atoms, coordinates)))


def mean_field(atoms, coordinates_angstrom, spec):
    molecule = gto.M(
        atom=list(zip(atoms, coordinates_angstrom.tolist())),
        basis="def2-svp",
        charge=int(spec["formal_charge"]),
        spin=int(spec["multiplicity"]) - 1,
        unit="Angstrom",
        verbose=4,
    )
    mf = dft.RKS(molecule).density_fit()
    mf.xc = "pbe"
    mf.grids.level = 2
    mf.conv_tol = 1.0e-8
    mf.max_cycle = 160
    return d3_energy(mf, version="d3bj")


def energy(atoms, coordinates, spec):
    mf = mean_field(atoms, coordinates, spec)
    value = float(mf.kernel())
    if not mf.converged:
        raise RuntimeError("SCF did not converge")
    return value


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--spec", required=True)
    parser.add_argument("--geometry", required=True)
    parser.add_argument("--output", required=True)
    args = parser.parse_args()
    spec_path = Path(args.spec)
    geometry_path = Path(args.geometry)
    output_path = Path(args.output)
    spec = json.loads(spec_path.read_text())
    if spec["method"].replace(" ", "").upper() != "PBE":
        raise ValueError("method is not locked PBE")
    if spec["basis"].lower() != "def2-svp":
        raise ValueError("basis is not locked def2-SVP")
    if spec["dispersion"].replace(" ", "").upper() != "D3(BJ)":
        raise ValueError("dispersion is not locked D3(BJ)")
    if spec["constraints"]:
        raise ValueError("fixed-geometry pilot cannot carry optimization constraints")
    if sha256(geometry_path) != spec["input_geometry_sha256"]:
        raise ValueError("input geometry artifact checksum mismatch")

    threads = int(os.environ.get("PROMETHEUS_PYSCF_THREADS", "4"))
    lib.num_threads(threads)
    os.environ["OMP_NUM_THREADS"] = str(threads)
    atoms, coordinates = read_xyz(geometry_path)
    if len(atoms) != int(spec["geometry_atom_count"]):
        raise ValueError("geometry atom count differs from specification")

    started = time.time()
    mf = mean_field(atoms, coordinates, spec)
    electronic_energy = float(mf.kernel())
    if not mf.converged:
        raise RuntimeError("SCF did not converge")
    gradient = np.asarray(mf.nuc_grad_method().kernel(), dtype=float)
    force = -gradient

    # Independent central finite-difference check along canonical S26->H56.
    # PySCF gradients are Hartree/bohr. Coordinates are displaced in bohr then
    # converted to Angstrom for molecule construction.
    sulfur = 25
    hydrogen = 55
    axis = coordinates[hydrogen] - coordinates[sulfur]
    axis /= np.linalg.norm(axis)
    step_bohr = 1.0e-3
    bohr_to_angstrom = 0.529177210903
    plus = coordinates.copy()
    minus = coordinates.copy()
    plus[hydrogen] += axis * step_bohr * bohr_to_angstrom
    minus[hydrogen] -= axis * step_bohr * bohr_to_angstrom
    plus_energy = energy(atoms, plus, spec)
    minus_energy = energy(atoms, minus, spec)
    finite_difference = (plus_energy - minus_energy) / (2.0 * step_bohr)
    analytic_projection = float(np.dot(gradient[hydrogen], axis))

    plus_xyz = output_path.with_name("finite_difference_plus.xyz")
    minus_xyz = output_path.with_name("finite_difference_minus.xyz")
    write_xyz(plus_xyz, atoms, plus, "VALIDATION_AUXILIARY H56 +1e-3 bohr along S26-to-H56")
    write_xyz(minus_xyz, atoms, minus, "VALIDATION_AUXILIARY H56 -1e-3 bohr along S26-to-H56")
    auxiliary_common = {
        "role": "VALIDATION_AUXILIARY", "units": {"energy": "hartree"},
        "protocol": {"method": "PBE", "basis": "def2-SVP", "dispersion": "D3(BJ)",
                     "density_fitted": True, "environment": "gas phase"},
        "software": {"python": platform.python_version(), "pyscf": pyscf.__version__,
                     "dftd3": getattr(dftd3, "__version__", "unknown")}}
    for side, xyz, side_energy in (("plus", plus_xyz, plus_energy), ("minus", minus_xyz, minus_energy)):
        auxiliary = dict(auxiliary_common)
        auxiliary.update({"side": side, "energy_hartree": side_energy,
                          "geometry_sha256": sha256(xyz),
                          "parent_specification_checksum": spec["specification_checksum"]})
        output_path.with_name(f"finite_difference_{side}.json").write_text(
            json.dumps(auxiliary, indent=2, sort_keys=True) + "\n")

    np.savetxt(output_path.with_name("gradient_hartree_per_bohr.txt"), gradient, fmt="%.16e")
    np.savetxt(output_path.with_name("force_hartree_per_bohr.txt"), force, fmt="%.16e")
    result = {
        "status": "CONVERGED",
        "specification_checksum": spec["specification_checksum"],
        "geometry_identity": spec["geometry_identity"],
        "input_geometry_sha256": sha256(geometry_path),
        "calculation_specification_sha256": sha256(spec_path),
        "energy_hartree": electronic_energy,
        "gradient_hartree_per_bohr": gradient.tolist(),
        "force_hartree_per_bohr": force.tolist(),
        "gradient_norm_hartree_per_bohr": float(np.linalg.norm(gradient)),
        "gradient_force_identity_max_abs": float(np.max(np.abs(gradient + force))),
        "finite_difference_audit": {
            "coordinate": "H56 displacement along S26->H56",
            "step_bohr": step_bohr,
            "central_difference_hartree_per_bohr": finite_difference,
            "analytic_gradient_projection_hartree_per_bohr": analytic_projection,
            "absolute_difference_hartree_per_bohr": abs(finite_difference - analytic_projection),
            "plus_energy_hartree": plus_energy,
            "minus_energy_hartree": minus_energy,
            "plus_geometry_sha256": sha256(plus_xyz),
            "minus_geometry_sha256": sha256(minus_xyz),
        },
        "scf_converged": bool(mf.converged),
        "units": {"energy": "hartree", "gradient": "hartree/bohr", "force": "hartree/bohr"},
        "force_definition": "force = -gradient",
        "protocol": {
            "method": "PBE",
            "basis": "def2-SVP",
            "dispersion": "D3(BJ)",
            "density_fitted": True,
            "environment": "gas phase",
            "grid_level": 2,
            "scf_convergence": 1.0e-8,
            "max_scf_cycles": 160,
        },
        "software": {
            "python": platform.python_version(),
            "pyscf": pyscf.__version__,
            "dftd3": getattr(dftd3, "__version__", "unknown"),
            "numpy": np.__version__,
        },
        "threads": threads,
        "elapsed_seconds": time.time() - started,
    }
    output_path.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", dir=output_path.parent, delete=False) as handle:
        json.dump(result, handle, indent=2, sort_keys=True)
        handle.write("\n")
        temporary = Path(handle.name)
    temporary.replace(output_path)


if __name__ == "__main__":
    main()
