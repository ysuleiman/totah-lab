# Step 2 General Arbitrary-Nucleus 3D Vector Forces

## Classification

`STEP_2_GENERAL_VECTOR_FORCES_COMPLETE`

Step 2 is closed. Step 3 was not started.

## Implemented path

- General Filippi–Umrigar `r^-4` normalized space-warp weights and Cartesian divergences.
- One fused general Slater–Jastrow directional graph carrying all total-SWCT and bare-nuclear directions.
- Complete Coulomb-potential directional derivatives.
- Canonically ordered per-nucleus `(Fx,Fy,Fz)` results in hartree/bohr.
- Exact `force = -gradient` conversion in the Java quantum backend.
- State traversal, local-energy evaluation, directional-pass, wall-time, peak-heap, and component instrumentation.
- Independent central finite-difference SWCT oracle retained as nonproduction validation code.
- Step 0/1 immutable request identity, exact-bit JSON, checksum validation, synchronous registry, and immediate reuse remain active.

## Correctness evidence

- All six H2 Cartesian components agree with the independent central-difference oracle within `2e-7 Ha/bohr`.
- Analytic execution uses one fused traversal/pass per sample; the two-sided six-component oracle uses 12 traversals per sample.
- Total translational force is zero within `2e-12 Ha/bohr` on the symmetric isolated fixture.
- Axial H2 transverse components and torque are zero within `2e-12 Ha/bohr` where fixture symmetry requires.
- Equivalent-nucleus permutation swaps the canonical force rows without changing physics.
- Rigid rotation rotates the force vector consistently.
- Deterministic replay is bitwise identical.
- The single-nucleus hydrogen fixture returns exactly zero translational force.
- The Java backend returns all canonical vector components, exact negative gradients, explicit units and exact-bit artifacts.
- A repeated identity-complete request invokes the backend exactly once and then returns reusable registered evidence.
- Bounded streaming is preserved; no sample population and no dense third-order tensor are retained.
- Step 0 scalar SWCT and Step 1 H/He/H2/general-molecular regressions remain in the full suite.
- Full Prometheus verification: `290` tests, `0` failures, `0` errors, `0` skipped (`BUILD SUCCESS`).

## Interpretation and limits

This closes the arbitrary-nucleus Cartesian force infrastructure gate. It does not qualify force accuracy for a new molecule, start LiH, or establish production molecular-QM accuracy. Those require the separately authorized first real multi-nuclear validation after Step 2.
