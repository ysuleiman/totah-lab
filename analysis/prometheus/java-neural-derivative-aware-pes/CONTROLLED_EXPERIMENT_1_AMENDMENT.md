# Controlled Experiment 1 Amendment — Diagnostic Arm Selection

Status: `PRELOCK_METHOD_SELECTION`

## Capability boundary

This experiment is `REFERENCE_ASSISTED_DIAGNOSTIC`. It may answer whether the
existing geometry-conditioned state can represent the trusted compressed-region
slope. Its result cannot become production ab-initio QM evidence and cannot make
external forces a permanent Prometheus input.

Prometheus capability classes are:

- `AB_INITIO`: Hamiltonian and molecular geometry only;
- `REFERENCE_ASSISTED_DIAGNOSTIC`: external energies/forces may test capacity,
  but outputs are ineligible as production QM evidence;
- `SURROGATE`: learns external QM labels and cannot be presented as ab initio.

The implementation and result metadata must carry this class explicitly.

## Literature result versus Prometheus inference

Established literature:

- Czarnecki et al., *Sobolev Training for Neural Networks*, NeurIPS 2017,
  arXiv `1706.04859`, establishes that matching function values does not by
  itself match derivatives and that derivative observations can improve
  approximation.
- Cooper et al., *npj Computational Materials* 6, 54 (2020), DOI
  `10.1038/s41524-020-0323-8`, convert force labels into Taylor-expanded local
  energy observations to avoid the higher derivative/memory cost of direct
  force training. This is surrogate-potential precedent, not proof for VMC.
- Scherbela et al., *Nature Communications* 14, 8185 (2023), DOI
  `10.1038/s41467-023-44216-9`, demonstrate transferable neural wavefunctions
  trained across compounds/geometries and geometry selection that may depend on
  prior energy variance.

Prometheus inference to falsify: the shared H2 state may possess sufficient
capacity, while equal variational allocation leaves compressed-region slope
fidelity underoptimized.

## Candidate A — direct Sobolev force constraint

For `F_theta=-dE_theta/dR`, differentiating

`[(F_theta-F_ref)/epsilon_F]^2`

with respect to parameters requires `dF_theta/dtheta`, a mixed
nuclear-coordinate/parameter derivative. A direct analytic implementation adds
a derivative order that the current graph does not expose. A centered numerical
implementation requires energy/parameter-gradient evaluations at displaced
geometries and is then computationally equivalent to Candidate B while being a
less transparent abstraction.

## Candidate B — symmetric force-derived local-energy constraint

Use

`F_secant(theta) = -[E_theta(R+delta)-E_theta(R-delta)]/(2 delta)`

inside a dimensionless force residual. Its parameter derivative uses the two
ordinary normalized-energy parameter gradients already produced by the SR
statistics path:

`dF_secant/dtheta = -[dE_+/dtheta-dE_-/dtheta]/(2 delta)`.

It therefore requires two displaced energy/state traversals per objective pass,
no new autodiff order, and no analytic force estimator in training. It is the
smallest clean falsification experiment and is selected.

## Locked selection

Select Candidate B only. Use equal dimensionless energy and force blocks with
pre-existing error scales `epsilon_E=0.015 Ha` and
`epsilon_F=0.030 Ha/bohr`. Use `delta=0.05 bohr`, one quarter of the existing
0.2-bohr compressed-region grid spacing, before observing the result. No second
arm or displacement search is allowed.

## Production hypotheses — design only

None is executed here:

1. **Adaptive geometry allocation:** allocate additional variational sampling or
   optimization using preregistered variance, stationarity, uncertainty,
   neighboring-energy disagreement, and curvature indicators.
2. **Shared plus local residual:** `theta(R)=theta_shared+Delta theta(R)`, with a
   small smooth correction regularized to zero, compared against fully
   independent wavefunctions.
3. **Internal derivative consistency:** compare differential-SWCT force with the
   central derivative of the same state. This can detect inconsistency but cannot
   make a globally wrong PES correct.
4. **Curvature-driven mesh:** use
   `|E(R+d)-2E(R)+E(R-d)|/d^2` with variance/stationarity to add geometry support
   only where needed.

These are Prometheus hypotheses, not established consequences of the cited
literature. Each requires a separate preregistered ab-initio experiment.
