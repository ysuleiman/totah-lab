#!/usr/bin/env python3
"""Regression gate for the immutable publication evidence audit."""

import json
import subprocess
import sys
from pathlib import Path


here = Path(__file__).resolve().parent
completed = subprocess.run(
    [sys.executable, str(here / "audit_torsion_publication_record.py"), "--verify-only"],
    check=True, capture_output=True, text=True)
result = json.loads(completed.stdout)
assert result["status"] == "PASS"
assert result["cross_axis_contamination"] == "NONE"
assert result["nested_checksums"]["PSI"] == 9051
assert result["archives"]["PSI"] == "1e339fc04bf495521095f8f6e6ff93286b0da7f2252fc27b0a90c450ddd55818"
print("TORSION_PUBLICATION_AUDIT_TESTS_PASS=4")
