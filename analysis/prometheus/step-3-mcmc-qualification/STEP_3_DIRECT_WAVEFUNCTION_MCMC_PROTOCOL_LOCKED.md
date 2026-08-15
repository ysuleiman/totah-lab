# Step 3 direct-|Psi|^2 molecular MCMC qualification — locked

## Frozen upstream state

The three H2O geometries, general Slater–Jastrow representation, Coulomb
Hamiltonian, BLOCK-preconditioned matrix-free SR optimizer, analytic
differential-SWCT force estimator, reference data, scientific accuracy gates,
atom order, units, and evidence lifecycle from Step 3 remain unchanged. This
unit changes only the electron-configuration sampling mechanism. The prior
Halton importance-sampling failure remains immutable negative evidence.

## Intervention

Both samplers target `pi(R) proportional to |Psi(R)|^2` and update one electron
at a time. Retained configurations carry unit statistical weight.

1. `RANDOM_WALK_METROPOLIS` is the correctness oracle. Its symmetric Gaussian
   proposal uses the ordinary `|Psi'|^2 / |Psi|^2` acceptance ratio.
2. `METROPOLIS_ADJUSTED_LANGEVIN` is the production candidate. With
   `g=grad(log|Psi|)`, it proposes `r'=r+dt*g+sqrt(dt)*xi` and includes the exact
   forward/reverse Gaussian Hastings ratio.

Multiple independent walkers are bounded in memory. Proposal scale adaptation
is permitted during warmup only, using a deterministic multiplicative update;
the final scale is frozen for measurement. The measurement phase performs no
adaptation.

## Locked qualification configurations

- Analytic H fixture: 8 walkers, 400 warmup sweeps, 500 retained samples per
  walker, 2 sweeps between retained states. The exact `1s |Psi|^2` radial mean
  is 1.5 bohr. Random-walk initial scale 0.8 bohr and target acceptance 0.50;
  MALA initial `dt=0.3 bohr^2` and target acceptance 0.55. Adapt every 20
  warmup sweeps. Seed 8128.
- H2O sampler qualification at the frozen EQ geometry: 8 walkers, 200 warmup
  sweeps, 128 retained samples per walker, 2 sweeps between retained states,
  adaptation every 20 warmup sweeps. Random-walk initial scale 0.20 bohr,
  target 0.50; MALA initial `dt=0.03 bohr^2`, target 0.55. Seed 20260815.
- If and only if MALA qualifies, the same frozen MALA configuration is applied
  once to EQ, COMPRESSED, and STRETCHED for the Step 3 rerun. The wavefunction
  parameters are the already frozen optimizer-corrected parameters for each
  geometry; they are not retrained or tuned in this sampler unit.

## Sampler qualification gates

- Random-walk hydrogen radial mean within 0.15 bohr of 1.5 bohr.
- MALA hydrogen radial mean agrees with the random-walk oracle within 0.15
  bohr and independently lies within 0.15 bohr of 1.5 bohr.
- Exact deterministic seeded replay of retained configurations, acceptance
  counts, frozen step, and all non-timing diagnostics.
- Measurement acceptance in `[0.30, 0.75]` for both H2O kernels.
- H2O autocorrelation-adjusted normalized ESS `>=0.20`.
- Maximum retained-state sticking fraction `<=0.25`.
- Between-walker split-chain diagnostic `Rhat<=1.20`.
- Every local energy finite; top 1% of samples contributes `<=0.50` of local-
  energy variance and top 5% contributes `<=0.80`.
- Fixed bounded storage of exactly 1,024 retained configurations; peak heap
  below 512 MiB; no Python.

These are statistical-health gates, not H2O physics gates. Failure stops before
the Step 3 physics rerun. Passing them permits exactly one unchanged Step 3
energy/force assessment. A statistically healthy sampler followed by failed
energy/force gates is evidence about the frozen wavefunction/model, not
authorization for further sampler changes.

## Classification

Exactly one primary classification is allowed:

- `DIRECT_WAVEFUNCTION_MCMC_QUALIFIED_STEP3_RERUN_COMPLETE`
- `RANDOM_WALK_METROPOLIS_QUALIFICATION_FAILED`
- `MALA_QUALIFICATION_FAILED`
- `DIRECT_MCMC_CORRECTNESS_DEFECT`

