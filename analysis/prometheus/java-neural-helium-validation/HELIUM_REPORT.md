# Prometheus Java Neural-QM Helium Gate

Classification: `HELIUM_INTERACTING_ELECTRON_GATE_PASSED`

This is Prometheus's first interacting-electron physics gate. The owned Java solver evaluates

`H = -1/2 (nabla_1^2 + nabla_2^2) - 2/r1 - 2/r2 + 1/r12`

for a clamped, infinitely massive nucleus in atomic units.

Two models were evaluated on the same deterministic six-dimensional importance samples:

1. an uncorrelated Hartree-like product `exp[-zeta(r1+r2)]`, with `zeta=27/16`;
2. a permutation-symmetric correlated neural state with explicit `r12` dependence.

The correlated state enforces the electron-electron cusp through a `1+r12/2` factor and uses only cusp-safe radial invariants for the neural correction. Values, all six Cartesian first derivatives, and the full six-dimensional Laplacian share one forward automatic-differentiation evaluation.

## Frozen result

- Trusted nonrelativistic reference energy: `-2.903724377034120 Ha`
- Analytic uncorrelated baseline: `-2.847656250000000 Ha`
- Sampled uncorrelated baseline: `-2.849223111568466 Ha`
- Uncorrelated quadrature audit error: `0.001566861568465860 Ha`
- Correlated neural energy: `-2.896874454089165 Ha`
- Absolute energy error: `0.006849922944954567 Ha`
- Fraction of the analytic-baseline-to-exact correlation gap recovered: `0.8778285755686801`
- Electron-nuclear cusp: `-1.999981125921923` (error `1.8874e-05`)
- Electron-electron cusp: `0.4999738024036304` (error `2.6198e-05`)
- Permutation-symmetry error: `0`
- Explicit `r12` amplitude log-response: `0.3889905633674375`
- Maximum 6D gradient finite-difference error: `2.1524e-09`
- Full Laplacian finite-difference error: `4.9152e-09`
- Virial ratio `-2T/V`: `0.9938527939378777`
- Local-energy variance: `0.04483998218615799 Ha^2`
- Evaluation-grid stability difference: `0.0006638042011219980 Ha`
- Three-seed converged-energy spread: `0.0001656049170843943 Ha`
- Exact deterministic optimizer replay parameter difference: `0`

Every requested state evaluation was counted once per configuration. The functional consumes the value and shared derivative bundle from that one evaluation; it does not ask the state independently for the gradient or Laplacian.

This establishes a credible interacting-electron neural calculation and substantial correlation recovery. It is not chemical accuracy, a general fermionic state, or a molecular calculation. The next separate gate is H2, which must introduce multiple nuclei and nuclear-geometry dependence without weakening this helium regression.
