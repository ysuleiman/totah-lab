# Literature and Architecture Provenance

## Literature mathematics

| Method | Scientific role | Prometheus status | Source |
|---|---|---|---|
| Stochastic reconfiguration | Variational metric / regularized linear solve | Dense small-system reference exists; matrix-free form proposed | Sorella, DOI `10.1103/PhysRevB.71.241103` |
| ZV/ZVZB | Reduce estimator variance and approximate-state bias | Frozen diagnostic implementations; not qualified | Assaraf-Caffarel, DOI `10.1063/1.1621615` |
| Correlated sampling + coordinate transformation | Reduce uncertainty of neighboring-geometry differences | Frozen paired FD control exists | Filippi-Umrigar, DOI `10.1103/PhysRevB.61.R16291` |
| Adjoint differential SWCT | Obtain force components near energy-evaluation cost | Proposed analytic-directional experiment | Sorella-Capriotti, DOI `10.1063/1.3516208` |
| Neural-VMC force comparison | Joint force accuracy/contribution/variance/cost benchmark | Frozen study follows its estimator separation | Qian et al., DOI `10.1063/5.0112344` |
| SWCT scaling/regularization | Control force overhead and infinite variance | Variance benefit reproduced diagnostically | Nakano et al., DOI `10.1063/5.0076302` |
| Acceptance-ratio Pulay and compact HF estimators | Soften divergence, regularize tails, lower force variance | Proposed estimator experiment after PES-derivative attribution; not implemented | Linteau et al., arXiv `2603.14521` |

The first five force-method PDFs are archived under
`reference/prometheus-nuclear-force-literature/` with SHA-256 values in its
`SOURCE_MANIFEST.csv`. The March 2026 Linteau manuscript is cited by immutable
arXiv identity; it has not yet been added to the local archive by this review.

## Force Field X architecture

Review source: `https://github.com/SchniedersLab/forcefieldx`, commit
`3ff9accb0a0feea0fd913bb9cba0a3080fb9a435`.

| FFX component | Observed pattern | Prometheus adaptation boundary |
|---|---|---|
| `MultiDoubleArray` | Per-thread arrays followed by explicit reduction | Use typed worker-local sufficient statistics; do not copy implementation |
| `AtomicDoubleArray3D` | Selectable accumulator strategy and explicit reduce/reset | Keep accumulation strategy behind an immutable execution plan |
| `PCGSolver` | Matrix action, physical preconditioner, convergence, and parallel regions separated; flexible option exists | Implement SR operator and wavefunction-aware preconditioners from SR mathematics |
| `ParticleMeshEwald` | Coordinates real/reciprocal physical components | Separate Prometheus work by scientific dependency, not arbitrary loops |
| `RealSpaceEnergyRegion` | Fused pair energy/gradient loops with thread-local state | Request all valid observables from one sample kernel |
| `ReciprocalSpace` | Dedicated reciprocal responsibility | Preserve typed component boundaries and accounting |
| `InducedDipoleFieldRegion` | Explicit parallel sections and reduction | Make dependency/reduction phases visible and deterministic |
| `ForceFieldEnergy` | Typed orchestration of energy/gradient and component timing | One immutable evaluation request drives a fused scientific result |

No Force Field X source is copied into Prometheus. The temporary review checkout
is not a project dependency or deliverable.

## Evidence sources for the architecture review

- Frozen decision: `../java-neural-nuclear-force-estimator-study/NUCLEAR_FORCE_ESTIMATOR_DECISION_REVIEW.md`
- Measured costs: `../java-neural-nuclear-force-estimator-study/NUCLEAR_FORCE_ESTIMATOR_COST.csv`
- Evaluation/memory audit: `../java-neural-nuclear-force-estimator-study/REDUNDANT_EVALUATION_AND_MEMORY_AUDIT.md`
- Current dense SR implementation:
  `software/modules/prometheus/src/main/java/totah/lab/prometheus/variational/GeometryConditionedStochasticReconfigurationOptimizer.java`
- Current SWCT implementation:
  `software/modules/prometheus/src/main/java/totah/lab/prometheus/variational/force/HydrogenMoleculeSpaceWarpForceEstimator.java`
- Current derivative primitive:
  `software/modules/prometheus/src/main/java/totah/lab/prometheus/numerics/SecondOrderJet.java`
