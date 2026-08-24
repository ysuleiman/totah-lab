import importlib.util
import tempfile
import sys
import unittest
from pathlib import Path

import numpy as np
from pyscf import gto
from pyscf.hessian import thermo

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "recovery", HERE / "recover_stationary_qualification.py")
RECOVERY = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = RECOVERY
SPEC.loader.exec_module(RECOVERY)


class StationaryRecoveryTest(unittest.TestCase):
    def test_actual_get_tr_contract_is_six_flat_vectors(self):
        mol = gto.M(atom="O 0 0 0; H 0 -.757 .587; H 0 .757 .587",
                    basis="sto-3g", unit="Angstrom", verbose=0)
        masses = np.asarray(mol.atom_mass_list(isotope_avg=True))
        contract = thermo._get_TR(masses, mol.atom_coords())
        self.assertIsInstance(contract, tuple)
        self.assertEqual(len(contract), 6)
        self.assertTrue(all(np.asarray(vector).shape == (9,) for vector in contract))
        matrix = np.stack(contract, axis=0)
        self.assertEqual(matrix.shape, (6, 9))
        self.assertEqual(np.linalg.matrix_rank(matrix), 6)

    def test_recovery_source_contains_no_expensive_backend_call(self):
        source = (HERE / "recover_stationary_qualification.py").read_text()
        for forbidden in (".kernel(", "to_gpu(", "nuc_grad_method(", ".Hessian(",
                          "run_optimizer(", "get_dispersion("):
            self.assertNotIn(forbidden, source)

    def test_sign_invariant_mode_comparison(self):
        modes = np.asarray([[[1.0, 2.0, 3.0]], [[4.0, 5.0, 6.0]]])
        self.assertEqual(RECOVERY.sign_invariant_mode_error(modes, -modes), 0.0)

    def test_malformed_rigid_body_contract_fails_closed(self):
        with self.assertRaisesRegex(RuntimeError, "vector dimension"):
            original = thermo._get_TR
            try:
                thermo._get_TR = lambda mass, coords: tuple(np.zeros(2) for _ in range(6))
                RECOVERY.rigid_body_matrix(np.ones(56), np.zeros((56, 3)))
            finally:
                thermo._get_TR = original

    def test_checksum_verifier_fails_closed_for_modified_artifact(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            artifact = root / "value.txt"
            artifact.write_text("original")
            manifest = root / "SHA256SUMS"
            manifest.write_text(f"{RECOVERY.sha256(artifact)}  value.txt\n")
            self.assertEqual(RECOVERY.verify_checksum_file(root, manifest), 1)
            artifact.write_text("modified")
            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                RECOVERY.verify_checksum_file(root, manifest)


if __name__ == "__main__":
    unittest.main()
