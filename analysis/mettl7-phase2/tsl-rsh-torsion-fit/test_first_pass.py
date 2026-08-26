#!/usr/bin/env python3
"""Fail-closed checks for the publication torsion-fit first pass."""

import csv
import hashlib
import json
from pathlib import Path


here = Path(__file__).resolve().parent
checks = 0
for line in (here / "SHA256SUMS").read_text().splitlines():
    expected, relative = line.split(maxsplit=1)
    path = here / relative.strip()
    assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest() == expected
    checks += 1
decision = json.loads((here / "08_PUBLICATION/FIRST_PASS_DECISION.json").read_text())
assert decision["publication_inputs_verified"] is True; checks += 1
assert decision["raw_qm_artifacts_modified"] is False; checks += 1
assert decision["acceptance_gates_locked"] is False; checks += 1
assert decision["ready_to_fit"] is False; checks += 1
rows = list(csv.DictReader((here / "03_PREFIT_BASELINE/PRE_FIT_MM_BASELINE.csv").open()))
assert {axis: sum(row["axis"] == axis for row in rows) for axis in ("CHI", "PHI", "PSI")} == {"CHI": 24, "PHI": 18, "PSI": 14}; checks += 1
mapping = json.loads((here / "02_TOPOLOGY_MAPPING/TORSION_PARAMETER_COUPLING_MATRIX.json").read_text())
assert set(mapping["coupling_matrix"]); checks += 1
print(f"TORSION_FIT_FIRST_PASS_TESTS_PASS={checks}")
