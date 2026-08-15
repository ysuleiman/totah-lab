# Nuclear-Force Estimator Capability Study — Locked Protocol

## Scientific boundary

This is not another H2 wavefunction generation. Freeze exactly:

- the 20 shared coefficients from scientific identity
  `5ec28eac758bdc8310d3ad0b556bb140349fe0585fc3586633e6776d60d56db4`;
- all nine frozen curve energies and the three force geometries;
- Hamiltonian and cusp-safe state mathematics;
- deterministic sampling definitions and sample counts;
- reference forces and all prior tolerances.

No state optimization, parameter movement, new ansatz feature, sampler change,
iteration extension, or threshold relaxation is permitted.

## Phase 0: identity and baseline replay

Load the frozen candidate by scientific identity. Reproduce the three existing
direct-estimator force records bit-for-bit. A mismatch stops the study as
`FROZEN_BASELINE_REPLAY_MISMATCH`.

## Phase 1: estimator correctness fixtures

Before H2 evaluation, each new estimator must pass analytic one-electron and
two-center fixtures for:

- force equals negative energy derivative;
- Hartree/bohr units;
- nuclear interchange antisymmetry and zero transverse force;
- exact accounting of one unique state bundle per configuration where the
  estimator is analytic;
- common-random-number pairing where the estimator is finite-difference;
- coordinate-transform Jacobian against an independent numerical derivative;
- deterministic replay and bounded streaming memory.

## Phase 2: preregistered estimator panel

Evaluate in this order, stopping an implementation that fails its fixtures:

1. `DIRECT_HF_PULAY_BASELINE`: frozen current estimator.
2. `DIRECT_HF_PULAY_PARAMETER_RESPONSE_AUDIT`: retain the frozen parameters,
   measure the nonzero variational-gradient residual, and evaluate whether the
   missing `sum_k dE/dtheta_k * dtheta_k/dR` contribution can explain the
   trusted-PES disagreement. The response must come from a preregistered
   implicit/Lagrangian response equation; it may not be obtained by retraining
   neighboring geometries. If the response equation is underdetermined, report
   that fact rather than regularizing after inspection.

   Locked numerical realization: evaluate the 20-component energy gradient on
   the same deterministic configurations; central-difference that gradient with
   parameter step `1e-4` to form the local energy Hessian and with geometry step
   `1e-3 bohr` to form the mixed derivative. Solve
   `H_theta_theta * dtheta/dR = -d(g_theta)/dR` by unregularized pivoted
   elimination. Report rank and pivot ratio. No diagonal shift, pseudoinverse,
   truncation, or parameter movement is allowed. A rank-deficient or pivot-ratio
   `<1e-10` system is `PARAMETER_RESPONSE_UNDERDETERMINED` and produces no
   corrected-force claim.
3. `BARE_HELLMANN_FEYNMAN`: Qian Eq. 5, retained as a diagnostic rather than a
   preferred estimator because of its documented variance and first-order
   wavefunction error.
4. `CORRELATED_SAMPLING_FINITE_DIFFERENCE`: central energy derivative with identical
   unwarped configurations at `delta=1e-3 bohr`; already available as a frozen
   comparator, not a success criterion by itself.
5. `SPACE_WARP_COORDINATE_TRANSFORMATION`: Qian Eqs. 12--15 and the published
   Filippi-Umrigar space
   warp with electron displacement weights proportional to inverse fourth-power
   electron-nucleus distance, including the exact transformation Jacobian and
   reweighting. No adjustable exponent or damping is allowed.
6. `ASSARAF_CAFFAREL_ZV`: Qian Eq. 11 using only the minimal auxiliary
   function in Eqs. 9--10.
7. `ASSARAF_CAFFAREL_ZVZB`: Qian Eq. 6 using that same fixed minimal auxiliary
   function; no fitted auxiliary function is allowed.
8. `ZVZB_SWCT`: combine only the independently qualified published ZVZB and
   SWCT components; no new adjustable term is introduced.

No hybrid estimator may be invented after results are seen.

## Frozen evaluation

Use R=1.0, 1.4, and 3.0 bohr with the existing 72,000-configuration sets. Report
mean force, Hellmann-Feynman/warp/Pulay/ZV/ZB components as applicable, sample
variance, standard error, effective paired-sample count, wall time, state
evaluations, and peak batch size.

Report raw statistics and, separately, the Qian-style predeclared 3-IQR-clipped
diagnostic. Raw statistics control every acceptance classification.

Compare against:

- the same frozen trusted-reference PES secants;
- the existing common-random-number model finite differences;
- exact H2 force antisymmetry between nuclei;
- zero transverse-force expectation.

For each estimator record force error, variance, CPU time, state evaluations,
and the efficiency diagnostic `force_error^2 * CPU_time`. This diagnostic does
not replace any scientific acceptance gate.

## Gates

An estimator is `CAPABILITY_QUALIFIED` only if all are true:

- maximum trusted-reference error `<=0.03 Ha/bohr`;
- maximum disagreement with the independently implemented frozen energy finite
  difference `<=0.01 Ha/bohr`;
- force sign correct at every geometry;
- nuclear antisymmetry error `<=1e-10 Ha/bohr` and transverse force
  `<=1e-10 Ha/bohr` for the deterministic diatomic construction;
- no unit, sign, Jacobian, derivative, identity, or accounting failure;
- variance is finite and is reported, but no variance-reduction threshold is
  retrofitted after results.

## Classification

Exactly one final primary classification:

- `LITERATURE_FORCE_ESTIMATOR_CAPABILITY_QUALIFIED`
- `VARIANCE_REDUCED_BUT_FORCE_GATE_FAILS`
- `NO_ESTIMATOR_RESOLVES_FROZEN_FORCE_FAILURE`
- `ESTIMATOR_IMPLEMENTATION_CORRECTNESS_DEFECT`
- `FROZEN_BASELINE_REPLAY_MISMATCH`

A failed study does not authorize wavefunction tuning or LiH. A passed study
qualifies an estimator capability only; production molecular forces require a
separate validation.
