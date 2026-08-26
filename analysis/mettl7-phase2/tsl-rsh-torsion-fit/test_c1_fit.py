#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path

import parmed as pmd

import close_publication_gates as gates
import run_c1_fit as c1
import run_first_pass as first


class C1PreexecutionTests(unittest.TestCase):
    def test_locked_six_parameter_model(self):
        protocol = __import__("json").loads(c1.PROTOCOL_PATH.read_text())
        self.assertEqual(6, len(protocol["parameters"]))
        self.assertEqual({1, 2, 7, 12, 17, 30}, {int(row["id"].split("_")[-1]) for row in protocol["parameters"]})
        self.assertEqual("equal-surface: arithmetic mean of the three per-surface mean squared residuals", protocol["primary_weighting"])

    def test_candidate_changes_only_mapped_terms(self):
        initial = c1.initial_parameters()
        candidate = dict(initial)
        candidate[1] += 0.05
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "candidate.parm7"
            receipt = c1.build_candidate(candidate, path)
            self.assertTrue(receipt["frozen_components_unchanged"])
            self.assertTrue(receipt["unrelated_dihedrals_unchanged"])
            baseline = gates.torsion_snapshot(pmd.load_file(str(first.BASELINE)))
            changed = gates.torsion_snapshot(pmd.load_file(str(path)))
            expected = set(c1.mapping_by_type()[1])
            actual = {identity for identity in baseline if baseline[identity] != changed[identity]}
            # All six groups are cloned, but groups at baseline are numerically
            # identical; only the deliberately perturbed type-1 group changes.
            self.assertEqual(expected, actual)

    def test_weight_function_is_frozen_and_bounded(self):
        self.assertEqual(1.0, c1.low_weight(0.0))
        self.assertEqual(1.0, c1.low_weight(1.0))
        self.assertEqual(0.0, c1.low_weight(10.0))
        self.assertGreater(c1.low_weight(5.0), 0.0)
        self.assertLess(c1.low_weight(5.0), 1.0)


if __name__ == "__main__":
    unittest.main()
