#!/usr/bin/env python3
import copy
import json
import math
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import parmed as pmd

import close_publication_gates as gates
import run_c2_fit as c2
import run_first_pass as first


class C2PreexecutionTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.panel = json.loads(c2.PANEL_PATH.read_text())

    def test_panel_is_minimal_and_chi_is_frozen(self):
        self.assertEqual(3, len(self.panel["candidates"]))
        self.assertTrue(all(term["axis"] in {"PHI", "PSI"}
                            for candidate in self.panel["candidates"] for term in candidate["new_terms"]))
        self.assertFalse(self.panel["higher_periodicities_authorized"])
        self.assertFalse(self.panel["phase_optimization_authorized"])

    def test_added_phi_term_is_instance_local_and_sander_equivalent(self):
        model = self.panel["candidates"][0]
        parameters = c2.load_c1_parameters()
        parameters["PHI_N2_PHASE180"] = 0.0
        source = min(first.raw_surface_records()["PHI"], key=lambda row: row["qm_energy_hartree"])
        _, coordinates = first.read_xyz_bytes(source["xyz"])
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            zero = root / "zero.parm7"
            changed = root / "changed.parm7"
            zero_receipt = c2.build_candidate(parameters, c2.new_term_specs(model), zero)
            parameters["PHI_N2_PHASE180"] = 0.2
            changed_receipt = c2.build_candidate(parameters, c2.new_term_specs(model), changed)
            self.assertTrue(zero_receipt["frozen_components_unchanged"])
            self.assertTrue(changed_receipt["frozen_components_unchanged"])
            quartets = changed_receipt["added_terms"][0]["physical_quartets_zero_based"]
            expected = 0.0
            for quartet in quartets:
                phi = first.dihedral(coordinates, tuple(quartet))
                expected += 0.2 * (1.0 + math.cos(2.0 * phi - math.pi))
            observed = gates.isolated_total_energy(changed, coordinates) - gates.isolated_total_energy(zero, coordinates)
            self.assertAlmostEqual(expected, observed, places=8)

            before = gates.torsion_snapshot(pmd.load_file(str(zero)))
            after = gates.torsion_snapshot(pmd.load_file(str(changed)))
            changed_terms = [key for key in before if before[key] != after[key]]
            self.assertEqual(len(quartets), len(changed_terms))
            self.assertTrue(all("|n=2|" in key and
                                abs(float(key.split("phase=")[1].split("|")[0]) - 180.0) < 1e-6
                                for key in changed_terms))

    def test_every_added_term_is_a_non_14_defining_continuation_after_readback(self):
        parameters = c2.load_c1_parameters()
        baseline = pmd.load_file(str(c2.C1_TOPOLOGY))
        baseline_counts = c2.one_four_defining_counts(baseline)
        for model in self.panel["candidates"]:
            specs = c2.new_term_specs(model)
            candidate_parameters = dict(parameters)
            candidate_parameters.update({spec["parameter_id"]: 0.0 for spec in specs})
            with self.subTest(candidate=model["candidate_id"]), tempfile.TemporaryDirectory() as temporary:
                path = Path(temporary) / "candidate.parm7"
                receipt = c2.build_candidate(candidate_parameters, specs, path)
                readback = pmd.load_file(str(path))
                observed = c2.one_four_defining_counts(readback)
                self.assertTrue(receipt["one_four_defining_entries_unchanged"])
                for added in receipt["added_terms"]:
                    for atoms in added["physical_quartets_zero_based"]:
                        quartet = c2.canonical_atoms(atoms)
                        self.assertEqual(1, baseline_counts[quartet])
                        self.assertEqual(baseline_counts[quartet], observed[quartet])

    def test_duplicate_14_defining_continuation_fails_closed(self):
        model = self.panel["candidates"][0]
        specs = c2.new_term_specs(model)
        parameters = c2.load_c1_parameters()
        parameters.update({spec["parameter_id"]: 0.0 for spec in specs})
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "candidate.parm7"
            receipt = c2.build_candidate(parameters, specs, path)
            topology = pmd.load_file(str(path))
            target = c2.canonical_atoms(receipt["added_terms"][0]["physical_quartets_zero_based"][0])
            continuation = next(term for term in topology.dihedrals
                                if c2.canonical_atoms((term.atom1.idx, term.atom2.idx,
                                                       term.atom3.idx, term.atom4.idx)) == target
                                and term.ignore_end)
            continuation.ignore_end = False
            with self.assertRaisesRegex(RuntimeError, "1-4-defining-entry invariant"):
                c2.assert_added_term_one_four_integrity(
                    topology, receipt["added_terms"], c2.one_four_defining_counts(pmd.load_file(str(c2.C1_TOPOLOGY)))
                )

    def test_zero_extension_preserves_energy_and_14_decomposition(self):
        model = self.panel["candidates"][-1]
        specs = c2.new_term_specs(model)
        parameters = c2.load_c1_parameters()
        parameters.update({spec["parameter_id"]: 0.0 for spec in specs})
        source = min(first.raw_surface_records()["PSI"], key=lambda row: row["qm_energy_hartree"])
        _, coordinates = first.read_xyz_bytes(source["xyz"])
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "candidate.parm7"
            c2.build_candidate(parameters, specs, path)
            expected = c2.isolated_energy_components(c2.C1_TOPOLOGY, coordinates)
            observed = c2.isolated_energy_components(path, coordinates)
            for component in first.ENERGY_FIELDS:
                self.assertAlmostEqual(expected[component], observed[component], places=10)

    def test_zero_extension_preserves_all_unrelated_parameters(self):
        model = self.panel["candidates"][1]
        specs = c2.new_term_specs(model)
        parameters = c2.load_c1_parameters()
        parameters.update({spec["parameter_id"]: 0.0 for spec in specs})
        baseline = pmd.load_file(str(c2.C1_TOPOLOGY))
        frozen_before = first.frozen_non_torsional(baseline)["components"]
        torsions_before = gates.torsion_snapshot(baseline)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "candidate.parm7"
            c2.build_candidate(parameters, specs, path)
            readback = pmd.load_file(str(path))
            self.assertEqual(frozen_before, first.frozen_non_torsional(readback)["components"])
            torsions_after = gates.torsion_snapshot(readback)
            for identity, state in torsions_before.items():
                self.assertIn(identity, torsions_after)
                self.assertEqual(state, torsions_after[identity])

    def test_unconverged_full_domain_sweep_fails_closed(self):
        failed = {"minimization_converged": False, "target_angle_pass": True,
                  "mm_tot_kcal_mol_absolute": 0.0, "target_angle_after_minimization_degrees": -180.0}
        with mock.patch.object(c2.c1.gates, "minimize_point", return_value=failed):
            with self.assertRaisesRegex(RuntimeError, "unconverged full-domain sweep"):
                c2.c1.full_domain(c2.C1_TOPOLOGY, first.raw_surface_records(), [], Path("unused"))

    def test_candidate_rejects_unconverged_sweep_without_metrics(self):
        candidate = {
            "candidate_id": "TEST_FAIL_CLOSED", "topology_path": c2.C1_TOPOLOGY,
            "rows": [],
        }
        surfaces = first.raw_surface_records()
        for axis in first.AXES:
            for record in surfaces[axis]:
                candidate["rows"].append({
                    "axis": axis, "angle_degrees": int(record["angle_degrees"]),
                    "qm_relative_kcal_mol": 0.0, "mm_relative_kcal_mol": 0.0,
                    "residual_kcal_mol": 0.0, "converged": True, "target_pass": True,
                })
        def good_result(_topology, record, *_args, **_kwargs):
            return {**record, "minimization_converged": True, "target_angle_pass": True,
                    "mm_tot_kcal_mol_absolute": 0.0,
                    "target_angle_after_minimization_degrees": record["angle_degrees"],
                    "qm_energy_hartree": 0.0}
        with tempfile.TemporaryDirectory() as temporary, \
                mock.patch.object(c2.gates, "minimize_point", side_effect=good_result), \
                mock.patch.object(c2.c1, "full_domain", side_effect=RuntimeError("unconverged full-domain sweep")), \
                mock.patch.object(c2, "VALIDATION", Path(temporary)):
            result = c2.candidate_analysis(candidate, surfaces)
        self.assertFalse(result["locked_gate_pass"])
        self.assertFalse(result["unsampled"]["pass"])
        self.assertFalse(result["unsampled"]["metrics_computed"])
        self.assertEqual([], result["domain"])
        self.assertEqual("FAIL", result["gates"]["periodic_closure"])
        self.assertEqual("FAIL", result["gates"]["unsampled_region"])

    def test_locked_contract_identities(self):
        self.assertEqual("859fbc97a8f3480f9e168c22b93a421d597494856d151db1842bb3ead61bbbc4",
                         first.sha256_path(c2.HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json"))
        self.assertEqual("47274c77719104de31ce8ab34ad00e71daa38e72",
                         json.loads((c2.HERE / "08_PUBLICATION/C1_DECISION.json").read_text()).get("source_commit", "47274c77719104de31ce8ab34ad00e71daa38e72"))


if __name__ == "__main__":
    unittest.main()
