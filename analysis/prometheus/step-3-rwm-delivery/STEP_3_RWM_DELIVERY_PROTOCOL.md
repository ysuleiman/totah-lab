# Frozen Step 3 delivery execution with qualified RWM

This execution implements the user-authorized delivery decision. It uses the
qualified direct-|Psi|2 electron-by-electron random-walk Metropolis sampler at
spacing 8 and measures the frozen H2O Step 3 physics. MALA is not involved.

Frozen inputs remain the Step 3 EQ, COMPRESSED, and STRETCHED geometries,
optimizer-corrected wavefunction parameters, reference energies/forces,
Hamiltonian, analytic differential-SWCT estimator, units, atom order, and all
scientific gates. No optimization or parameter update occurs.

Sampler per geometry: 8 walkers, 200 warmup sweeps, 128 retained states per
walker, 8 sweeps between retained states, initial scale 0.20 bohr, target
warmup acceptance 0.50, adaptation every 20 warmup sweeps, seed 20260815. The
scale is frozen during measurement.

Energy uncertainty is `sqrt(local-energy variance / autocorrelation-adjusted
ESS)`. Force-component uncertainty is the standard error of eight independent
walker-level force estimates. Sampling and observable wall times are recorded
separately.

The unchanged Step 3 gates are applied. Failure is a delivered scientific
result and does not authorize sampler, state, SR, or SWCT changes.
