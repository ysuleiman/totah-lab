# Prometheus Java Neural-QM Hydrogen Gate

Classification: `HYDROGEN_COULOMB_GATE_PASSED`

Prometheus solved the non-relativistic, clamped-nucleus hydrogen atom entirely in owned Java code. The Hamiltonian is

`H = -1/2 nabla^2 - 1/r`

in atomic units. The trial state is the physics-informed neural form

`psi(r) = exp(-r) [1 + r^2 N(r)]`.

The exponential envelope enforces the exact hydrogenic cusp and asymptotic class. `N(r)` is a Prometheus-owned four-unit tanh network trained variationally. Three-dimensional gradients and the Laplacian are computed analytically from one shared forward evaluation and checked independently by Cartesian finite differences away from the singular origin. Normalization and energies use midpoint quadrature on the unbounded radial domain through `r=t/(1-t)` with the full `4 pi r^2 dr` volume element.

## Frozen result

- Energy: `-0.4999799810124552 Ha`
- Exact energy: `-0.5000000000000000 Ha`
- Absolute energy error: `2.001898754483422e-05 Ha`
- Normalized wavefunction RMSE: `0.001046182202673917`
- Normalized overlap: `0.9999994527513977`
- Cusp logarithmic derivative: `-0.9999997469561788 bohr^-1`
- Asymptotic decay exponent: `0.9980413608085070 bohr^-1`
- Maximum Cartesian gradient-component audit error: `2.293397710628753e-08`
- Cartesian Laplacian audit error: `6.871578106526499e-08`
- Schrodinger residual RMS: `0.02320664275315388`

All preregistered gates passed. This result validates the Coulomb singularity treatment, radial-to-Cartesian derivative mapping, three-dimensional Laplacian, unbounded-domain integration, cusp behavior, asymptotic decay, and Java neural variational optimization for a one-electron system. It does not validate electron-electron correlation; helium is the next separate physics gate.

The earlier infinite-square-well implementation and evidence remain frozen independently as a permanent regression benchmark.
