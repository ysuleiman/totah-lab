import hashlib
import importlib.util
import io
import sys
import unittest
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location("seal", HERE / "seal_results.py")
SEAL = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = SEAL
SPEC.loader.exec_module(SEAL)


class SealResultsTest(unittest.TestCase):
    def test_source_identities(self):
        self.assertEqual(SEAL.sha256(SEAL.SOURCE_RESULTS), SEAL.EXPECTED_RESULTS_SHA)
        self.assertEqual(SEAL.sha256(SEAL.SOURCE_RUNTIME), SEAL.EXPECTED_RUNTIME_SHA)
        self.assertEqual(SEAL.sha256(SEAL.SOURCE_PACKAGE), SEAL.EXPECTED_PACKAGE_SHA)

    def test_all_nested_checksums_verify(self):
        with zipfile.ZipFile(SEAL.SOURCE_RESULTS) as archive:
            self.assertEqual(SEAL.verify_nested_checksums(archive), (93, 1291))

    def test_checksum_corruption_fails_closed(self):
        payload = io.BytesIO()
        with zipfile.ZipFile(payload, "w") as archive:
            archive.writestr("results/value.txt", "modified")
            archive.writestr("results/SHA256SUMS", hashlib.sha256(b"original").hexdigest() + "  value.txt\n")
        payload.seek(0)
        with zipfile.ZipFile(payload) as archive:
            with self.assertRaisesRegex(RuntimeError, "checksum mismatch"):
                SEAL.verify_nested_checksums(archive)

    def test_sealing_source_has_no_expensive_execution_path(self):
        source = (HERE / "seal_results.py").read_text()
        for forbidden in ("to_gpu(", ".kernel(", "nuc_grad_method(", ".Hessian(",
                          "run_optimizer(", "get_dispersion("):
            self.assertNotIn(forbidden, source)

    def test_existing_sealed_output_verifies(self):
        result = SEAL.OUTPUT / "INGESTION_RESULT.json"
        self.assertTrue(result.is_file())
        for row in (SEAL.OUTPUT / "SEAL_SHA256SUMS").read_text().splitlines():
            expected, relative = row.split(maxsplit=1)
            self.assertEqual(SEAL.sha256(SEAL.OUTPUT / relative.strip()), expected)


if __name__ == "__main__":
    unittest.main()
