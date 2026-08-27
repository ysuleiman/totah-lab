#!/usr/bin/env python3
"""Build the immutable preregistration for the final PHI x PSI program."""
from __future__ import annotations

import csv
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
COARSE = list(range(-180, 180, 30))
FINE = list(range(-180, 180, 15))


def dump(path: Path, value) -> None:
    path.write_text(json.dumps(value, indent=2, sort_keys=True, allow_nan=False) + "\n")


def sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def split_for(phi: int, psi: int) -> str:
    # Six-by-six periodic 60-degree spatial blocks. Entire blocks, rather than
    # neighboring cells, are assigned together.
    bp = ((phi + 180) // 60) % 6
    bq = ((psi + 180) // 60) % 6
    color = (bp + 2 * bq) % 6
    return "TEST" if color == 0 else ("VALIDATION" if color == 3 else "TRAIN")


protocol = {
    "schema": "tsl-rsh-final-2d-qm-preregistration-v1",
    "objective": "deliver a validated usable TSL-RSH Amber force field",
    "stage_a": {
        "axes": {"PHI_zero_based": [25, 9, 8, 7], "PSI_zero_based": [9, 8, 7, 1]},
        "grid_degrees": COARSE,
        "target_cell_count": 144,
        "constraints": "both PHI and PSI fixed simultaneously; all other allowed coordinates relaxed",
        "wavefront": "periodic 2-D multidirectional nearest-neighbor propagation with deterministic same-round lowest-energy reduction",
        "neighbors": "four axial periodic neighbors (+/-30 PHI, +/-30 PSI)",
        "multistart_seeds": ["MIN01", "MIN02", "MIN04"],
        "authoritative_cell": "lowest-energy checksum-valid converged geometry-gated candidate at the tuple cell",
        "energy_decrease_threshold_hartree": 1e-5,
        "propagation_energy_upper_limit_kcal_mol": 15.0,
        "propagation_energy_upper_limit_hartree": 0.023904021564607733,
    },
    "qm_protocol": {
        "method": "PBE", "dispersion": "D3(BJ)", "basis": "def2-SVP",
        "density_fitting_basis": "def2-SVP-JKFIT", "grid_level": 5,
        "grid_response_gradient": True, "scf_tolerance": 1e-8,
        "scf_max_cycles": 160, "initial_guess": "MINAO", "charge": 0,
        "multiplicity": 1, "simple_dftd3": "1.5.0",
        "d3_parameters": {"s6": 1.0, "s8": 0.7875, "a1": 0.4289,
                          "a2": 4.4407, "alp": 14.0, "s9": 0.0, "atm": False},
        "software": {"PySCF": "2.14.0", "GPU4PySCF": "1.8.0",
                     "CuPy": "13.4.1", "geomeTRIC": "1.1.1"},
    },
    "geometry_gates": ["atom_order", "connectivity", "chirality",
                       "PHI_target_within_0.1_degree", "PSI_target_within_0.1_degree"],
    "retry_policy": {"expected_candidate_failures_isolated": True,
                     "programming_or_protocol_failures_fail_closed": True,
                     "maximum_geometric_iterations": 300,
                     "completed_candidates_never_rerun": True},
    "stage_b_refinement": {
        "fine_grid_degrees": FINE,
        "selection_is_model_independent": True,
        "select_unlabeled_15_degree_cells_if_any": [
            "inside the periodic connected component containing the Stage-A global minimum with coarse corner energy <=10 kcal/mol, plus one fine-cell boundary ring",
            "adjacent coarse plaquette has absolute mixed energy finite difference >=1.0 kcal/mol",
            "adjacent coarse row/column has absolute second energy difference >=1.0 kcal/mol",
            "adjacent Stage-A C1 absolute residual >=1.0 kcal/mol",
            "plaquette straddles the 10 kcal/mol thermal boundary",
            "cell borders a Stage-A local minimum or barrier/saddle edge",
            "same-cell alternatives differ by >=0.25 Angstrom heavy-atom RMSD and lie within 1.0 kcal/mol",
        ],
        "selection_union_then_periodic_neighbor_closure": True,
        "no_model_fit_used_for_selection": True,
        "maximum_surface": "complete 24x24 grid (576 cells) if predetermined sufficiency remains false",
    },
    "sufficiency_gate": {
        "evaluated_only_after_selected_refinement_completes": True,
        "thermal_interpolation_max_abs_kcal_mol": 0.25,
        "nonthermal_le20_interpolation_max_abs_kcal_mol": 0.50,
        "minimum_location_stability_degrees": 15,
        "relative_minimum_energy_stability_kcal_mol": 0.50,
        "barrier_location_stability_degrees": 15,
        "barrier_height_stability_kcal_mol": 1.0,
        "periodic_seam_max_abs_kcal_mol": 0.10,
        "requirements": ["all selected cells converged or explicitly failed closed",
                         "connected thermal basin resolved", "coupling curvature resolved",
                         "competing minima resolved", "bounding barriers resolved",
                         "all stated interpolation/stability limits pass"],
        "failure_action": "add the predetermined periodic fine-neighbor closure of failing regions and repeat; if unresolved, complete all 576 cells",
    },
    "spatial_split": {
        "sealed_algorithm": "60-degree periodic blocks; color=(phi_block+2*psi_block) mod 6; TEST color 0, VALIDATION color 3, other colors TRAIN",
        "labels_hidden": {"TRAIN": False, "VALIDATION_until_model_frozen": True,
                          "TEST_until_final_candidate_frozen": True},
        "same_cell_alternatives_follow_cell_partition": True,
    },
    "benchmark_cost_gate": {
        "authorized_before_production": True,
        "cells": [
            {"phi": -60, "psi": -60, "class": "known-low-energy"},
            {"phi": -90, "psi": -60, "class": "ordinary-basin"},
            {"phi": -60, "psi": -90, "class": "coupled-displacement"},
            {"phi": 0, "psi": 0, "class": "moderate-strain"},
            {"phi": 120, "psi": 120, "class": "difficult-boundary"},
            {"phi": -180, "psi": -180, "class": "periodic-seam"},
        ],
        "exact_production_protocol_required": True,
        "production_grid_automatically_authorized": False,
        "stop_and_shutdown_after_benchmark": True,
        "projection": "report measured median and nearest-rank P90 GPU-hours/cell for both projected energy-pruned count and all 144 cells using live price",
        "refinement_cost": "UNKNOWN_UNTIL_STAGE_A",
    },
    "model_decision": {
        "evaluate_frozen_C1_first": True,
        "C1_pass_uses_frozen_gates": {"whole_rmse_max": 1.0, "whole_mae_max": 0.75,
            "whole_max_abs_max": 2.0, "thermal_weighted_rmse_max": 1.0,
            "thermal_mae_max": 0.75, "minimum_angle_error_max_degrees": 15,
            "barrier_angle_error_max_degrees": 15, "barrier_height_error_max_kcal_mol": 1.0,
            "periodic_closure_max_kcal_mol": 0.1},
        "if_C1_passes": "use C1; no more complex fit",
        "if_C1_fails": "fit the preregistered symmetry-constrained instance-specific additive model on TRAIN only",
        "if_additive_heldout_thermal_fails": "ADDITIVE_TORSION_FUNCTIONAL_FORM_INSUFFICIENT; stop before any CMAP implementation",
        "no_C4_C5_independent_Fourier_sequence": True,
    },
    "final_validation": {"bands_kcal_mol": [1, 5, 10], "also_report_gt10": True,
        "metrics": ["RMSE", "MAE", "MAX_ABS", "signed_error", "minimum_positions",
                    "relative_minimum_energies", "basin_topology", "PHI_PSI_coupling",
                    "barrier_topology", "interpolation_stability", "outlier_cells"],
        "authoritative_CHI_recheck_required": True,
        "integrated_MD_validation_required_before_completion": True},
    "prohibitions": ["new_1D_C4_C5", "modify_C1_C2_C3", "change_QM_protocol_midcampaign",
                     "fabricate_unconverged_cells", "label_interpolation_as_QM", "tune_on_TEST",
                     "post_result_gate_changes", "training_only_success_claim"],
}

ROOT.mkdir(parents=True, exist_ok=True)
dump(ROOT / "FINAL_2D_WORKFLOW_PREREGISTRATION.json", protocol)
with (ROOT / "STAGE_A_GRID.csv").open("w", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=["cell_id", "phi_degrees", "psi_degrees", "spatial_partition"])
    writer.writeheader()
    for phi in COARSE:
        for psi in COARSE:
            writer.writerow({"cell_id": f"PHI{phi:+04d}_PSI{psi:+04d}", "phi_degrees": phi,
                             "psi_degrees": psi, "spatial_partition": split_for(phi, psi)})
with (ROOT / "FINE_GRID_PARTITION.csv").open("w", newline="") as handle:
    writer = csv.DictWriter(handle, fieldnames=["cell_id", "phi_degrees", "psi_degrees", "spatial_partition", "stage_a_cell"])
    writer.writeheader()
    for phi in FINE:
        for psi in FINE:
            writer.writerow({"cell_id": f"PHI{phi:+04d}_PSI{psi:+04d}", "phi_degrees": phi,
                             "psi_degrees": psi, "spatial_partition": split_for(phi, psi),
                             "stage_a_cell": phi in COARSE and psi in COARSE})
files = sorted(p for p in ROOT.iterdir() if p.is_file() and p.name != "SHA256SUMS")
(ROOT / "SHA256SUMS").write_text("".join(f"{sha(p)}  {p.name}\n" for p in files))
