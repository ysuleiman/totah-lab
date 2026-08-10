#!/usr/bin/env python3
import csv
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
systems = list(csv.DictReader((HERE / "matrix_summary.csv").open()))
states = list(csv.DictReader((HERE / "all_states.csv").open()))
manifest = json.loads((HERE / "manifest.json").read_text())

assert len(systems) == 8
assert {row["system"] for row in systems} == {
    "7A_WT", "7B_WT", "7A_F43L", "7A_F199G", "7A_F43L_F199G",
    "7B_L43F", "7B_G199F", "7B_L43F_G199F"
}
assert len(states) == 36 and all(row["status"] == "PASS" for row in states)
for row in states:
    assert float(row["attack_angle_deg"]) >= 150.0
    assert 2.8 - 1e-6 <= float(row["catalytic_distance_A"]) <= 3.2 + 1e-6
    assert float(row["superpocket_atom_fraction"]) >= 0.70
    assert int(row["protein_pairs_lt_2A"]) == 0 and int(row["sam_pairs_lt_2A"]) == 0
    assert float(row["backbone_rmsd_A"]) <= 0.25
    assert float(row["max_atom_displacement_A"]) <= 1.50
    assert float(row["max_bond_deviation_A"]) <= 0.02
assert manifest["protocol_sha256"] == "a0169325200c3d5e8d37d74be3adedef440f6b3cce4b87b034f838bb1eab7311"
print("Stage 3 productive-TSL validation: PASS (8 systems, 36 states)")
