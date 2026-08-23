#!/usr/bin/env python3
"""Frozen A100 runner for the prepared TSL-RSH curvature campaign.

This runner is packaged but never executed during preparation. Each completed
point is atomically renamed into place only after all component artifacts and
their checksums have been written.
"""

from __future__ import annotations

import csv
import hashlib
import json
import os
import platform
import shutil
import time
import uuid
from importlib.metadata import version
from pathlib import Path

import numpy as np

MANIFEST = Path("CURVATURE_GEOMETRY_MANIFEST.csv")
PROTOCOL = Path("FROZEN_GPU_QM_PROTOCOL.json")
OUTPUT = Path("curvature_gpu_results")
EXPECTED_ELEMENTS = ["C"] * 5 + ["O"] + ["C"] * 16 + ["O", "O", "C", "S"] + ["H"] * 30
PARAMETER_DB_SHA256 = "b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645"
D3 = {"s6": 1.0, "s8": 0.7875, "s9": 0.0, "a1": 0.4289, "a2": 4.4407, "alp": 14.0}


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def atomic_text(path: Path, text: str) -> None:
    temporary = Path(str(path) + ".tmp")
    temporary.write_text(text)
    os.replace(temporary, path)


def read_xyz(path: Path) -> tuple[list[str], np.ndarray]:
    lines = path.read_text().splitlines(); count = int(lines[0]); rows = [line.split() for line in lines[2:2 + count]]
    elements = [row[0] for row in rows]; xyz = np.asarray([[float(value) for value in row[1:4]] for row in rows])
    if count != 56 or elements != EXPECTED_ELEMENTS or xyz.shape != (56, 3) or not np.isfinite(xyz).all():
        raise RuntimeError(f"geometry identity/order mismatch: {path}")
    return elements, xyz


def d3_database() -> Path:
    import dftd3
    root = Path(dftd3.__file__).resolve().parent
    candidates = [root / "parameters.toml", root.parent / "parameters.toml", *root.parent.glob("**/parameters.toml")]
    for path in candidates:
        if path.is_file() and sha256(path) == PARAMETER_DB_SHA256:
            return path
    raise RuntimeError("frozen dftd3 parameter database not found")


def run_one(row: dict[str, str]) -> None:
    import cupy as cp
    import pyscf
    from dftd3.interface import DispersionModel, RationalDampingParam
    from gpu4pyscf.dft import gen_grid as gpu_grid
    from gpu4pyscf.dft import radi as gpu_radi
    from pyscf import dft, gto
    from pyscf.data.nist import BOHR

    if pyscf.__version__ != "2.14.0" or version("gpu4pyscf-cuda12x") != "1.8.0" or version("dftd3") != "1.5.0":
        raise RuntimeError("software identity mismatch")
    campaign_id = row["campaign_id"]
    source = Path(row["geometry_path"])
    if not source.is_file() or sha256(source) != row["geometry_sha256"]:
        raise RuntimeError(f"geometry checksum mismatch: {campaign_id}")
    final = OUTPUT / campaign_id
    if final.exists():
        raise RuntimeError(f"refusing to overwrite existing result: {final}")
    partial = OUTPUT / f".{campaign_id}.partial.{uuid.uuid4().hex}"
    partial.mkdir(parents=True)
    try:
        shutil.copyfile(source, partial / "geometry.xyz")
        elements, xyz = read_xyz(partial / "geometry.xyz")
        molecule = gto.M(atom=list(zip(elements, xyz.tolist())), basis="def2-svp", charge=0, spin=0, unit="Angstrom", verbose=4, max_memory=24000)
        if molecule.nelectron != 202:
            raise RuntimeError("electron count mismatch")
        cpu = dft.RKS(molecule).density_fit(auxbasis="def2-svp-jkfit")
        cpu.xc = "pbe"; cpu.grids.level = 2; cpu.conv_tol = 1e-8; cpu.max_cycle = 160; cpu.init_guess = "minao"; cpu.chkfile = None
        gpu = cpu.to_gpu()
        gpu.grids.level = 2; gpu.grids.prune = gpu_grid.nwchem_prune; gpu.grids.becke_scheme = gpu_grid.original_becke
        gpu.grids.radi_method = gpu_radi.treutler; gpu.grids.radii_adjust = gpu_radi.treutler_atomic_radii_adjust
        if gpu_grid.get_C_interface_scheme_id(gpu.grids.becke_scheme) != 100:
            raise RuntimeError("GPU grid semantic preflight failed")
        cycles = {"count": 0}
        gpu.callback = lambda environment: cycles.update(count=max(cycles["count"], int(environment.get("cycle", -1)) + 1))
        pool = cp.get_default_memory_pool(); free_before, total_memory = cp.cuda.runtime.memGetInfo(); start = time.perf_counter(); scf_start = time.perf_counter()
        electronic_energy = float(gpu.kernel()); cp.cuda.Stream.null.synchronize(); scf_seconds = time.perf_counter() - scf_start
        if not gpu.converged:
            raise RuntimeError("SCF not converged")
        gradient_start = time.perf_counter(); electronic_gradient_raw = gpu.nuc_grad_method().kernel(); cp.cuda.Stream.null.synchronize()
        electronic_gradient = cp.asnumpy(electronic_gradient_raw) if isinstance(electronic_gradient_raw, cp.ndarray) else np.asarray(electronic_gradient_raw)
        gradient_seconds = time.perf_counter() - gradient_start
        numbers = np.asarray([{"H": 1, "C": 6, "O": 8, "S": 16}[element] for element in elements], dtype=np.int32)
        dispersion = DispersionModel(numbers, xyz / BOHR).get_dispersion(RationalDampingParam(**D3), grad=True)
        d3_energy = float(dispersion["energy"]); d3_gradient = np.asarray(dispersion["gradient"])
        total_gradient = electronic_gradient + d3_gradient; force = -total_gradient
        if any(array.shape != (56, 3) or not np.isfinite(array).all() for array in (electronic_gradient, d3_gradient, total_gradient, force)):
            raise RuntimeError("nonfinite or malformed gradient component")
        if not np.array_equal(force, -total_gradient):
            raise RuntimeError("force=-gradient identity failure")
        free_after, _ = cp.cuda.runtime.memGetInfo(); total_seconds = time.perf_counter() - start
        arrays = {"electronic_gradient_hartree_per_bohr.txt": electronic_gradient, "d3_gradient_hartree_per_bohr.txt": d3_gradient, "total_gradient_hartree_per_bohr.txt": total_gradient, "force_hartree_per_bohr.txt": force}
        for name, array in arrays.items():
            np.savetxt(partial / name, array, fmt="%.17e")
        result = {
            "status": "CONVERGED", "campaign_id": campaign_id, "geometry_sha256": sha256(source), "atom_count": 56,
            "elements": elements, "charge": 0, "multiplicity": 1, "spin_pyscf": 0, "electron_count": 202,
            "electronic_energy_hartree": electronic_energy, "electronic_gradient_hartree_per_bohr": electronic_gradient.tolist(),
            "d3_energy_hartree": d3_energy, "d3_gradient_hartree_per_bohr": d3_gradient.tolist(),
            "total_energy_hartree": electronic_energy + d3_energy, "total_gradient_hartree_per_bohr": total_gradient.tolist(),
            "force_hartree_per_bohr": force.tolist(), "force_definition": "force=-total_gradient", "scf_converged": True,
            "scf_cycles": cycles["count"], "timings": {"scf_seconds": scf_seconds, "gradient_seconds": gradient_seconds, "total_seconds": total_seconds},
            "protocol": json.loads(PROTOCOL.read_text()),
            "gpu": {"name": str(cp.cuda.runtime.getDeviceProperties(0)["name"]), "total_bytes": int(total_memory), "free_before_bytes": int(free_before), "free_after_bytes": int(free_after), "memory_pool_peak_proxy_bytes": int(pool.total_bytes())},
            "software": {"python": platform.python_version(), "pyscf": pyscf.__version__, "gpu4pyscf": version("gpu4pyscf-cuda12x"), "dftd3": version("dftd3"), "cupy": cp.__version__, "d3_parameter_database_sha256": sha256(d3_database())}
        }
        atomic_text(partial / "result.json", json.dumps(result, indent=2, sort_keys=True) + "\n")
        files = sorted(path for path in partial.iterdir() if path.is_file() and path.name != "SHA256SUMS")
        atomic_text(partial / "SHA256SUMS", "".join(f"{sha256(path)}  {path.name}\n" for path in files))
        os.replace(partial, final)
    except Exception:
        failed = OUTPUT / f"{campaign_id}.FAILED.{uuid.uuid4().hex}"
        os.replace(partial, failed)
        raise


def main() -> None:
    if os.environ.get("CURVATURE_CAMPAIGN_EXECUTION_AUTHORIZED") != "YES_AFTER_INDEPENDENT_REVIEW":
        raise RuntimeError("campaign is preparation-only until independent review explicitly authorizes execution")
    protocol = json.loads(PROTOCOL.read_text())
    if protocol.get("qm_protocol_matches_gpu60") is not True or protocol.get("qm_executed") is not False:
        raise RuntimeError("frozen protocol preflight failed")
    rows = list(csv.DictReader(MANIFEST.open(newline="")))
    if len(rows) != 76 or len({row["campaign_id"] for row in rows}) != 76 or len({row["geometry_sha256"] for row in rows}) != 76:
        raise RuntimeError("frozen curvature manifest identity/count failure")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for row in rows:
        run_one(row)


if __name__ == "__main__":
    main()
