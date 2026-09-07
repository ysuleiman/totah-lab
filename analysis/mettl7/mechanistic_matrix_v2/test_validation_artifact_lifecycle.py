import sys, tempfile, unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parent))
from validation_artifact_lifecycle import invalidate_success_manifest

class ValidationArtifactLifecycleTest(unittest.TestCase):
    def test_failed_rerun_cannot_leave_success_named_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); success = root / "FINAL_SHA256SUMS"; failure = root / "VALIDATION_FAILURE_RECEIPT.json"
            success.write_text("old-success\n")
            receipt = invalidate_success_manifest(success, failure, ["count mismatch"])
            self.assertFalse(success.exists())
            self.assertEqual("RENAMED_SUPERSEDED", receipt["success_manifest_disposition"])
            self.assertTrue(Path(receipt["superseded_manifest"]).read_text() == "old-success\n")
            self.assertTrue(failure.is_file())

    def test_failure_without_prior_success_still_writes_receipt(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); success = root / "FINAL_SHA256SUMS"; failure = root / "VALIDATION_FAILURE_RECEIPT.json"
            receipt = invalidate_success_manifest(success, failure, ["missing input"])
            self.assertEqual("ABSENT", receipt["success_manifest_disposition"])
            self.assertTrue(failure.is_file())

if __name__ == "__main__": unittest.main()
