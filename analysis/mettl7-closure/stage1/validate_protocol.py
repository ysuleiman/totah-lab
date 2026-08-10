#!/usr/bin/env python3
"""Fail-fast structural checks for the locked Stage 1 protocol."""
import json
from pathlib import Path

HERE = Path(__file__).resolve().parent
protocol = json.loads((HERE / "protocol.json").read_text())

expected = {
    "7A_WT", "7B_WT", "7A_F43L", "7A_F199G", "7A_F43L_F199G",
    "7B_L43F", "7B_G199F", "7B_L43F_G199F"
}
actual = {system["id"] for system in protocol["systems"]}
assert actual == expected, (actual, expected)
assert protocol["status"] == "LOCKED_BEFORE_STAGE2"
assert protocol["physical_state"] == "protein_plus_fixed_SAM"
assert protocol["productive_tsl"]["protein_heavy_atom_pairs_lt_2A"] == 0
assert protocol["productive_tsl"]["sam_nonreactive_heavy_atom_pairs_lt_2A"] == 0
assert protocol["productive_tsl"]["superpocket_atom_fraction_min"] == 0.70
assert protocol["dcmb_docking"]["seeds"] == [1, 7, 42]
assert protocol["dcmb_docking"]["exhaustiveness"] == 32
assert "No master verdict" in protocol["verdict_policy"]
print("Stage 1 protocol validation: PASS")
