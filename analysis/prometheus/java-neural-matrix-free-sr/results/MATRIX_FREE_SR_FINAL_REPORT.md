# Matrix-Free SR Final Report

Classification: `MATRIX_FREE_SR_NOT_EQUIVALENT`

- Dense control construction+solve: 495636875 ns
- Matrix-free initial statistics: 1912214125 ns
- Operator equivalence: true
- Structured preconditioning beneficial: true

- NONE: 37 iterations, 37 operator applications, relative residual 3.12e-12, max update error 7.78e-13, pass=false
- DIAGONAL: 53 iterations, 53 operator applications, relative residual 7.35e-12, max update error 8.18e-13, pass=false
- BLOCK_BY_WAVEFUNCTION_COMPONENT: 47 iterations, 47 operator applications, relative residual 4.28e-13, max update error 8.16e-13, pass=true

No H2 optimization or scientific target changed. Synthetic scaling tests storage/solver behavior only.
