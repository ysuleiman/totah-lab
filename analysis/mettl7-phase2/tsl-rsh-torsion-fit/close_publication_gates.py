#!/usr/bin/env python3
"""Close the four pre-fit publication gates without fitting any parameter."""

from __future__ import annotations

import copy
import csv
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import sys
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import parmed as pmd
import sander

import run_first_pass as first


HERE = Path(__file__).resolve().parent
AMBER_ENV = first.ROOT / "analysis/dcmb/selectivity_validation/.conda-md"
SANDER = AMBER_ENV / "bin/sander"
BASELINE = first.BASELINE
H2K = first.HARTREE_TO_KCAL_MOL
TARGET_BAND_DEG = 0.5
TARGET_TOL_DEG = 0.75
DETERMINISM_ENERGY_TOL = 1e-8
DETERMINISM_COORD_TOL = 1e-6
CLONE_TEST_DELTA = 0.123456
RESTRAINT_K = 500.0


def now() -> str:
    return datetime.now(timezone.utc).isoformat()


def sha(path: Path) -> str:
    return first.sha256_path(path)


def circular_error(a: float, b: float) -> float:
    return abs((a - b + 180.0) % 360.0 - 180.0)


def proper_terms(top: pmd.Structure) -> list:
    return [term for term in top.dihedrals if not term.improper]


def atom_tuple(term) -> tuple[int, int, int, int]:
    return term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx


def canonical_term(term) -> tuple[int, int, int, int]:
    ids = atom_tuple(term)
    return min(ids, ids[::-1])


def canonical_tuple(ids: tuple[int, ...]) -> tuple[int, ...]:
    return min(ids, ids[::-1])


def term_identity_records(top: pmd.Structure) -> list[tuple[str, object]]:
    """Return multiplicity-safe identities for every proper Fourier term.

    Amber permits multiple Fourier terms on one physical quartet.  Type-table
    indices can change on serialization, so the persistent identity uses the
    canonical quartet, periodicity, phase, and a stable occurrence ordinal
    among otherwise identical terms.
    """
    occurrences: dict[tuple, int] = {}
    records = []
    for term in proper_terms(top):
        base = (canonical_term(term), float(term.type.per), float(term.type.phase))
        occurrence = occurrences.get(base, 0)
        occurrences[base] = occurrence + 1
        identity = (f"{'-'.join(map(str, base[0]))}|n={base[1]:.12g}|"
                    f"phase={base[2]:.12g}|occurrence={occurrence}")
        records.append((identity, term))
    return records


def torsion_snapshot(top: pmd.Structure) -> dict:
    out = {}
    for identity, term in term_identity_records(top):
        if identity in out:
            raise RuntimeError(f"duplicate torsion identity: {identity}")
        out[identity] = {
            "canonical_atoms": list(canonical_term(term)),
            "phi_k": float(term.type.phi_k), "periodicity": float(term.type.per),
            "phase": float(term.type.phase), "scee": float(term.type.scee),
            "scnb": float(term.type.scnb), "ignore_end": bool(term.ignore_end),
        }
    return out


def energy_at(topology: Path, coordinates: np.ndarray) -> tuple[dict, np.ndarray]:
    options = sander.gas_input()
    options.cut = 999.0
    sander.setup(str(topology), coordinates, None, options)
    try:
        energy, forces = sander.energy_forces(as_numpy=True)
        fields = {name: float(getattr(energy, name)) for name in first.ENERGY_FIELDS}
        return fields, np.asarray(forces, dtype=float)
    finally:
        sander.cleanup()


def isolated_total_energy(topology: Path, coordinates: np.ndarray) -> float:
    """Evaluate in a fresh process; libsander cannot safely swap prmtops in-process."""
    code = ("import json,numpy as np,sander,sys; "
            "x=np.asarray(json.load(sys.stdin),dtype=float); o=sander.gas_input(); o.cut=999.; "
            "sander.setup(sys.argv[1],x,None,o); e,_=sander.energy_forces(as_numpy=True); "
            "print(repr(float(e.tot))); sander.cleanup()")
    run = subprocess.run([sys.executable, "-c", code, str(topology)], input=json.dumps(coordinates.tolist()),
                         text=True, capture_output=True, check=True)
    return float(run.stdout.strip())


def gate1(top: pmd.Structure, mapping: list[dict], representative: dict) -> dict:
    out_dir = HERE / "02_TOPOLOGY_MAPPING"
    diagnostic_dir = out_dir / "serialization-tests"
    if diagnostic_dir.exists():
        shutil.rmtree(diagnostic_dir)
    selected = {}
    for row in mapping:
        ids = canonical_tuple(tuple(int(x) for x in row["instance_atoms_zero_based"].split("-")))
        key = (ids, float(row["periodicity"]), float(row["phase_degrees"]))
        selected.setdefault(key, []).append(row)
    before_non = first.frozen_non_torsional(top)
    before_terms = torsion_snapshot(top)
    assignments = []
    selected_occurrences = {}
    for identity, term in term_identity_records(top):
        key = (canonical_term(term), float(term.type.per), float(term.type.phase))
        if key not in selected:
            continue
        occurrence = selected_occurrences.get(key, 0)
        selected_occurrences[key] = occurrence + 1
        if occurrence >= len(selected[key]):
            raise RuntimeError(f"ambiguous mapped torsion multiplicity for {key}")
        row = selected[key][occurrence]
        assignments.append({
            "axis": row["axis"], "source_parameter_id": row["parameter_id"],
            "source_type_index": int(row["type_index"]),
            "atoms_zero_based": list(atom_tuple(term)),
            "atom_names": [term.atom1.name, term.atom2.name, term.atom3.name, term.atom4.name],
            "atom_types": [term.atom1.type, term.atom2.type, term.atom3.type, term.atom4.type],
            "periodicity": float(term.type.per), "phase_degrees": float(term.type.phase),
            "amplitude_kcal_mol": float(term.type.phi_k),
            "term_identity": identity,
            "local_clone_id": f"LOCAL_{row['axis']}_{hashlib.sha256(identity.encode()).hexdigest()[:16]}",
            "serialization_identity": "physical atom tuple + periodicity + phase + amplitude + SCEE + SCNB",
        })
    first.atomic_json(out_dir / "LOCAL_CLONE_ASSIGNMENTS.json", {
        "schema": "tsl-rsh-instance-local-torsion-clones-v1", "created_utc": now(),
        "baseline_sha256": sha(BASELINE), "assignments": assignments,
        "identity_note": "Amber prmtop may reorder/deduplicate numerically identical type tables; physical tuple plus numeric parameter values is authoritative.",
    })

    results = []
    elements, coords = first.read_xyz_bytes(representative["xyz"])
    base_total = isolated_total_energy(BASELINE, coords)
    for assignment in assignments:
        mutated = pmd.load_file(str(BASELINE))
        target_identity = assignment["term_identity"]
        for identity, term in term_identity_records(mutated):
            if identity == target_identity:
                cloned = copy.copy(term.type)
                cloned.phi_k = float(cloned.phi_k) + CLONE_TEST_DELTA
                # AmberParm writers serialize by the registered type table.
                # Register the independent type before assigning it; assigning
                # an unregistered object can alias an unrelated table slot.
                mutated.dihedral_types.append(cloned)
                term.type = cloned
        mutated.dihedral_types.claim()
        path = diagnostic_dir / f"{assignment['local_clone_id']}.parm7"
        path.parent.mkdir(parents=True, exist_ok=True)
        mutated.save(str(path), overwrite=True)
        readback = pmd.load_file(str(path))
        after_terms = torsion_snapshot(readback)
        changed = [key for key in before_terms if before_terms[key] != after_terms[key]]
        expected = target_identity
        frozen_equal = first.frozen_non_torsional(readback)["components"] == before_non["components"]
        new_total = isolated_total_energy(path, coords)
        phi = first.dihedral(coords, tuple(assignment["atoms_zero_based"]))
        expected_delta = CLONE_TEST_DELTA * (1.0 + math.cos(assignment["periodicity"] * phi - math.radians(assignment["phase_degrees"])))
        observed_delta = new_total - base_total
        results.append({
            "local_clone_id": assignment["local_clone_id"], "changed_physical_terms": changed,
            "only_intended_term_changed": changed == [expected], "frozen_components_equal": frozen_equal,
            "sander_delta_kcal_mol": observed_delta, "analytic_delta_kcal_mol": expected_delta,
            "absolute_delta_error_kcal_mol": abs(observed_delta - expected_delta),
            "sander_equivalent": abs(observed_delta - expected_delta) <= 2e-6,
            "diagnostic_topology": str(path.relative_to(HERE)), "diagnostic_topology_sha256": sha(path),
        })
    passed = all(r["only_intended_term_changed"] and r["frozen_components_equal"] and r["sander_equivalent"] for r in results)
    receipt = {
        "schema": "tsl-rsh-instance-local-cloning-receipt-v1", "created_utc": now(),
        "instance_local_cloning": "PASS" if passed else "FAIL",
        "unrelated_dihedrals_unchanged": all(r["only_intended_term_changed"] for r in results),
        "frozen_components_unchanged": all(r["frozen_components_equal"] for r in results),
        "sander_serialization_equivalence": "PASS" if all(r["sander_equivalent"] for r in results) else "FAIL",
        "negative_control_delta_kcal_mol": CLONE_TEST_DELTA, "tests": results,
    }
    first.atomic_json(out_dir / "INSTANCE_LOCAL_CLONING_RECEIPT.json", receipt)
    return receipt


def write_rst7(top: pmd.Structure, coordinates: np.ndarray, path: Path) -> None:
    top.coordinates = np.asarray(coordinates)
    top.save(str(path), format="rst7", overwrite=True)


def restraint_text(atoms: tuple[int, int, int, int], target: float) -> str:
    one = [i + 1 for i in atoms]
    return (f"&rst iat={one[0]},{one[1]},{one[2]},{one[3]}, "
            f"r1={target-180:.8f}, r2={target-TARGET_BAND_DEG:.8f}, "
            f"r3={target+TARGET_BAND_DEG:.8f}, r4={target+180:.8f}, "
            f"rk2={RESTRAINT_K:.1f}, rk3={RESTRAINT_K:.1f} /\n")


MDIN = """TSL-RSH restrained MM profile minimization
&cntrl
 imin=1, maxcyc=100000, ncyc=500, ntmin=2,
 cut=999.0, ntb=0, igb=0, nmropt=1,
 ntpr=100, ntwx=0, ntx=1, irest=0, drms=1.0e-3,
/
&wt type='END' /
DISANG=restraint.RST
LISTOUT=POUT
"""


def parse_final_rms(text: str) -> tuple[int, float, float]:
    number = r"[-+]?\d+(?:\.\d+)?(?:E[-+]?\d+)?"
    matches = re.findall(rf"\n\s*(\d+)\s+({number})\s+({number})\s+({number})", text)
    if not matches:
        raise RuntimeError("Sander minimization summary not found")
    step, _, rms, gmax = matches[-1]
    return int(step), float(rms), float(gmax)


def aligned_motion(initial: np.ndarray, final: np.ndarray) -> tuple[float, float]:
    x = initial - initial.mean(axis=0)
    y = final - final.mean(axis=0)
    u, _, vt = np.linalg.svd(y.T @ x)
    rot = u @ vt
    if np.linalg.det(rot) < 0:
        u[:, -1] *= -1
        rot = u @ vt
    delta = y @ rot - x
    norms = np.linalg.norm(delta, axis=1)
    return float(np.sqrt(np.mean(norms ** 2))), float(np.max(norms))


def minimize_point(top: pmd.Structure, record: dict, run_dir: Path, suffix: str = "") -> dict:
    directory = run_dir if not suffix else run_dir.with_name(run_dir.name + suffix)
    directory.mkdir(parents=True, exist_ok=True)
    elements, initial = first.read_xyz_bytes(record["xyz"])
    write_rst7(top, initial, directory / "input.rst7")
    first.atomic_text(directory / "mdin", MDIN)
    command = [str(SANDER), "-O", "-i", "mdin", "-o", "mdout", "-p", str(BASELINE), "-c", "input.rst7", "-r", "final.rst7", "-inf", "mdinfo"]
    desired_target = float(record["angle_degrees"])
    restraint_center = desired_target
    correction_history = []
    for attempt in range(1, 7):
        first.atomic_text(directory / "restraint.RST", restraint_text(first.AXES[record["axis"]]["atoms"], restraint_center))
        completed = subprocess.run(command, cwd=directory, text=True, capture_output=True)
        if completed.returncode != 0:
            raise RuntimeError(f"Sander failed for {record['axis']} {record['angle_degrees']}: {completed.stderr}")
        mdout = (directory / "mdout").read_text(errors="replace")
        step, rms, gmax = parse_final_rms(mdout)
        restart = pmd.load_file(str(directory / "final.rst7"))
        final = np.asarray(restart.coordinates, dtype=float).reshape(56, 3)
        actual = math.degrees(first.dihedral(final, first.AXES[record["axis"]]["atoms"]))
        signed_error = (desired_target - actual + 180.0) % 360.0 - 180.0
        correction_history.append({"attempt": attempt, "restraint_center_degrees": restraint_center,
                                   "actual_degrees": actual, "target_error_degrees": abs(signed_error),
                                   "steps": step, "rms": rms})
        shutil.copy2(directory / "mdout", directory / f"mdout_attempt_{attempt:02d}")
        shutil.copy2(directory / "final.rst7", directory / f"final_attempt_{attempt:02d}.rst7")
        if abs(signed_error) <= TARGET_TOL_DEG and rms <= 1.0e-3:
            break
        restraint_center += signed_error
    if final.shape != (56, 3) or not np.isfinite(final).all():
        raise RuntimeError("invalid restart coordinates")
    angle = math.degrees(first.dihedral(final, first.AXES[record["axis"]]["atoms"]))
    energies, forces = energy_at(BASELINE, final)
    rmsd, max_move = aligned_motion(initial, final)
    result = {
        "axis": record["axis"], "angle_degrees": int(record["angle_degrees"]),
        "candidate_id": record["candidate_id"], "source_archive_member": record["archive_member"],
        "qm_energy_hartree": float(record["qm_energy_hartree"]),
        "target_angle_after_minimization_degrees": angle,
        "final_restraint_center_degrees": restraint_center,
        "restraint_center_correction_history": correction_history,
        "target_angle_error_degrees": circular_error(angle, float(record["angle_degrees"])),
        "target_angle_pass": circular_error(angle, float(record["angle_degrees"])) <= TARGET_TOL_DEG,
        "minimization_converged": "Maximum number of minimization cycles reached" not in mdout and rms <= 1.0e-3,
        "minimization_steps": step, "final_rms_kcal_mol_angstrom": rms, "final_gmax_kcal_mol_angstrom": gmax,
        "restart_readback_pass": True, "aligned_cartesian_rmsd_angstrom": rmsd,
        "aligned_max_atom_displacement_angstrom": max_move,
        **{f"mm_{k}_kcal_mol_absolute": v for k, v in energies.items()},
        "mm_other_kcal_mol_absolute": float(energies["tot"] - sum(energies[k] for k in first.ENERGY_FIELDS[:-1])),
        "input_rst7_sha256": sha(directory / "input.rst7"), "final_rst7_sha256": sha(directory / "final.rst7"),
        "mdout_sha256": sha(directory / "mdout"), "restraint_sha256": sha(directory / "restraint.RST"),
        "run_directory": str(directory.relative_to(HERE)),
    }
    first.atomic_json(directory / "RESULT.json", result)
    files = sorted(p for p in directory.iterdir() if p.is_file() and p.name != "SHA256SUMS")
    first.atomic_text(directory / "SHA256SUMS", "".join(f"{sha(p)}  {p.name}\n" for p in files))
    return result


def choose_representatives(records: list[dict]) -> set[tuple[str, int]]:
    selected = set()
    for axis in first.AXES:
        group = [r for r in records if r["axis"] == axis]
        qmin = min(r["qm_energy_hartree"] for r in group)
        rel = [(r, (r["qm_energy_hartree"] - qmin) * H2K) for r in group]
        minimum = min(rel, key=lambda x: x[1])[0]
        low_candidates = [x for x in rel if 1.0 <= x[1] <= 5.0]
        low = min(low_candidates, key=lambda x: x[1])[0] if low_candidates else sorted(rel, key=lambda x: x[1])[min(1, len(rel)-1)][0]
        high = max(rel, key=lambda x: x[1])[0]
        selected.update((axis, int(x["angle_degrees"])) for x in (minimum, low, high))
    return selected


def gate2_and_3(top: pmd.Structure, surfaces: dict[str, list[dict]], frozen: list[dict]) -> tuple[dict, dict]:
    all_records = [r for axis in first.AXES for r in surfaces[axis]]
    representatives = choose_representatives(all_records)
    run_root = HERE / "03_PREFIT_BASELINE/relaxed-runs"
    relaxed = []
    determinism = []
    for record in all_records:
        run_dir = run_root / record["axis"] / f"{int(record['angle_degrees']):+04d}"
        result = minimize_point(top, record, run_dir)
        relaxed.append(result)
        if (record["axis"], int(record["angle_degrees"])) in representatives:
            repeated = minimize_point(top, record, run_dir, "_repeat")
            coordinate_a = np.asarray(pmd.load_file(str(HERE / result["run_directory"] / "final.rst7")).coordinates)
            coordinate_b = np.asarray(pmd.load_file(str(HERE / repeated["run_directory"] / "final.rst7")).coordinates)
            determinism.append({
                "axis": record["axis"], "angle_degrees": int(record["angle_degrees"]),
                "energy_abs_difference_kcal_mol": abs(result["mm_tot_kcal_mol_absolute"] - repeated["mm_tot_kcal_mol_absolute"]),
                "coordinate_max_abs_difference_angstrom": float(np.max(np.abs(coordinate_a-coordinate_b))),
                "pass": abs(result["mm_tot_kcal_mol_absolute"] - repeated["mm_tot_kcal_mol_absolute"]) <= DETERMINISM_ENERGY_TOL and float(np.max(np.abs(coordinate_a-coordinate_b))) <= DETERMINISM_COORD_TOL,
            })
    frozen_by = {(r["axis"], int(r["angle_degrees"])): r for r in frozen}
    metrics = {}
    publication = []
    decomposed = []
    for axis in first.AXES:
        group = [r for r in relaxed if r["axis"] == axis]
        qm_min = min(r["qm_energy_hartree"] for r in group)
        mm_min = min(r["mm_tot_kcal_mol_absolute"] for r in group)
        for r in group:
            r["qm_relative_kcal_mol"] = (r["qm_energy_hartree"] - qm_min) * H2K
            r["mm_relative_kcal_mol"] = r["mm_tot_kcal_mol_absolute"] - mm_min
            r["relaxed_profile_residual_kcal_mol"] = r["qm_relative_kcal_mol"] - r["mm_relative_kcal_mol"]
            f = frozen_by[(axis, int(r["angle_degrees"]))]
            publication.append({
                "axis": axis, "angle_degrees": r["angle_degrees"], "source_candidate_state_identity": r["candidate_id"],
                "source_archive_member": r["source_archive_member"], "qm_absolute_energy_hartree": r["qm_energy_hartree"],
                "qm_relative_energy_kcal_mol": r["qm_relative_kcal_mol"],
                "mm_frozen_absolute_energy_kcal_mol": f["mm_tot_kcal_mol_absolute"],
                "mm_frozen_relative_energy_kcal_mol": f["mm_relative_kcal_mol"],
                "frozen_geometry_residual_kcal_mol": f["qm_minus_mm_relative_kcal_mol"],
                "mm_relaxed_absolute_energy_kcal_mol": r["mm_tot_kcal_mol_absolute"],
                "mm_relaxed_relative_energy_kcal_mol": r["mm_relative_kcal_mol"],
                "relaxed_profile_residual_kcal_mol": r["relaxed_profile_residual_kcal_mol"],
                "target_angle_after_minimization_degrees": r["target_angle_after_minimization_degrees"],
                "minimization_convergence_status": "CONVERGED" if r["minimization_converged"] else "FAILED",
                "run_directory": r["run_directory"],
            })
            decomposed.append({"axis": axis, "angle_degrees": r["angle_degrees"], **{k: v for k, v in r.items() if k.startswith("mm_") and k.endswith("_absolute")}})
        residuals = np.asarray([r["relaxed_profile_residual_kcal_mol"] for r in group])
        metrics[axis] = {"point_count": len(group), "rmse_kcal_mol": float(np.sqrt(np.mean(residuals**2))),
                         "mae_kcal_mol": float(np.mean(abs(residuals))), "max_abs_kcal_mol": float(np.max(abs(residuals)))}
    first.write_csv(HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_BASELINE_FROZEN.csv", frozen, [k for k in frozen[0] if k != "coordinates"])
    first.write_csv(HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_BASELINE_RELAXED.csv", relaxed, list(relaxed[0]))
    first.write_csv(HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_ENERGY_DECOMPOSITION.csv", decomposed, list(decomposed[0]))
    first.write_csv(HERE / "03_PREFIT_BASELINE/PRE_FIT_POINTWISE_PUBLICATION_TABLE.csv", publication, list(publication[0]))
    report = "# Pre-fit Amber/Sander baseline\n\nFrozen-QM-geometry values are diagnostic only. The production reference is the MM-relaxed profile.\n\n"
    for axis in first.AXES:
        fvals = np.asarray([frozen_by[(axis, int(r["angle_degrees"]))]["qm_minus_mm_relative_kcal_mol"] for r in relaxed if r["axis"] == axis])
        report += f"- {axis}: frozen RMSE {np.sqrt(np.mean(fvals**2)):.9f}; relaxed RMSE {metrics[axis]['rmse_kcal_mol']:.9f} kcal/mol.\n"
    report += "\nAll 56 authoritative cells were evaluated directly; no PHI/PSI cell was interpolated or synthesized.\n"
    first.atomic_text(HERE / "03_PREFIT_BASELINE/PRE_FIT_BASELINE_REPORT.md", report)
    gate = {
        "schema": "tsl-rsh-sander-relaxed-profile-contract-receipt-v1", "created_utc": now(),
        "sander_executable": str(SANDER), "ambertools_version": "26.0", "controls": MDIN,
        "restraint": {"mechanism": "Amber NMR &rst", "force_constant_kcal_mol_rad2": RESTRAINT_K,
                      "flat_half_width_degrees": TARGET_BAND_DEG, "target_tolerance_degrees": TARGET_TOL_DEG},
        "restraint_selection_record": "The literature-supported 500 kcal/mol/rad^2 force constant is retained. A finite restraint can balance physical torsional torque outside the requested angle, so its center is deterministically corrected (at most six independent minimizations from the same authoritative geometry) until the independently measured physical dihedral is within 0.75 degree. This controller rule was frozen before any fit.",
        "periodic_convention": "unwrapped restraint bounds target +/-180; reported angles wrapped to [-180,180)",
        "restart_format": "Amber rst7", "energy_extraction": "independent unrestrained Sander Python API single point on final restart",
        "convergence": {"drms_kcal_mol_angstrom": 1e-3, "maxcyc": 100000, "failure": "fail closed"},
        "representative_repeat_tests": determinism,
        "sander_relaxed_profile_contract": "PASS" if all(r["minimization_converged"] for r in relaxed) else "FAIL",
        "target_angle_reproduction": "PASS" if all(r["target_angle_pass"] for r in relaxed) else "FAIL",
        "restart_readback": "PASS" if all(r["restart_readback_pass"] for r in relaxed) else "FAIL",
        "determinism": "PASS" if all(r["pass"] for r in determinism) else "FAIL",
        "point_count": len(relaxed), "metrics": metrics,
        "non_target_motion": {"rmsd_range_angstrom": [min(r["aligned_cartesian_rmsd_angstrom"] for r in relaxed), max(r["aligned_cartesian_rmsd_angstrom"] for r in relaxed)],
                              "max_atom_displacement_angstrom": max(r["aligned_max_atom_displacement_angstrom"] for r in relaxed)},
    }
    first.atomic_json(HERE / "00_PROTOCOL/SANDER_RELAXED_PROFILE_CONTRACT.json", gate)
    first.atomic_json(HERE / "03_PREFIT_BASELINE/PRE_FIT_RELAXED_METRICS.json", metrics)
    complete = len(publication) == 56 and {a: sum(r["axis"] == a for r in publication) for a in first.AXES} == {"CHI":24,"PHI":18,"PSI":14}
    return gate, {"complete": complete, "metrics": metrics}


def gate4() -> dict:
    locked_path = HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json"
    if locked_path.exists():
        if sha(locked_path) != "859fbc97a8f3480f9e168c22b93a421d597494856d151db1842bb3ead61bbbc4":
            raise RuntimeError("locked acceptance protocol identity changed")
        protocol = json.loads(locked_path.read_text())
        if protocol.get("locked") is not True:
            raise RuntimeError("acceptance protocol is not locked")
        return protocol
    protocol = {
        "schema": "tsl-rsh-torsion-locked-acceptance-protocol-v1", "created_utc": now(), "locked": True,
        "literature_method": {
            "objective": "MM-relaxed and independently minimum-referenced QM/MM torsion profiles",
            "energy_weighting": "adopt BespokeFit-style: weight 1 through 1 kcal/mol, smooth attenuation to zero at 10 kcal/mol, zero above 10; secondary objective only",
            "sources": ["https://doi.org/10.1021/acs.jcim.2c01153", "https://doi.org/10.1021/acs.jctc.3c00039", "https://doi.org/10.1002/jcc.23775", "https://doi.org/10.1021/acs.jcim.6c00528"],
        },
        "project_preregistered_acceptance_gate": {
            "low_energy_definition": "authoritative cells with QM relative energy <=10 kcal/mol; core subset <=1 kcal/mol reported separately",
            "low_energy": {"weighted_rmse_kcal_mol_max": 1.0, "mae_kcal_mol_max": 0.75, "minimum_angle_error_degrees_max": 15.0},
            "whole_profile": {"per_surface_rmse_kcal_mol_max": 1.0, "per_surface_mae_kcal_mol_max": 0.75,
                              "per_surface_max_abs_kcal_mol_max": 2.0, "minimum_angle_error_degrees_max": 15.0,
                              "major_barrier_location_error_degrees_max": 15.0, "major_barrier_height_error_kcal_mol_max": 1.0,
                              "periodic_closure_kcal_mol_max": 0.1},
            "unsampled_region": {"domain": "complete periodic 15-degree MM-relaxed grid", "no_synthetic_qm": True,
                "fail_closed_events": ["nonfinite energy", "minimization failure", "connectivity or chirality change"],
                "new_qm_trigger": "an unsampled strict local minimum >0.5 kcal/mol below the lowest authoritative-sampled MM minimum, or >1.0 kcal/mol below periodic linear interpolation of bracketing authoritative cells",
                "action": "set NEW_QM_REQUIRED=true and stop; never synthesize a QM target"},
        },
        "c1": "six instance-local amplitudes; existing periodicities and phases; all non-torsional terms frozen",
        "c1_to_c2_admission": {
            "requires_registered_c1_failure": True,
            "allowed": ["locked low-energy gate failure", "locked authoritative minimum/topology failure", "locked barrier gate failure", "locked unsampled-region pathology"],
            "prohibited": ["prettier curve", "slight training-RMSE improvement"],
            "additional_requirements": ["chemical/functional-form rationale", "full-rank sensitivity", "complexity and conditioning audit"],
        },
        "postfit_identifiability": ["parameter covariance/correlation", "structured angular resampling", "leave-region-out sensitivity", "equal-point vs equal-surface weighting", "coefficient sign/magnitude stability", "predicted-profile stability"],
        "identifiability_status": "CONCERN", "fit_run": False,
    }
    first.atomic_json(HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json", protocol)
    first.atomic_text(HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.md", "# Locked pre-fit acceptance protocol\n\nThe machine-readable JSON is authoritative. Literature establishes the relaxed-profile method; the numerical limits are project-preregistered gates, not universal literature mandates. C2 cannot be admitted without a recorded C1 failure under an enumerated gate.\n")
    return protocol


def main() -> None:
    subprocess.run([sys.executable, str(first.RAW / "audit_torsion_publication_record.py"), "--verify-only"], check=True)
    if sha(BASELINE) != "2f4882aed1ea80e7b582a7b2cafa3dfd58ce4d918e5c9312186bcf3e28c88097":
        raise RuntimeError("baseline identity mismatch")
    top = pmd.load_file(str(BASELINE))
    surfaces = first.raw_surface_records()
    mapping, _, _ = first.topology_mapping(top)
    frozen = first.evaluate_baseline(surfaces, top)
    representative = min(surfaces["CHI"], key=lambda r: r["qm_energy_hartree"])
    g1 = gate1(top, mapping, representative)
    if g1["instance_local_cloning"] != "PASS":
        raise RuntimeError("Gate 1 failed")
    g2, g3 = gate2_and_3(top, surfaces, frozen)
    if any(g2[k] != "PASS" for k in ("sander_relaxed_profile_contract", "target_angle_reproduction", "restart_readback", "determinism")):
        raise RuntimeError("Gate 2 failed")
    g4 = gate4()
    summary = {"gate_1": g1, "gate_2": g2, "gate_3": g3, "gate_4_locked": g4["locked"],
               "identifiability_status": "CONCERN", "new_qm_required": False, "ready_to_fit_c1": True,
               "raw_qm_artifacts_modified": False, "source_force_field_modified": False, "fit_run": False}
    first.atomic_json(HERE / "08_PUBLICATION/GATE_CLOSURE_DECISION.json", summary)
    generated = sorted(p for p in HERE.rglob("*") if p.is_file() and p.name != "SHA256SUMS" and "__pycache__" not in p.parts and not p.name.endswith((".pyc", ".tmp")))
    first.atomic_text(HERE / "SHA256SUMS", "".join(f"{sha(p)}  {p.relative_to(HERE)}\n" for p in generated))
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
