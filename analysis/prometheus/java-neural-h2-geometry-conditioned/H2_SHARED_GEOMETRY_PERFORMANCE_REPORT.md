# Shared-Geometry Performance Report

- Shared parameters: 20
- Locked optimization iterations: 120
- Shared objective passes: 121
- Training state evaluations: 2,722,500
- Maximum emitted batch: 512 configurations
- Work reduction versus 1,057 Generation-2 objective passes: 88.55%
- Deterministic replay: exact
- Three-seed mean-energy spread: 0.001533 Ha

The shared state reproduced the H2 curve accurately with one parameter vector:
RMSE 0.004598 Ha and maximum error 0.005875 Ha. It also passed the cusp,
symmetry, 6D-gradient, Laplacian, variance, independent-sample-spread, smoothness,
equilibrium, well-depth, and dissociation checks. The optimizer did not meet its
locked stopping rule before the 120-iteration ceiling, so the complete shared
state gate remains failed despite the strong physical metrics.

