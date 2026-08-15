# Step 1 General Molecular Representation — Locked Protocol

## Scope

Step 1 only. This protocol generalizes molecular identity, Coulomb physics, fermionic state evaluation, streamed SR observations, and reuse identity. It does not implement nuclear vector forces, LiH validation, production-QM qualification, a new optimizer, SWCT changes, or research-backlog work.

## Representation

- Immutable, unit-explicit molecular types describe ordered nuclei, atomic number, Cartesian position, molecular charge, electron count, alpha/beta sectors, and multiplicity.
- Consistency requires `Ne = sum(ZA) - Q`, `Nalpha + Nbeta = Ne`, `Nalpha - Nbeta = multiplicity - 1`, and parity compatibility.
- Internal molecular quantum coordinates use bohr and energies use hartree.
- Supported first implementation limit: 16 electrons and 32 nuclei. This is an explicit resource boundary, not a two-electron model assumption.

## Hamiltonian

Use the nonrelativistic Born–Oppenheimer Coulomb Hamiltonian with independently exposed kinetic, electron–nuclear, electron–electron, nuclear–nuclear, and total local-energy components. Coincident charged particles are rejected as singular fixtures; no hidden clipping changes the Hamiltonian.

## Fermionic state

Use separate alpha and beta Slater determinants multiplied by a positive, permutation-symmetric electron–electron Jastrow factor. Atom-centered cusp-compatible exponential orbitals provide geometry conditioning and electron–nuclear features. Same-spin exchange changes determinant sign; opposite-spin correlation remains symmetric. The initial architecture is real-valued.

One shared state evaluation constructs distances, determinants, log amplitude/sign, electron gradients/Laplacian, parameter log derivatives, and local-energy components. Energy and streamed SR consume this bundle; no independent state recomputation pipeline is authorized.

## Numerical stack

- Bounded deterministic batches only.
- Existing BLOCK-preconditioned matrix-free SR only.
- Step 0 evidence identity, exact-bit artifacts, synchronous registration, and immediate reuse remain mandatory.
- Existing H/He/H2 scientific classifications remain frozen. General-path fixtures compare representation/physics invariants without reopening those decisions.

## Acceptance

All gates in the user-authorized Step 1 instruction must pass. Any failure produces a precise blocking classification. Stop after the Step 1 decision and commit.
