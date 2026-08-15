# Controlled Experiment 1 — Derivative-Aware H2 PES Objective

Status: `LOCKED_BEFORE_IMPLEMENTATION_AND_EXECUTION_AFTER_AMENDMENT`

## Scientific question

Can one derivative-aware geometry-conditioned objective improve the compressed
H2 PES slope without degrading the already-good energy PES?

This is a `REFERENCE_ASSISTED_DIAGNOSTIC` architecture-capability experiment,
not an attempt to make H2 pass. Its candidate is ineligible to generate
production ab-initio evidence even if every gate passes.
The nuclear-force estimator study, frozen H2 models/evidence, wavefunction form,
reference table, and all previous classifications remain immutable.

## Compared models

### A — frozen energy-only baseline

Reuse, without recomputation or modification, the accepted historical result in
`analysis/prometheus/java-neural-h2-geometry-conditioned/`:

- representation: `h2-shared-cubic-chebyshev-cusp-safe-v1`;
- 20 shared cubic-Chebyshev coefficients;
- frozen scientific identity:
  `5ec28eac758bdc8310d3ad0b556bb140349fe0585fc3586633e6776d60d56db4`;
- frozen classification: `GEOMETRY_CONDITIONED_H2_FORCE_FAILED`;
- frozen energy RMSE: `0.004598452792880898 Ha`;
- frozen R=1.0 model-FD force: `0.3229217237653570 Ha/bohr`;
- frozen R=1.0 trusted force: `0.3621964426997232 Ha/bohr`;
- frozen R=1.0 slope error: `0.0392747189343662 Ha/bohr`.

The historical baseline is not rerun. Its immutable CSV/JSON values are the A
arm. This prevents an unnecessary stochastic computation and preserves its
scientific identity.

### B — single derivative-aware intervention

Use the same state architecture, frozen baseline coefficients, energy-training
geometries, deterministic sampling, SR covariance, and SR update settings, and
downstream validation logic. Add only the objective/RHS contribution defined
below. No new features, layers, backflow, estimator, or per-geometry parameters.

## Mathematical objective

Let the nine separately normalized sampled variational energies be `E_R(theta)`
at

`R = [0.8, 1.0, 1.2, 1.4, 1.6, 2.0, 3.0, 4.0, 6.0] bohr`.

The diagnostic energy block is

`L_E(theta) = (1/9) sum_R [(E_R(theta)-E_ref(R))/epsilon_E]^2`,

where the pre-existing energy RMSE gate fixes `epsilon_E=0.015 Ha`.

Define the diagnostic local secant using common deterministic configurations at
`R0=1.0 bohr` and locked Taylor displacement `delta_diag=0.05 bohr`:

`F_theta_diag(R0) = -[E_(R0+delta_diag)(theta) - E_(R0-delta_diag)(theta)]/(2 delta_diag)`.

The trusted target remains the frozen reference-PES secant used by the existing
force gate:

`F_ref(R0) = -[E_ref(1.2)-E_ref(0.8)]/0.4`

`            = 0.3621964426997232 Ha/bohr`.

The selected symmetric force-derived local-energy diagnostic is

`L_diag(theta) = L_E(theta) + [(F_theta_diag(R0)-F_ref(R0))/epsilon_F]^2`,


`epsilon_F = 0.030 Ha/bohr`, the pre-existing trusted-force gate. The energy and
force blocks therefore have equal dimensionless weight and use pre-existing
scientific scales. There is no tunable raw force multiplier.

For the optimizer RHS, use the analytic parameter gradient of each normalized
sampled energy already computed by Prometheus and the symmetric derivative

`dF_theta_diag/dtheta = -[dE_+/dtheta-dE_-/dtheta]/(2 delta_diag)`,

so

`dL_diag/dtheta = (2/9) sum_R [(E_R-E_ref)/epsilon_E^2] dE_R/dtheta`

` + 2[(F_theta_diag-F_ref)/epsilon_F^2] dF_theta_diag/dtheta`.

The SR metric remains the equal-geometry mean energy covariance. The derivative
term changes only the objective value and RHS; it does not create a post-hoc
force-dependent metric or alter regularization.

Candidate selection is justified in `CONTROLLED_EXPERIMENT_1_AMENDMENT.md`.
Direct force-loss differentiation would require a mixed parameter/nuclear
derivative; the selected Taylor/secant form uses two ordinary energy-gradient
evaluations and adds no derivative order.

## Training data and sampling

- Energy training geometries: all nine radii listed above.
- Energy sampling per geometry/pass: 2,500 deterministic two-center Halton
  configurations, exponent `1.15`, skip `43`, batch maximum `512`.
- Derivative development geometry: R=1.0 only.
- Derivative displaced evaluations: R=0.95 and 1.05 on the same 2,500
  deterministic R=1.0 configurations; no independently resampled clouds.
- Derivative holdouts: R=1.4 and R=3.0. Their force values never enter training,
  stopping, regularization, or parameter selection.
- Diagnostic energy labels and validation energies: the same frozen nine-point
  external reference table. Their use makes this arm reference-assisted and
  permanently ineligible as production ab-initio evidence.
- No new QM/reference calculation is generated.

## Optimization

- Start: the exact frozen model-A parameter vector. This isolates local
  representational capacity rather than repeating global architecture training.
- SR learning rate `0.05`; diagonal regularization `1e-3`.
- Maximum 120 iterations; minimum 18; patience 8.
- Dimensionless improvement tolerance `1.3333333333333333e-5`, obtained from the
  prior `2e-7 Ha` tolerance divided by `epsilon_E`; maximum coefficient update
  `0.10`.
- Stopping monitors the locked dimensionless `L_diag`.
- Because a dimensionless objective has arbitrary global scale, rescale its RHS
  each pass to the L2 norm of the simultaneously available original energy-only
  RHS; this preserves the diagnostic direction and the existing SR step-size
  scale without changing the relative energy/force weighting. If either norm is
  below `1e-14`, do not divide; a zero diagnostic RHS is stationary.
- One primary run and one exact replay establish deterministic reproducibility.
- Two fixed perturbations (`+/-` the historical coefficient perturbation rule)
  around the frozen baseline are run for seed-spread assessment; they cannot
  select the model.
- No extension, restart, lambda change, or alternative objective after results.

## Downstream evaluation

Reuse the existing immutable evaluation logic:

- two independent 72,000-configuration energy sets per radius, skips 1009 and
  50021, exponent 1.15, batch maximum 512;
- local PES slopes/model-FD forces at R=1.0, 1.4, and 3.0 using common samples
  and validation delta 0.001 bohr;
- native force-estimator results are reported for continuity but are not the
  training target and cannot rescue a failed PES-slope gate;
- exact existing cusp, exchange, nuclear-interchange, 6D gradient, Laplacian,
  virial, variance, seed-spread, smoothness, equilibrium, well-depth, and
  dissociation audits;
- a diagnostic dense curve at R=0.8 through 2.0 in 0.1-bohr increments and 2.0
  through 6.0 in 0.25-bohr increments, using fixed deterministic samples, checks
  for non-finite values, additional stationary points, and adjacent-slope jumps.
  It is diagnostic only and cannot alter locked acceptance thresholds.

## Locked gates

### Correctness gate

Failure of any item classifies `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`:

- selected Taylor-objective gradient passes centered finite-difference audit with
  maximum component error `<=3e-5` on a deterministic small fixture;
- force sign and units are `F=-dE/dR`, Ha/bohr;
- deterministic primary/replay parameters and metrics match exactly;
- zero redundant state evaluations are reported;
- all results are finite and atom/electron ordering is unchanged.

### Primary R=1.0 derivative gate

Both must pass:

- absolute model-FD/trusted-force error `<=0.020 Ha/bohr`;
- error reduction relative to frozen A `>=50%`.

### Energy/PES preservation gates

All existing absolute gates remain:

- RMSE `<=0.015 Ha`; maximum error `<=0.025 Ha`;
- equilibrium error `<=0.08 bohr`; well-depth error `<=0.015 Ha`;
- dissociation error `<=0.010 Ha`;
- per-point variance `<=0.10 Ha^2`, independent spread `<=0.005 Ha`;
- smooth curve, correct signs around the minimum, R=1.4 virial error `<=0.08`.

Additional non-degradation gates relative to frozen A:

- RMSE `<=0.006598452792880898 Ha` (A plus 0.002 Ha);
- maximum error `<=0.008874592483881891 Ha` (A plus 0.003 Ha).

### Physics/reproducibility gates

- nuclear/electron cusp errors `<=0.015`;
- exchange and nuclear-interchange errors `<=1e-12`;
- 6D gradient error `<=3e-6`; Laplacian error `<=5e-4`;
- three-seed energy spread `<=0.01 Ha`;
- optimizer converges under the locked stopping rule.

### Held-out derivative gates

At R=1.4 and R=3.0, model-FD/trusted-force absolute error must be `<=0.03
Ha/bohr` and may not worsen by more than `0.010 Ha/bohr` relative to frozen A.
No model change occurs after these holdouts are evaluated.

## Cost instrumentation

Record separately for optimization and validation:

- dimensionless objective/statistics passes and SR iterations;
- state evaluations;
- local-energy evaluations;
- derivative-force evaluations and displaced state evaluations;
- deterministic sample count;
- wall time in nanoseconds;
- maximum emitted batch size;
- peak observed used Java heap (sampled at controlled pass boundaries).

Peak observed heap is an instrumentation lower bound, not operating-system RSS.
The bounded streaming architecture and batch maximum 512 are invariant.

## Classification

- `DERIVATIVE_AWARE_PES_OBJECTIVE_SUPPORTED`: correctness, primary derivative,
  energy/PES, physics/reproducibility, and holdout gates all pass.
- `DERIVATIVE_AWARE_PES_OBJECTIVE_PARTIALLY_SUPPORTED`: correctness and primary
  derivative gates pass and the absolute energy/PES physics gates pass, but a
  secondary non-degradation, convergence, or derivative-holdout gate fails.
- `DERIVATIVE_AWARE_PES_OBJECTIVE_NOT_SUPPORTED`: primary derivative gate fails
  or the absolute energy/PES/physics model is degraded.
- `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`: any correctness gate fails.

Classification rules and thresholds cannot change after protocol locking.

## Hard boundaries

No analytic differential SWCT, direct force-loss arm, alternative diagnostic
displacement, matrix-free SR, structured preconditioner,
backflow, new neural feature/layer, LiH, larger molecule, production promotion,
or broad rewrite is authorized. A negative result is frozen and returned to the
architecture review.
