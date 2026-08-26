#!/usr/bin/env python3
import csv
import copy
import hashlib
import json
import math
import tempfile
import unittest
from pathlib import Path

import parmed as pmd

import close_publication_gates as gates
import run_first_pass as first


HERE = Path(__file__).resolve().parent


class PublicationGateTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.g1 = json.loads((HERE / "02_TOPOLOGY_MAPPING/INSTANCE_LOCAL_CLONING_RECEIPT.json").read_text())
        cls.g2 = json.loads((HERE / "00_PROTOCOL/SANDER_RELAXED_PROFILE_CONTRACT.json").read_text())
        cls.g4 = json.loads((HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json").read_text())
        with (HERE / "03_PREFIT_BASELINE/PRE_FIT_POINTWISE_PUBLICATION_TABLE.csv").open() as handle:
            cls.table = list(csv.DictReader(handle))

    def test_instance_local_parameter_isolation(self):
        self.assertEqual("PASS", self.g1["instance_local_cloning"])
        self.assertTrue(all(x["only_intended_term_changed"] for x in self.g1["tests"]))

    def test_unrelated_generic_torsions_preserved(self):
        self.assertTrue(self.g1["unrelated_dihedrals_unchanged"])

    def test_frozen_component_checksums_preserved(self):
        self.assertTrue(self.g1["frozen_components_unchanged"])
        self.assertTrue(all(x["frozen_components_equal"] for x in self.g1["tests"]))

    def test_amber_serialization_readback(self):
        self.assertEqual("PASS", self.g1["sander_serialization_equivalence"])
        self.assertLess(max(x["absolute_delta_error_kcal_mol"] for x in self.g1["tests"]), 2e-6)

    def test_sander_restraint_target_angle(self):
        self.assertEqual("PASS", self.g2["target_angle_reproduction"])
        with (HERE / "03_PREFIT_BASELINE/PRE_FIT_MM_BASELINE_RELAXED.csv").open() as handle:
            for row in csv.DictReader(handle):
                self.assertLessEqual(float(row["target_angle_error_degrees"]), 0.75)

    def test_restart_readback(self):
        self.assertEqual("PASS", self.g2["restart_readback"])

    def test_deterministic_minimization(self):
        self.assertEqual("PASS", self.g2["determinism"])
        self.assertTrue(all(x["pass"] for x in self.g2["representative_repeat_tests"]))

    def test_canonical_counts_and_no_synthetic_points(self):
        counts = {axis: sum(row["axis"] == axis for row in self.table) for axis in first.AXES}
        self.assertEqual({"CHI": 24, "PHI": 18, "PSI": 14}, counts)
        self.assertEqual(56, len(self.table))
        self.assertTrue(all(row["source_candidate_state_identity"] and row["source_archive_member"] for row in self.table))

    def test_hartree_conversion_constant(self):
        self.assertEqual(627.509474, first.HARTREE_TO_KCAL_MOL)

    def test_pointwise_relative_reference(self):
        for axis in first.AXES:
            rows = [r for r in self.table if r["axis"] == axis]
            self.assertAlmostEqual(0.0, min(float(r["qm_relative_energy_kcal_mol"]) for r in rows), places=9)
            self.assertAlmostEqual(0.0, min(float(r["mm_relaxed_relative_energy_kcal_mol"]) for r in rows), places=9)

    def test_acceptance_gate_immutable_identity(self):
        self.assertTrue(self.g4["locked"])
        self.assertEqual(
            "859fbc97a8f3480f9e168c22b93a421d597494856d151db1842bb3ead61bbbc4",
            hashlib.sha256((HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json").read_bytes()).hexdigest(),
        )

    def test_c2_requires_registered_c1_failure(self):
        rule = self.g4["c1_to_c2_admission"]
        self.assertTrue(rule["requires_registered_c1_failure"])
        self.assertNotIn("slight training-RMSE improvement", rule["allowed"])
        self.assertIn("slight training-RMSE improvement", rule["prohibited"])

    def test_no_fit_or_qm_boundary(self):
        decision = json.loads((HERE / "08_PUBLICATION/GATE_CLOSURE_DECISION.json").read_text())
        self.assertFalse(decision["fit_run"])
        self.assertFalse(decision["raw_qm_artifacts_modified"])
        self.assertFalse(decision["source_force_field_modified"])

    def test_multiple_fourier_terms_on_same_quartet_are_independent(self):
        surfaces = first.raw_surface_records()
        source = min(surfaces["CHI"], key=lambda row: row["qm_energy_hartree"])
        _, coordinates = first.read_xyz_bytes(source["xyz"])
        quartet = (8, 9, 25, 55)
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            original = pmd.load_file(str(first.BASELINE))
            base_term = next(t for t in gates.proper_terms(original) if gates.canonical_term(t) == quartet)
            extra_type = copy.copy(base_term.type)
            extra_type.phi_k = 0.2
            extra_type.per = 1
            extra_type.phase = 180.0
            original.dihedral_types.append(extra_type)
            extra = pmd.topologyobjects.Dihedral(base_term.atom1, base_term.atom2, base_term.atom3,
                                                  base_term.atom4, type=extra_type)
            extra.ignore_end = True
            original.dihedrals.append(extra)
            original.dihedral_types.claim()
            original_path = root / "multi-term.parm7"
            original.save(str(original_path), overwrite=True)
            readback = pmd.load_file(str(original_path))
            same_quartet = [(identity, term) for identity, term in gates.term_identity_records(readback)
                            if gates.canonical_term(term) == quartet]
            self.assertEqual(2, len(same_quartet))
            self.assertEqual({1.0, 3.0}, {float(term.type.per) for _, term in same_quartet})

            before = gates.torsion_snapshot(readback)
            target_identity, target = next(item for item in same_quartet if float(item[1].type.per) == 1.0)
            changed_type = copy.copy(target.type)
            changed_type.phi_k += gates.CLONE_TEST_DELTA
            readback.dihedral_types.append(changed_type)
            target.type = changed_type
            readback.dihedral_types.claim()
            changed_path = root / "multi-term-changed.parm7"
            readback.save(str(changed_path), overwrite=True)
            after_topology = pmd.load_file(str(changed_path))
            after = gates.torsion_snapshot(after_topology)
            changed = [identity for identity in before if before[identity] != after[identity]]
            self.assertEqual([target_identity], changed)
            other_identity = next(identity for identity, term in same_quartet if float(term.type.per) == 3.0)
            self.assertEqual(before[other_identity], after[other_identity])
            unrelated = [identity for identity in before if not identity.startswith("8-9-25-55|")]
            self.assertTrue(all(before[identity] == after[identity] for identity in unrelated))

            delta = gates.isolated_total_energy(changed_path, coordinates) - gates.isolated_total_energy(original_path, coordinates)
            phi = first.dihedral(coordinates, quartet)
            expected = gates.CLONE_TEST_DELTA * (1.0 + math.cos(phi - math.pi))
            self.assertAlmostEqual(expected, delta, places=10)


if __name__ == "__main__":
    unittest.main()
