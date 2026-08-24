import importlib.util
import json
import sys
import unittest
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
MANIFEST = json.loads((HERE / "CAMPAIGN_MANIFEST.json").read_text())
SPEC = importlib.util.spec_from_file_location("campaign", HERE / "run_min02_min04_stationary_a100.py")
CAMPAIGN = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CAMPAIGN
SPEC.loader.exec_module(CAMPAIGN)


class PackageContractTest(unittest.TestCase):
    def test_scope_and_prohibitions(self):
        self.assertEqual(MANIFEST["scope"], ["MIN02", "MIN04"])
        self.assertTrue(all(MANIFEST["prohibitions"].values()))

    def test_protocol_is_exact_frozen_level5_protocol(self):
        protocol = MANIFEST["protocol"]
        self.assertEqual(protocol["method"], "PBE")
        self.assertEqual(protocol["dispersion"], "D3(BJ)")
        self.assertEqual(protocol["basis"], "def2-SVP")
        self.assertEqual(protocol["density_fitting_auxbasis"], "def2-SVP-JKFIT")
        self.assertEqual(protocol["grid_level"], 5)
        self.assertIs(protocol["grid_response_gradient"], True)
        self.assertIs(protocol["grid_response_hessian"], True)
        self.assertEqual(protocol["scf_conv_tol"], 1e-8)
        self.assertEqual(protocol["simple_dftd3_version"], "1.5.0")

    def test_historical_geometry_hashes(self):
        for minimum_id, expected in (("MIN02", "38336cb66b98c55b5d1d15edd9fbcbdc55e6f7840894af6b43f02372cfd9f3f8"),
                                     ("MIN04", "b54a3b68c7508151b8ba17b568d50d201a542e3291dccd57e35a9888470aaf27")):
            path = CAMPAIGN.ARCHIVE_ROOT / MANIFEST["inputs"][minimum_id]["path"]
            self.assertEqual(CAMPAIGN.sha256(path), expected)

    def test_endpoint_audit_is_identical_to_min01(self):
        audit = MANIFEST["endpoint_gradient_audit"]
        self.assertEqual(audit["columns_zero_based"], [27, 28, 29, 75, 76, 77, 165, 166, 167])
        self.assertEqual(audit["h_bohr"], 0.001)
        self.assertEqual(audit["half_h_bohr"], 0.0005)
        self.assertEqual(audit["failure_action"], "STOP_STRUCTURE_BEFORE_HESSIAN")

    def test_basin_identity_requires_every_locked_gate(self):
        elements = ["C"] * 22 + ["O"] * 3 + ["S"] + ["H"] * 30
        coords = np.arange(168, dtype=float).reshape(56, 3) * 0.01
        base = CAMPAIGN.endpoint_record
        record = {"elements": elements, "coords": coords, "energy_hartree": -1.0,
                  "s_h_angstrom": 1.0, "s_c_angstrom": 1.0,
                  "phi_deg": 0.0, "psi_deg": 0.0, "chi_deg": 0.0,
                  "angle_9_10_26_deg": 100.0, "angle_11_10_26_deg": 100.0,
                  "angle_56_26_10_deg": 100.0}
        same, _ = CAMPAIGN.pair_identity(record, dict(record), MANIFEST["basin_identity"])
        self.assertTrue(same)
        changed = dict(record, energy_hartree=-0.999)
        same, _ = CAMPAIGN.pair_identity(record, changed, MANIFEST["basin_identity"])
        self.assertFalse(same)

    def test_no_results_are_prepopulated(self):
        self.assertFalse((HERE / "results").exists())

    def test_each_structure_has_a_checksum_bound_publication_receipt(self):
        source = (HERE / "run_min02_min04_stationary_a100.py").read_text()
        self.assertIn("SCIENTIFIC_ARTIFACT_SHA256SUMS", source)
        self.assertIn("PUBLICATION_RECEIPT.json", source)
        self.assertIn("scientific_artifact_manifest_sha256", source)

    def test_every_packaged_artifact_checksum_verifies(self):
        self.assertGreater(CAMPAIGN.verify_package_integrity(), 30)


if __name__ == "__main__":
    unittest.main()
