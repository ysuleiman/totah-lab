#!/usr/bin/env python3
"""Run the frozen matched-Vina analysis against the corrected comparison set."""
from __future__ import annotations

import importlib.util
from pathlib import Path


ROOT = Path(__file__).resolve().parents[3]
SOURCE = ROOT / "research/mettl7-netarsudil-sam-mechanism/scripts/analyze_matched_vina.py"
BASE = ROOT / "research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction"

spec = importlib.util.spec_from_file_location("matched_analysis", SOURCE)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)
module.BASE = BASE
module.RAW = BASE / "raw"
module.PREP = BASE / "prepared"
module.OUT = BASE / "analysis"
module.OUT.mkdir(parents=True, exist_ok=True)
module.main()
