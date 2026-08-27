#!/usr/bin/env python3

import copy
import json
import math
import tempfile
import unittest
from pathlib import Path

import numpy as np
import parmed as pmd

import run_c3_diagnostic as c3


class C3PreregistrationTests(unittest.TestCase):
    def test_frozen_point_counts(self):
        self.assertEqual({"CHI": 24, "PHI": 18, "PSI": 14},
                         {axis: len(rows) for axis, rows in c3.first.raw_surface_records().items()})

    def test_only_two_authorized_parameters(self):
        self.assertEqual({"CHI_N2_RESIDUAL", "PHI_N3_RESIDUAL"}, set(c3.PROTOCOL["parameters"]))
        self.assertFalse(c3.PROTOCOL["psi_term_authorized"])

    def test_projection_to_amber_phase_identity(self):
        for value in c3.PHASES["parameters"].values():
            amplitude, phase = value["amplitude_phi_k_kcal_mol"], math.radians(value["phase_degrees"])
            self.assertAlmostEqual(value["a_kcal_mol"], amplitude * math.cos(phase), places=14)
            self.assertAlmostEqual(value["b_kcal_mol"], amplitude * math.sin(phase), places=14)

    def test_bounds_are_loo_envelopes_and_contain_initial(self):
        for value in c3.PROTOCOL["parameters"].values():
            lo, hi = value["bounds_kcal_mol"]
            self.assertLessEqual(lo, value["initial_amplitude_kcal_mol"])
            self.assertLessEqual(value["initial_amplitude_kcal_mol"], hi)
            self.assertIn("leave-one-point", value["bound_basis"])

    def test_zero_extension_and_one_four_integrity(self):
        _, coordinates = c3.first.read_xyz_bytes(c3.first.raw_surface_records()["CHI"][0]["xyz"])
        baseline = c3.c2.isolated_energy_components(c3.C1_TOPOLOGY, coordinates)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "zero.parm7"
            receipt = c3.build_topology({name: 0.0 for name in c3.PROTOCOL["parameters"]}, path)
            observed = c3.c2.isolated_energy_components(path, coordinates)
        self.assertTrue(receipt["one_four_defining_entries_unchanged"])
        self.assertLessEqual(abs(observed["tot"] - baseline["tot"]), 1e-9)
        self.assertLessEqual(abs(observed["elec_14"] - baseline["elec_14"]), 1e-10)
        self.assertLessEqual(abs(observed["vdw_14"] - baseline["vdw_14"]), 1e-10)

    def test_amber_energy_oracle_for_each_new_term(self):
        surfaces = c3.first.raw_surface_records()
        for parameter_id in c3.PROTOCOL["parameters"]:
            spec = c3.term_spec(parameter_id)
            record = surfaces[spec["axis"]][0]
            _, coordinates = c3.first.read_xyz_bytes(record["xyz"])
            delta = 0.123456
            with tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                zero, changed = root / "zero.parm7", root / "changed.parm7"
                c3.build_topology({parameter_id: 0.0}, zero)
                c3.build_topology({parameter_id: delta}, changed)
                observed = c3.c2.isolated_energy_components(changed, coordinates)["tot"] - c3.c2.isolated_energy_components(zero, coordinates)["tot"]
            quartets = sorted({c3.c2.canonical_atoms(row["atoms_zero_based"]) for row in c3.c2.assignments(spec["axis"])})
            expected = delta * sum(1 + math.cos(spec["periodicity"] * c3.first.dihedral(coordinates, atoms) - math.radians(spec["phase_degrees"])) for atoms in quartets)
            self.assertLessEqual(abs(observed - expected), 1e-8)

    def test_c1_coefficients_are_not_adjustable(self):
        c1_ids = set(c3.c1_parameters())
        for candidate in c3.PROTOCOL["candidates"]:
            self.assertTrue(c1_ids.isdisjoint(candidate["adjustable_parameters"]))


if __name__ == "__main__":
    unittest.main()
