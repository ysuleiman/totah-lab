# Trusted TSL-RSH evidence report

## Decision

The homogeneous A100 GPU-60 partition is the only current `REPRODUCIBLE_COMPLETE`
QM force-label set admitted to future fitting. All 60 nested checksum manifests,
geometry/result hashes, 56-atom ordering, C22H30O3S composition, neutral-singlet
state, finite 56x3 component arrays, exact component sum, exact force=-gradient,
and SCF convergence were reverified without recomputing QM.

Historical CPU, Hessian, RESP/ESP, vdW, Amber, Delta, MACE, and atlas artifacts
remain visible in the manifest but are not silently treated as equivalent GPU
labels. Missing derivative decomposition, model state, or leakage produces an
explicit limitation, quarantine, or invalidation.

## Frozen physical domain

Relative energy is defined independently within MIN01, MIN02, and MIN04 using
the lowest verified GPU-60 total energy in that minimum. The observed maximum
relative energy and force of non-force-cloud torsional/optimization/other
geometries define the empirical bound-like envelope. Force-cloud points beyond
that energy envelope are stress-only; points inside the energy envelope but
beyond its force envelope are stability guards. This uses observed physical
provenance and support, not an arbitrary kcal/mol cutoff.

The original deterministic family/minimum-stratified validation selections are
preserved for non-stress points. Stress-only points are disjoint. Cross-partition
Kabsch RMSD screening found no pair at or below 0.01 A.

## Counts

- TRUSTED: 60
- TRUSTED_WITH_LIMITATION: 9
- QUARANTINED: 2
- INVALIDATED: 2
- UNKNOWN: 0
- Trusted QM labels/geometries: 60 / 60
- CORE_EQUILIBRIUM: 7
- EXTENDED_BOUND_DOMAIN: 35
- STABILITY_GUARD: 8
- STRESS_TEST_ONLY: 10
- TRAIN / VALIDATION / STRESS_TEST: 39 / 11 / 10

## Gaps and prohibitions

The GPU-60 set does not directly label protein-like, SAM-approach, explicit
thiol-H-bond, or intermolecular repulsive geometries. Those are coverage gaps,
not inferred labels. The historical CPU MIN01 no-D3 gradient and historical CPU
force-cloud component decomposition remain missing; the best-Amber 26-vector
model state remains absent. Metric-only model studies are not reusable model
states. No threshold was changed, no QM was run, and no model was fitted.
