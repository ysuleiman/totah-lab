#!/usr/bin/env python3
import copy
import json
import math
import tempfile
import unittest
from pathlib import Path

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

    def test_locked_contract_identities(self):
        self.assertEqual("859fbc97a8f3480f9e168c22b93a421d597494856d151db1842bb3ead61bbbc4",
                         first.sha256_path(c2.HERE / "00_PROTOCOL/LOCKED_ACCEPTANCE_PROTOCOL.json"))
        self.assertEqual("47274c77719104de31ce8ab34ad00e71daa38e72",
                         json.loads((c2.HERE / "08_PUBLICATION/C1_DECISION.json").read_text()).get("source_commit", "47274c77719104de31ce8ab34ad00e71daa38e72"))


if __name__ == "__main__":
    unittest.main()
