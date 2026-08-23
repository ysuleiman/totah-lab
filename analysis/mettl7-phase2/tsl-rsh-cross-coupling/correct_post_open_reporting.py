#!/usr/bin/env python3
"""Correct the one-shot energy report without reopening any QM label.

The original one-shot evaluator accidentally applied the baseline energy offset
inside the already-referenced additive predictor. Forces were unaffected. This
repair consumes only the prior immutable additive metrics/residual report, the
new scalar coefficient, and the already-persisted comparison; it never reads a
geometry or QM result artifact.
"""

from __future__ import annotations

import copy
import csv
import json
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
OLD = HERE.parent / "tsl-rsh-representability"


def rms(values):
    values = np.asarray(values, dtype=float)
    return float(np.sqrt(np.mean(values * values)))


def translated(old: dict[str, object]) -> dict[str, object]:
    return {
        "count": old["count"],
        "energy_rms_kcal_mol": old["energy_rms_kcal_mol"],
        "relative_energy_rms_kcal_mol": old["relative_energy_rms_kcal_mol"],
        "global_force_component_rms_kcal_mol_angstrom": old["global_force_component_rms_kcal_mol_angstrom"],
        "sulfur_local_force_component_rms_kcal_mol_angstrom": old["sulfur_local_force_component_rms_kcal_mol_angstrom"],
        "s_h_projected_force_error_kcal_mol_angstrom": old["s_h_projected_force_rms_kcal_mol_angstrom"],
        "c_s_projected_force_error_kcal_mol_angstrom": old["c_s_projected_force_rms_kcal_mol_angstrom"],
        "torsional_projected_force_error_kcal_mol_angstrom": old["sulfur_torsional_projected_force_rms_kcal_mol_angstrom"],
    }


def repair(name: str, old_name: str, partition: str, coefficient: float) -> dict[str, object]:
    current = json.loads((HERE / name).read_text())
    old = json.loads((OLD / old_name).read_text())
    current["additive"] = translated(old)
    current["per_minimum"] = {minimum: {**models, "additive": translated(old["per_minimum"][minimum])} for minimum, models in current["per_minimum"].items()}
    rows = [row for row in csv.DictReader((OLD / "RESIDUAL_DIAGNOSTICS.csv").open(newline="")) if row["partition"] == partition]
    cross = copy.deepcopy(current["additive"])
    cross["energy_rms_kcal_mol"] = rms([float(row["energy_error"]) + coefficient for row in rows])
    current["cross_term"] = cross
    for minimum, models in current["per_minimum"].items():
        subset = [row for row in rows if row["minimum"] == minimum]
        candidate = copy.deepcopy(models["additive"])
        candidate["energy_rms_kcal_mol"] = rms([float(row["energy_error"]) + coefficient for row in subset])
        models["cross_term"] = candidate
    current["post_open_reporting_correction"] = {
        "applied": True,
        "defect": "baseline training energy offset was passed into the already-referenced additive predictor, double-shifting reported additive/cross energies; forces were unaffected",
        "qm_labels_reopened": False,
        "replacement_source": "prior immutable additive metrics and residual diagnostics plus frozen scalar cross coefficient",
    }
    (HERE / name).write_text(json.dumps(current, indent=2, sort_keys=True) + "\n")
    return current


def main() -> None:
    coefficient = float(json.loads((HERE / "FIT_ARTIFACT/fit-artifact.json").read_text())["finalParameterVector"][0])
    validation = repair("VALIDATION_COMPARISON.json", "VALIDATION_METRICS.json", "VALIDATION", coefficient)
    stress = repair("STRESS_TEST_COMPARISON.json", "STRESS_TEST_METRICS.json", "STRESS_TEST", coefficient)
    decision = json.loads((HERE / "REPRESENTABILITY_DECISION.json").read_text())
    report = f"""# TSL-RSH cross-coupling representability report

This controlled experiment used the immutable 39/11/10 GPU-60 split and the
frozen additive result at commit `6d139cfb94130a660b8916fca280caa846883af2`.
Discovery and model construction read only the 39 training labels. Fourteen
translation/rotation-invariant coordinates were defined, including periodic
sin/cos semantics for all torsions. Generalized residual forces were obtained
from `Q = pinv(J.T) DeltaF`, not Cartesian component correlations. Synthetic
finite-difference and force-recovery tests passed before interpretation.

No candidate pair survived the predetermined one-standard-error CV selection.
The best mean candidate, CHI-ETA2, improved mean training CV loss by only 3.8%,
less than its sampling uncertainty. Consequently the frozen minimal model has
zero physical cross terms and one energy-reference nuisance coefficient. This
is an explicit data-limited negative result, not a fitted claim that coupling is
absent.

| Validation metric | Frozen baseline | Frozen additive | Cross candidate |
|---|---:|---:|---:|
| Energy RMS, kcal/mol | {validation['baseline']['energy_rms_kcal_mol']:.6f} | {validation['additive']['energy_rms_kcal_mol']:.6f} | {validation['cross_term']['energy_rms_kcal_mol']:.6f} |
| Relative-energy RMS, kcal/mol | {validation['baseline']['relative_energy_rms_kcal_mol']:.6f} | {validation['additive']['relative_energy_rms_kcal_mol']:.6f} | {validation['cross_term']['relative_energy_rms_kcal_mol']:.6f} |
| Global force-component RMS, kcal/mol/A | {validation['baseline']['global_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['additive']['global_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['cross_term']['global_force_component_rms_kcal_mol_angstrom']:.6f} |
| Sulfur-local force-component RMS, kcal/mol/A | {validation['baseline']['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['additive']['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} | {validation['cross_term']['sulfur_local_force_component_rms_kcal_mol_angstrom']:.6f} |

The cross candidate cannot repair the frozen additive model's negative local
C-S-H harmonic curvature because no evidence-supported physical cross term was
selected. `PHYSICAL_STABILITY_PASS` is therefore false. The model-class decision
is **{decision['representability_decision']}**: the 39-point training set does
not resolve a defensible low-order pairwise extension, and arbitrary extra terms
are prohibited.

Validation was opened once after artifact/receipt verification. A post-open
reporting defect double-shifted additive energies in the first generated JSON;
it was corrected without reopening QM labels from the prior immutable additive
residual artifact and the frozen scalar cross coefficient. Forces were never
affected. Stress results remain separate. No QM, neural model, threshold change,
or validation-driven model choice occurred.
"""
    (HERE / "CROSS_TERM_REPRESENTABILITY_REPORT.md").write_text(report)
    print(json.dumps({"validation": validation["cross_term"], "stress": stress["cross_term"], "qm_labels_reopened": False}, indent=2))


if __name__ == "__main__":
    main()
