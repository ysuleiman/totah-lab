# Prometheus H2 Multi-Geometry Molecular Gate — Locked Protocol

Status: `PREREGISTERED_BEFORE_H2_EXECUTION`

The infinite-well, hydrogen, and helium implementations and evidence remain immutable regressions. H2 is a new Born–Oppenheimer molecular gate; no earlier result or threshold may be changed to accommodate it.

## Scientific definition

The clamped-nuclei Hamiltonian in atomic units is

`H(R) = -1/2(∇1²+∇2²) - 1/r1A - 1/r1B - 1/r2A - 1/r2B + 1/r12 + 1/R`.

The requested state is the `1-Sigma-g-plus` singlet ground state. Nuclear repulsion is included in every reported total energy. The dissociation limit is two neutral hydrogen atoms, `-1.0 Ha`.

Reference values are the explicitly correlated Born–Oppenheimer calculations of Sims and Hagstrom, J. Chem. Phys. 124, 094101 (2006), and the higher-precision equilibrium confirmation of Pachucki, Phys. Rev. A 82, 032509 (2010). The former publishes the curve from 0.4–6.0 bohr and explicitly includes powers of `r12`; the latter reports approximately 10^-15 precision over 0.1–20 bohr.

- NIST-hosted primary paper: https://math.nist.gov/mcsd/savg/papers/h2.pdf
- Pachucki primary preprint: https://arxiv.org/abs/1007.0322

## Frozen curve

| R (bohr) | Reference total energy (Ha) | Role |
|---:|---:|---|
| 0.8 | -1.0200566663601389 | compressed |
| 1.0 | -1.1245397195465791 | compressed shoulder |
| 1.2 | -1.1649352434400281 | approach to well |
| 1.4 | -1.1744757142200755 | equilibrium neighborhood |
| 1.6 | -1.1685833733709263 | post-equilibrium |
| 2.0 | -1.1381329571315035 | stretched bond |
| 3.0 | -1.0573262688692439 | strong static-correlation region |
| 4.0 | -1.0163902529471283 | dissociation approach |
| 6.0 | -1.0008357076542279 | near-separated atoms |

The trusted equilibrium reference is `R_e = 1.4011 bohr`, `E = -1.174475931400216 Ha`.

## State models

Two models are mandatory and use the same deterministic integration points at each R:

1. `H2_UNCORRELATED_MOLECULAR_BASELINE`: a nuclear-interchange- and electron-exchange-symmetric molecular-orbital product without explicit `r12`.
2. `H2_CORRELATED_NEURAL_STATE`: a singlet spatial state with explicit `r12`, electron exchange symmetry, nuclear interchange symmetry, electron–electron cusp, and spherical-average electron–nuclear cusps at both nuclei.

The correlated state must retain covalent/Heitler–London capacity at large R. A single doubly occupied bonding orbital is not an acceptable dissociation model.

## Execution and reuse

The primary continuation path is increasing R:

`0.8 → 1.0 → 1.2 → 1.4 → 1.6 → 2.0 → 3.0 → 4.0 → 6.0 bohr`.

Each converged, validated parameter state is persisted with R, model identity, integration-set identity, optimizer identity, gates, and checksums. It becomes the initial state for the next R but never changes the next calculation's scientific identity.

Cold-start controls are independently run at `R=1.6`, `3.0`, and `6.0 bohr` from a preregistered common seed. Report iteration count, objective evaluations, and wall time for warm and cold execution. Continuation succeeds as a performance mechanism only if final energies remain scientifically equivalent while aggregate objective evaluations or wall time decreases by at least 20%.

No independently optimized point may be recomputed when an identical validated state already exists. Shared deterministic samples and geometry invariants are mandatory-reuse nodes; large derivative state is cache-if-beneficial.

## Locked gates

All gates must pass simultaneously:

- Curve RMSE versus the nine frozen references: `<= 0.015 Ha`.
- Curve maximum absolute error: `<= 0.025 Ha`.
- Equilibrium location: `|R_e(predicted)-1.4011| <= 0.08 bohr` using a smooth local fit that does not train the wavefunction.
- Well depth relative to `-1 Ha`: absolute error `<= 0.015 Ha`.
- At R=6, total energy within `0.010 Ha` of `-1 Ha`, with no ionic-collapse branch.
- No discontinuity: adjacent secant slopes must be finite; the fitted curve and `dE/dR` must be continuous with no jump exceeding `0.05 Ha/bohr` beyond numerical uncertainty.
- Correct force sign on each side of the minimum.
- Electron–nuclear spherical-average cusp error at each nucleus/electron representative: `<= 0.015`.
- Electron–electron cusp error: `<= 0.015`.
- Electron-exchange amplitude error: `<= 1e-12`.
- Nuclear-interchange amplitude error: `<= 1e-12`.
- Maximum independent six-dimensional Cartesian gradient-component error: `<= 3e-6`.
- Full six-dimensional Laplacian error: `<= 5e-4`.
- Virial-ratio error at R=1.4: `<= 0.08`.
- Local-energy variance at R=1.4: `<= 0.10 Ha^2`; variance must remain finite at every R.
- Deterministic replay parameter difference: `<= 1e-14`.
- Three-seed final-energy spread at R=1.4 and R=4.0: `<= 0.01 Ha`.
- Integration-size energy stability at R=1.4, R=3.0, and R=6.0: `<= 0.005 Ha`.
- Exactly one shared state evaluation per configuration per objective evaluation.

The geometry-conditioned state `Psi(r1,r2;R;theta)` is a separately reported experimental comparison. It may not replace the per-geometry curve unless it independently passes the same held curve gates. Nuclear forces must be compared against derivatives of the frozen reference curve and finite differences of independently converged energies.

## Hard failure

Failure at stretched geometries cannot be hidden by reporting equilibrium-only performance. If the state cannot describe both the bonded well and separated-atom limit without pathological parameters or symmetry/cusp failure, classify `H2_MULTI_GEOMETRY_GATE_FAILED` and preserve the negative result. Do not relax the curve or dissociation gates.
