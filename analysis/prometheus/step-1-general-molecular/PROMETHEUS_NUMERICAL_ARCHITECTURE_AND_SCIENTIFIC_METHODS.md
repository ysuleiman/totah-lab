# Prometheus Numerical Architecture and Scientific Methods — Step 1 State

## Accepted production architecture

Prometheus owns the complete Java 21 molecular representation and state-evaluation path. Immutable domain objects define ordered nuclei, integer nuclear charge, unit-explicit positions, molecular charge, electron count, alpha/beta populations, and multiplicity. The supported Step 1 boundary is 1–16 electrons and 1–32 nuclei.

The molecular Hamiltonian is the clamped-nucleus, nonrelativistic Born–Oppenheimer Coulomb Hamiltonian in atomic units. Its local energy remains separated into kinetic, electron–nuclear, electron–electron, and nuclear–nuclear terms.

The accepted fermionic representation is

`Psi = det(A_alpha) det(A_beta) exp(J)`.

The determinants enforce antisymmetry independently in each spin sector. Atom-centered exponential orbitals supply nuclear geometry and cusp-compatible radial features. The positive pair Jastrow is symmetric under electron exchange and uses the electron-pair cusp coefficients 1/4 (parallel spin) and 1/2 (opposite spin). This follows the Slater determinant, Jastrow correlation, and Kato cusp lineage; modern neural-VMC determinant/Jastrow architectures such as PauliNet and FermiNet motivate the separation between invariant learned/correlated features and an antisymmetric determinant envelope.

Primary mathematical provenance:

- J. C. Slater, *Phys. Rev.* **34**, 1293 (1929), DOI `10.1103/PhysRev.34.1293`.
- R. Jastrow, *Phys. Rev.* **98**, 1479 (1955), DOI `10.1103/PhysRev.98.1479`.
- T. Kato, *Commun. Pure Appl. Math.* **10**, 151 (1957), DOI `10.1002/cpa.3160100201`.
- J. Hermann, Z. Schätzle, F. Noé, *Nature Chemistry* **12**, 891 (2020), DOI `10.1038/s41557-020-0544-y`.
- D. Pfau et al., *Phys. Rev. Research* **2**, 033429 (2020), DOI `10.1103/PhysRevResearch.2.033429`.

## Shared differentiation graph

One `MolecularStateEvaluation` returns log magnitude, real sign, wavefunction value, all electron gradients, the electron Laplacian, parameter derivatives, reusable pair distances, and optional local-energy components. Coordinate and parameter variables enter the same forward second-order AD graph. Parameter derivatives therefore do not trigger perturbed state reevaluations. Energy and streamed stochastic reconfiguration consume this same bundle.

## Optimization and evidence lifecycle

The optimizer remains the Step 0 `BLOCK_PRECONDITIONED_MATRIX_FREE_SR` family. The general adapter streams bounded, replayable samples through the covariance operator; it does not retain a full sample population. True residuals are independently recomputed. Dense SR is not selectable.

General molecular execution identity adds the full ordered nuclear/electronic molecular hash, Hamiltonian protocol, wavefunction architecture/version, and optimizer protocol to the immutable Step 0 request identity. The existing synchronous checksum-verified JSONL registry still guarantees immediate and restart-safe reuse.

## Complexity

For `Ne` electrons, `Nn` nuclei, largest spin-sector size `Ns`, parameter count `P`, and bounded batch size `B`:

- reusable distance features: time `O(Ne*Nn + Ne^2)`, memory `O(Ne*Nn + Ne^2)` per state;
- determinant elimination: time `O(Ns^3)` scalar operations;
- diagonal second-order forward AD: each scalar carries `O(3Ne + P)` state, so determinant time is `O(Ns^3*(3Ne+P))` and live determinant memory is `O(Ns^2*(3Ne+P))`;
- streamed SR covariance application: `O(B*P^2)` arithmetic for the current outer products, with `O(P^2 + B_state)` bounded live memory and no retained global sample population.

The explicit electron/nuclear limits prevent accidental catastrophic construction. This is a correctness architecture, not yet a large-system performance qualification.

## Validation and negative evidence

Validated together: charge/electron/spin consistency, exact hydrogen local energy, legacy He/H2 Coulomb equivalence, same-spin exchange sign, probability/local-energy exchange invariance, electron–nuclear and electron–electron cusp fixtures, singularity rejection, equivalent-nucleus permutation, translation/rotation invariance, streamed BLOCK SR, identity-complete zero-recomputation reuse, and exact-bit Step 0 evidence regression.

Step 0 evidence and all historical H2/force/optimizer negative results remain unchanged. Step 1 does not claim LiH readiness, nuclear vector forces, chemical accuracy for arbitrary molecules, or production-QM qualification.
