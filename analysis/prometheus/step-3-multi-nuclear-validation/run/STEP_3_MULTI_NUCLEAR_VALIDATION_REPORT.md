# Step 3 multi-nuclear H2O validation

## Classification

`STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED`

Dominant blocker: `optimization_convergence`

## Frozen-gate results

- Completed geometries: 0/3
- Energy and force accuracy metrics: not reached
- Exact force=-gradient: not reached
- Accepted-result immediate reuse: not reached
- Failed-attempt persistence: PASS; all three scientific identities are recorded in the synchronous JSONL registry
- Python path: absent

## Execution failures

- EQ: EvidenceExecutionException: Java neural execution failed
- COMPRESSED: EvidenceExecutionException: Java neural execution failed
- STRETCHED: EvidenceExecutionException: Java neural execution failed

## Root-cause isolation

A reduced Java-only diagnostic preserved the frozen numerical settings while separating state evaluation from the first SR solve. All 512 EQ molecular state/local-energy evaluations completed. The first BLOCK-preconditioned matrix-free SR solve then stopped with the exact cause:

`java.lang.IllegalArgumentException: PCG invalid preconditioned residual`

This localizes the first blocking gate to `optimization_convergence`: the frozen BLOCK preconditioner/covariance system produces an invalid preconditioned residual for the ten-electron H2O state before parameter optimization or force validation can complete. It is not evidence of an energy-accuracy or force-accuracy failure because those gates were never reached. It is also not evidence of a basic state-evaluation failure: the complete 512-state diagnostic traversal succeeded.

Step 3 is frozen at this classification. No correction, tuning, Step 4, or new molecule was started.

## Regression status

The complete Prometheus suite passed after freezing this result: **293 tests, 0 failures, 0 errors, 0 skipped**.
