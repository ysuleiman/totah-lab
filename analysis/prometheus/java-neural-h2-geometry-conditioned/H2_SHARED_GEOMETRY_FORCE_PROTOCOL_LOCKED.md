# H2 Shared-Geometry State and Nuclear-Force Protocol — Locked Before Execution

## Immutable predecessors

The following remain frozen and are not tuned or superseded:

- `H2_MULTI_GEOMETRY_GATE_FAILED`
- `H2_GENERATION2_MULTI_GEOMETRY_GATE_FAILED`
- `NO_FUNDAMENTAL_CORRECTNESS_DEFECT_OBSERVED`

This is a new capability experiment, not H2 Generation-3.

## Shared state

- One shared geometry-conditioned model covers `R = 0.8, 1.0, 1.2, 1.4, 1.6, 2.0, 3.0, 4.0, 6.0 bohr`.
- Geometry encoder: cubic Chebyshev features of `x(R)=2*(R-0.8)/(6.0-0.8)-1`, linearly mapped to the five parameters of the unchanged `h2-covalent-r12-neural-v1` state.
- Twenty trainable shared coefficients; no per-geometry parameters.
- Initial constant coefficients generate `[1,0,0,0,0]`; all geometry-dependent coefficients start at zero.
- Exact chain-rule parameter derivatives. Existing cusp, symmetry, and asymptotic construction remains unchanged.

## Global optimization and sampling

- One global SR/natural-gradient optimization over all nine geometries.
- Each geometry contributes its separately normalized and centered SR covariance/gradient; the global update is their equal-weight mean.
- `eta=0.05`, diagonal regularization `1e-3`, maximum 120 iterations, minimum 18, patience 8, improvement tolerance `2e-7 Ha`, maximum coefficient update `0.10`.
- Training per geometry: 2,500 deterministic two-center Halton configurations, exponent 1.15, skip 43.
- Evaluation per geometry: two independent 72,000-configuration sets, exponent 1.15, skips 1009 and 50021.
- Batches contain at most 512 configurations. Only sufficient statistics and immutable evidence are retained.
- No continuation or per-geometry refinement is allowed.

## Curve and physics gates

The shared model must satisfy the substantive Generation-2 physics gates:

- RMSE `<=0.015 Ha`; maximum error `<=0.025 Ha`;
- equilibrium error `<=0.08 bohr`; well-depth error `<=0.015 Ha`; dissociation error `<=0.010 Ha`;
- variance `<=0.10 Ha^2` and independent-set spread `<=0.005 Ha` at every geometry;
- smooth curve and correct force signs around the minimum;
- virial error at R=1.4 `<=0.08`;
- nuclear and electron cusp errors `<=0.015`;
- exchange and nuclear-interchange errors `<=1e-12`;
- 6D gradient error `<=3e-6`; Laplacian error `<=5e-4`;
- three-seed shared-model energy spread `<=0.01 Ha`;
- exact deterministic replay; zero redundant state evaluations.

The global optimizer must report convergence under its locked stopping rule. No post-result iteration extension is allowed.

## Nuclear-force gate

The native Java force estimator is

`F_R = -(<dH/dR> + 2*(<O_R E_L>-<O_R><E_L>))`.

- `dH/dR` is analytic for nuclei at `z=+-R/2`, including `-1/R^2` nuclear-repulsion derivative.
- `O_R` includes explicit R dependence of the cusp-safe state and the shared encoder response.
- Force units are Hartree/bohr; positive force increases R.
- Model finite-difference audit uses common deterministic samples and `delta=1e-3 bohr` at R=1.0, 1.4, and 3.0. Maximum analytic-versus-model-FD error: `<=0.01 Ha/bohr`.
- Trusted-reference force uses the existing reference PES with central secants: `(R,delta)=(1.0,0.2),(1.4,0.2),(3.0,1.0)`. Maximum model-versus-reference-force error: `<=0.03 Ha/bohr`.
- Force estimator variance and Hellmann-Feynman/Pulay components are reported separately.
- Nuclear forces remain ineligible for force-field evidence unless every force gate passes.

## Performance and memory

Compare against frozen Generation-2:

- curve metrics and force quality;
- objective/statistics passes;
- total state evaluations;
- wall time;
- peak emitted batch size and estimated retained sample bytes.

Primary shared-work metric is `1 - shared_global_objective_passes / 1057`. Configuration/state evaluations are reported separately. Faster execution cannot compensate for failed physics.

## One-shot decision

This protocol is checksum-locked before execution. No architecture, threshold, optimizer, sampler, or force setting changes after results are observed. Final classification is exactly one:

- `GEOMETRY_CONDITIONED_H2_FORCE_VALIDATED`
- `SHARED_STATE_PASSES_FORCE_FAILS`
- `SHARED_STATE_FAILS_FORCE_PASSES`
- `GEOMETRY_CONDITIONED_H2_FORCE_FAILED`
- `FUNDAMENTAL_CORRECTNESS_DEFECT`

No LiH, general fermionic scaling, transferable pretraining, or production nuclear-force use begins in this experiment. A failure is preserved and returned for a capability decision; it does not start an open-ended H2 tuning cycle.
