# Prometheus-owned Java neural quantum milestone

Classification: `JAVA_NEURAL_QUANTUM_END_TO_END_VALIDATED`

## Problem

One particle in a one-dimensional infinite square well on `0 <= x <= 1` in
atomic units:

```text
H = -1/2 d2/dx2
psi(0) = psi(1) = 0
E0 = pi^2/2
psi0(x) = sqrt(2) sin(pi x)
```

The trial state follows the Lagaris construction principle:

```text
psi_trial(x) = x(1-x) N(x; theta)
```

The envelope satisfies both boundary conditions exactly. `N` is a Prometheus-
owned pure-Java dense neural network with six tanh hidden units and a linear
output. No external ML or numerical framework is used.

## Execution

- deterministic 101-point development quadrature;
- Rayleigh-quotient variational objective;
- pure-Java central-difference Adam parameter optimization, 350 iterations;
- one shared network pass produces value, first derivative and second derivative;
- reverse propagation produces the wavefunction-value parameter gradient;
- independent 401-point energy/residual evaluation;
- independent 801-point normalized wavefunction comparison;
- independent finite-difference audit of first and second spatial derivatives.

## Result

| Metric | Result |
|---|---:|
| Initial energy | 5.001000050010001 Ha |
| Optimized energy | 4.934865176861422 Ha |
| Exact energy | 4.934802200544679 Ha |
| Absolute energy error | 0.00006297631674279103 Ha |
| Normalized wavefunction RMSE | 0.0007214285091457735 |
| Normalized wavefunction overlap | 0.9999997397704522 |
| Schrodinger residual RMS | 0.04252371051183779 |
| Left/right boundary values | exactly 0 / exactly 0 |
| First derivative audit error | 8.00e-9 |
| Second derivative audit error | 3.02e-8 |

All preregistered implementation gates passed.

## Scope

This validates the owned Java neural-QM machinery on a tiny known eigenproblem:
network evaluation, shared derivatives, immutable parameters, variational energy,
optimization, boundary construction, and comparison with an analytic solution.

It does not establish molecular accuracy, fermionic antisymmetry, many-electron
scaling, VMC, or replacement of PBE-D3(BJ)/def2-SVP.
