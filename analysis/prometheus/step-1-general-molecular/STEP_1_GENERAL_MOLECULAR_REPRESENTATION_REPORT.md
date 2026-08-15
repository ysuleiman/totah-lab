# Step 1 General Molecular Representation Report

## Decision

`STEP_1_GENERAL_MOLECULAR_REPRESENTATION_COMPLETE`

Step 1 is closed. Step 2 was not started.

## Acceptance-gate evidence

- General immutable molecular representation: implemented with unit-explicit positions, ordered nuclei, atomic numbers, molecular charge, electron count, alpha/beta populations, and multiplicity.
- Consistency: `Ne = sum(Z) - Q`, spin population, multiplicity, parity, ordering, and explicit resource limits are enforced.
- General Coulomb Hamiltonian: implemented without H2 axial assumptions and exposes all four physical energy components plus total.
- General fermionic state: separate arbitrary-size alpha/beta Slater determinants, atom-centered geometry features, and symmetric electron-pair Jastrow correlation.
- Shared evaluation: log magnitude, sign, value, electron gradients/Laplacian, parameter derivatives, distances, and local energy arise from one forward graph.
- H/He/H2 migration: H returns the exact `-0.5 Ha` local energy; He and H2 Coulomb components agree with the frozen legacy Hamiltonian formulas on identical coordinates.
- Antisymmetry: explicit same-spin exchange reverses the amplitude sign while preserving squared amplitude and local energy.
- Physics: cusp fixtures, singularity behavior, equivalent-nucleus permutation, translation invariance, and rotation invariance pass.
- Bounded numerics: the general state feeds the existing BLOCK-preconditioned matrix-free SR path from a replayable bounded source. No sample population is retained.
- Reuse: general molecule/Hamiltonian/wavefunction/optimizer identity is embedded in the immutable request. An identical second request invokes the backend zero times and reuses accepted evidence.
- Exact evidence: the Step 0 exact-bit artifact and synchronous registry regressions remain green.
- No forbidden work: no general nuclear vector forces, LiH, production qualification, optimizer family, SWCT change, HVP/PHL work, or research backlog was started.

## Regressions and reproducibility

The targeted Step 1 suite is deterministic and includes molecular, antisymmetry, invariance, SR, identity, persistence, and exact-bit tests. Final full Maven result: **285 tests, 0 failures, 0 errors, 0 skipped**. The commit identifier is recorded in repository history; the final artifact content is recorded in the checksum manifest.

## Limitations

This gate validates representation and architecture. It does not establish chemical accuracy for new molecules. The first orbital/Jastrow construction is deliberately compact and has explicit 16-electron/32-nucleus limits. Larger and chemically predictive states require later separately authorized qualification.
