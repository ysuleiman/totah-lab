#!/usr/bin/env python3
"""Publication first pass: immutable inputs, AMBER mapping, baseline, and design audit.

Run with the accepted AmberTools/ParmEd environment. This script performs no
parameter optimization and never writes to the raw torsion campaign directory.
"""

from __future__ import annotations

import csv
import hashlib
import io
import json
import math
import os
import subprocess
import sys
import tarfile
import zipfile
from collections import defaultdict
from pathlib import Path, PurePosixPath

import numpy as np
import parmed as pmd
import sander


HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
RAW = ROOT / "analysis/mettl7-phase2/tsl-rsh-torsiondrive-campaign"
BASELINE_DIR = ROOT / "analysis/mettl7-phase2/tsl-rsh-force54-fit-package/TSL_RSH_FORCE54_FIT_PACKAGE/baseline"
BASELINE = BASELINE_DIR / "baseline.parm7"
BASELINE_IDENTITY = BASELINE_DIR / "BASELINE_IDENTITY.json"
HARTREE_TO_KCAL_MOL = 627.509474
AXES = {
    "CHI": {"atoms": (55, 25, 9, 8), "archive": "TSL_RSH_TORSIONDRIVE_BACKUP-20260825T144336Z-1-001.zip"},
    "PHI": {"atoms": (25, 9, 8, 7), "archive": "TSL_RSH_PHI_RUNPOD_RESULTS.tar.gz"},
    "PSI": {"atoms": (9, 8, 7, 1), "archive": "TSL_RSH_PSI_RUNPOD_RESULTS.tar.gz"},
}
OUTPUT_DIRS = [
    "00_PROTOCOL", "01_INPUT_MANIFEST", "02_TOPOLOGY_MAPPING", "03_PREFIT_BASELINE",
    "04_FIT", "05_VALIDATION", "06_FIGURES", "07_TABLES", "08_PUBLICATION",
]


def sha256_path(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for block in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def atomic_text(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(text)
    os.replace(temporary, path)


def atomic_json(path: Path, value: object) -> None:
    atomic_text(path, json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def read_xyz_bytes(data: bytes) -> tuple[list[str], np.ndarray]:
    lines = data.decode().splitlines()
    if int(lines[0]) != 56:
        raise RuntimeError("expected 56 atoms")
    rows = [line.split() for line in lines[2:58]]
    if len(rows) != 56:
        raise RuntimeError("incomplete XYZ")
    return [row[0] for row in rows], np.asarray([[float(v) for v in row[1:4]] for row in rows])


def dihedral(coordinates: np.ndarray, atoms: tuple[int, int, int, int]) -> float:
    p0, p1, p2, p3 = (coordinates[index] for index in atoms)
    b0 = -(p1 - p0)
    b1 = p2 - p1
    b2 = p3 - p2
    b1 /= np.linalg.norm(b1)
    v = b0 - np.dot(b0, b1) * b1
    w = b2 - np.dot(b2, b1) * b1
    return float(math.atan2(np.dot(np.cross(b1, v), w), np.dot(v, w)))


def raw_surface_records() -> dict[str, list[dict]]:
    surfaces: dict[str, list[dict]] = {}
    replay = json.loads((RAW / "CHI_OFFLINE_REPLAY_RESULT.json").read_text())
    desired = {int(angle): float(energy) for angle, energy in replay["all_paid_result_best_energies_hartree"].items()}
    chi_path = RAW / AXES["CHI"]["archive"]
    with zipfile.ZipFile(chi_path) as archive:
        candidates = []
        for name in archive.namelist():
            if name.endswith("/WAVEFRONT_RECORD.json"):
                record = json.loads(archive.read(name))
                target = int(record["target_degrees"])
                if target in desired and abs(float(record["energy_hartree"]) - desired[target]) <= 1e-12:
                    candidate = PurePosixPath(name).parent
                    candidates.append({
                        "axis": "CHI", "angle_degrees": target, "qm_energy_hartree": desired[target],
                        "candidate_id": candidate.name, "archive_member": str(candidate / "final.xyz"),
                        "xyz": archive.read(str(candidate / "final.xyz")),
                    })
        by_angle = defaultdict(list)
        for candidate in candidates:
            by_angle[candidate["angle_degrees"]].append(candidate)
        if set(by_angle) != set(desired) or any(len(values) != 1 for values in by_angle.values()):
            raise RuntimeError("canonical CHI geometry reconstruction is not unique")
        surfaces["CHI"] = [by_angle[angle][0] for angle in sorted(by_angle)]
    for axis in ("PHI", "PSI"):
        path = RAW / AXES[axis]["archive"]
        with tarfile.open(path, "r:gz") as archive:
            state_name = f"results/{axis}/WAVEFRONT_STATE.json"
            state = json.load(archive.extractfile(state_name))
            if state["torsion"] != axis or state["queue"]:
                raise RuntimeError(f"{axis} state is not complete")
            records = []
            for angle_text, cell in sorted(state["cells"].items(), key=lambda item: int(item[0])):
                identifier = cell["task_id"]
                member = f"results/{axis}/candidates/{identifier}/final.xyz"
                records.append({
                    "axis": axis, "angle_degrees": int(angle_text),
                    "qm_energy_hartree": float(cell["energy_hartree"]), "candidate_id": identifier,
                    "archive_member": member, "xyz": archive.extractfile(member).read(),
                })
            surfaces[axis] = records
    return surfaces


def parameter_source(type_index: int) -> str:
    # The accepted prmtop records numeric parameters but not source-line identity.
    # This mapping is established from the accepted baseline's upstream frcmod.
    return {
        1: "original parmchk2 completion; c6-c6-c6-hc analogue",
        2: "original parmchk2 completion; X-c2-c3-X analogue",
        7: "original parmchk2 completion; c3-c2-c3-hc analogue",
        12: "original parmchk2 completion; X-c3-c3-X analogue",
        17: "original parmchk2 completion; X-c3-sh-X analogue",
        30: "original parmchk2 completion; h1-c3-sh-hs analogue",
    }.get(type_index, "accepted baseline prmtop; per-term upstream source unavailable")


def topology_mapping(topology: pmd.Structure) -> tuple[list[dict], dict, dict]:
    rows = []
    axis_types: dict[str, set[int]] = {}
    all_instances_by_type: dict[int, list[tuple[int, int, int, int]]] = defaultdict(list)
    for term in topology.dihedrals:
        if term.improper:
            continue
        index = topology.dihedral_types.index(term.type)
        ids = (term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx)
        all_instances_by_type[index].append(ids)
    for axis, definition in AXES.items():
        center = {definition["atoms"][1], definition["atoms"][2]}
        axis_types[axis] = set()
        for term in topology.dihedrals:
            ids = (term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx)
            if term.improper or {ids[1], ids[2]} != center:
                continue
            index = topology.dihedral_types.index(term.type)
            axis_types[axis].add(index)
            rows.append({
                "axis": axis, "parameter_id": f"PRMTOP_DIHEDRAL_TYPE_{index}", "type_index": index,
                "instance_atoms_zero_based": "-".join(map(str, ids)),
                "instance_atom_names": "-".join(atom.name for atom in (term.atom1, term.atom2, term.atom3, term.atom4)),
                "instance_atom_types": "-".join(atom.type for atom in (term.atom1, term.atom2, term.atom3, term.atom4)),
                "periodicity": int(term.type.per), "phase_degrees": float(term.type.phase),
                "force_constant_kcal_mol": float(term.type.phi_k), "scee": float(term.type.scee),
                "scnb": float(term.type.scnb), "parameter_source": parameter_source(index),
                "molecular_instance_count": len(all_instances_by_type[index]),
            })
    coupling = {}
    for index in sorted(set().union(*axis_types.values())):
        coupling[f"PRMTOP_DIHEDRAL_TYPE_{index}"] = {
            axis: index in axis_types[axis] for axis in AXES
        }
    shared = {key: value for key, value in coupling.items() if sum(value.values()) > 1}
    return rows, coupling, shared


ENERGY_FIELDS = ("bond", "angle", "dihedral", "imp", "elec", "vdw", "elec_14", "vdw_14", "tot")


def evaluate_baseline(surfaces: dict[str, list[dict]], topology: pmd.Structure) -> list[dict]:
    flattened = [record for axis in AXES for record in surfaces[axis]]
    elements, first = read_xyz_bytes(flattened[0]["xyz"])
    expected = [atom.atomic_number for atom in topology.atoms]
    atomic_numbers = {"H": 1, "C": 6, "O": 8, "S": 16}
    if [atomic_numbers[element] for element in elements] != expected:
        raise RuntimeError("baseline topology and QM atom ordering differ")
    options = sander.gas_input()
    options.cut = 999.0
    sander.setup(str(BASELINE), first, None, options)
    output = []
    try:
        for record in flattened:
            current_elements, coordinates = read_xyz_bytes(record["xyz"])
            if current_elements != elements:
                raise RuntimeError("surface atom order changed")
            sander.set_positions(coordinates)
            energy, _ = sander.energy_forces(as_numpy=True)
            row = {key: record[key] for key in ("axis", "angle_degrees", "qm_energy_hartree", "candidate_id", "archive_member")}
            row["qm_energy_kcal_mol_absolute"] = record["qm_energy_hartree"] * HARTREE_TO_KCAL_MOL
            for field in ENERGY_FIELDS:
                row[f"mm_{field}_kcal_mol_absolute"] = float(getattr(energy, field))
            row["coordinates"] = coordinates
            output.append(row)
    finally:
        sander.cleanup()
    for axis in AXES:
        group = [row for row in output if row["axis"] == axis]
        qm_min = min(row["qm_energy_kcal_mol_absolute"] for row in group)
        mm_min = min(row["mm_tot_kcal_mol_absolute"] for row in group)
        component_min = {field: min(row[f"mm_{field}_kcal_mol_absolute"] for row in group) for field in ENERGY_FIELDS[:-1]}
        for row in group:
            row["qm_relative_kcal_mol"] = row["qm_energy_kcal_mol_absolute"] - qm_min
            row["mm_relative_kcal_mol"] = row["mm_tot_kcal_mol_absolute"] - mm_min
            row["qm_minus_mm_relative_kcal_mol"] = row["qm_relative_kcal_mol"] - row["mm_relative_kcal_mol"]
            for field in ENERGY_FIELDS[:-1]:
                row[f"mm_{field}_relative_to_component_min_kcal_mol"] = row[f"mm_{field}_kcal_mol_absolute"] - component_min[field]
    return output


def torsion_design(topology: pmd.Structure, baseline_rows: list[dict], mapping_rows: list[dict]) -> tuple[list[dict], dict]:
    parameter_indices = sorted({int(row["type_index"]) for row in mapping_rows})
    instances = defaultdict(list)
    # Production-safe directions clone a parameter type and assign it only to
    # the mapped physical instances about the selected scanned central bond.
    # They must not mutate all unrelated instances that share a generic type.
    for row in mapping_rows:
        index = int(row["type_index"])
        atoms = tuple(int(value) for value in row["instance_atoms_zero_based"].split("-"))
        if atoms not in instances[index]:
            instances[index].append(atoms)
    design_rows = []
    columns = [f"PRMTOP_DIHEDRAL_TYPE_{index}_AMPLITUDE" for index in parameter_indices]
    for row in baseline_rows:
        output = {"axis": row["axis"], "angle_degrees": row["angle_degrees"],
                  "target_residual_kcal_mol": row["qm_minus_mm_relative_kcal_mol"]}
        for index, column in zip(parameter_indices, columns):
            parameter = topology.dihedral_types[index]
            phase = math.radians(float(parameter.phase))
            output[column] = sum(1.0 + math.cos(float(parameter.per) * dihedral(row["coordinates"], atoms) - phase)
                                 for atoms in instances[index])
        design_rows.append(output)
    matrix = np.asarray([[row[column] for column in columns] for row in design_rows])
    # Relative-profile offsets are removed independently per surface.
    for axis in AXES:
        selection = np.asarray([i for i, row in enumerate(design_rows) if row["axis"] == axis])
        matrix[selection] -= matrix[selection].mean(axis=0)
    active = np.linalg.norm(matrix, axis=0) > 1e-12
    active_matrix = matrix[:, active]
    singular = np.linalg.svd(active_matrix, compute_uv=False)
    rank = int(np.linalg.matrix_rank(active_matrix))
    surface_counts = {axis: sum(row["axis"] == axis for row in design_rows) for axis in AXES}
    surface_weights = np.asarray([1.0 / math.sqrt(surface_counts[row["axis"]]) for row in design_rows])
    equal_surface_matrix = active_matrix * surface_weights[:, None]
    equal_surface_singular = np.linalg.svd(equal_surface_matrix, compute_uv=False)
    equal_surface_rank = int(np.linalg.matrix_rank(equal_surface_matrix))
    normalized = active_matrix / np.linalg.norm(active_matrix, axis=0, keepdims=True)
    correlation = normalized.T @ normalized
    audit = {
        "schema": "tsl-rsh-torsion-prefit-identifiability-v1",
        "diagnostic_only": True,
        "model": "instance-local clones of existing AMBER dihedral-type amplitude directions",
        "columns": columns, "active_columns": [column for column, keep in zip(columns, active) if keep],
        "observation_count": int(matrix.shape[0]), "parameter_count": int(matrix.shape[1]),
        "active_parameter_count": int(active.sum()), "numerical_rank": rank,
        "full_column_rank": rank == int(active.sum()), "singular_values": singular.tolist(),
        "condition_number": float(singular[0] / singular[-1]) if singular.size and singular[-1] > 0 else None,
        "column_cosine_correlation_matrix": correlation.tolist(),
        "equal_point_weighting": {"policy": "each authoritative point has unit weight", "rank": rank,
                                  "singular_values": singular.tolist(),
                                  "condition_number": float(singular[0] / singular[-1])},
        "equal_surface_weighting": {"policy": "each surface squared-error sum divided by its authoritative point count",
                                    "primary_for_production": True, "rank": equal_surface_rank,
                                    "singular_values": equal_surface_singular.tolist(),
                                    "condition_number": float(equal_surface_singular[0] / equal_surface_singular[-1])},
        "interpretation": "This fixed-geometry matrix is a diagnostic local sensitivity only; the production MM-relaxed objective is nonlinear and must be reevaluated after every parameter update.",
        "serialization_requirement": "Each direction must be a cloned prmtop dihedral type assigned only to mapped physical instances; generic source types remain immutable.",
    }
    for output, values in zip(design_rows, matrix):
        output.update({column: float(value) for column, value in zip(columns, values)})
    return design_rows, audit


def write_csv(path: Path, rows: list[dict], fields: list[str]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fields, lineterminator="\n", extrasaction="ignore")
        writer.writeheader()
        writer.writerows(rows)
    os.replace(temporary, path)


def write_protocol() -> None:
    text = """# Preregistered torsional fitting first-pass protocol

## Boundary

Raw CHI/PHI/PSI archives are immutable. This first pass performs integrity
verification, topology mapping, frozen-geometry Sander diagnostics, design
construction, and identifiability analysis only. It performs no fit.

## Reference energies and units

For each independent surface, `Delta E(theta) = E(theta) - min(E)`.
The single conversion constant is `1 Eh = 627.509474 kcal/mol`. The
frozen-QM-geometry residual `Delta E_QM - Delta E_MM` is diagnostic and is not
the final production objective.

## Production MM-relaxed objective (preregistered, not executed here)

At every parameter iteration and every authoritative grid point:

1. start from that point's authoritative QM-optimized geometry;
2. apply an Amber dihedral restraint to the selected axis only, centered at the
   authoritative grid angle, with `rk2=rk3=500 kcal/mol/rad^2` and a `+/-0.5
   degree` flat region;
3. minimize every remaining coordinate with Sander, using the immutable baseline
   topology plus only the candidate proper-torsion changes;
4. use the converged physical MM energy after subtracting the explicit restraint
   energy; fail the objective if minimization, connectivity, chirality, atom
   order, or the `0.5 degree` realization gate fails;
5. independently reference each relaxed MM surface to its own minimum;
6. compare it with the correspondingly referenced QM surface.

This follows established relaxed-profile practice and prevents a torsion term
from being accepted merely because it compensates on frozen QM coordinates.
No unconstrained MD is included. Before execution, the exact Sander restraint
file and minimization controls require a numerical convention/serialization
test and a restart-determinism test.

## Weighting and model selection

Both equal-point and equal-surface objectives will be reported. Equal-surface
weighting is the preregistered primary policy so the 24/18/14 point counts do not
silently change scientific importance. All authoritative points remain reported.
An OpenFF/BespokeFit-style energy sensitivity analysis (flat through 1 kcal/mol,
attenuated through 10 kcal/mol, zero above 10 kcal/mol) will be reported only as
a secondary comparison; it cannot remove points from unweighted validation.

Candidate complexity proceeds from existing periodicities to chemically
justified additions. A more complex model must improve structured angular
holdout behavior, critical-point topology, and conditioning—not only training
RMSE. Periodicities, phases, 1-4 scalings, charges, LJ, bonds, angles, and
impropers remain frozen unless separately authorized.

## Predictive check

Model selection will use a preregistered structured angular holdout, not random
points: every fourth available ordered grid cell per surface, with four rotations
of the starting offset. The model is refit on the remaining cells for each
rotation, and all rotations are reported. Missing PHI/PSI cells remain missing.

## Acceptance status

Literature establishes relaxed-profile objectives and a 1 kcal/mol energy scale,
but does not supply a universal publication acceptance gate for this molecule.
The proposed numerical gates are recorded separately for review and are not yet
locked. No final optimization may begin until they are explicitly approved.
"""
    atomic_text(HERE / "00_PROTOCOL/TORSION_FIT_FIRST_PASS_PROTOCOL.md", text)


def canonical_digest(value: object) -> str:
    return hashlib.sha256(json.dumps(value, sort_keys=True, separators=(",", ":")).encode()).hexdigest()


def frozen_non_torsional(topology: pmd.Structure) -> dict:
    charges = [float(atom.charge) for atom in topology.atoms]
    lj = [{"index": atom.idx, "type": atom.type, "epsilon": float(atom.epsilon), "rmin": float(atom.rmin)}
          for atom in topology.atoms]
    bonds = [{"atoms": [bond.atom1.idx, bond.atom2.idx], "k": float(bond.type.k), "req": float(bond.type.req)}
             for bond in topology.bonds]
    angles = [{"atoms": [angle.atom1.idx, angle.atom2.idx, angle.atom3.idx], "k": float(angle.type.k),
               "theteq_degrees": float(angle.type.theteq)} for angle in topology.angles]
    impropers = [{"atoms": [term.atom1.idx, term.atom2.idx, term.atom3.idx, term.atom4.idx],
                  "phi_k": float(term.type.phi_k), "periodicity": float(term.type.per),
                  "phase_degrees": float(term.type.phase)} for term in topology.dihedrals if term.improper]
    scaling = sorted({(float(term.type.scee), float(term.type.scnb)) for term in topology.dihedrals})
    return {
        "schema": "tsl-rsh-frozen-non-torsional-parameters-v1", "status": "FROZEN",
        "baseline_topology_sha256": sha256_path(BASELINE),
        "components": {
            "atomic_charges": {"count": len(charges), "canonical_sha256": canonical_digest(charges)},
            "bond_parameters": {"count": len(bonds), "canonical_sha256": canonical_digest(bonds)},
            "angle_parameters": {"count": len(angles), "canonical_sha256": canonical_digest(angles)},
            "lennard_jones_parameters": {"count": len(lj), "canonical_sha256": canonical_digest(lj)},
            "improper_torsions": {"count": len(impropers), "canonical_sha256": canonical_digest(impropers)},
            "one_four_scaling": {"values_scee_scnb": scaling, "canonical_sha256": canonical_digest(scaling)},
        },
        "charge_provenance": json.loads(BASELINE_IDENTITY.read_text())["charge_mol2"],
        "force_field_identity": json.loads(BASELINE_IDENTITY.read_text())["identity"],
        "environmental_force_fields": {
            "SAM": "NOT_PRESENT_OR_EVALUATED_IN_ISOLATED_LIGAND_TORSION_FIT",
            "protein": "NOT_PRESENT_OR_EVALUATED_IN_ISOLATED_LIGAND_TORSION_FIT",
            "water": "NOT_PRESENT_OR_EVALUATED_IN_ISOLATED_LIGAND_TORSION_FIT",
            "ions": "NOT_PRESENT_OR_EVALUATED_IN_ISOLATED_LIGAND_TORSION_FIT",
            "requirement": "Downstream system identities must be frozen separately before installation/MD; they cannot affect this gas-phase fit objective.",
        },
        "mutation_policy": "No component in this record may change during torsion fitting; only instance-local cloned proper-torsion types may change after authorization.",
    }


def main() -> None:
    for directory in OUTPUT_DIRS:
        (HERE / directory).mkdir(parents=True, exist_ok=True)
    subprocess.run([sys.executable, str(RAW / "audit_torsion_publication_record.py"), "--verify-only"], check=True,
                   stdout=subprocess.DEVNULL)
    if sha256_path(BASELINE) != "2f4882aed1ea80e7b582a7b2cafa3dfd58ce4d918e5c9312186bcf3e28c88097":
        raise RuntimeError("baseline topology checksum mismatch")
    topology = pmd.load_file(str(BASELINE))
    surfaces = raw_surface_records()
    mapping_rows, coupling, shared = topology_mapping(topology)
    baseline_rows = evaluate_baseline(surfaces, topology)
    design_rows, identifiability = torsion_design(topology, baseline_rows, mapping_rows)

    write_protocol()
    input_manifest = {
        "schema": "tsl-rsh-torsion-fit-input-manifest-v1", "raw_qm_artifacts_modified": False,
        "publication_manifest": {"path": str(RAW / "TORSION_PUBLICATION_REPRODUCIBILITY_MANIFEST.json"),
                                 "sha256": sha256_path(RAW / "TORSION_PUBLICATION_REPRODUCIBILITY_MANIFEST.json")},
        "raw_archives": {axis: {"path": str(RAW / definition["archive"]),
                                "sha256": sha256_path(RAW / definition["archive"]),
                                "authoritative_points": len(surfaces[axis])}
                         for axis, definition in AXES.items()},
        "baseline_force_field": {"path": str(BASELINE), "sha256": sha256_path(BASELINE),
                                 "identity_path": str(BASELINE_IDENTITY),
                                 "identity_sha256": sha256_path(BASELINE_IDENTITY),
                                 "identity": json.loads(BASELINE_IDENTITY.read_text())["identity"]},
        "hartree_to_kcal_mol": HARTREE_TO_KCAL_MOL,
    }
    atomic_json(HERE / "01_INPUT_MANIFEST/PUBLICATION_INPUT_MANIFEST.json", input_manifest)
    atomic_json(HERE / "02_TOPOLOGY_MAPPING/TORSION_PARAMETER_COUPLING_MATRIX.json",
                {"coupling_matrix": coupling, "shared_across_scanned_axes": shared})
    map_fields = list(mapping_rows[0])
    write_csv(HERE / "02_TOPOLOGY_MAPPING/TORSION_TOPOLOGY_MAPPING.csv", mapping_rows, map_fields)
    atoms = []
    for index, atom in enumerate(topology.atoms):
        atoms.append({"atom_index_zero_based": index, "atom_index_one_based": index + 1, "atom_name": atom.name,
                      "atom_type": atom.type, "atomic_number": atom.atomic_number, "charge_e": atom.charge,
                      "mass_amu": atom.mass, "bonded_neighbors_zero_based": ";".join(map(str, sorted(a.idx for a in atom.bond_partners)))})
    write_csv(HERE / "02_TOPOLOGY_MAPPING/ATOM_AND_CONNECTIVITY_MAP.csv", atoms, list(atoms[0]))
    atomic_json(HERE / "02_TOPOLOGY_MAPPING/FROZEN_NON_TORSIONAL_PARAMETERS.json", frozen_non_torsional(topology))
    sharing = []
    for row in mapping_rows:
        if not any(item["parameter_id"] == row["parameter_id"] for item in sharing):
            local_count = sum(item["parameter_id"] == row["parameter_id"] for item in mapping_rows)
            sharing.append({"parameter_id": row["parameter_id"], "molecular_instance_count": row["molecular_instance_count"],
                            "mapped_scan_instance_count": local_count,
                            "unrelated_instance_count": int(row["molecular_instance_count"]) - local_count,
                            "generic_type_safe_to_mutate_globally": int(row["molecular_instance_count"]) == local_count,
                            "required_production_action": "clone type and assign mapped instances only" if int(row["molecular_instance_count"]) != local_count else "mapped type may be cloned for provenance; no unrelated instance is affected"})
    write_csv(HERE / "02_TOPOLOGY_MAPPING/PARAMETER_SHARING_AUDIT.csv", sharing, list(sharing[0]))

    baseline_fields = [key for key in baseline_rows[0] if key != "coordinates"]
    write_csv(HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_BASELINE.csv", baseline_rows, baseline_fields)
    metrics = {}
    for axis in AXES:
        residual = np.asarray([row["qm_minus_mm_relative_kcal_mol"] for row in baseline_rows if row["axis"] == axis])
        metrics[axis] = {"rmse_kcal_mol": float(np.sqrt(np.mean(residual ** 2))),
                         "mae_kcal_mol": float(np.mean(np.abs(residual))),
                         "max_abs_kcal_mol": float(np.max(np.abs(residual))), "point_count": int(residual.size)}
    atomic_json(HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_METRICS.json", metrics)
    baseline_report = "# Pre-fit frozen-geometry MM diagnostic\n\n"
    baseline_report += "These values are a decomposition diagnostic, not the production fitting objective. " \
                       "The production objective is the preregistered MM-relaxed profile.\n\n"
    for axis, values in metrics.items():
        baseline_report += f"- {axis}: RMSE {values['rmse_kcal_mol']:.6f}, MAE {values['mae_kcal_mol']:.6f}, max {values['max_abs_kcal_mol']:.6f} kcal/mol ({values['point_count']} points).\n"
    atomic_text(HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_BASELINE.md", baseline_report)
    write_csv(HERE / "04_FIT/FIT_DESIGN_MATRIX.csv", design_rows, list(design_rows[0]))
    atomic_json(HERE / "04_FIT/IDENTIFIABILITY_AUDIT.json", identifiability)
    fit_candidates = {
        "schema": "tsl-rsh-torsion-fit-model-candidates-v1", "fit_run": False,
        "candidates": [
            {"id": "C0", "definition": "immutable accepted baseline; no fitted coefficient", "purpose": "prefit reference"},
            {"id": "C1", "definition": "instance-local cloned amplitude corrections to existing mapped periodicities; generic types, phases, periodicities and 1-4 scaling fixed", "priority": 1},
            {"id": "C2", "definition": "only if C1 fails: add missing n=1..3 amplitudes to an instance-local Amber-serializable torsion type", "priority": 2,
             "admission": "requires structured-holdout improvement, full rank, acceptable conditioning, and chemical rationale"},
        ],
        "prohibited": ["independent per-surface fit when a parameter is shared", "free 1-4 scaling", "charge/LJ changes",
                       "arbitrary high periodicity", "selection from training RMSE alone"],
    }
    atomic_json(HERE / "04_FIT/FIT_MODEL_CANDIDATES.json", fit_candidates)
    gates = {
        "schema": "tsl-rsh-torsion-proposed-acceptance-gates-v1", "locked": False,
        "status": "PROPOSED_FOR_REVIEW_NOT_APPLIED",
        "proposal": {"per_surface_relaxed_profile_rmse_kcal_mol_max": 1.0,
                     "per_surface_relaxed_profile_mae_kcal_mol_max": 0.75,
                     "per_surface_max_abs_error_kcal_mol_max": 2.0,
                     "minimum_angle_error_degrees_max": 15.0,
                     "major_barrier_height_error_kcal_mol_max": 1.0,
                     "major_barrier_location_error_degrees_max": 15.0,
                     "periodic_closure_error_kcal_mol_max": 0.1,
                     "structured_holdout_must_improve_over_baseline": True,
                     "no_new_wrong_minimum_or_barrier_topology": True,
                     "amber_serialization_energy_force_equivalence_required": True},
        "basis": [
            "OpenFF BespokeFit uses relaxed MM torsion profiles and a 1.0 kcal/mol objective scale; published profile RMSE near 0.35 kcal/mol is a performance reference, not a universal gate.",
            "OpenFF Sage uses independently referenced QM/MM relative profiles and constrained MM relaxation.",
            "AFFDO uses a 500 kcal/mol/rad^2 target-dihedral restraint with a +/-0.5 degree band in constrained MM scans.",
            "One 15-degree grid interval is the natural location-resolution unit of this campaign.",
        ],
        "review_requirement": "Numerical limits beyond the literature's objective scale are a conservative project proposal and require explicit approval before fitting.",
    }
    atomic_json(HERE / "00_PROTOCOL/PROPOSED_ACCEPTANCE_GATES.json", gates)
    summary = {
        "publication_inputs_verified": True, "raw_qm_artifacts_modified": False,
        "baseline_force_field_identified": True, "baseline_force_field_sha256": sha256_path(BASELINE),
        "shared_torsion_parameters": sorted(shared), "prefit_metrics": metrics,
        "identifiability": identifiability, "identifiability_classification": "CONCERN",
        "acceptance_gates_locked": False,
        "new_qm_required": False, "ready_to_fit": False,
        "blockers": ["explicit review/lock of proposed acceptance gates",
                     "production Sander MM-relaxation restraint/minimization convention and restart tests",
                     "final choice between C1 and conditional C2 model-selection protocol",
                     "instance-local torsion-type cloning/read-back equivalence gate for generic shared types"],
    }
    atomic_json(HERE / "08_PUBLICATION/FIRST_PASS_DECISION.json", summary)
    generated = sorted(path for path in HERE.rglob("*")
                       if path.is_file() and path.name not in {"SHA256SUMS"}
                       and "__pycache__" not in path.parts and not path.name.endswith((".pyc", ".tmp")))
    manifest = "".join(f"{sha256_path(path)}  {path.relative_to(HERE)}\n" for path in generated)
    atomic_text(HERE / "SHA256SUMS", manifest)
    print(json.dumps({"status": "PASS", "metrics": metrics, "identifiability": identifiability,
                      "shared_parameters": sorted(shared), "fit_run": False}, indent=2))


if __name__ == "__main__":
    main()
