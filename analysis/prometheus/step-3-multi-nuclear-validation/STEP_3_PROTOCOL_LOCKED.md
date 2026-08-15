# Step 3 multi-nuclear validation protocol — locked before execution

## Scope

Validate the frozen Step 1 general molecular path and frozen Step 2 analytic differential-SWCT vector-force path on neutral singlet H2O. No architecture, force mathematics, optimizer-family, sampling, thresholds, or geometry changes are permitted after execution starts.

## Electronic state and ordering

- Canonical nuclei: `0=O, 1=H1, 2=H2`.
- Charge: 0. Multiplicity: 1. Electrons: 10 (`5 alpha, 5 beta`).
- Coordinates: source Cartesian angstrom converted once using `1 angstrom = 1.8897261254578281 bohr`.
- Energy: hartree. Force: hartree/bohr.

## Frozen geometries

| ID | r(OH1), A | r(OH2), A | HOH, deg | Role | H2O-13 source record |
|---|---:|---:|---:|---|---:|
| EQ | 0.95 | 0.95 | 105 | near equilibrium | line 18917 |
| COMPRESSED | 0.85 | 0.95 | 100 | asymmetric compressed/distorted | line 6132 |
| STRETCHED | 0.95 | 1.10 | 115 | asymmetric stretched/distorted | line 19977 |

## Frozen numerical path

- State: `general-slater-jastrow-atom-centered-v1`, cusp initialization unchanged.
- Hamiltonian: nonrelativistic all-electron Born-Oppenheimer Coulomb.
- Optimizer: existing BLOCK-preconditioned matrix-free SR only.
- SR: 4 iterations; learning rate 0.02; damping 0.01; maximum update 0.10; block size 2; maximum PCG iterations 200; relative true-residual tolerance `1e-10`; absolute tolerance `1e-12`.
- Sampling: deterministic streaming multi-center Halton importance source; 512 optimization samples; exponent 4.0; skip 101; batch size 64. Evaluation uses four independent 128-sample blocks at skips 1009, 2017, 3019, and 4027. Center selection is nuclear-charge weighted. The protocol is identical at every geometry.
- Force: frozen general analytic differential-SWCT estimator. Central differences are not used as the trusted force reference.
- Memory: samples stream in bounded batches; no dense third-order tensor.
- Runtime: Java 21 only; zero Python.

## Reference conversion

- Relative energy: H2O-13 `PS_energy` in eV divided by `27.211386245988 eV/hartree` and added to `-76.4390 hartree`.
- Force: H2O-13 `ps_force` in eV/angstrom multiplied by `0.019446903811488874` to obtain hartree/bohr.
- Source archive SHA-256: `b7107a499eb39088a769534ffc1cf59815006f5a82127c53274cd5deea4ac7c4`.

## Gates fixed before execution

- Every geometry completes without numerical/correctness failure.
- Absolute energy error at every geometry <= 0.015 hartree; three-geometry energy RMSE <= 0.010 hartree.
- Force-component RMSE across 27 components <= 0.010 hartree/bohr; maximum absolute force-component error <= 0.025 hartree/bohr.
- Energy standard error per geometry <= 0.005 hartree; force-component standard error <= 0.010 hartree/bohr.
- Exact force/gradient sign, units, and canonical order.
- Translational force norm <= 0.005 hartree/bohr; planar z components <= 0.005 hartree/bohr; EQ hydrogen permutation consistency <= 0.005 hartree/bohr.
- Every SR solve satisfies its frozen independent true-residual gate.
- Bitwise deterministic replay.
- Identical second request is `REUSE_EXISTING` with zero executor calls.
- Peak-heap growth stays below 512 MiB and bounded streaming invariants hold.
- Existing full Prometheus suite remains green.

Any gate failure freezes `STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED` with a dominant blocker. It is not repaired inside Step 3.
