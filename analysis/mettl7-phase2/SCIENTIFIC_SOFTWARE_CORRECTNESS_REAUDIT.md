# Scientific software correctness re-audit

## Scope and immutable inputs

- Branch at audit start: `main`
- HEAD at audit start: `19bed572b04eb73cf90b7265f07262379bdee6e3`
- No new QM was run and the 60 GPU-QM labels were not changed.
- No model was trained and no scientific acceptance threshold was changed.
- Atlas v1 artifacts are preserved and explicitly invalidated in
  `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/ATLAS_RESULTS_INVALIDATION.json`.

## Confirmed defects and corrections

| Severity | Defect | Affected calculations/artifacts | Root cause | Correction and adversarial test | Scientific consequence |
|---|---|---|---|---|---|
| Critical | Held-out energy leakage | Conservative atlas v1 LOO/LOMO results, predictions, report and decision | Energy labels were centered using the minimum over all 60 labels before folds | Each prediction now derives its energy origin from `TRAIN_LABEL_IDS` only. Absurd held-out-energy/gradient mutation leaves the frozen prediction unchanged; legitimate training-gradient mutation changes it. | Every prior atlas conclusion remains invalid. Corrected v2 values were recomputed separately. |
| Critical | Held-out gradient/secant leakage | Secant-Hessian atlas v1 results, predictions, coverage-derived decision and report | Curvature diagnostics/Hessians were globally fitted before validation folds | Global curvature construction was removed. LOO and LOMO Hessian sets are fitted independently from fold training gradients only. Entire held-out-minimum mutation is invariant. | Previous sampling/representation, transfer and support conclusions are withdrawn. |
| High | Validation interpretation was implicit | Both atlas implementations | Held-out coordinates entered geometry scaling/graph construction without declaring transductive validation | Corrected artifacts state `TRANSDUCTIVE_LABEL_FREE_GEOMETRY_METRIC` or `TRANSDUCTIVE_LABEL_FREE_783_GEOMETRY_GRAPH`; held-out labels remain inaccessible until scoring. | Corrected results must not be described as inductive transfer. |
| High | Force execution identity omitted derivative runtime | FermiNet nuclear-force result identity | `NuclearForceConfiguration.identity()` covered the estimator but derivative engine and result-bit-relevant parallelism were independent | `FermiNetForceScientificIdentity` hashes estimator configuration, derivative engine/configuration, parameter, geometry, dataset, checkpoint, root, sampling and model identities. Collision tests vary estimator, engine and parallelism. | Previously emitted estimator-only identities are incomplete provenance. Numerical force arrays were not recomputed. |
| High | Declared checksum strings could appear verified | FermiNet force context/output provenance | Context validation checked state parameters/geometry but accepted arbitrary checkpoint/root strings | Checkpoint files are now SHA-256 verified and deserialized; payload parameter, geometry and root declarations are checked. Output separates declared provenance from cryptographic verification. Independent root-artifact verification remains explicitly false unless performed. | Historical force outputs lacking the verification record must be treated as declared provenance. |
| High | Programming failures could become NaN samples | AC-ZV, AC-ZVZB and derivative force estimators | Broad `RuntimeException` catches treated arbitrary implementation faults as physical nonfinite samples | Expected wavefunction-node/coalescence cases use `FermiNetPhysicalSingularityException`; arbitrary runtime failures propagate and abort. Existing injected-corruption tests and force tests pass. | Silent scientific corruption is prevented; genuine physical singularities remain explicit nonfinite evidence. |
| Medium | Physical validity conflated with finiteness | FermiNet nuclear-force qualification | The only acceptance-like boolean was `completeFiniteVector`; exact distinct-z equality detected planarity | Finite-vector validity remains separate. New diagnostics report net force and charge-center torque without inventing gates. Planarity uses `5e-12 bohr`, five times the frozen geometry decimal precision. Asymmetric O/H tests keep every force/torque term nonzero. | No prior force result is relabeled pass/fail; additional diagnostics are now persisted. |
| Medium | Misnamed paired ESS | Correlated-FD diagnostics | `min(ESS+, ESS-)` was called paired effective sample size although it is only a conservative marginal proxy | API-compatible accessor `conservativeMarginalEffectiveSampleSize()` added; historical accessor deprecated. Hand-computable weights `[1,2]` give `ESS=9/5`. | Stored numeric values do not change; their interpretation is corrected. |
| Medium | Chain SE silently assumed equal usable chain lengths | SWCT/AC force statistics and correlated FD | Nonfinite filtering could make chain lengths unequal while the equal-chain formula was still used | Unequal usable chain lengths now yield an explicit unavailable (`NaN`) chain SE; correlated FD fails closed if total samples are not divisible by chains. Synthetic chain means 2 and 6 reproduce SE 2 exactly. | Existing homogeneous finite datasets are numerically unchanged. |
| Medium | Step-3 repeated sampling identity was ambiguous | `WaterMoleculeStep3Calculation` and downstream provenance | Four SR iterations reused skip 101 without stating whether this was fixed quadrature or VMC resampling | Repository evidence and deterministic replay tests establish this path as the frozen deterministic-quadrature validation objective. Constants and `trainingSampleIdentity()` document and test intentional reuse. | Algorithm is unchanged; the scientific interpretation is now explicit. |
| Low | Matrix-free SR regression test assumed at least one operator pass | `GeneralMolecularMatrixFreeSrOptimizerTest` | Absolute-tolerance convergence can occur before the first operator application and conventionally reports unit relative residual | Test now distinguishes zero-pass absolute convergence from streamed relative convergence. Production solver was not changed. | Independent test defect; no FermiNet or GPU-60 result changed. |
| Medium | Green Maven output obscured qualification skips | Locked/artifact-backed FermiNet qualification tests | JUnit assumptions appeared as an ordinary successful build | `generate_scientific_qualification_report.py` parses source guards and Surefire XML into only `RUN_AND_PASS` or `NOT_RUN_MISSING_REQUIRED_ARTIFACT`. | Eight qualifications ran and passed; two locked campaigns did not run and are not scientific passes. |

## Corrected existing-data results

All values below were recomputed from the immutable 60 stored GPU-QM results.

| Method | Energy RMS (kcal/mol) | Global force RMS (kcal/mol/A) | Sulfur-local force RMS (kcal/mol/A) |
|---|---:|---:|---:|
| GAFF2 | 14.095242238109378 | 14.943684203855238 | 21.472434196268300 |
| Delta V2 2B | 13.839315688133777 | 14.844019676849976 | 21.454008954221056 |
| MACE-OFF24 zero-shot | 6.067682629201743 | 9.042488259746270 | 10.146534039991106 |

Sulfur-local RMS by minimum:

| Method | MIN01 | MIN02 | MIN04 |
|---|---:|---:|---:|
| GAFF2 | 13.899872350358297 | 15.509102516249312 | 30.813269523428225 |
| Delta V2 2B | 13.563350579070196 | 15.181306026685610 | 31.086765174536044 |
| MACE-OFF24 zero-shot | 9.224166553510491 | 9.251992526983251 | 11.754652047076473 |

Corrected transductive atlas comparison:

| Atlas | Validation | Old sulfur-local RMS | Corrected v2 sulfur-local RMS |
|---|---|---:|---:|
| First order | LOO | 24.801684776948420 | 24.801684776948413 |
| First order | LOMO | 26.937069764383020 | 26.937069764383015 |
| Manifold second order | LOO | 23.395073257338407 | 23.395073257338120 |
| Manifold second order | LOMO | 24.598997854926683 | 24.598997854926566 |

The near-identical aggregates do not rehabilitate v1: v1 is invalid because its
prediction construction violated holdout isolation. Corrected v2 is a distinct,
audited rerun. Geometry support and local-descriptor outputs are in the corrected
machine-readable artifacts and are not used here to select a production model.

## Invalidated artifacts

- `CONSERVATIVE_LOCAL_QM_ATLAS_RESULT.json`
- `CONSERVATIVE_LOCAL_QM_ATLAS_PREDICTIONS.json`
- `CONSERVATIVE_LOCAL_QM_ATLAS_DECISION.json`
- `CONSERVATIVE_LOCAL_QM_ATLAS_REPORT.md`
- `SECANT_HESSIAN_MANIFOLD_ATLAS_RESULT.json`
- `SECANT_HESSIAN_MANIFOLD_ATLAS_PREDICTIONS.json`
- `SECANT_HESSIAN_MANIFOLD_ATLAS_DECISION.json`
- `SECANT_HESSIAN_MANIFOLD_ATLAS_REPORT.md`
- `GPU783_MANIFOLD_SUPPORT_COVERAGE.csv`

## Validation and test evidence

- Prometheus final full suite: 449 tests, 447 passed, 0 failed, 2 skipped.
- Atlas adversarial tests: 4 passed.
- Combined relevant verification: 453 tests, 451 passed, 0 failed, 2 skipped.
- Frozen GPU-60 campaign manifest: every entry in `SHA256SUMS` verified successfully before commit.
- Qualification registry: 8 `RUN_AND_PASS`; 2 `NOT_RUN_MISSING_REQUIRED_ARTIFACT`.
- Label-scramble invariance: pass for every LOO/LOMO fold in inductive and transductive guard modes.
- Label-removal invariance: pass for every LOO/LOMO fold in both guard modes.

## Unresolved risks

1. The corrected 783-geometry manifold result is transductive, not an inductive generalization claim.
2. Root parameter identity is checked against the checkpoint declaration and locked known checksum, but generic context verification cannot cryptographically verify a root unless its independent artifact is supplied.
3. Two expensive locked FermiNet campaigns were not activated; see `SCIENTIFIC_QUALIFICATION_STATUS.json`.
4. Atlas geometry scaling and graph topology are high-dimensional design choices. Holdout integrity is established, but representation adequacy is not established by software correctness.
5. No production fitting method was selected and the force-field problem is not declared solved.

## Corrected artifacts

- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/GPU60_BASELINE_RESIDUAL_STUDY.json`
- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/CONSERVATIVE_LOCAL_QM_ATLAS_RESULT_CORRECTED_V2.json`
- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/CONSERVATIVE_LOCAL_QM_ATLAS_PREDICTIONS_CORRECTED_V2.json`
- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/SECANT_HESSIAN_MANIFOLD_ATLAS_RESULT_CORRECTED_V2.json`
- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/SECANT_HESSIAN_MANIFOLD_ATLAS_PREDICTIONS_CORRECTED_V2.json`
- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/GPU783_MANIFOLD_SUPPORT_COVERAGE_CORRECTED_V2.csv`
- `execution-unit-05O/gpu-qm-campaign/baseline-residual-study/VALIDATION_PROVENANCE_AUDIT.md`
- `SCIENTIFIC_QUALIFICATION_STATUS.json`
