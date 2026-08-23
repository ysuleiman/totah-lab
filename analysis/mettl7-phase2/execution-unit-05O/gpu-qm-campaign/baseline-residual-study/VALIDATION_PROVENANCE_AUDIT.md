# Validation provenance audit

Previous atlas result: **INVALIDATED**.

Confirmed defects: global 60-label energy centering exposed held-out energies;
global secant curvature exposed held-out gradients. Corrected validation uses
training-fold-only energy origins and fold-scoped curvature. The 783-geometry
manifold is explicitly **transductive geometry-only**: held-out coordinates may
enter its graph, while held-out QM labels remain inaccessible until scoring.

- LOO isolation proven: `true`
- LOMO isolation proven: `true`
- Label scramble invariant: `true`
- Label removal invariant: `true`
- Corrected atlas rerun performed: `true`

Machine-readable evidence is in `VALIDATION_INTEGRITY_TESTS.json`,
`LABEL_SCRAMBLE_TEST.json`, `LABEL_REMOVAL_TEST.json`, and
`VALIDATION_PROVENANCE_AUDIT.json`.
