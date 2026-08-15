# Prometheus Geometry-Conditioned Quantum Architecture

## Boundary

Prometheus owns this implementation in Java. No Python or external ML runtime participates.

```text
internuclear distance R
        |
        v
GeometryEncoder -- shared immutable parameters theta
        |
        v
five local H2 state parameters
        |
electron coordinates (r1,r2) --> existing cusp-safe correlated H2 state --> psi
```

The first encoder is deliberately small: a cubic Chebyshev basis of a bounded transform of R, followed by a linear map to the five already validated H2 state parameters. It has twenty shared coefficients. This makes the geometry dependence continuous and differentiable without asking the model to relearn electron-nuclear cusps, the electron-electron cusp, exchange symmetry, nuclear-interchange symmetry, or the asymptotic envelope.

The local state remains `h2-covalent-r12-neural-v1`. Its analytic coordinate gradient and 6D Laplacian are reused. Shared-parameter derivatives are obtained by an exact chain rule from the local-state derivatives and encoder Jacobian.

## Execution

One global stochastic-reconfiguration update accumulates separately centered Rayleigh gradients/covariances at every geometry and averages them. It never treats electronic samples from different Hamiltonians as one normalization population.

Sampling is deterministic and emitted in batches of at most 512 configurations. Each state bundle is evaluated once and supplies amplitude, coordinate gradient, Laplacian, local energy, geometry derivative, and shared-parameter derivatives.

## Nuclear force

For fixed shared parameters, the variational derivative is

```text
dE/dR = <dH/dR> + 2 [<O_R E_L> - <O_R><E_L>]
O_R = d ln(psi) / dR
F_R = -dE/dR
```

The first term is the explicit Hellmann-Feynman electron-nuclear plus nuclear-repulsion derivative. The covariance term is the wavefunction-response/Pulay contribution, including the encoder's geometry dependence. Both are accumulated on the same streamed configurations. The estimator is independently checked against common-random-number finite differences of the Prometheus energy and against finite differences of the trusted reference curve.

## Reuse policy

The architecture exposes `NONE`, `PARAMETER_WARM_START`, `SHARED_GEOMETRY_MODEL`, `SHARED_FEATURES`, and `AUTO_SELECT`, but this experiment locks `SHARED_GEOMETRY_MODEL`. Generation-2 showed that warm-start continuation can be counterproductive; no policy is assumed beneficial without measured evidence.
