#!/usr/bin/env python3
"""Prepare the frozen training-only additive classical fit; never reads held-out labels."""

from __future__ import annotations

import csv
import json
import platform
import subprocess
from pathlib import Path

import numpy as np
import parmed as pmd
import sander

from classical_common import (
    BASELINE_IDENTITY, EVIDENCE, LOCAL_ATOMS, PARAMETER_NAMES, PARAMETER_UNITS, RESULTS,
    ROOT, TOPOLOGY, energy_features, force_features, geometry, load_qm, sha256, split,
)

HERE = Path(__file__).resolve().parent
DESIGN = HERE / "TRAINING_FIT_REQUEST.json"
PARAMETERS = HERE / "FITTABLE_PARAMETER_MANIFEST.csv"
SPEC = HERE / "ADDITIVE_CLASSICAL_MODEL_SPEC.json"
BASELINE = HERE / "BASELINE_PREDICTIONS.json"
AUDIT_COMMIT = "c8723de6a8d97836fd1daf1dce3bd28d231a8d3a"


def baseline_predictions(ids: list[str]) -> dict[str, dict[str, object]]:
    topology = pmd.load_file(str(TOPOLOGY))
    first_symbols, first_coordinates = geometry(RESULTS / ids[0] / "geometry.xyz")
    if [atom.atomic_number for atom in topology.atoms] != [{"H": 1, "C": 6, "O": 8, "S": 16}[symbol] for symbol in first_symbols]:
        raise ValueError("Amber topology atom order does not match GPU evidence")
    options = sander.gas_input()
    options.cut = 999.0
    sander.setup(str(TOPOLOGY), first_coordinates, None, options)
    output = {}
    try:
        for artifact_id in ids:
            symbols, coordinates = geometry(RESULTS / artifact_id / "geometry.xyz")
            if symbols != first_symbols:
                raise ValueError(f"atom-order mismatch: {artifact_id}")
            sander.set_positions(coordinates)
            energy, force = sander.energy_forces(as_numpy=True)
            output[artifact_id] = {
                "energy_kcal_mol": float(energy.tot),
                "force_kcal_mol_angstrom": np.asarray(force).reshape(56, 3).tolist(),
                "terms_kcal_mol": {name: float(getattr(energy, name)) for name in ("bond", "angle", "dihedral", "elec", "elec_14", "vdw", "vdw_14", "imp")},
            }
    finally:
        sander.cleanup()
    return output


def parameter_inventory() -> list[dict[str, object]]:
    topology = pmd.load_file(str(TOPOLOGY))
    rows = []
    for atom in topology.atoms:
        rows.append({"PARAMETER_ID": f"ATOM_{atom.idx + 1}_TYPE", "VALUE": atom.type, "UNIT": "Amber atom type", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "baseline.parm7", "FROZEN_OR_FITTABLE": "FROZEN"})
        rows.append({"PARAMETER_ID": f"ATOM_{atom.idx + 1}_CHARGE", "VALUE": f"{atom.charge:.16g}", "UNIT": "elementary_charge", "SOURCE": str(BASELINE_IDENTITY.relative_to(ROOT)), "PROVENANCE": "accepted AmberTools26 three-minimum RESP", "FROZEN_OR_FITTABLE": "FROZEN"})
        rows.append({"PARAMETER_ID": f"ATOM_{atom.idx + 1}_LJ_RMIN", "VALUE": f"{atom.rmin:.16g}", "UNIT": "angstrom", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2 topology", "FROZEN_OR_FITTABLE": "FROZEN"})
        rows.append({"PARAMETER_ID": f"ATOM_{atom.idx + 1}_LJ_EPSILON", "VALUE": f"{atom.epsilon:.16g}", "UNIT": "kcal/mol", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2 topology", "FROZEN_OR_FITTABLE": "FROZEN"})
    for bond in topology.bonds:
        rows.append({"PARAMETER_ID": f"BOND_{bond.atom1.idx + 1}_{bond.atom2.idx + 1}_K", "VALUE": f"{bond.type.k:.16g}", "UNIT": "kcal/mol/angstrom^2", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2/parmchk2 topology", "FROZEN_OR_FITTABLE": "FROZEN_BASELINE"})
        rows.append({"PARAMETER_ID": f"BOND_{bond.atom1.idx + 1}_{bond.atom2.idx + 1}_REQ", "VALUE": f"{bond.type.req:.16g}", "UNIT": "angstrom", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2/parmchk2 topology", "FROZEN_OR_FITTABLE": "FROZEN_BASELINE"})
    for angle in topology.angles:
        identity = f"{angle.atom1.idx + 1}_{angle.atom2.idx + 1}_{angle.atom3.idx + 1}"
        rows.append({"PARAMETER_ID": f"ANGLE_{identity}_K", "VALUE": f"{angle.type.k:.16g}", "UNIT": "kcal/mol/radian^2", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2/parmchk2 topology", "FROZEN_OR_FITTABLE": "FROZEN_BASELINE"})
        rows.append({"PARAMETER_ID": f"ANGLE_{identity}_THETAEQ", "VALUE": f"{angle.type.theteq:.16g}", "UNIT": "degree", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2/parmchk2 topology", "FROZEN_OR_FITTABLE": "FROZEN_BASELINE"})
    for index, dihedral in enumerate(topology.dihedrals):
        kind = "IMPROPER" if dihedral.improper else "PROPER"
        identity = "_".join(str(atom.idx + 1) for atom in (dihedral.atom1, dihedral.atom2, dihedral.atom3, dihedral.atom4))
        rows.append({"PARAMETER_ID": f"{kind}_{index + 1}_{identity}", "VALUE": f"pk={dihedral.type.phi_k:.16g};phase={dihedral.type.phase:.16g};periodicity={dihedral.type.per:.16g};scee={dihedral.type.scee:.16g};scnb={dihedral.type.scnb:.16g}", "UNIT": "kcal/mol;degree;dimensionless", "SOURCE": str(TOPOLOGY.relative_to(ROOT)), "PROVENANCE": "GAFF2/parmchk2 topology including 1-4 scaling", "FROZEN_OR_FITTABLE": "FROZEN_BASELINE"})
    for name, unit in zip(PARAMETER_NAMES, PARAMETER_UNITS):
        rows.append({"PARAMETER_ID": name, "VALUE": "TO_BE_FITTED_FROM_39_TRAIN_IDS", "UNIT": unit, "SOURCE": "ADDITIVE_CLASSICAL_MODEL_SPEC.json", "PROVENANCE": "preregistered additive correction; validation labels inaccessible", "FROZEN_OR_FITTABLE": "NUISANCE_FITTABLE" if name == "ENERGY_REFERENCE_OFFSET" else "FITTABLE"})
    return rows


def main() -> None:
    frozen_split = split()
    train_ids = list(frozen_split["TRAIN_IDS"])
    validation_ids = list(frozen_split["VALIDATION_IDS"])
    stress_ids = list(frozen_split["STRESS_TEST_IDS"])
    if (len(train_ids), len(validation_ids), len(stress_ids)) != (39, 11, 10):
        raise ValueError("frozen split membership changed")
    # Baseline MM evaluation is label-free; computing it for all IDs does not open held-out QM labels.
    baseline = baseline_predictions(train_ids + validation_ids + stress_ids)
    BASELINE.write_text(json.dumps({"topology_sha256": sha256(TOPOLOGY), "predictions": baseline}, indent=2, sort_keys=True) + "\n")

    energy_rows, force_rows, energy_targets, force_targets = [], [], [], []
    for artifact_id in train_ids:
        qm_energy, qm_force, coordinates = load_qm(artifact_id)
        base = baseline[artifact_id]
        energy_rows.append(energy_features(coordinates))
        force_rows.append(force_features(coordinates).reshape(-1, len(PARAMETER_NAMES)))
        energy_targets.append(qm_energy - float(base["energy_kcal_mol"]))
        force_targets.append((qm_force - np.asarray(base["force_kcal_mol_angstrom"])).reshape(-1))
    energy_rows = np.asarray(energy_rows)
    force_rows = np.vstack(force_rows)
    energy_targets = np.asarray(energy_targets)
    force_targets = np.concatenate(force_targets)
    energy_scale = float(np.sqrt(np.mean((energy_targets - np.mean(energy_targets)) ** 2)))
    force_scale = float(np.sqrt(np.mean(force_targets ** 2)))
    energy_weight = 0.5 / len(energy_targets) / energy_scale ** 2
    force_weight = 0.5 / len(force_targets) / force_scale ** 2
    design = np.vstack((energy_rows, force_rows))
    targets = np.concatenate((energy_targets, force_targets))
    weights = np.concatenate((np.full(len(energy_targets), energy_weight), np.full(len(force_targets), force_weight)))
    weighted = design * np.sqrt(weights[:, None])
    normalized = weighted / np.linalg.norm(weighted, axis=0)
    singular = np.linalg.svd(normalized, compute_uv=False)
    rank = int(np.linalg.matrix_rank(normalized))
    if rank != len(PARAMETER_NAMES):
        raise ValueError(f"preregistered additive design is not identifiable: rank {rank}/{len(PARAMETER_NAMES)}")

    source_checksums = {
        "trusted_manifest": sha256(EVIDENCE / "TRUSTED_TSL_RSH_EVIDENCE_MANIFEST.csv"),
        "frozen_split": sha256(EVIDENCE / "FROZEN_TRAIN_VALIDATION_SPLIT.json"),
        "topology": sha256(TOPOLOGY),
        "baseline_predictions": sha256(BASELINE),
    }
    request = {
        "modelFamily": "AMBER_ADDITIVE_INTRAMOLECULAR_LINEAR_CORRECTION",
        "modelVersion": "TSL_RSH_GPU60_REPRESENTABILITY_V1",
        "basisDefinition": "Frozen Amber/GAFF2 nonbonded+bonded baseline plus local S-C/S-H harmonic corrections, three sulfur-local angle harmonic corrections, and independent n=1..3 proper Fourier torsions; no cross terms",
        "parameterNames": PARAMETER_NAMES,
        "parameterUnits": PARAMETER_UNITS,
        "designMatrix": design.tolist(),
        "targets": targets.tolist(),
        "rowWeights": weights.tolist(),
        "frozenParameters": {"formal_charge": 0.0, "multiplicity": 1.0, "amber_scee": 1.2, "amber_scnb": 2.0},
        "objectiveDefinition": "0.5*mean((energy residual/training energy scale)^2)+0.5*mean((all Cartesian force-component residual/training force scale)^2); exploratory weights frozen before fitting",
        "objectiveWeights": {"energy_block": 0.5, "force_block": 0.5, "energy_row_weight": energy_weight, "force_row_weight": force_weight},
        "trainingIds": train_ids,
        "validationIds": validation_ids,
        "normalizationState": {"energy_scale_kcal_mol": repr(energy_scale), "force_scale_kcal_mol_angstrom": repr(force_scale), "source": "training-only baseline residual RMS; energy centered only for scale"},
        "optimizerConfiguration": {"algorithm": "twice-reorthogonalized modified Gram-Schmidt QR", "regularization": "NONE", "rank": str(rank), "scaled_condition_number": repr(float(singular[0] / singular[-1]))},
        "seed": 0,
        "sourceDatasetChecksums": source_checksums,
        "codeCommitSha": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip(),
    }
    DESIGN.write_text(json.dumps(request, indent=2, sort_keys=True) + "\n")
    inventory = parameter_inventory()
    with PARAMETERS.open("w", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=["PARAMETER_ID", "VALUE", "UNIT", "SOURCE", "PROVENANCE", "FROZEN_OR_FITTABLE"], lineterminator="\n")
        writer.writeheader(); writer.writerows(inventory)
    spec = {
        "schema": "tsl-rsh-additive-classical-model-spec-v1",
        "scientific_question": "Can a conventional additive classical intramolecular model represent the trusted isolated-TSL conformational QM surface?",
        "domain_language_correction": {"historical_term": "EXTENDED_BOUND_DOMAIN", "current_interpretation": "CURRENT_CONFORMATIONAL_DEVELOPMENT_DOMAIN", "warning": "Dataset-derived high energy envelopes are not experimentally established thermally populated bound-state limits.", "memberships_changed": False},
        "split_counts": {"train": 39, "validation": 11, "stress_test": 10},
        "validation_labels_read_during_preparation": False,
        "model": request["basisDefinition"],
        "charges": "FROZEN", "lj": "FROZEN", "impropers": "FROZEN", "all_nonlisted_bonded_parameters": "FROZEN",
        "fittable_parameter_count_including_energy_reference": len(PARAMETER_NAMES),
        "physical_fittable_parameter_count": len(PARAMETER_NAMES) - 1,
        "objective": request["objectiveDefinition"],
        "objective_weights": request["objectiveWeights"],
        "identifiability": {"rank": rank, "columns": len(PARAMETER_NAMES), "scaled_condition_number": float(singular[0] / singular[-1]), "training_only": True},
        "thresholds_changed": False,
    }
    SPEC.write_text(json.dumps(spec, indent=2, sort_keys=True) + "\n")
    print(json.dumps({"train": len(train_ids), "parameters": len(PARAMETER_NAMES), "rank": rank, "condition": float(singular[0] / singular[-1]), "energy_scale": energy_scale, "force_scale": force_scale, "validation_labels_read": False, "software": {"python": platform.python_version(), "parmed": pmd.__version__}}, indent=2))


if __name__ == "__main__":
    main()
