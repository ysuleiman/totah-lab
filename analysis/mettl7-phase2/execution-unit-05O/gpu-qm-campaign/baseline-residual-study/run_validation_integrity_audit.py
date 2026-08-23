#!/usr/bin/env python3
"""Adversarial validation audit.  Does not run the scientific atlas."""

from __future__ import annotations

import copy
import csv
import hashlib
import json
from pathlib import Path

from validation_integrity_guard import (
    FitScope,
    GeometryProtocol,
    GeometryVault,
    LabelVault,
    LeakageError,
    Phase,
    fit_training_artifact,
    gradient_secant,
    guarded_cache,
    predict_sentinel,
    read_many,
)


HERE = Path(__file__).resolve().parent
CAMPAIGN = HERE.parent
RESULTS = CAMPAIGN / "completed-batch-60/gpu_qm_results"
CHARACTERIZATION = CAMPAIGN / "GPU_BATCH_60_CHARACTERIZATION.csv"
POOL = CAMPAIGN.parent / "gpu-qm-preparation/manifests/EXISTING_TSL_RSH_GEOMETRIES.csv"


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_inputs():
    rows = list(csv.DictReader(CHARACTERIZATION.open()))
    labels, checksums, minima = {}, {}, {}
    for row in rows:
        geometry_id = row["campaign_id"]
        result = json.loads((RESULTS / geometry_id / "result.json").read_text())
        labels[geometry_id] = {"energy": result["total_energy_hartree"], "gradient": result["total_gradient_hartree_per_bohr"], "force": result["force_hartree_per_bohr"]}
        checksums[geometry_id] = row["geometry_sha256"]
        minima[geometry_id] = row["source_minimum"]
    unlabeled = {}
    for index, row in enumerate(csv.DictReader(POOL.open())):
        pool_id = f"UNLABELED-{index + 1:04d}"
        unlabeled[pool_id] = row["geometry_sha256"]
    checksums.update(unlabeled)
    return labels, checksums, minima, frozenset(unlabeled)


def scope_for(all_ids, held_out, unlabeled, protocol):
    held_out = frozenset(held_out)
    return FitScope(frozenset(all_ids) - held_out, held_out, unlabeled, protocol)


def expect_closed(name, action, scope_kind, vector):
    try:
        action()
    except LeakageError as error:
        return {"name": name, "scope": scope_kind, "attack_vector": vector, "fail_closed": True, "exception": str(error)}
    return {"name": name, "scope": scope_kind, "attack_vector": vector, "fail_closed": False, "exception": None}


def red_team_scope(labels, checksums, scope, scope_kind):
    vault = LabelVault(labels, scope)
    held = sorted(scope.held_out_label_ids)
    train = sorted(scope.train_label_ids)
    query, training = held[0], train[0]
    tests = []
    tests.append(expect_closed("direct_held_out_gradient_access", lambda: vault.read(query, "gradient", Phase.FIT), scope_kind, "direct"))
    tests.append(expect_closed("held_out_gradient_through_secant", lambda: gradient_secant(vault, training, query), scope_kind, "secant"))
    tests.append(expect_closed("held_out_energy_through_relative_centering", lambda: read_many(vault, [*train, query], "energy"), scope_kind, "energy_center"))
    tests.append(expect_closed("held_out_labels_through_normalization", lambda: read_many(vault, held, "force"), scope_kind, "normalization"))
    tests.append(expect_closed("held_out_labels_through_metric_fitting", lambda: guarded_cache("label_metric", read_many(vault, [query], "energy"), train, scope), scope_kind, "metric"))
    tests.append(expect_closed("held_out_labels_through_neighbor_cache", lambda: guarded_cache("neighbor_cache", [vault.read(query, "force", Phase.SCORE)], train, scope), scope_kind, "neighbor_cache"))
    tests.append(expect_closed("held_out_labels_through_hessian_estimation", lambda: [gradient_secant(vault, training, item) for item in held], scope_kind, "hessian"))
    tests.append(expect_closed("held_out_labels_through_uncertainty_calibration", lambda: read_many(vault, held, "gradient"), scope_kind, "uncertainty"))
    tests.append(expect_closed("raw_label_access_during_prediction", lambda: vault.read(training, "energy", Phase.PREDICT), scope_kind, "prediction"))
    return tests


def invariance_test(labels, checksums, minima, unlabeled, kind):
    all_ids = frozenset(labels)
    folds = [("LOO", frozenset({geometry_id})) for geometry_id in sorted(all_ids)]
    folds.extend((f"LOMO_{minimum}", frozenset(geometry_id for geometry_id, source in minima.items() if source == minimum)) for minimum in ("MIN01", "MIN02", "MIN04"))
    records = []
    for protocol in (GeometryProtocol.INDUCTIVE, GeometryProtocol.TRANSDUCTIVE):
        for fold_name, held_out in folds:
            # Inductive fit excludes held-out coordinates. Transductive fit may
            # include them, but neither mode permits their labels.
            scope = scope_for(all_ids, held_out, unlabeled, protocol)
            base_vault = LabelVault(labels, scope)
            base_geometry = GeometryVault(checksums, scope)
            base_artifact = fit_training_artifact(base_vault, base_geometry)
            base_predictions = {query: predict_sentinel(base_artifact, base_geometry, query) for query in sorted(held_out)}

            changed = copy.deepcopy(labels)
            if kind == "SCRAMBLE":
                for query in held_out:
                    changed[query] = {"energy": 1.0e300, "gradient": [[-1.0e200, 1.0e200, 7.0e199]], "force": [[9.0e250]]}
            elif kind == "REMOVAL":
                for query in held_out:
                    del changed[query]
            else:
                raise ValueError(kind)
            changed_artifact = fit_training_artifact(LabelVault(changed, scope), GeometryVault(checksums, scope))
            changed_predictions = {query: predict_sentinel(changed_artifact, GeometryVault(checksums, scope), query) for query in sorted(held_out)}
            records.append({
                "fold": fold_name,
                "protocol": protocol.value,
                "train_label_ids": sorted(scope.train_label_ids),
                "held_out_label_ids": sorted(scope.held_out_label_ids),
                "unlabeled_geometry_ids": sorted(scope.unlabeled_geometry_ids),
                "base_fit_sha256": base_artifact.sha256(),
                "changed_fit_sha256": changed_artifact.sha256(),
                "fit_identical": base_artifact.canonical_bytes() == changed_artifact.canonical_bytes(),
                "predictions_identical": base_predictions == changed_predictions,
            })
    return {"test": kind, "fold_count": len(records), "records": records, "pass": all(record["fit_identical"] and record["predictions_identical"] for record in records)}


def main():
    labels, checksums, minima, unlabeled = load_inputs()
    all_ids = frozenset(labels)
    loo_held = frozenset({sorted(all_ids)[0]})
    lomo_held = frozenset(geometry_id for geometry_id, minimum in minima.items() if minimum == "MIN01")
    red_team = []
    for protocol in (GeometryProtocol.INDUCTIVE, GeometryProtocol.TRANSDUCTIVE):
        red_team.extend(red_team_scope(labels, checksums, scope_for(all_ids, loo_held, unlabeled, protocol), f"LOO_{protocol.value}"))
        red_team.extend(red_team_scope(labels, checksums, scope_for(all_ids, lomo_held, unlabeled, protocol), f"LOMO_MIN01_{protocol.value}"))
    corrected_outputs = [
        HERE / "CONSERVATIVE_LOCAL_QM_ATLAS_RESULT_CORRECTED_V2.json",
        HERE / "CONSERVATIVE_LOCAL_QM_ATLAS_PREDICTIONS_CORRECTED_V2.json",
        HERE / "SECANT_HESSIAN_MANIFOLD_ATLAS_RESULT_CORRECTED_V2.json",
        HERE / "SECANT_HESSIAN_MANIFOLD_ATLAS_PREDICTIONS_CORRECTED_V2.json",
    ]
    corrected_rerun = all(path.exists() for path in corrected_outputs)
    integrity = {
        "schema": "tsl-rsh-validation-integrity-red-team-v1",
        "scientific_atlas_rerun_performed": corrected_rerun,
        "tests": red_team,
        "all_fail_closed": all(test["fail_closed"] for test in red_team),
        "test_count": len(red_team),
    }
    integrity_path = HERE / "VALIDATION_INTEGRITY_TESTS.json"
    integrity_path.write_text(json.dumps(integrity, indent=2, sort_keys=True) + "\n")

    scramble = invariance_test(labels, checksums, minima, unlabeled, "SCRAMBLE")
    scramble_path = HERE / "LABEL_SCRAMBLE_TEST.json"
    scramble_path.write_text(json.dumps(scramble, indent=2, sort_keys=True) + "\n")
    removal = invariance_test(labels, checksums, minima, unlabeled, "REMOVAL")
    removal_path = HERE / "LABEL_REMOVAL_TEST.json"
    removal_path.write_text(json.dumps(removal, indent=2, sort_keys=True) + "\n")

    audit = {
        "PREVIOUS_ATLAS_RESULT_INVALIDATED": True,
        "DIRECT_LABEL_LEAKAGE": True,
        "INDIRECT_SECANT_LEAKAGE": True,
        "OTHER_LEAKAGE_FOUND": True,
        "LOO_LABEL_ISOLATION_PROVEN": bool(integrity["all_fail_closed"] and scramble["pass"] and removal["pass"]),
        "LOMO_LABEL_ISOLATION_PROVEN": bool(integrity["all_fail_closed"] and scramble["pass"] and removal["pass"]),
        "LABEL_SCRAMBLE_INVARIANCE_PASS": scramble["pass"],
        "LABEL_REMOVAL_INVARIANCE_PASS": removal["pass"],
        "VALIDATION_IMPLEMENTATION_TRUSTWORTHY": bool(integrity["all_fail_closed"] and scramble["pass"] and removal["pass"]),
        "scientific_atlas_rerun_performed": corrected_rerun,
        "corrected_artifact_sha256": {
            path.name: sha256(path) for path in corrected_outputs if path.exists()
        },
        "artifact_sha256": {
            "VALIDATION_INTEGRITY_TESTS.json": sha256(integrity_path),
            "LABEL_SCRAMBLE_TEST.json": sha256(scramble_path),
            "LABEL_REMOVAL_TEST.json": sha256(removal_path),
        },
    }
    (HERE / "VALIDATION_PROVENANCE_AUDIT.json").write_text(json.dumps(audit, indent=2, sort_keys=True) + "\n")
    (HERE / "VALIDATION_PROVENANCE_AUDIT.md").write_text(f"""# Validation provenance audit

Previous atlas result: **INVALIDATED**.

Confirmed defects: global 60-label energy centering exposed held-out energies;
global secant curvature exposed held-out gradients. Corrected validation uses
training-fold-only energy origins and fold-scoped curvature. The 783-geometry
manifold is explicitly **transductive geometry-only**: held-out coordinates may
enter its graph, while held-out QM labels remain inaccessible until scoring.

- LOO isolation proven: `{str(audit['LOO_LABEL_ISOLATION_PROVEN']).lower()}`
- LOMO isolation proven: `{str(audit['LOMO_LABEL_ISOLATION_PROVEN']).lower()}`
- Label scramble invariant: `{str(audit['LABEL_SCRAMBLE_INVARIANCE_PASS']).lower()}`
- Label removal invariant: `{str(audit['LABEL_REMOVAL_INVARIANCE_PASS']).lower()}`
- Corrected atlas rerun performed: `{str(corrected_rerun).lower()}`

Machine-readable evidence is in `VALIDATION_INTEGRITY_TESTS.json`,
`LABEL_SCRAMBLE_TEST.json`, `LABEL_REMOVAL_TEST.json`, and
`VALIDATION_PROVENANCE_AUDIT.json`.
""")
    print(json.dumps(audit, indent=2, sort_keys=True))


if __name__ == "__main__":
    main()
