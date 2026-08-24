import importlib.util
import json
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "stationary", HERE / "run_min01_stationary_optimization_a100.py")
RUNNER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RUNNER
SPEC.loader.exec_module(RUNNER)


class StationaryOptimizationContractTest(unittest.TestCase):
    def test_closure_evidence_is_verified_and_qualified(self):
        result = RUNNER.verify_closure()
        self.assertTrue(result["level5_derivative_grid_qualified"])
        self.assertTrue(result["energy_convergence_pass"])
        self.assertTrue(result["gradient_convergence_pass"])

    def test_gradient_audit_is_gated_before_hessian(self):
        manifest = json.loads((HERE / "OPTIMIZATION_MANIFEST.json").read_text())
        self.assertEqual(manifest["hessian_qualification"]["condition"],
                         "endpoint_gradient_audit.pass == true")
        self.assertEqual(manifest["endpoint_gradient_audit"]["failure_action"],
                         "STOP_BEFORE_HESSIAN")

    def test_scientific_scope_is_min01_only(self):
        manifest = json.loads((HERE / "OPTIMIZATION_MANIFEST.json").read_text())
        self.assertEqual(manifest["scope"], ["MIN01"])
        self.assertTrue(manifest["prohibitions"]["MIN02"])
        self.assertTrue(manifest["prohibitions"]["MIN04"])
        self.assertTrue(manifest["prohibitions"]["model_fit"])

    def test_missing_grid_response_capability_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "lacks grid_response"):
            RUNNER.require_gpu_grid_response(object(), "RKS gradient")

    def test_integrity_classification_rejects_any_negative_mode(self):
        policy = json.loads((HERE / "OPTIMIZATION_MANIFEST.json").read_text())[
            "stationary_point_classification"]
        self.assertIn("strictly positive", policy["verified_local_minimum"])
        self.assertIn("negative", policy["saddle"])
        self.assertIn("never", policy["legacy_minus20_minus50_magnitude_bands"])

    def test_checksum_validator_rejects_unlisted_result(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            (directory / "listed.txt").write_text("listed")
            RUNNER.write_checksums(directory)
            (directory / "stdout_only_result.txt").write_text("unlisted")
            with self.assertRaisesRegex(RuntimeError, "coverage mismatch"):
                RUNNER.verify_checksums(directory)

    def test_checksum_validator_rejects_deleted_result(self):
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            artifact = directory / "result.json"
            artifact.write_text("{}")
            RUNNER.write_checksums(directory)
            artifact.unlink()
            with self.assertRaisesRegex(RuntimeError, "missing or modified"):
                RUNNER.verify_checksums(directory)

    def test_actual_pyscf_rigid_body_return_contract_stacks_to_6_by_3n(self):
        import numpy as np
        from pyscf import gto
        from pyscf.hessian import thermo
        mol = gto.M(atom="O 0 0 0; H 0 -.757 .587; H 0 .757 .587",
                    basis="sto-3g", unit="Angstrom", verbose=0)
        masses = np.asarray(mol.atom_mass_list(isotope_avg=True))
        contract = thermo._get_TR(masses, mol.atom_coords())
        self.assertIsInstance(contract, tuple)
        self.assertEqual(len(contract), 6)
        self.assertTrue(all(np.asarray(vector).shape == (3 * mol.natm,) for vector in contract))
        matrix = np.stack(contract, axis=0)
        self.assertEqual(matrix.shape, (6, 3 * mol.natm))
        self.assertEqual(np.linalg.matrix_rank(matrix), 6)


if __name__ == "__main__":
    unittest.main()
