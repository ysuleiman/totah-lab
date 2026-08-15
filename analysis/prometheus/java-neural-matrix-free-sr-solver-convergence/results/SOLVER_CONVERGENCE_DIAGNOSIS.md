# Matrix-Free SR Solver-Convergence Diagnosis

Classification: `BLOCK_PRECONDITIONED_MATRIX_FREE_SR_QUALIFIED`

The E4 negative result remains unchanged. The covariance operator, H2 state, damping, target, and gates were not altered.

- damped minimum eigenvalue: 0.0010000000
- damped maximum eigenvalue: 38.159659
- spectral condition number: 38159.659
- inherited E4 operator/scaling gates: true

## Solver results

- NONE / BASELINE_RECURSIVE_PCG: iterations=37, true relative residual=3.00e-12, residual gap=1.44e-16, max update error=7.78e-13, A-conjugacy loss=0.999, pass=false
- NONE / PCG_TRUE_RESIDUAL: iterations=49, true relative residual=7.40e-12, residual gap=3.34e-17, max update error=7.86e-13, A-conjugacy loss=1.00, pass=false
- NONE / PCG_TRUE_RESIDUAL_COMPENSATED: iterations=49, true relative residual=5.58e-13, residual gap=2.64e-17, max update error=7.78e-13, A-conjugacy loss=0.997, pass=true
- DIAGONAL / BASELINE_RECURSIVE_PCG: iterations=53, true relative residual=7.23e-12, residual gap=1.58e-16, max update error=8.18e-13, A-conjugacy loss=0.999, pass=false
- DIAGONAL / PCG_TRUE_RESIDUAL: iterations=71, true relative residual=3.51e-12, residual gap=2.90e-17, max update error=7.90e-13, A-conjugacy loss=0.999, pass=false
- DIAGONAL / PCG_TRUE_RESIDUAL_COMPENSATED: iterations=71, true relative residual=1.82e-12, residual gap=2.46e-17, max update error=8.23e-13, A-conjugacy loss=0.997, pass=false
- BLOCK_BY_WAVEFUNCTION_COMPONENT / BASELINE_RECURSIVE_PCG: iterations=47, true relative residual=4.18e-13, residual gap=2.33e-16, max update error=8.16e-13, A-conjugacy loss=0.972, pass=true
- BLOCK_BY_WAVEFUNCTION_COMPONENT / PCG_TRUE_RESIDUAL: iterations=52, true relative residual=8.86e-13, residual gap=1.63e-17, max update error=8.11e-13, A-conjugacy loss=0.984, pass=true
- BLOCK_BY_WAVEFUNCTION_COMPONENT / PCG_TRUE_RESIDUAL_COMPENSATED: iterations=52, true relative residual=7.96e-12, residual gap=2.14e-17, max update error=8.13e-13, A-conjugacy loss=0.996, pass=false

## Supported diagnoses

- `FLOATING_POINT_REDUCTION_LIMIT:NONE`
- `KRYLOV_CONJUGACY_LOSS:NONE`
- `KRYLOV_CONJUGACY_LOSS:DIAGONAL`
- `KRYLOV_CONJUGACY_LOSS:BLOCK_BY_WAVEFUNCTION_COMPONENT`

No scientific model or target changed.
