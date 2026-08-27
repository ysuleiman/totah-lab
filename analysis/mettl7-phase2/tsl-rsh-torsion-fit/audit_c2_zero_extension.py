#!/usr/bin/env python3
"""Prove that zero-amplitude C2 continuations reproduce the sealed C1 Hamiltonian."""

from __future__ import annotations

import json
import sys
import tempfile
from pathlib import Path

import numpy as np
import parmed as pmd

import close_publication_gates as gates
import run_c1_fit as c1
import run_c2_fit as c2
import run_first_pass as first


OUTPUT = c2.C2 / "C2_ZERO_EXTENSION_IDENTITY.json"
FROZEN_ENERGY_IDENTITY_TOL_KCAL_MOL = 1e-9
RELAXED_PROFILE_REPLAY_TOL_KCAL_MOL = 1e-4
COMPONENT_IDENTITY_TOL_KCAL_MOL = 1e-10


def classify(report: dict) -> dict:
    report.update({
        "frozen_energy_identity_tolerance_kcal_mol": FROZEN_ENERGY_IDENTITY_TOL_KCAL_MOL,
        "relaxed_profile_replay_tolerance_kcal_mol": RELAXED_PROFILE_REPLAY_TOL_KCAL_MOL,
        "component_identity_tolerance_kcal_mol": COMPONENT_IDENTITY_TOL_KCAL_MOL,
        "tolerance_basis": "Hamiltonian identity is exact at fixed coordinates; 1e-4 kcal/mol bounds deterministic endpoint replay noise under the locked Sander minimization contract.",
    })
    report["zero_extension_reproduces_c1"] = all(
        x["topology_1_4_invariant_pass"]
        and x["max_pointwise_frozen_energy_delta_kcal_mol"] <= FROZEN_ENERGY_IDENTITY_TOL_KCAL_MOL
        and x["max_pointwise_relaxed_profile_delta_kcal_mol"] <= RELAXED_PROFILE_REPLAY_TOL_KCAL_MOL
        and x["max_elec14_delta_kcal_mol"] <= COMPONENT_IDENTITY_TOL_KCAL_MOL
        and x["max_vdw14_delta_kcal_mol"] <= COMPONENT_IDENTITY_TOL_KCAL_MOL
        for x in report["candidates"].values()
    )
    return report


def main() -> None:
    if sys.argv[1:] == ["--classify-existing"]:
        report = classify(json.loads(OUTPUT.read_text()))
        first.atomic_json(OUTPUT, report)
        print(json.dumps(report, indent=2))
        if not report["zero_extension_reproduces_c1"]:
            raise SystemExit("zero-extension identity failed")
        return
    panel = json.loads(c2.PANEL_PATH.read_text())
    surfaces = first.raw_surface_records()
    baseline = pmd.load_file(str(c2.C1_TOPOLOGY))
    baseline_counts = c2.one_four_defining_counts(baseline)
    report = {"schema": "tsl-rsh-c2-zero-extension-identity-v1", "candidates": {}}
    global_energy = global_elec14 = global_vdw14 = global_profile = 0.0

    with tempfile.TemporaryDirectory(prefix="tsl-c2-zero-extension-") as temporary:
        root = Path(temporary)
        baseline_relaxed = {}
        for axis in first.AXES:
            results = [gates.minimize_point(
                baseline, record, root / "relaxed" / "C1" / axis / f"{int(record['angle_degrees']):+04d}",
                topology_path=c2.C1_TOPOLOGY,
            ) for record in surfaces[axis]]
            for row in c1.relative_rows(axis, results):
                baseline_relaxed[(row["axis"], row["angle_degrees"])] = row["mm_relative_kcal_mol"]
        for model in panel["candidates"]:
            specs = c2.new_term_specs(model)
            parameters = c2.load_c1_parameters()
            parameters.update({spec["parameter_id"]: 0.0 for spec in specs})
            topology_path = root / f"{model['candidate_id']}.parm7"
            receipt = c2.build_candidate(parameters, specs, topology_path)
            readback = pmd.load_file(str(topology_path))
            counts = c2.one_four_defining_counts(readback)
            topology_pass = all(counts[c2.canonical_atoms(atoms)] == baseline_counts[c2.canonical_atoms(atoms)] == 1
                                for added in receipt["added_terms"]
                                for atoms in added["physical_quartets_zero_based"])
            energy_delta = elec14_delta = vdw14_delta = 0.0
            relaxed_rows = []
            for axis in first.AXES:
                point_results = []
                for record in surfaces[axis]:
                    _, coordinates = first.read_xyz_bytes(record["xyz"])
                    expected = c2.isolated_energy_components(c2.C1_TOPOLOGY, coordinates)
                    observed = c2.isolated_energy_components(topology_path, coordinates)
                    energy_delta = max(energy_delta, abs(observed["tot"] - expected["tot"]))
                    elec14_delta = max(elec14_delta, abs(observed["elec_14"] - expected["elec_14"]))
                    vdw14_delta = max(vdw14_delta, abs(observed["vdw_14"] - expected["vdw_14"]))
                    point_results.append(gates.minimize_point(
                        readback, record,
                        root / "relaxed" / model["candidate_id"] / axis / f"{int(record['angle_degrees']):+04d}",
                        topology_path=topology_path,
                    ))
                relaxed_rows.extend(c1.relative_rows(axis, point_results))
            if not all(row["converged"] and row["target_pass"] for row in relaxed_rows):
                raise RuntimeError(f"zero-extension relaxation failed for {model['candidate_id']}")
            profile_delta = max(abs(row["mm_relative_kcal_mol"] - baseline_relaxed[(row["axis"], row["angle_degrees"])])
                                for row in relaxed_rows)
            candidate = {
                "topology_1_4_invariant_pass": topology_pass,
                "authoritative_point_count": len(relaxed_rows),
                "max_pointwise_frozen_energy_delta_kcal_mol": energy_delta,
                "max_pointwise_relaxed_profile_delta_kcal_mol": profile_delta,
                "max_elec14_delta_kcal_mol": elec14_delta,
                "max_vdw14_delta_kcal_mol": vdw14_delta,
            }
            report["candidates"][model["candidate_id"]] = candidate
            global_energy = max(global_energy, energy_delta, profile_delta)
            global_elec14 = max(global_elec14, elec14_delta)
            global_vdw14 = max(global_vdw14, vdw14_delta)
            global_profile = max(global_profile, profile_delta)

    report.update({
        "max_pointwise_c2_at_c1_energy_delta_kcal_mol": global_energy,
        "max_pointwise_relaxed_profile_delta_kcal_mol": global_profile,
        "max_c2_at_c1_elec14_delta_kcal_mol": global_elec14,
        "max_c2_at_c1_vdw14_delta_kcal_mol": global_vdw14,
    })
    report = classify(report)
    first.atomic_json(OUTPUT, report)
    print(json.dumps(report, indent=2))
    if not report["zero_extension_reproduces_c1"]:
        raise SystemExit("zero-extension identity failed")


if __name__ == "__main__":
    main()
