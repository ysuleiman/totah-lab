#!/usr/bin/env python3
"""Qualified level-5 MIN01 optimization and gated stationary-point qualification."""
from __future__ import annotations

import csv, hashlib, io, json, os, platform, subprocess, sys, time, zipfile
from collections import Counter
from importlib.metadata import version as package_version
from pathlib import Path

ROOT = Path(__file__).resolve().parent
INPUT, OUTPUT = ROOT / "input", ROOT / "results"
EVIDENCE = ROOT / "immutable-evidence/TSL_RSH_MIN01_LEVEL5_GRID_CONVERGENCE_CLOSURE_RESULTS.zip"
MANIFEST = ROOT / "OPTIMIZATION_MANIFEST.json"
N_ATOMS, N_CART, GRID_LEVEL = 56, 168, 5
MASS_CONVENTION = "PySCF isotope-average atomic masses; mol.atom_mass_list(isotope_avg=True)"
EXPECTED_COMPOSITION = {"C": 22, "H": 30, "O": 3, "S": 1}
GEOMETRIC_CONVERGENCE = {"convergence_energy": 1e-5, "convergence_gmax": 0.004,
                         "convergence_grms": 0.001, "convergence_dmax": 0.005,
                         "convergence_drms": 0.002}
D3_PARAMETERS = {"s6": 1.0, "s8": 0.7875, "s9": 0.0,
                 "a1": 0.4289, "a2": 4.4407, "alp": 14.0}
D3_DATABASE_SHA256 = "b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645"
D3_H_BOHR, D3_CHECK_H_BOHR = 0.001, 0.0005
D3_CHECK_COLUMNS = [27, 28, 29, 75, 76, 77, 165, 166, 167]
AUDIT_COLUMNS = D3_CHECK_COLUMNS
AUDIT_H_BOHR, AUDIT_CHECK_H_BOHR = 0.001, 0.0005


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


def atomic_array(path: Path, value) -> None:
    import numpy as np
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("wb") as target:
        np.save(target, np.asarray(value))
    os.replace(temporary, path)


def atomic_matrix(path: Path, value) -> None:
    import numpy as np
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    np.savetxt(temporary, np.asarray(value), fmt="%.17e")
    os.replace(temporary, path)


def write_checksums(directory: Path) -> None:
    paths = sorted(p for p in directory.rglob("*") if p.is_file() and p.name != "SHA256SUMS")
    atomic_text(directory / "SHA256SUMS", "".join(
        f"{sha256(path)}  {path.relative_to(directory)}\n" for path in paths))


def verify_checksums(directory: Path) -> None:
    manifest = directory / "SHA256SUMS"
    if not manifest.is_file():
        raise RuntimeError(f"missing checksum manifest: {manifest}")
    listed = set()
    for row in manifest.read_text().splitlines():
        expected, relative = row.split(maxsplit=1)
        relative = relative.strip()
        path = directory / relative
        listed.add(relative)
        if not path.is_file() or sha256(path) != expected:
            raise RuntimeError(f"missing or modified persisted result: {path}")
    actual = {str(path.relative_to(directory)) for path in directory.rglob("*")
              if path.is_file() and path.name != "SHA256SUMS"}
    if listed != actual:
        raise RuntimeError(f"checksum manifest coverage mismatch in {directory}")


def install_environment() -> None:
    subprocess.check_call([sys.executable, "-m", "pip", "install", "--no-cache-dir",
        "pyscf==2.14.0", "gpu4pyscf-cuda12x==1.8.0", "cupy-cuda12x==13.4.1",
        "cutensor-cu12==2.2.0", "dftd3==1.5.0", "geometric==1.1.1"])


def read_xyz(path: Path):
    import numpy as np
    lines = path.read_text().splitlines()
    rows = [line.split() for line in lines[2:]]
    if int(lines[0]) != N_ATOMS or len(rows) != N_ATOMS:
        raise RuntimeError("MIN01 must contain exactly 56 atoms")
    return [r[0] for r in rows], np.asarray([[float(x) for x in r[1:4]] for r in rows])


def xyz_text(elements, coordinates, comment):
    rows = [str(len(elements)), comment]
    rows.extend(f"{e:<2s} {r[0]: .12f} {r[1]: .12f} {r[2]: .12f}"
                for e, r in zip(elements, coordinates))
    return "\n".join(rows) + "\n"


def molecule(elements, coordinates_angstrom, verbose=0):
    from pyscf import gto
    mol = gto.M(atom=list(zip(elements, coordinates_angstrom.tolist())), basis="def2-svp",
                charge=0, spin=0, unit="Angstrom", verbose=verbose, max_memory=24000)
    if mol.natm != N_ATOMS or mol.nelectron != 202:
        raise RuntimeError("molecular identity mismatch")
    return mol


def mass_vector(mol):
    import numpy as np
    value = np.asarray(mol.atom_mass_list(isotope_avg=True), dtype=float)
    persisted = np.asarray(json.loads((INPUT / "MASS_VECTOR.json").read_text())["mass_vector_amu"])
    if not np.array_equal(value, persisted):
        raise RuntimeError("runtime mass vector differs from persisted mass vector")
    return value


def verify_closure() -> dict:
    with zipfile.ZipFile(EVIDENCE) as archive:
        for row in archive.read("results/SHA256SUMS").decode().splitlines():
            expected, relative = row.split(maxsplit=1)
            payload = archive.read("results/" + relative.strip())
            if hashlib.sha256(payload).hexdigest() != expected:
                raise RuntimeError(f"closure nested checksum mismatch: {relative}")
        result = json.loads(archive.read("results/LEVEL5_GRID_CLOSURE_RESULT.json"))
    if result["level5_derivative_grid_qualified"] is not True:
        raise RuntimeError("level-5 derivative grid was not qualified")
    if not result["energy_convergence_pass"] or not result["gradient_convergence_pass"]:
        raise RuntimeError("level-5/level-6 closure gates did not both pass")
    return result


def require_gpu_grid_response(derivative, observable):
    if not hasattr(derivative, "grid_response"):
        raise RuntimeError(f"{observable} lacks grid_response capability")
    derivative.grid_response = True
    backend = f"{type(derivative).__module__}.{type(derivative).__qualname__}"
    if derivative.grid_response is not True:
        raise RuntimeError(f"{observable} did not honor grid_response=True")
    if not type(derivative).__module__.startswith("gpu4pyscf."):
        raise RuntimeError(f"{observable} is not GPU4PySCF: {backend}")
    return derivative, backend


def configure_gpu(mol):
    from pyscf import dft
    from pyscf.dft import gen_grid, radi
    from gpu4pyscf.dft import gen_grid as gpu_grid
    from gpu4pyscf.dft import radi as gpu_radi
    cpu = dft.RKS(mol).density_fit(auxbasis="def2-svp-jkfit")
    cpu.xc = "pbe"
    cpu.grids.level = GRID_LEVEL
    cpu.grids.prune = gen_grid.nwchem_prune
    cpu.grids.becke_scheme = gen_grid.original_becke
    cpu.grids.radi_method = radi.treutler_ahlrichs
    cpu.grids.radii_adjust = radi.treutler_atomic_radii_adjust
    cpu.conv_tol, cpu.max_cycle, cpu.init_guess, cpu.chkfile = 1e-8, 160, "minao", None
    gpu = cpu.to_gpu()
    gpu.grids.level = GRID_LEVEL
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
    require_gpu_grid_response(gpu.nuc_grad_method(), "RKS gradient")
    require_gpu_grid_response(gpu.Hessian(), "RKS Hessian")
    return gpu


def d3_database_path():
    import dftd3
    package = Path(dftd3.__file__).resolve().parent
    for root in (package, package.parent):
        for path in root.glob("**/parameters.toml"):
            if sha256(path) == D3_DATABASE_SHA256:
                return path
    raise RuntimeError("frozen simple-dftd3 parameter database not found")


def d3_value(elements, coordinates_bohr):
    import numpy as np
    from dftd3.interface import DispersionModel, RationalDampingParam
    numbers = np.asarray([{"H": 1, "C": 6, "O": 8, "S": 16}[e] for e in elements], dtype=np.int32)
    result = DispersionModel(numbers, np.asarray(coordinates_bohr)).get_dispersion(
        RationalDampingParam(**D3_PARAMETERS), grad=True)
    return float(result["energy"]), np.asarray(result["gradient"], dtype=float)


def calculate(elements, coordinates_bohr, directory, gradient_required=True):
    import cupy as cp
    import numpy as np
    from pyscf.data.nist import BOHR
    mol = molecule(elements, np.asarray(coordinates_bohr) * BOHR)
    gpu = configure_gpu(mol)
    cycles = {"count": 0}
    gpu.callback = lambda env: cycles.update(count=max(cycles["count"], int(env.get("cycle", -1)) + 1))
    electronic_energy = float(gpu.kernel())
    if not gpu.converged:
        raise RuntimeError("SCF did not converge")
    electronic_gradient = dispersion_gradient = total_gradient = None
    gradient_backend = None
    if gradient_required:
        gradient_object, gradient_backend = require_gpu_grid_response(gpu.nuc_grad_method(), "RKS gradient")
        raw = gradient_object.kernel()
        if gradient_object.grid_response is not True:
            raise RuntimeError("gradient lost grid_response=True during execution")
        electronic_gradient = cp.asnumpy(raw) if isinstance(raw, cp.ndarray) else np.asarray(raw)
    dispersion_energy, d3_gradient = d3_value(elements, coordinates_bohr)
    if gradient_required:
        dispersion_gradient = d3_gradient
        total_gradient = electronic_gradient + dispersion_gradient
    directory.mkdir(parents=True, exist_ok=False)
    atomic_text(directory / "geometry.xyz", xyz_text(elements, np.asarray(coordinates_bohr) * BOHR,
                                                       "MIN01 level-5 stationary optimization"))
    if gradient_required:
        atomic_matrix(directory / "electronic_gradient_hartree_per_bohr.txt", electronic_gradient)
        atomic_matrix(directory / "dispersion_gradient_hartree_per_bohr.txt", dispersion_gradient)
        atomic_matrix(directory / "total_gradient_hartree_per_bohr.txt", total_gradient)
        atomic_matrix(directory / "force_hartree_per_bohr.txt", -total_gradient)
    result = {"electronic_energy_hartree": electronic_energy,
              "dispersion_energy_hartree": dispersion_energy,
              "total_energy_hartree": electronic_energy + dispersion_energy,
              "gradient_requested": gradient_required, "grid_response_gradient": True,
              "grid_response_hessian": True, "gradient_backend": gradient_backend,
              "grid_level": GRID_LEVEL, "scf_converged": True, "scf_cycles": cycles["count"],
              "mass_convention": MASS_CONVENTION, "mass_vector_amu": mass_vector(mol).tolist(),
              "geometry_sha256": sha256(directory / "geometry.xyz")}
    atomic_json(directory / "result.json", result)
    write_checksums(directory)
    return result, total_gradient, gpu


def optimize(elements, initial_angstrom):
    import geometric.engine, geometric.molecule, geometric.optimize
    import numpy as np
    from pyscf.data.nist import BOHR
    root = OUTPUT / "optimization"
    initial = root / "initial.xyz"
    atomic_text(initial, xyz_text(elements, initial_angstrom, "historical MIN01 optimization start"))
    step_counter = {"value": 0}

    class Engine(geometric.engine.Engine):
        def calc_new(self, coords, dirname):
            step = root / "steps" / f"step_{step_counter['value']:04d}"
            step_counter["value"] += 1
            result, gradient, _ = calculate(elements, np.asarray(coords).reshape(N_ATOMS, 3), step)
            atomic_json(step / "OPTIMIZER_STATE.json", {
                "optimizer": "geomeTRIC", "calculation_index_zero_based": step_counter["value"] - 1,
                "geometric_work_directory": str(dirname), "convergence": GEOMETRIC_CONVERGENCE,
                "geometry_sha256": result["geometry_sha256"],
                "energy_hartree": result["total_energy_hartree"],
                "gradient_sha256": sha256(step / "total_gradient_hartree_per_bohr.txt")})
            atomic_json(step / "execution_receipt.json", {
                "observable": ["energy", "gradient"], "components": ["PBE", "D3(BJ)", "total"],
                "grid_level": 5, "grid_response_gradient": True,
                "geometry_sha256": result["geometry_sha256"],
                "result_sha256": sha256(step / "result.json"),
                "optimizer_state_sha256": sha256(step / "OPTIMIZER_STATE.json")})
            write_checksums(step)
            return {"energy": result["total_energy_hartree"], "gradient": gradient.reshape(-1)}

    gm = geometric.molecule.Molecule(str(initial))
    optimized = geometric.optimize.run_optimizer(input=str(initial), customengine=Engine(gm),
        prefix=str(root / "geometric"), maxiter=300, **GEOMETRIC_CONVERGENCE)
    final_angstrom = np.asarray(optimized.xyzs[-1])
    final_path = root / "final.xyz"
    atomic_text(final_path, xyz_text(elements, final_angstrom, "MIN01 optimized endpoint"))
    final_result, final_gradient, _ = calculate(
        elements, final_angstrom / BOHR, root / "endpoint_single_point")
    atomic_json(root / "OPTIMIZATION_RESULT.json", {
        "status": "CONVERGED", "steps_persisted": step_counter["value"],
        "convergence": GEOMETRIC_CONVERGENCE, "endpoint_geometry_sha256": sha256(final_path),
        "endpoint_total_energy_hartree": final_result["total_energy_hartree"]})
    write_checksums(root)
    return final_angstrom, final_result, final_gradient


def endpoint_gradient_audit(elements, coordinates_angstrom, analytic_gradient, manifest):
    import numpy as np
    from pyscf.data.nist import BOHR
    root = OUTPUT / "endpoint_gradient_audit"
    flat = (coordinates_angstrom / BOHR).reshape(-1)
    details, errors, step_differences = {}, [], []
    for column in AUDIT_COLUMNS:
        estimates = {}
        for label, h in (("h", AUDIT_H_BOHR), ("half_h", AUDIT_CHECK_H_BOHR)):
            energies = {}
            for sign, name in ((1, "plus"), (-1, "minus")):
                displaced = flat.copy()
                displaced[column] += sign * h
                result, _, _ = calculate(elements, displaced.reshape(N_ATOMS, 3),
                    root / f"column_{column:03d}" / label / name, gradient_required=False)
                energies[name] = result["total_energy_hartree"]
            estimates[label] = (energies["plus"] - energies["minus"]) / (2 * h)
        analytic = float(analytic_gradient.reshape(-1)[column])
        error = estimates["half_h"] - analytic
        step_difference = estimates["half_h"] - estimates["h"]
        errors.append(error); step_differences.append(step_difference)
        details[str(column)] = {"analytic_hartree_per_bohr": analytic,
            "finite_difference_h": estimates["h"], "finite_difference_half_h": estimates["half_h"],
            "error": error, "step_difference": step_difference}
    criteria = manifest["endpoint_gradient_audit"]["criteria"]
    rms = float(np.sqrt(np.mean(np.asarray(errors)**2)))
    maximum = float(np.max(np.abs(errors)))
    step_maximum = float(np.max(np.abs(step_differences)))
    passed = (rms <= criteria["rms_difference_hartree_per_bohr_max"]
              and maximum <= criteria["max_difference_hartree_per_bohr_max"]
              and step_maximum <= criteria["step_convergence_max_hartree_per_bohr"])
    record = {"pass": passed, "columns_zero_based": AUDIT_COLUMNS,
        "h_bohr": AUDIT_H_BOHR, "half_h_bohr": AUDIT_CHECK_H_BOHR,
        "rms_difference_hartree_per_bohr": rms, "max_difference_hartree_per_bohr": maximum,
        "step_convergence_max_hartree_per_bohr": step_maximum,
        "criteria": criteria, "details": details}
    atomic_json(root / "ENDPOINT_GRADIENT_AUDIT.json", record)
    write_checksums(root)
    return record


def d3_hessian(elements, coordinates_bohr, root):
    import numpy as np
    flat = np.asarray(coordinates_bohr).reshape(-1)
    raw = np.empty((N_CART, N_CART))
    for column in range(N_CART):
        plus, minus = flat.copy(), flat.copy()
        plus[column] += D3_H_BOHR; minus[column] -= D3_H_BOHR
        raw[:, column] = (d3_value(elements, plus.reshape(N_ATOMS, 3))[1].reshape(-1)
                          - d3_value(elements, minus.reshape(N_ATOMS, 3))[1].reshape(-1)) / (2 * D3_H_BOHR)
    convergence = {}
    for column in D3_CHECK_COLUMNS:
        plus, minus = flat.copy(), flat.copy()
        plus[column] += D3_CHECK_H_BOHR; minus[column] -= D3_CHECK_H_BOHR
        check = (d3_value(elements, plus.reshape(N_ATOMS, 3))[1].reshape(-1)
                 - d3_value(elements, minus.reshape(N_ATOMS, 3))[1].reshape(-1)) / (2 * D3_CHECK_H_BOHR)
        residual = check - raw[:, column]
        convergence[str(column)] = {"max_abs": float(np.max(np.abs(residual))),
                                    "rms": float(np.sqrt(np.mean(residual**2)))}
    atomic_json(root / "dispersion_hessian_fd_diagnostics.json", {
        "h_bohr": D3_H_BOHR, "half_h_bohr": D3_CHECK_H_BOHR,
        "raw_asymmetry_max": float(np.max(np.abs(raw - raw.T))), "convergence": convergence})
    return 0.5 * (raw + raw.T)


def qualify_hessian(elements, coordinates_angstrom, manifest):
    import cupy as cp
    import numpy as np
    from pyscf.hessian import thermo
    from pyscf.data.nist import BOHR
    root = OUTPUT / "stationary_point_qualification"
    mol = molecule(elements, coordinates_angstrom)
    masses = mass_vector(mol)
    atomic_text(root / "geometry.xyz", xyz_text(
        elements, coordinates_angstrom, "MIN01 qualified endpoint Hessian geometry"))
    gpu = configure_gpu(mol); gpu.kernel()
    if not gpu.converged:
        raise RuntimeError("endpoint Hessian SCF did not converge")
    hessian_object, backend = require_gpu_grid_response(gpu.Hessian(), "RKS Hessian")
    raw_gpu = hessian_object.kernel()
    if hessian_object.grid_response is not True:
        raise RuntimeError("Hessian lost grid_response=True during execution")
    electronic4 = cp.asnumpy(raw_gpu) if isinstance(raw_gpu, cp.ndarray) else np.asarray(raw_gpu)
    if electronic4.shape != (N_ATOMS, N_ATOMS, 3, 3) or not np.isfinite(electronic4).all():
        raise RuntimeError("invalid electronic Hessian")
    electronic_raw = electronic4.transpose(0, 2, 1, 3).reshape(N_CART, N_CART)
    electronic = 0.5 * (electronic_raw + electronic_raw.T)
    dispersion = d3_hessian(elements, coordinates_angstrom / BOHR, root)
    total = electronic + dispersion
    for name, matrix in (("electronic", electronic), ("dispersion", dispersion), ("total", total)):
        if matrix.shape != (N_CART, N_CART) or not np.isfinite(matrix).all():
            raise RuntimeError(f"invalid {name} Hessian")
        atomic_array(root / f"{name}_hessian_hartree_per_bohr2.npy", matrix)
        atomic_matrix(root / f"{name}_hessian_hartree_per_bohr2.txt", matrix)
    total4 = total.reshape(N_ATOMS, 3, N_ATOMS, 3).transpose(0, 2, 1, 3)
    modes = thermo.harmonic_analysis(mol, total4, exclude_trans=True, exclude_rot=True,
                                    imaginary_freq=True, mass=masses)
    raw_frequencies = np.asarray(modes["freq_wavenumber"])
    signed = np.asarray([-abs(x.imag) if abs(x.imag) > 1e-12 else x.real for x in raw_frequencies])
    if np.any(signed == 0.0) or not np.isfinite(signed).all():
        raise RuntimeError("invalid or suspicious exact-zero projected vibrational spectrum")
    normal_modes = np.asarray(modes["norm_mode"])
    eigenvalues = np.asarray(modes["force_const_au"])
    if signed.shape != (N_CART - 6,) or normal_modes.shape != (N_CART - 6, N_ATOMS, 3):
        raise RuntimeError("projected frequency/mode dimensions are invalid")
    atomic_matrix(root / "signed_frequencies_cm-1.txt", signed.reshape(-1, 1))
    atomic_matrix(root / "signed_mass_weighted_eigenvalues_atomic_units.txt",
                  eigenvalues.reshape(-1, 1))
    atomic_array(root / "normal_modes_cartesian_per_sqrt_amu.npy", normal_modes)
    atomic_array(root / "normal_modes_mass_weighted.npy",
                 normal_modes * np.sqrt(masses)[None, :, None])
    signed_negative_count = int(np.sum(signed < 0.0))
    negative_count = int(np.sum(signed < -20.0))
    ambiguous_count = int(np.sum((signed >= -50.0) & (signed < -20.0)))
    severe_count = int(np.sum(signed < -50.0))
    classification = "VERIFIED_LOCAL_MINIMUM" if signed_negative_count == 0 else "SADDLE_POINT"
    legacy_magnitude_diagnostic = ("NO_MODE_BELOW_MINUS20" if negative_count == 0 else
        ("MODE_BELOW_MINUS50" if severe_count else "MODE_IN_MINUS50_TO_MINUS20"))
    coordinates_bohr = mol.atom_coords()
    center = np.einsum("i,ix->x", masses, coordinates_bohr) / np.sum(masses)
    tr_contract = thermo._get_TR(masses, coordinates_bohr - center)
    if not isinstance(tr_contract, tuple) or len(tr_contract) != 6:
        raise RuntimeError("unexpected PySCF rigid-body return contract")
    if any(np.asarray(vector).shape != (N_CART,) for vector in tr_contract):
        raise RuntimeError("PySCF rigid-body vector has unexpected dimension")
    tr_vectors = np.stack(tr_contract, axis=0)
    tr_singular_values = np.linalg.svd(tr_vectors, compute_uv=False)
    rigid_rank = int(np.linalg.matrix_rank(tr_vectors))
    if rigid_rank != 6:
        raise RuntimeError(f"unexpected rigid-body projection rank: {rigid_rank}")
    atomic_json(root / "rigid_body_projection_diagnostics.json", {
        "molecule_is_nonlinear": True, "rigid_body_projection_rank": rigid_rank,
        "rigid_body_vector_count": int(tr_vectors.shape[0]),
        "rigid_body_singular_values": tr_singular_values.tolist(),
        "cartesian_dimension": N_CART, "projected_vibrational_dimension": len(signed),
        "projection": "PySCF harmonic_analysis exclude_trans=True exclude_rot=True",
        "mass_vector_sha256": sha256(INPUT / "MASS_VECTOR.json")})
    component_provenance = {
        "observable": "Cartesian Hessian", "method": "PBE-D3(BJ)/def2-SVP",
        "geometry_sha256": sha256(root / "geometry.xyz"), "grid_level": 5,
        "components": {
            "electronic": {"producer": backend, "grid_response": True,
                "output_sha256": sha256(root / "electronic_hessian_hartree_per_bohr2.npy")},
            "dispersion": {"producer": "simple-dftd3 1.5.0 symmetric gradient differences",
                "parameters": D3_PARAMETERS, "output_sha256": sha256(root / "dispersion_hessian_hartree_per_bohr2.npy")},
            "total": {"producer": "component sum", "equation": "total=electronic+dispersion",
                "output_sha256": sha256(root / "total_hessian_hartree_per_bohr2.npy")}},
        "execution_identity": {"runner_sha256": sha256(Path(__file__)),
            "runtime_environment_sha256": sha256(OUTPUT / "RUNTIME_ENVIRONMENT.json")}}
    atomic_json(root / "HESSIAN_COMPONENT_EXECUTION_PROVENANCE.json", component_provenance)
    atomic_json(root / "STATIONARY_POINT_RESULT.json", {
        "classification": classification, "lowest_signed_frequency_cm-1": float(np.min(signed)),
        "negative_signed_frequency_count": signed_negative_count,
        "negative_below_minus20_count": negative_count, "minus50_to_minus20_count": ambiguous_count,
        "negative_below_minus50_count": severe_count,
        "legacy_magnitude_diagnostic_not_integrity_classification": legacy_magnitude_diagnostic,
        "projected_vibrational_mode_count": len(signed), "projected_rigid_body_mode_count": 6,
        "mass_convention": MASS_CONVENTION, "mass_vector_amu": masses.tolist(),
        "mass_vector_sha256": sha256(INPUT / "MASS_VECTOR.json"),
        "geometry_sha256": sha256(root / "geometry.xyz"),
        "electronic_hessian_sha256": sha256(root / "electronic_hessian_hartree_per_bohr2.npy"),
        "dispersion_hessian_sha256": sha256(root / "dispersion_hessian_hartree_per_bohr2.npy"),
        "total_hessian_sha256": sha256(root / "total_hessian_hartree_per_bohr2.npy"),
        "frequency_source_total_hessian_sha256": sha256(root / "total_hessian_hartree_per_bohr2.npy"),
        "hessian_components_complete": True, "frequency_mode_integrity_pass": True,
        "grid_level": 5, "grid_response_hessian": True, "hessian_backend": backend,
        "electronic_raw_asymmetry_max": float(np.max(np.abs(electronic_raw - electronic_raw.T))),
        "composition_max_abs": float(np.max(np.abs(total - (electronic + dispersion)))),
        "frequency_gate": manifest["stationary_point_classification"]})
    write_checksums(root)
    return {"classification": classification, "negative_vibrational_mode_count": signed_negative_count,
            "hessian_components_complete": True, "frequency_mode_integrity_pass": True}


def validate_publication_evidence(audit, qualification) -> bool:
    required = [
        OUTPUT / "RUNTIME_ENVIRONMENT.json", OUTPUT / "optimization/initial.xyz",
        OUTPUT / "optimization/final.xyz", OUTPUT / "optimization/OPTIMIZATION_RESULT.json",
        OUTPUT / "endpoint_gradient_audit/ENDPOINT_GRADIENT_AUDIT.json",
        OUTPUT / "stationary_point_qualification/geometry.xyz",
        OUTPUT / "stationary_point_qualification/electronic_hessian_hartree_per_bohr2.npy",
        OUTPUT / "stationary_point_qualification/dispersion_hessian_hartree_per_bohr2.npy",
        OUTPUT / "stationary_point_qualification/total_hessian_hartree_per_bohr2.npy",
        OUTPUT / "stationary_point_qualification/signed_mass_weighted_eigenvalues_atomic_units.txt",
        OUTPUT / "stationary_point_qualification/signed_frequencies_cm-1.txt",
        OUTPUT / "stationary_point_qualification/normal_modes_cartesian_per_sqrt_amu.npy",
        OUTPUT / "stationary_point_qualification/normal_modes_mass_weighted.npy",
        OUTPUT / "stationary_point_qualification/rigid_body_projection_diagnostics.json",
        OUTPUT / "stationary_point_qualification/HESSIAN_COMPONENT_EXECUTION_PROVENANCE.json",
        OUTPUT / "stationary_point_qualification/STATIONARY_POINT_RESULT.json"]
    missing = [str(path) for path in required if not path.is_file()]
    if missing:
        raise RuntimeError(f"publication evidence missing: {missing}")
    steps = sorted((OUTPUT / "optimization/steps").glob("step_*"))
    if not steps:
        raise RuntimeError("optimization produced no persisted steps")
    for step in steps:
        for name in ("geometry.xyz", "result.json", "electronic_gradient_hartree_per_bohr.txt",
                     "dispersion_gradient_hartree_per_bohr.txt", "total_gradient_hartree_per_bohr.txt",
                     "force_hartree_per_bohr.txt", "OPTIMIZER_STATE.json", "execution_receipt.json"):
            if not (step / name).is_file():
                raise RuntimeError(f"optimization step evidence missing: {step / name}")
        verify_checksums(step)
    verify_checksums(OUTPUT / "optimization")
    verify_checksums(OUTPUT / "endpoint_gradient_audit")
    verify_checksums(OUTPUT / "stationary_point_qualification")
    if not audit["pass"] or not qualification["hessian_components_complete"] \
            or not qualification["frequency_mode_integrity_pass"]:
        raise RuntimeError("scientific qualification gates incomplete")
    return True


def validate_stopped_evidence() -> bool:
    for path in (OUTPUT / "optimization/OPTIMIZATION_RESULT.json",
                 OUTPUT / "endpoint_gradient_audit/ENDPOINT_GRADIENT_AUDIT.json"):
        if not path.is_file():
            raise RuntimeError(f"stopped-run evidence missing: {path}")
    verify_checksums(OUTPUT / "optimization")
    verify_checksums(OUTPUT / "endpoint_gradient_audit")
    return True


def main():
    install_environment()
    import cupy as cp
    import pyscf
    manifest = json.loads(MANIFEST.read_text())
    for relative, expected in manifest["input_sha256"].items():
        path = ROOT / relative
        if not path.is_file() or sha256(path) != expected:
            raise RuntimeError(f"missing or modified input: {relative}")
    closure = verify_closure()
    if pyscf.__version__ != "2.14.0" or package_version("gpu4pyscf-cuda12x") != "1.8.0":
        raise RuntimeError("locked PySCF/GPU4PySCF identity mismatch")
    if package_version("dftd3") != "1.5.0" or sha256(d3_database_path()) != D3_DATABASE_SHA256:
        raise RuntimeError("locked simple-dftd3 identity mismatch")
    gpu_name = cp.cuda.runtime.getDeviceProperties(0)["name"]
    gpu_name = gpu_name.decode() if isinstance(gpu_name, bytes) else str(gpu_name)
    if "A100" not in gpu_name.upper():
        raise RuntimeError(f"A100 required, found {gpu_name}")
    elements, coordinates = read_xyz(INPUT / "MIN01_historical_saddle.xyz")
    if dict(Counter(elements)) != EXPECTED_COMPOSITION:
        raise RuntimeError("composition mismatch")
    with (INPUT / "ATOM_ORDER.csv").open(newline="") as source:
        rows = list(csv.DictReader(source))
    if [r["element"] for r in rows] != elements:
        raise RuntimeError("atom order mismatch")
    mass_vector(molecule(elements, coordinates))
    OUTPUT.mkdir(parents=True, exist_ok=False)
    atomic_json(OUTPUT / "RUNTIME_ENVIRONMENT.json", {
        "runner_sha256": sha256(Path(__file__)), "manifest_sha256": sha256(MANIFEST),
        "closure_archive_sha256": sha256(EVIDENCE), "closure_decision": closure["decision"],
        "python": platform.python_version(), "platform": platform.platform(),
        "pyscf": pyscf.__version__, "gpu4pyscf": package_version("gpu4pyscf-cuda12x"),
        "dftd3": package_version("dftd3"), "geometric": package_version("geometric"),
        "cupy": cp.__version__, "gpu": gpu_name,
        "pip_freeze": subprocess.check_output([sys.executable, "-m", "pip", "freeze"], text=True).splitlines()})
    endpoint, endpoint_result, endpoint_gradient = optimize(elements, coordinates)
    audit = endpoint_gradient_audit(elements, endpoint, endpoint_gradient, manifest)
    if not audit["pass"]:
        stopped_evidence_complete = validate_stopped_evidence()
        atomic_json(OUTPUT / "FINAL_RESULT.json", {"status": "STOPPED_ENDPOINT_GRADIENT_AUDIT_FAILED",
            "endpoint_gradient_audit_pass": False, "Hessian_run": False,
            "publication_evidence_complete": stopped_evidence_complete,
            "MIN02_run": False, "MIN04_run": False, "model_fit_run": False,
            "force_field_fit_run": False, "thresholds_changed": False,
            "GPU60_recomputed": False, "CURVATURE76_recomputed": False})
        write_checksums(OUTPUT); return
    qualification = qualify_hessian(elements, endpoint, manifest)
    publication_complete = validate_publication_evidence(audit, qualification)
    atomic_json(OUTPUT / "FINAL_RESULT.json", {"status": "COMPLETE",
        "optimization_converged": True,
        "endpoint_gradient_audit_pass": True, "Hessian_run": True,
        "hessian_components_complete": qualification["hessian_components_complete"],
        "negative_vibrational_mode_count": qualification["negative_vibrational_mode_count"],
        "frequency_mode_integrity_pass": qualification["frequency_mode_integrity_pass"],
        "publication_evidence_complete": publication_complete,
        "stationary_point_classification": qualification["classification"],
        "endpoint_energy_hartree": endpoint_result["total_energy_hartree"],
        "MIN02_run": False, "MIN04_run": False, "model_fit_run": False,
        "force_field_fit_run": False, "thresholds_changed": False,
        "GPU60_recomputed": False, "CURVATURE76_recomputed": False})
    write_checksums(OUTPUT)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        if OUTPUT.is_dir():
            atomic_json(OUTPUT / "FAILURE.json", {"status": "FAILED_PRESERVED",
                "exception_type": type(error).__name__, "message": str(error),
                "MIN02_run": False, "MIN04_run": False, "model_fit_run": False})
            write_checksums(OUTPUT)
        raise
