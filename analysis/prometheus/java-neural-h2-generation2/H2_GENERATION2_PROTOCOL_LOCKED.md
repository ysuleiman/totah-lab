# H2 Generation-2 Nine-Geometry Protocol — Locked Before Execution

## Immutable lineage

Generation-1 remains `H2_MULTI_GEOMETRY_GATE_FAILED`. Its protocol, evidence, thresholds, and checksums are immutable and are neither overwritten nor reinterpreted by this experiment. The compressed-H2 root-cause package remains separate diagnostic evidence.

Generation-2 asks whether the same five-parameter correlated H2 state passes the same molecular-physics gate when the evidenced optimizer and sampling limitations are corrected.

## Scientific target and grid

- Born-Oppenheimer electronic H2 ground-state curve including nuclear repulsion.
- Fixed internuclear separations: `R = 0.8, 1.0, 1.2, 1.4, 1.6, 2.0, 3.0, 4.0, 6.0 bohr`.
- The exact Generation-1 reference energies and atom/nuclear conventions are reused.
- Ansatz: unchanged `h2-covalent-r12-neural-v1`, five parameters only.
- No richer features, backflow, geometry conditioning, LiH, or production nuclear forces.

## Controlled numerical changes

### Optimizer

Replace finite-difference Adam with the qualified deterministic stochastic-reconfiguration/natural-gradient optimizer:

- `O_k = (1/psi) dpsi/dtheta_k`
- `S_kl = <O_k O_l> - <O_k><O_l>`
- `g_k = 2(<E_L O_k> - <E_L><O_k>)`
- solve `(S + 1e-3 I) delta = -0.05 g`
- maximum 120 iterations; minimum 18; patience 8; improvement tolerance `2e-7 Ha`; maximum absolute parameter update `0.10`.

All wavefunction values, coordinate derivatives, Laplacians, local energies, and parameter derivatives for one configuration must come from one shared state-evaluation bundle. No duplicate state evaluation is permitted.

### Sampling

- Training sufficient statistics: deterministic two-center Halton importance sampling, 2,500 configurations, exponent `1.15`, skip `43`.
- Final evaluation: two independent 72,000-configuration sets, exponent `1.15`, skips `1009` and `50021`.
- The reported energy and variance are the arithmetic means of the two independent estimates; their absolute difference is the integration-stability diagnostic.
- The exponent is not changed: the root-cause study supported resolution but did not establish retargeting as the cause.
- Sampling is streamed in deterministic batches of at most 512 configurations. Only sufficient statistics, checkpoints, parameters, and provenance are retained.

## Continuation and controls

- Curve order is increasing R. The converged state at one R initializes the next R.
- Cold seed remains `[1.0, 0.0, 0.0, 0.0, 0.0]`.
- Cold-start controls are run at `R=1.6, 3.0, 6.0` with otherwise identical Gen-2 protocol.
- Independent seed controls at `R=1.4` and `R=4.0` use the cold seed plus `[0.8,0.03,-0.02,0.01,-0.01]` and `[1.2,-0.03,0.02,-0.01,0.01]`.
- Continuation and cold controls are independent evidence. No control changes the warm curve.

## Unchanged acceptance gates

All must pass simultaneously:

- curve RMSE `<= 0.015 Ha`;
- maximum absolute point error `<= 0.025 Ha`;
- equilibrium bond length within `0.08 bohr` of `1.4011 bohr`;
- well-depth error `<= 0.015 Ha` relative to `0.174475931400216 Ha`;
- `|E(R=6)+1| <= 0.010 Ha`;
- smooth adjacent-slope behavior under the Generation-1 `0.5 Ha/bohr` bound;
- correct force-sign behavior around the fitted minimum;
- `|virial ratio(R=1.4)-1| <= 0.08`;
- local-energy variance `<= 0.10 Ha^2` at every geometry;
- convergence at every geometry;
- independent-set energy spread `<= 0.005 Ha` at every geometry;
- nuclear-cusp maximum error `<= 0.015`;
- electron-electron cusp error `<= 0.015`;
- electron-exchange and nuclear-interchange errors `<= 1e-12`;
- maximum 6D gradient error `<= 3e-6`;
- 6D Laplacian error `<= 5e-4`;
- multi-seed maximum energy spread `<= 0.01 Ha`;
- deterministic replay produces identical parameters, energies, iteration counts, and scientific identity;
- zero redundant state evaluations.

No threshold may change after execution begins.

## Performance accounting

For every geometry report Gen-1 versus Gen-2 iterations, objective/statistics passes, wall time, variance, and energy. Gen-2 objective evaluations count complete SR sufficient-statistics passes (`iterations + final evaluation`), not individual configurations. Configuration/state evaluations are reported separately.

Primary work reduction:

`1 - total_Gen2_objective_evaluations / total_Gen1_objective_evaluations`

Wall-clock speedup is reported separately. Acceleration may be claimed only if the complete physics gate passes.

## Evidence preservation and decision

Every completed point is synchronously checkpointed. Failed attempts remain preserved. The final classification is exactly one of:

- `H2_GENERATION2_MULTI_GEOMETRY_GATE_PASSED`
- `H2_GENERATION2_MULTI_GEOMETRY_GATE_FAILED`

On failure, the report identifies the evidenced remaining limitation without relaxing or rerunning the gate. Geometry-conditioned training, general fermionic architectures, production backflow, LiH, and production nuclear-force use remain blocked until this decision is complete.

This is a one-shot Generation-2 validation, not an open-ended H2 tuning cycle. After the frozen decision, perform one capability review and advance to the next approved capability unless the result exposes a fundamental correctness defect in derivatives, symmetry, cusp behavior, numerical integrity, atom/state identity, or evidence persistence. A localized residual energy or variance miss alone does not authorize Gen-2 parameter tuning, threshold changes, or repeated H2 generations.
