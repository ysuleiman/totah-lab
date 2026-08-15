# Exact Finite-Objective Differentiation — Controlled Follow-up

Status: `LOCKED_BEFORE_IMPLEMENTATION_AND_EXECUTION`

## Frozen predecessor

The rejected Controlled Experiment 1 and every artifact under
`analysis/prometheus/java-neural-derivative-aware-pes/` remain immutable. Its
classification is `DERIVATIVE_AWARE_OBJECTIVE_CORRECTNESS_DEFECT`. This unit is
a new scientific identity and must not patch, reinterpret, or overwrite the
covariance-gradient implementation that failed its pre-iteration audit.

## Scientific question

Can Prometheus differentiate the exact finite deterministic quadrature
objective used by the derivative-aware H2 diagnostic, including the parameter
dependence of the local-energy Laplacian, with sufficient correctness and cost
to permit one controlled training run?

This remains a `REFERENCE_ASSISTED_DIAGNOSTIC`. It cannot create production
ab-initio evidence.

## Frozen data and objective

Reuse the predecessor's diagnostic data, deterministic quadrature, state
architecture, starting coefficients, units, signs, and scientific scales:

- radii: `[0.8, 1.0, 1.2, 1.4, 1.6, 2.0, 3.0, 4.0, 6.0]` bohr;
- 2,500 deterministic importance points per geometry, exponent `1.15`, skip
  `43`, maximum emitted batch `512`;
- energy scale `epsilon_E=0.015 Ha`;
- local secant at `R0=1.0 bohr`, `delta=0.05 bohr`;
- force target `0.3621964426997232 Ha/bohr` and scale
  `epsilon_F=0.030 Ha/bohr`;
- `F=-dE/dR`;
- unchanged frozen 20-parameter geometry-conditioned H2 state.

For each geometry, the finite normalized quadrature energy is

`E(theta) = sum_s w_s psi_s(theta)^2 E_L,s(theta) /
            sum_s w_s psi_s(theta)^2`,

where

`E_L,s(theta) = -1/2 [nabla^2 psi_s(theta)]/psi_s(theta) + V_s`.

The exact derivative must include derivatives of the quadrature weights,
wavefunction, and local energy:

`dE_L/dtheta = -1/2 [(d nabla^2 psi/dtheta) psi
                      - (nabla^2 psi)(dpsi/dtheta)]/psi^2`.

The loss is exactly the predecessor's dimensionless finite objective:

`L = (1/9) sum_R [(E_R-E_ref,R)/epsilon_E]^2
     + [(F_diag-F_ref)/epsilon_F]^2`,

`F_diag = -[E_1.05-E_0.95]/0.10`.

No covariance identity may substitute for the derivative of this finite
objective. The SR metric may retain the ordinary log-derivative covariance,
but the optimizer RHS must be the exact derivative above.

## Isolated Java implementation

Implement a new Java-only mixed parameter/spatial automatic-differentiation
path. It must propagate, in one state graph:

- scalar value;
- six Cartesian first derivatives;
- six diagonal Cartesian second derivatives;
- parameter derivatives of all three quantities.

Shared primal state evaluations feed every derivative. The rejected optimizer
is not edited or called. No Python, external ML framework, numerical-QM backend,
new wavefunction feature, or public API change is authorized.

## Mandatory pre-iteration gates

Training is forbidden until all gates pass on the frozen starting vector:

1. **State equivalence:** value and coordinate Laplacian agree with the frozen
   state evaluator to absolute tolerance `1e-11` on at least 24 deterministic
   configurations spanning R=0.8, 1.0, 1.4, and 3.0 bohr.
2. **Primitive AD audit:** every implemented arithmetic/unary operation passes
   independent centered finite differences for parameter tangents and spatial
   first/diagonal-second derivatives.
3. **Objective audit:** centered parameter finite differences with step
   `2e-6` agree with the analytic/AD loss gradient with maximum component error
   `<=3e-5` and RMS error `<=1e-5` on a deterministic 96-point-per-geometry
   fixture. Finite differences read objective values only.
4. **Force convention:** `F=-dE/dR`, Ha/bohr.
5. **Integrity:** all values finite, atom/electron order unchanged, and no
   redundant state-graph evaluation within a sample.

Any failure before iteration 1 classifies
`EXACT_FINITE_OBJECTIVE_CORRECTNESS_DEFECT`; training must not start.

## Cost feasibility gate

After correctness passes, benchmark one exact 2,500-point diagnostic
objective-and-gradient pass without training. Training is computationally
prohibitive if either:

- projected wall time for the locked primary run, exact replay, and two fixed
  perturbation runs at 121 passes each exceeds 30 minutes, using the measured
  pass time without an optimistic speed factor; or
- peak observed Java heap exceeds 1 GiB.

If prohibitive, classify `EXACT_FINITE_OBJECTIVE_COMPUTATIONALLY_PROHIBITIVE`,
do not train, and return to a separately preregistered statistically qualified
stochastic-objective design. Do not silently fall back to covariance gradients.

## Training authorization and unchanged downstream gates

Only if correctness and cost gates pass may the single predecessor training
protocol run: learning rate `0.05`, SR diagonal regularization `1e-3`, maximum
120 iterations, minimum 18, patience 8, improvement tolerance
`1.3333333333333333e-5`, maximum coefficient update `0.10`, one exact replay,
and two fixed historical perturbation starts. The diagnostic-gradient RHS is
rescaled to the simultaneously computed energy-gradient norm exactly as in the
locked predecessor; both gradients must now be exact finite-objective
derivatives.

All predecessor correctness, R=1.0 derivative, energy/PES preservation,
physics, reproducibility, and held-out R=1.4/R=3.0 gates remain unchanged.
Holdouts remain sealed until a candidate is frozen. No adjustment is permitted
after unsealing.

## Instrumentation and persistence

Record objective/state/local-energy evaluations, mixed-AD node evaluations,
sample count, SR iterations, wall time, maximum batch, peak observed heap,
software version, git commit, protocol checksum, and artifact checksums.
Preserve failures as evidence.

## Classifications

- `EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_SUPPORTED`
- `EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_PARTIALLY_SUPPORTED`
- `EXACT_FINITE_OBJECTIVE_DIFFERENTIATION_NOT_SUPPORTED`
- `EXACT_FINITE_OBJECTIVE_CORRECTNESS_DEFECT`
- `EXACT_FINITE_OBJECTIVE_COMPUTATIONALLY_PROHIBITIVE`

Thresholds and classifications cannot change after this lock.

## Hard boundaries

No patch to the frozen failed implementation; no stochastic-gradient fallback
inside this unit; no new H2 ansatz, backflow, sampling change, LiH, larger
system, force-estimator reopening, Python, MD, or production promotion.
