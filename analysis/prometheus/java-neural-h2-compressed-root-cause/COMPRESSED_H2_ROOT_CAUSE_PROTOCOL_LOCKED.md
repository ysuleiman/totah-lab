# Compressed-H2 Root-Cause Protocol — Locked Before Execution

The frozen `H2_MULTI_GEOMETRY_GATE_FAILED` result and its artifacts are immutable. This diagnostic study covers exactly `R = 0.8, 1.0, 1.2 bohr`; it cannot change the original classification, checkpoints, thresholds, or protocol. No LiH or geometry-conditioned training is authorized.

## Frozen observations and gates

- All three compressed points exhausted the 120-iteration optimizer ceiling.
- Frozen variance: `R=1.0: 0.1262303254 Ha^2`; `R=1.2: 0.1141997095 Ha^2`.
- Original H2 gates remain unchanged, including energy error `<=0.025 Ha`, variance `<=0.10 Ha^2`, symmetry/cusp/derivative gates, and deterministic reproducibility.
- A diagnostic change is material only at `>=0.002 Ha` in energy or `>=0.01 Ha^2` in variance and may not materially worsen the other quantity.

## Common independent evaluation

Every frozen candidate is evaluated without further fitting on two independent deterministic 72,000-configuration Halton sets (`skip=1009` and `skip=50021`). Both use the same fixed-R Hamiltonian and shared-evaluation local-energy estimator. Report each set, their mean, and their spread. Training-set results never substitute for these estimates.

Each configuration is evaluated by the neural state exactly once per valid parameter state; its amplitude, parameter log-derivatives, coordinate gradient, and 6D Laplacian are reused. The numerical-intermediate policy is mandatory-reuse for state bundles, cache-if-beneficial for bounded reusable objects, and recompute-if-cheaper for large transient arrays.

## Arm 0 — Frozen baseline

`BASELINE_A` uses the exact archived five-parameter checkpoint for each R. It is not retrained. All intervention arms begin from that same R-specific checkpoint, and no result may overwrite it.

## Arm 1 — Optimization only: stochastic reconfiguration

Question: is the compressed failure caused by the Euclidean finite-difference Adam optimization geometry?

- `OPTIMIZATION_A`: archived finite-difference Adam result.
- `OPTIMIZATION_B_SR`: identical five-parameter ansatz and identical 2,500-point exponent-1.15 training set (`skip=43`). Replace only the optimizer with deterministic stochastic reconfiguration/natural gradient.
- The logarithmic derivatives are `O_k = (1/psi) dpsi/dtheta_k`.
- `S_kl = <O_k O_l> - <O_k><O_l>` and `g_k = 2(<E_L O_k> - <E_L><O_k>)`, with normalized `weight*psi^2` measures.
- Solve `(S + lambda I) delta = -eta g` with partial-pivot Gaussian elimination.
- Frozen settings: `eta=0.05`, `lambda=1e-3`, maximum 120 iterations, minimum 18 iterations, patience 8, improvement tolerance `2e-7 Ha`, maximum absolute update per parameter `0.10`.
- No adaptive damping, line search, post-result tuning, or optimizer combination.

Optimization is supported only if SR improves both independent evaluation sets materially, retains every original physics gate, and its covariance solve is finite and reproducible.

## Arm 2 — Sampling only: importance distribution and resolution

Question: is the frozen estimate or compressed-region coverage inadequate?

No fitting occurs in the primary sampling audit:

- `SAMPLING_A`: archived checkpoint, exponent `1.15`, 18,000 configurations.
- `SAMPLING_B_SIZE`: archived checkpoint, exponent `1.15`, 72,000 configurations.
- `SAMPLING_B_COMPRESSED`: archived checkpoint, exponent `1.60`, 72,000 configurations.

A secondary training-sampling A/B is permitted only with the frozen five-parameter ansatz and the original finite-difference Adam settings:

- `TRAINING_SAMPLE_A`: archived 2,500-point exponent-1.15 result.
- `TRAINING_SAMPLE_B`: 10,000-point exponent-1.60 set (`skip=43`), maximum 120 iterations; all other optimizer settings unchanged.

Sampling is supported only if independent large-set estimates or candidates trained under improved coverage produce consistent material improvement on both common evaluation sets. Improvement confined to the training objective is failure.

## Arm 3 — Ansatz capacity only: stronger correlation/electron-nuclear features

Question: is the five-parameter cusp-safe representation too restrictive?

- `ANSATZ_A`: archived five-parameter state.
- `ANSATZ_B_FEATURES`: add exactly three output weights multiplying three fixed, exchange-symmetric, nuclear-interchange-symmetric, cusp-safe hidden invariants. They add electron-electron/electron-nuclear correlation flexibility only at second order, so the explicit electron-nuclear and electron-electron cusp factors are unchanged. The new weights initialize to zero, reproducing A exactly.
- `ANSATZ_B_BACKFLOW_FEATURE`: independently add one fixed symmetric backflow-informed electron-pair/electron-nuclear invariant and one output weight initialized to zero. This is tested separately from `B_FEATURES`; it is not combined with it and is not claimed to be a general coordinate-backflow transformation.
- Both B arms use the same baseline 2,500-point exponent-1.15 training set and original finite-difference Adam settings. No sampling or optimizer intervention is mixed into this arm.

Ansatz capacity is supported only if a B arm materially lowers independently evaluated energy and variance, retains all cusp/symmetry/6D derivative gates, and does not rely on divergent or boundary-pinned weights.

## One-class-at-a-time invariant

No arm may combine SR, altered sampling, or added ansatz features. No intervention parameter may be changed after results are observed. A combined intervention is outside this study regardless of individual-arm outcome.

## Root-cause classification

End with exactly one primary classification:

- `OPTIMIZATION_LIMITATION_SUPPORTED`
- `SAMPLING_LIMITATION_SUPPORTED`
- `ANSATZ_CAPACITY_LIMITATION_SUPPORTED`
- `MULTIFACTOR_LIMITATION_SUPPORTED`
- `ROOT_CAUSE_UNRESOLVED`

Multiple independent A/B arms passing implies `MULTIFACTOR_LIMITATION_SUPPORTED`; it does not authorize a combined model. No thresholds are relaxed. No H2 production promotion, geometry-conditioned state, LiH, or downstream scale-up is authorized.
