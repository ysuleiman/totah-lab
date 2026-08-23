#!/usr/bin/env python3
"""Frozen MIN01-only A100 stationary-point recovery pilot.

This runner is intentionally fail-closed and publication-oriented.  It verifies the
packaged immutable inputs, reconstructs the signed total PBE+D3 unstable mode, performs
the symmetric energy gate, runs at most four geomeTRIC branches, clusters endpoints,
and Hessian-qualifies only unique endpoints.  Every calculation is atomically persisted;
no scientific result exists only in stdout.
"""
from __future__ import annotations

import csv
import hashlib
import json
import os
import platform
import shutil
import subprocess
import sys
import time
from collections import Counter
from importlib.metadata import version as package_version
from pathlib import Path

ROOT = Path(__file__).resolve().parent
INPUT = ROOT / "input"
OUTPUT = ROOT / "results"
MANIFEST = ROOT / "PILOT_MANIFEST.json"
EXPECTED_MANIFEST_SHA256 = "afcb9a0b8c41d8e8a0bbeb9a2857403cd713fa73716d0c11403d1091970c326c"

N_ATOMS = 56
N_CART = 168
CHARGE = 0
SPIN = 0
MULTIPLICITY = 1
EXPECTED_ELECTRONS = 202
EXPECTED_COMPOSITION = {"C": 22, "H": 30, "O": 3, "S": 1}

# q is a mass-weighted normal coordinate.  Cartesian Angstrom displacement is
# delta r_i = q * L_i / sqrt(m_i), where ||L||_2 = 1 in mass-weighted space.
ESCAPE_A_SQRT_AMU_ANGSTROM = 0.0500000000000000
BRANCHES = {"plus_a": 1.0, "minus_a": -1.0, "plus_half_a": 0.5, "minus_half_a": -0.5}

D3_VERSION = "1.5.0"
D3_DATABASE_SHA256 = "b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645"
D3_PARAMETERS = {"s6": 1.0, "s8": 0.7875, "s9": 0.0,
                 "a1": 0.4289, "a2": 4.4407, "alp": 14.0}
D3_HESSIAN_H_BOHR = 0.001
D3_HESSIAN_CHECK_H_BOHR = 0.0005
D3_CHECK_COLUMNS = [27, 28, 29, 75, 76, 77, 165, 166, 167]

GEOMETRIC_CONVERGENCE = {"convergence_energy": 1.0e-5, "convergence_gmax": 0.004,
                         "convergence_grms": 0.001, "convergence_dmax": 0.005,
                         "convergence_drms": 0.002}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(text)
    os.replace(temporary, path)


def atomic_json(path: Path, value) -> None:
    atomic_text(path, json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def atomic_array(path: Path, array) -> None:
    import numpy as np
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("wb") as target:
        np.save(target, np.asarray(array))
    os.replace(temporary, path)


def atomic_matrix_text(path: Path, array) -> None:
    import numpy as np
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    np.savetxt(temporary, np.asarray(array), fmt="%.17e")
    os.replace(temporary, path)


def write_sha256s(directory: Path) -> None:
    files = sorted(path for path in directory.rglob("*") if path.is_file()
                   and path.name != "SHA256SUMS")
    rows = [f"{sha256(path)}  {path.relative_to(directory)}" for path in files]
    atomic_text(directory / "SHA256SUMS", "\n".join(rows) + "\n")


def install_environment() -> None:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--no-cache-dir",
        "pyscf==2.14.0", "gpu4pyscf-cuda12x==1.8.0", "cupy-cuda12x==13.4.1",
        "cutensor-cu12==2.2.0", "dftd3==1.5.0", "geometric==1.1.1"])


def verify_manifest() -> dict:
    manifest = json.loads(MANIFEST.read_text())
    if EXPECTED_MANIFEST_SHA256 is not None and sha256(MANIFEST) != EXPECTED_MANIFEST_SHA256:
        raise RuntimeError("pilot manifest SHA-256 mismatch")
    for relative, expected in manifest["input_sha256"].items():
        path = ROOT / relative
        if not path.is_file() or sha256(path) != expected:
            raise RuntimeError(f"missing or modified pilot input: {relative}")
    if manifest["software_baseline"] != "83c267182af0144c7802f2503d545ad30e9a58d8":
        raise RuntimeError("software baseline mismatch")
    if manifest["scope"] != ["MIN01"] or manifest["maximum_optimization_branches"] != 4:
        raise RuntimeError("pilot scope expansion detected")
    return manifest


def read_xyz(path: Path):
    import numpy as np
    lines = path.read_text().splitlines()
    if int(lines[0]) != N_ATOMS or len(lines[2:]) != N_ATOMS:
        raise RuntimeError(f"expected exactly {N_ATOMS} atoms in {path}")
    rows = [line.split() for line in lines[2:]]
    elements = [row[0] for row in rows]
    coordinates = np.asarray([[float(x) for x in row[1:4]] for row in rows])
    return elements, coordinates


def xyz_text(elements, coordinates, comment: str) -> str:
    rows = [str(len(elements)), comment]
    rows += [f"{e:<2s} {r[0]: .12f} {r[1]: .12f} {r[2]: .12f}"
             for e, r in zip(elements, coordinates)]
    return "\n".join(rows) + "\n"


def molecule(elements, coordinates_angstrom, verbose=0):
    from pyscf import gto
    mol = gto.M(atom=list(zip(elements, coordinates_angstrom.tolist())), basis="def2-svp",
                charge=CHARGE, spin=SPIN, unit="Angstrom", verbose=verbose, max_memory=24000)
    if mol.natm != N_ATOMS or mol.nelectron != EXPECTED_ELECTRONS:
        raise RuntimeError("molecular identity mismatch")
    return mol


def validate_identity(elements, coordinates, manifest):
    with (INPUT / "ATOM_ORDER.csv").open(newline="") as source:
        rows = list(csv.DictReader(source))
    if [int(row["atom_index"]) for row in rows] != list(range(1, N_ATOMS + 1)):
        raise RuntimeError("atom-order indices are not canonical 1..56")
    if [row["element"] for row in rows] != elements:
        raise RuntimeError("atom order differs from frozen manifest")
    if dict(Counter(elements)) != EXPECTED_COMPOSITION:
        raise RuntimeError("composition is not C22H30O3S")
    if sha256(INPUT / "MIN01_historical_saddle.xyz") != manifest["geometry_sha256"]:
        raise RuntimeError("MIN01 geometry identity mismatch")
    molecule(elements, coordinates)


def total_mode(elements, coordinates, manifest):
    import numpy as np
    from pyscf.hessian import thermo
    pbe4 = np.load(INPUT / "MIN01_pbe_hessian_pyscf_axes.npy")
    d3 = np.load(INPUT / "MIN01_d3_hessian_canonical.npy")
    if pbe4.shape != (N_ATOMS, N_ATOMS, 3, 3) or d3.shape != (N_CART, N_CART):
        raise RuntimeError("Hessian component dimensions are invalid")
    if not np.isfinite(pbe4).all() or not np.isfinite(d3).all():
        raise RuntimeError("Hessian component contains nonfinite values")
    # PySCF axes are (atom_i, atom_j, axis_i, axis_j); canonical axes are
    # (atom_i, axis_i, atom_j, axis_j).  Never use bare reshape here.
    pbe = pbe4.transpose(0, 2, 1, 3).reshape(N_CART, N_CART)
    total_raw = pbe + d3
    total = 0.5 * (total_raw + total_raw.T)
    if not np.array_equal(total_raw, pbe + d3):
        raise RuntimeError("total Hessian composition failed")
    mol = molecule(elements, coordinates)
    total4 = total.reshape(N_ATOMS, 3, N_ATOMS, 3).transpose(0, 2, 1, 3)
    analysis = thermo.harmonic_analysis(mol, total4, exclude_trans=True,
                                        exclude_rot=True, imaginary_freq=True)
    complex_frequency = np.asarray(analysis["freq_wavenumber"])
    signed = np.asarray([-abs(x.imag) if abs(x.imag) > 1.0e-12 else x.real
                         for x in complex_frequency], dtype=float)
    mode_index = int(np.argmin(signed))
    cart_mode = np.asarray(analysis["norm_mode"], dtype=float)[mode_index]
    masses = np.asarray(mol.atom_mass_list(), dtype=float)
    weighted_norm = float(np.sum(masses[:, None] * cart_mode * cart_mode))
    if abs(weighted_norm - 1.0) > 5.0e-3:
        raise RuntimeError(f"mass-weighted mode normalization failed: {weighted_norm}")
    mw_mode = cart_mode * np.sqrt(masses)[:, None]
    mw_mode /= np.linalg.norm(mw_mode)
    cart_mode = mw_mode / np.sqrt(masses)[:, None]
    frequency = float(signed[mode_index])
    expected_frequency = manifest["hessian_reconstruction"]["expected_escape_frequency_cm-1"]
    if frequency >= 0.0 or abs(frequency - expected_frequency) > 1.0e-6:
        raise RuntimeError(f"unexpected escape-mode frequency: {frequency}")
    record = {
        "pbe_axis_layout": "[atom_i,atom_j,axis_i,axis_j]",
        "canonical_axis_layout": "[atom_i,axis_i,atom_j,axis_j]",
        "composition_equation": "total_raw = canonical(PBE) + D3",
        "pbe_sha256": sha256(INPUT / "MIN01_pbe_hessian_pyscf_axes.npy"),
        "d3_sha256": sha256(INPUT / "MIN01_d3_hessian_canonical.npy"),
        "total_raw_asymmetry_max": float(np.max(np.abs(total_raw - total_raw.T))),
        "mode_index_zero_based": mode_index,
        "signed_frequency_cm-1": frequency,
        "negative_mode_count": int(np.sum(signed < 0.0)),
        "mass_weighted_eigenvector_norm": float(np.linalg.norm(mw_mode)),
        "cartesian_mass_norm": float(np.sum(masses[:, None] * cart_mode * cart_mode)),
        "mass_weighting": "mw_mode=sqrt(mass_amu)*cart_mode; cart_mode=mw_mode/sqrt(mass_amu)",
        "atom_order_sha256": sha256(INPUT / "ATOM_ORDER.csv"),
        "geometry_sha256": sha256(INPUT / "MIN01_historical_saddle.xyz"),
    }
    return (frequency, cart_mode, pbe, d3, total, signed,
            np.asarray(analysis["force_const_au"], dtype=float),
            np.asarray(analysis["norm_mode"]), record)


def d3_database_path() -> Path:
    import dftd3
    roots = [Path(dftd3.__file__).resolve().parent, Path(dftd3.__file__).resolve().parent.parent]
    for root in roots:
        for path in root.glob("**/parameters.toml"):
            if sha256(path) == D3_DATABASE_SHA256:
                return path
    raise RuntimeError("frozen simple-dftd3 parameter database not found")


def d3_value(elements, coordinates_bohr, gradient=True):
    import numpy as np
    from dftd3.interface import DispersionModel, RationalDampingParam
    numbers = np.asarray([{"H": 1, "C": 6, "O": 8, "S": 16}[e] for e in elements], dtype=np.int32)
    result = DispersionModel(numbers, np.asarray(coordinates_bohr)).get_dispersion(
        RationalDampingParam(**D3_PARAMETERS), grad=gradient)
    return float(result["energy"]), np.asarray(result["gradient"], dtype=float) if gradient else None


def configure_gpu_object(mol):
    from pyscf import dft
    from pyscf.dft import gen_grid, radi
    from gpu4pyscf.dft import gen_grid as gpu_grid
    from gpu4pyscf.dft import radi as gpu_radi
    cpu = dft.RKS(mol).density_fit(auxbasis="def2-svp-jkfit")
    cpu.xc = "pbe"
    cpu.grids.level = 2
    cpu.grids.prune = gen_grid.nwchem_prune
    cpu.grids.becke_scheme = gen_grid.original_becke
    cpu.grids.radi_method = radi.treutler_ahlrichs
    cpu.grids.radii_adjust = radi.treutler_atomic_radii_adjust
    cpu.conv_tol = 1.0e-8
    cpu.max_cycle = 160
    cpu.init_guess = "minao"
    cpu.chkfile = None
    gpu = cpu.to_gpu()
    gpu.grids.level = 2
    gpu.grids.prune = gpu_grid.nwchem_prune
    gpu.grids.becke_scheme = gpu_grid.original_becke
    gpu.grids.radi_method = gpu_radi.treutler
    gpu.grids.radii_adjust = gpu_radi.treutler_atomic_radii_adjust
    expected = {"prune": gpu_grid.nwchem_prune, "becke_scheme": gpu_grid.original_becke,
                "radi_method": gpu_radi.treutler,
                "radii_adjust": gpu_radi.treutler_atomic_radii_adjust}
    for name, callback in expected.items():
        if getattr(gpu.grids, name) is not callback:
            raise RuntimeError(f"GPU grid callback identity mismatch: {name}")
    return gpu


def calculate(elements, coordinates_bohr, directory: Path, need_hessian=False):
    import cupy as cp
    import numpy as np
    from pyscf.data.nist import BOHR
    started = time.perf_counter()
    coordinates_angstrom = np.asarray(coordinates_bohr) * BOHR
    mol = molecule(elements, coordinates_angstrom, verbose=0)
    gpu = configure_gpu_object(mol)
    cycles = {"count": 0}
    gpu.callback = lambda env: cycles.update(count=max(cycles["count"], int(env.get("cycle", -1)) + 1))
    electronic_energy = float(gpu.kernel())
    if not gpu.converged:
        raise RuntimeError("SCF failed under frozen protocol")
    electronic_gradient_gpu = gpu.nuc_grad_method().kernel()
    electronic_gradient = cp.asnumpy(electronic_gradient_gpu) if isinstance(
        electronic_gradient_gpu, cp.ndarray) else np.asarray(electronic_gradient_gpu)
    d3_energy, d3_gradient = d3_value(elements, coordinates_bohr, gradient=True)
    total_gradient = electronic_gradient + d3_gradient
    result = {
        "electronic_energy_hartree": electronic_energy, "dispersion_energy_hartree": d3_energy,
        "total_energy_hartree": electronic_energy + d3_energy, "scf_converged": True,
        "scf_cycles": cycles["count"], "gradient_shape": list(total_gradient.shape),
        "elapsed_seconds": time.perf_counter() - started,
    }
    directory.mkdir(parents=True, exist_ok=True)
    atomic_matrix_text(directory / "electronic_gradient_hartree_per_bohr.txt", electronic_gradient)
    atomic_matrix_text(directory / "dispersion_gradient_hartree_per_bohr.txt", d3_gradient)
    atomic_matrix_text(directory / "total_gradient_hartree_per_bohr.txt", total_gradient)
    atomic_matrix_text(directory / "force_hartree_per_bohr.txt", -total_gradient)
    if need_hessian:
        electronic4_gpu = gpu.Hessian().kernel()
        electronic4 = cp.asnumpy(electronic4_gpu) if isinstance(electronic4_gpu, cp.ndarray) \
            else np.asarray(electronic4_gpu)
        if electronic4.shape != (N_ATOMS, N_ATOMS, 3, 3):
            raise RuntimeError(f"electronic Hessian shape unsupported: {electronic4.shape}")
        electronic = electronic4.transpose(0, 2, 1, 3).reshape(N_CART, N_CART)
        d3_raw = d3_hessian(elements, coordinates_bohr, directory)
        total_raw = electronic + d3_raw
        electronic_sym = 0.5 * (electronic + electronic.T)
        d3_sym = 0.5 * (d3_raw + d3_raw.T)
        total_sym = electronic_sym + d3_sym
        if not np.allclose(total_sym, electronic_sym + d3_sym, atol=1.0e-12, rtol=1.0e-10):
            raise RuntimeError("component-complete total Hessian arithmetic failed")
        for name, value in (("electronic_hessian", electronic_sym),
                            ("dispersion_hessian", d3_sym), ("total_hessian", total_sym)):
            if value.shape != (N_CART, N_CART) or not np.isfinite(value).all():
                raise RuntimeError(f"invalid {name}")
            atomic_array(directory / f"{name}_hartree_per_bohr2.npy", value)
            atomic_matrix_text(directory / f"{name}_hartree_per_bohr2.txt", value)
        qualify_hessian(mol, total_sym, directory, result)
        result["hessian_components"] = {
            "electronic": sha256(directory / "electronic_hessian_hartree_per_bohr2.npy"),
            "dispersion": sha256(directory / "dispersion_hessian_hartree_per_bohr2.npy"),
            "total": sha256(directory / "total_hessian_hartree_per_bohr2.npy"),
            "equation": "total=electronic+dispersion", "units": "hartree/bohr^2"}
    atomic_json(directory / "result.json", result)
    write_sha256s(directory)
    return result, total_gradient


def d3_hessian(elements, coordinates_bohr, directory):
    import numpy as np
    flat = np.asarray(coordinates_bohr).reshape(-1)
    raw = np.empty((N_CART, N_CART))
    for column in range(N_CART):
        plus, minus = flat.copy(), flat.copy()
        plus[column] += D3_HESSIAN_H_BOHR
        minus[column] -= D3_HESSIAN_H_BOHR
        raw[:, column] = (d3_value(elements, plus.reshape(N_ATOMS, 3))[1].reshape(-1)
                          - d3_value(elements, minus.reshape(N_ATOMS, 3))[1].reshape(-1)) \
                         / (2.0 * D3_HESSIAN_H_BOHR)
    convergence = {}
    for column in D3_CHECK_COLUMNS:
        plus, minus = flat.copy(), flat.copy()
        plus[column] += D3_HESSIAN_CHECK_H_BOHR
        minus[column] -= D3_HESSIAN_CHECK_H_BOHR
        smaller = (d3_value(elements, plus.reshape(N_ATOMS, 3))[1].reshape(-1)
                   - d3_value(elements, minus.reshape(N_ATOMS, 3))[1].reshape(-1)) \
                  / (2.0 * D3_HESSIAN_CHECK_H_BOHR)
        residual = smaller - raw[:, column]
        convergence[str(column)] = {"max_abs": float(np.max(np.abs(residual))),
                                    "rms": float(np.sqrt(np.mean(residual * residual)))}
    atomic_json(directory / "dispersion_hessian_fd_diagnostics.json", {
        "h_bohr": D3_HESSIAN_H_BOHR, "smaller_h_bohr": D3_HESSIAN_CHECK_H_BOHR,
        "selected_columns": D3_CHECK_COLUMNS,
        "raw_asymmetry_max": float(np.max(np.abs(raw - raw.T))), "convergence": convergence})
    return raw


def qualify_hessian(mol, total, directory, result):
    import numpy as np
    from pyscf.hessian import thermo
    total4 = total.reshape(N_ATOMS, 3, N_ATOMS, 3).transpose(0, 2, 1, 3)
    modes = thermo.harmonic_analysis(mol, total4, exclude_trans=True,
                                    exclude_rot=True, imaginary_freq=True)
    complex_frequency = np.asarray(modes["freq_wavenumber"])
    signed = np.asarray([-abs(x.imag) if abs(x.imag) > 1.0e-12 else x.real
                         for x in complex_frequency])
    if np.any(signed == 0.0):
        raise RuntimeError("exact zero in projected vibrational spectrum")
    normal_modes = np.asarray(modes["norm_mode"])
    masses = np.asarray(mol.atom_mass_list(), dtype=float)
    mass_weighted_modes = normal_modes * np.sqrt(masses)[None, :, None]
    atomic_matrix_text(directory / "signed_frequencies_cm-1.txt", signed.reshape(-1, 1))
    atomic_array(directory / "normal_modes_cartesian_per_sqrt_amu.npy", normal_modes)
    atomic_array(directory / "normal_modes_mass_weighted.npy", mass_weighted_modes)
    atomic_matrix_text(directory / "mass_weighted_eigenvalues_atomic_units.txt",
                       np.asarray(modes["force_const_au"], dtype=float).reshape(-1, 1))
    projection = {"nonlinear": True, "cartesian_dimension": N_CART,
                  "projected_rigid_body_modes": 6, "vibrational_mode_count": len(signed),
                  "negative_below_minus20_count": int(np.sum(signed < -20.0)),
                  "negative_below_minus50_count": int(np.sum(signed < -50.0)),
                  "near_zero_minus20_to_plus20_count": int(np.sum((signed >= -20.0) & (signed <= 20.0)))}
    atomic_json(directory / "rigid_body_projection_diagnostics.json", projection)
    result["signed_frequency_min_cm-1"] = float(np.min(signed))
    result["negative_mode_count"] = int(np.sum(signed < 0.0))
    result["stationary_point_classification"] = (
        "VERIFIED_LOCAL_MINIMUM" if not np.any(signed < -20.0)
        else "NOT_VERIFIED_LOCAL_MINIMUM")


def optimize_branch(elements, initial_angstrom, branch: str):
    import geometric.engine
    import geometric.molecule
    import geometric.optimize
    import numpy as np
    from pyscf.data.nist import BOHR
    branch_directory = OUTPUT / "optimization" / branch
    initial_path = branch_directory / "initial.xyz"
    atomic_text(initial_path, xyz_text(elements, initial_angstrom, f"MIN01 {branch} preregistered escape"))

    class Engine(geometric.engine.Engine):
        def calc_new(self, coords, dirname):
            step = branch_directory / "steps" / f"step_{len(self.stored_calcs):04d}"
            result, gradient = calculate(elements, np.asarray(coords).reshape(N_ATOMS, 3), step)
            atomic_text(step / "geometry.xyz", xyz_text(elements,
                np.asarray(coords).reshape(N_ATOMS, 3) * BOHR, f"{branch} geomeTRIC step"))
            atomic_json(step / "execution_receipt.json", {
                "observable": ["energy", "gradient"], "components": ["PBE", "D3(BJ)", "total"],
                "producer": "GPU4PySCF 1.8.0 + simple-dftd3 1.5.0",
                "method": "PBE-D3(BJ)/def2-SVP", "geometry_sha256": sha256(step / "geometry.xyz"),
                "result_sha256": sha256(step / "result.json")})
            write_sha256s(step)
            return {"energy": result["total_energy_hartree"], "gradient": gradient.reshape(-1)}

    mol = geometric.molecule.Molecule(str(initial_path))
    optimized = geometric.optimize.run_optimizer(input=str(initial_path), customengine=Engine(mol),
        prefix=str(branch_directory / "geometric"), maxiter=300, **GEOMETRIC_CONVERGENCE)
    final = np.asarray(optimized.xyzs[-1])
    final_path = branch_directory / "final.xyz"
    atomic_text(final_path, xyz_text(elements, final, f"MIN01 {branch} optimized endpoint"))
    final_result, _ = calculate(elements, final / BOHR, branch_directory / "final_single_point")
    atomic_json(branch_directory / "endpoint.json", {
        "branch": branch, "converged": True, "geometry_sha256": sha256(final_path),
        "total_energy_hartree": final_result["total_energy_hartree"],
        "geometric_convergence": GEOMETRIC_CONVERGENCE})
    write_sha256s(branch_directory)
    return final, final_result["total_energy_hartree"], final_path


def kabsch_heavy_rmsd(elements, left, right):
    import numpy as np
    mask = np.asarray([element != "H" for element in elements])
    a, b = left[mask].copy(), right[mask].copy()
    a -= a.mean(axis=0); b -= b.mean(axis=0)
    u, _, vt = np.linalg.svd(a.T @ b)
    if np.linalg.det(u @ vt) < 0: u[:, -1] *= -1
    residual = a @ (u @ vt) - b
    return float(np.sqrt(np.mean(np.sum(residual * residual, axis=1))))


def distance(coords, i, j):
    import numpy as np
    return float(np.linalg.norm(coords[i - 1] - coords[j - 1]))


def angle(coords, i, j, k):
    import numpy as np
    a, b = coords[i - 1] - coords[j - 1], coords[k - 1] - coords[j - 1]
    return float(np.degrees(np.arccos(np.clip(a @ b / np.linalg.norm(a) / np.linalg.norm(b), -1, 1))))


def dihedral(coords, i, j, k, l):
    import numpy as np
    p = [coords[x - 1] for x in (i, j, k, l)]
    b0, b1, b2 = -(p[1] - p[0]), p[2] - p[1], p[3] - p[2]
    b1 /= np.linalg.norm(b1)
    v, w = b0 - (b0 @ b1) * b1, b2 - (b2 @ b1) * b1
    return float(np.degrees(np.arctan2(np.cross(b1, v) @ w, v @ w)))


def circular_difference(a, b):
    return abs((a - b + 180.0) % 360.0 - 180.0)


def same_endpoint(elements, a, ea, b, eb):
    return (kabsch_heavy_rmsd(elements, a, b) <= 0.10
            and all(circular_difference(dihedral(a, *indices), dihedral(b, *indices)) <= 10.0
                    for indices in ((56, 26, 10, 9), (26, 10, 9, 8), (10, 9, 8, 2)))
            and abs(distance(a, 26, 56) - distance(b, 26, 56)) <= 0.03
            and abs(distance(a, 26, 10) - distance(b, 26, 10)) <= 0.03
            and all(abs(angle(a, *indices) - angle(b, *indices)) <= 3.0
                    for indices in ((9, 10, 26), (11, 10, 26), (56, 26, 10)))
            and abs(ea - eb) * 627.509474 <= 0.25)


def main():
    install_environment()
    manifest = verify_manifest()
    import cupy as cp
    import numpy as np
    import pyscf
    from pyscf.data.nist import BOHR
    if pyscf.__version__ != "2.14.0" or package_version("gpu4pyscf-cuda12x") != "1.8.0":
        raise RuntimeError("locked PySCF/GPU4PySCF identity mismatch")
    gpu_name = cp.cuda.runtime.getDeviceProperties(0)["name"]
    gpu_name = gpu_name.decode() if isinstance(gpu_name, bytes) else str(gpu_name)
    if "A100" not in gpu_name.upper():
        raise RuntimeError(f"A100 required, found {gpu_name}")
    if package_version("dftd3") != D3_VERSION or sha256(d3_database_path()) != D3_DATABASE_SHA256:
        raise RuntimeError("D3 implementation identity mismatch")

    elements, coordinates = read_xyz(INPUT / "MIN01_historical_saddle.xyz")
    validate_identity(elements, coordinates, manifest)
    frequency, cart_mode, pbe, d3, total, signed, eigenvalues, modes, mode_record = total_mode(
        elements, coordinates, manifest)
    OUTPUT.mkdir(parents=True, exist_ok=False)
    atomic_json(OUTPUT / "RUNTIME_ENVIRONMENT.json", {
        "software_baseline": manifest["software_baseline"],
        "runner_sha256": sha256(Path(__file__).resolve()),
        "manifest_sha256": sha256(MANIFEST),
        "python": platform.python_version(), "platform": platform.platform(),
        "pyscf": pyscf.__version__, "gpu4pyscf": package_version("gpu4pyscf-cuda12x"),
        "cupy": cp.__version__, "dftd3": package_version("dftd3"),
        "geometric": package_version("geometric"), "gpu": gpu_name,
        "cuda_runtime_version": int(cp.cuda.runtime.runtimeGetVersion()),
        "pip_freeze": subprocess.check_output(
            [sys.executable, "-m", "pip", "freeze"], text=True).splitlines()})
    atomic_json(OUTPUT / "mode_reconstruction.json", mode_record)
    atomic_array(OUTPUT / "corrected_pbe_hessian_canonical.npy", pbe)
    atomic_array(OUTPUT / "d3_hessian_canonical.npy", d3)
    atomic_array(OUTPUT / "corrected_total_pbe_d3_hessian.npy", total)
    atomic_matrix_text(OUTPUT / "signed_frequencies_cm-1.txt", signed.reshape(-1, 1))
    atomic_matrix_text(OUTPUT / "mass_weighted_eigenvalues_atomic_units.txt", eigenvalues.reshape(-1, 1))
    masses = np.asarray(molecule(elements, coordinates).atom_mass_list(), dtype=float)
    atomic_array(OUTPUT / "normal_modes_cartesian_per_sqrt_amu.npy", modes)
    atomic_array(OUTPUT / "normal_modes_mass_weighted.npy",
                 modes * np.sqrt(masses)[None, :, None])
    atomic_array(OUTPUT / "escape_cartesian_mode_per_sqrt_amu.npy", cart_mode)

    displaced = {}
    for branch, scale in BRANCHES.items():
        geometry = coordinates + scale * ESCAPE_A_SQRT_AMU_ANGSTROM * cart_mode
        prepared_elements, prepared_geometry = read_xyz(
            INPUT / "prepared_displacements" / f"{branch}.xyz")
        if prepared_elements != elements or not np.allclose(
                prepared_geometry, geometry, atol=5.0e-13, rtol=0.0):
            raise RuntimeError(f"prepared displacement mismatch: {branch}")
        path = OUTPUT / "displacements" / f"{branch}.xyz"
        atomic_text(path, xyz_text(elements, geometry, f"MIN01 {branch}; q={scale * ESCAPE_A_SQRT_AMU_ANGSTROM}"))
        displaced[branch] = geometry
    write_sha256s(OUTPUT / "displacements")

    origin_result, _ = calculate(elements, coordinates / BOHR, OUTPUT / "energy_gate" / "origin")
    gate_results = {}
    for branch, geometry in displaced.items():
        gate_results[branch], _ = calculate(elements, geometry / BOHR, OUTPUT / "energy_gate" / branch)
    e0 = origin_result["total_energy_hartree"]
    gate = {
        "equation": "E(+q)+E(-q)-2E(0) < 0",
        "a_second_difference_hartree": gate_results["plus_a"]["total_energy_hartree"]
            + gate_results["minus_a"]["total_energy_hartree"] - 2 * e0,
        "half_a_second_difference_hartree": gate_results["plus_half_a"]["total_energy_hartree"]
            + gate_results["minus_half_a"]["total_energy_hartree"] - 2 * e0,
        "energy_lowered": {name: value["total_energy_hartree"] < e0
                           for name, value in gate_results.items()},
    }
    gate["pass"] = gate["a_second_difference_hartree"] < 0 and gate["half_a_second_difference_hartree"] < 0
    atomic_json(OUTPUT / "energy_gate" / "ENERGY_CURVATURE_GATE.json", gate)
    write_sha256s(OUTPUT / "energy_gate")
    if not gate["pass"]:
        atomic_json(OUTPUT / "PILOT_RESULT.json", {"status": "STOPPED_ENERGY_CURVATURE_GATE_FAILED",
                    "escape_frequency_cm-1": frequency, "energy_gate": gate})
        write_sha256s(OUTPUT)
        return

    endpoints = []
    for branch in BRANCHES:
        geometry, energy, path = optimize_branch(elements, displaced[branch], branch)
        endpoints.append({"branch": branch, "geometry": geometry, "energy": energy, "path": path})
    unique = []
    for endpoint in sorted(endpoints, key=lambda item: item["energy"]):
        match = next((candidate for candidate in unique if same_endpoint(elements,
            endpoint["geometry"], endpoint["energy"], candidate["geometry"], candidate["energy"])), None)
        if match is None:
            endpoint["members"] = [endpoint["branch"]]
            unique.append(endpoint)
        else:
            match["members"].append(endpoint["branch"])
    cluster_record = []
    verified = []
    for index, endpoint in enumerate(unique, start=1):
        candidate = OUTPUT / "unique_endpoints" / f"candidate_{index:02d}"
        atomic_text(candidate / "geometry.xyz", xyz_text(elements, endpoint["geometry"],
                    f"MIN01 unique endpoint {index}"))
        result, _ = calculate(elements, endpoint["geometry"] / BOHR, candidate, need_hessian=True)
        if result["stationary_point_classification"] == "VERIFIED_LOCAL_MINIMUM":
            verified.append((endpoint, result, candidate))
        cluster_record.append({"candidate_id": index, "members": endpoint["members"],
            "energy_hartree": endpoint["energy"], "geometry_sha256": sha256(candidate / "geometry.xyz"),
            "classification": result["stationary_point_classification"],
            "negative_mode_count": result["negative_mode_count"]})
    atomic_json(OUTPUT / "ENDPOINT_CLUSTERS.json", cluster_record)
    lowest = min(verified, key=lambda item: item[1]["total_energy_hartree"]) if verified else None
    final = {"status": "PILOT_COMPLETE", "escape_mode_frequency_cm-1": frequency,
        "escape_displacement_sqrt_amu_angstrom": ESCAPE_A_SQRT_AMU_ANGSTROM,
        "optimization_branches_run": len(endpoints), "converged_branches": len(endpoints),
        "unique_endpoints": len(unique), "hessians_run": len(unique),
        "verified_minima_found": len(verified), "energy_gate": gate,
        "unique_endpoint_energies_hartree": [x["energy"] for x in unique],
        "lowest_minimum_energy_hartree": None if lowest is None else lowest[1]["total_energy_hartree"],
        "lowest_minimum_geometry_sha256": None if lowest is None else sha256(lowest[2] / "geometry.xyz"),
        "lowest_minimum_negative_mode_count": None if lowest is None else lowest[1]["negative_mode_count"],
        "pilot_decision": "MIN01_RECOVERY_DEMONSTRATED" if verified else "MIN01_RECOVERY_NOT_DEMONSTRATED",
        "software": {"python": platform.python_version(), "pyscf": pyscf.__version__,
                     "gpu4pyscf": package_version("gpu4pyscf-cuda12x"),
                     "dftd3": package_version("dftd3"), "geometric": package_version("geometric")},
        "hardware": {"gpu": gpu_name}, "manifest_sha256": sha256(MANIFEST)}
    atomic_json(OUTPUT / "PILOT_RESULT.json", final)
    write_sha256s(OUTPUT)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        if OUTPUT.is_dir():
            atomic_json(OUTPUT / "FAILURE.json", {
                "status": "FAILED_PRESERVED", "exception_type": type(error).__name__,
                "message": str(error)})
            write_sha256s(OUTPUT)
        raise
