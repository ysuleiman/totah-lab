#!/usr/bin/env python3
import csv, json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
data = json.loads((ROOT / "FINAL_2D_WORKFLOW_PREREGISTRATION.json").read_text())
coarse = list(csv.DictReader((ROOT / "STAGE_A_GRID.csv").open()))
fine = list(csv.DictReader((ROOT / "FINE_GRID_PARTITION.csv").open()))
assert len(coarse) == 144 and len({r["cell_id"] for r in coarse}) == 144
assert len(fine) == 576 and len({r["cell_id"] for r in fine}) == 576
assert {int(r["phi_degrees"]) for r in coarse} == set(range(-180, 180, 30))
assert {int(r["psi_degrees"]) for r in coarse} == set(range(-180, 180, 30))
assert {r["spatial_partition"] for r in fine} == {"TRAIN", "VALIDATION", "TEST"}
assert sum(r["stage_a_cell"] == "True" for r in fine) == 144
assert data["stage_a"]["target_cell_count"] == 144
assert data["qm_protocol"]["grid_level"] == 5
assert data["qm_protocol"]["grid_response_gradient"] is True
assert data["stage_a"]["energy_decrease_threshold_hartree"] == 1e-5
assert data["stage_a"]["propagation_energy_upper_limit_kcal_mol"] == 15.0
assert len(data["benchmark_cost_gate"]["cells"]) == 6
assert data["benchmark_cost_gate"]["production_grid_automatically_authorized"] is False
assert data["model_decision"]["evaluate_frozen_C1_first"] is True
assert data["final_validation"]["integrated_MD_validation_required_before_completion"] is True
print("PREREGISTRATION_TESTS=PASS")
