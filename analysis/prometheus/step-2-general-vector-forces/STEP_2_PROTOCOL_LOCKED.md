# Step 2 General Arbitrary-Nucleus 3D Vector Forces — Locked Protocol

## Scope

Step 2 only. Step 1 is immutable upstream infrastructure. No LiH, new scientific molecule, production-QM qualification, optimizer change, SWCT theory change, HVP/PHL work, or Step 3 work is authorized.

## Force mathematics

For every ordered nucleus and Cartesian axis, evaluate the Sorella–Capriotti differential space-warp force estimator. Use normalized Filippi–Umrigar weights `omega_A(r)=|r-R_A|^-4 / sum_B |r-R_B|^-4`. A single fused directional-forward-over-electronic-second-order graph carries all total-SWCT and bare-nuclear directions. It returns directional derivatives of the wavefunction, electronic Laplacian, and complete Coulomb potential.

For each component, accumulate the established Hellmann–Feynman/Pulay/Jacobian terms used by the qualified scalar H2 implementation. Central finite differences of the independently transformed finite objective are validation oracles only and are never the production estimator.

## Numerical and evidence invariants

- Canonical nucleus order, hartree/bohr forces, and `force = -gradient`.
- One state traversal per valid configuration carrying all `6*Nn` directional tangents.
- Bounded replayable streaming; no retained sample population and no dense third-order tensor.
- Existing BLOCK-preconditioned matrix-free SR, identity/reuse, exact-bit artifacts, synchronous registration, and zero Python remain unchanged.
- H, He, and H2 are implementation fixtures only.

## Acceptance

All user-authorized Step 2 gates must pass without threshold relaxation. Stop after the checksummed decision and commit.
