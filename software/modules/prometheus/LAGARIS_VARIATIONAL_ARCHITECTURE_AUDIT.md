# Lagaris-style variational quantum architecture audit

Reference: I. E. Lagaris, A. Likas, and D. I. Fotiadis, “Artificial Neural
Network Methods in Quantum Mechanics,” *Computer Physics Communications* 104
(1997) 1–14. DOI: `10.1016/S0010-4655(97)00054-4`.

## Scope and non-goals

The paper is used as architectural precedent for a differentiable trial-state
representation optimized through a quantum functional. It does not authorize an
ANN replacement for the locked PBE-D3(BJ)/def2-SVP reference protocol. No QM was
executed, no Python was added, and no neural or variational solver is claimed to
be production-ready.

## Existing-class audit

| Required concept | Existing Prometheus/Gaia correspondence | Finding |
|---|---|---|
| Quantum state | None | `QuantumEvidence` stores outputs, not a state that can be evaluated. |
| Parameterized differentiable function | None | Force-field candidates store parameter values but are not differentiable functions. |
| Hamiltonian | None | `QmProtocol` identifies a calculation protocol but cannot apply an operator to a state. |
| Optimizer | Metadata only | ForceBalance/QUBE strategy classes describe external workflows; they are not numerical optimizers. |
| Autodiff/derivative machinery | None | Parsed Cartesian gradients/Hessians are evidence values, not derivative operators. |
| Variational functional | None | Validation gates and comparison metrics do not implement a quantum variational objective. |
| Coordinates | Partial | Gaia `Point3D` and Prometheus `CartesianGeometry` describe nuclei/structures, not ordered many-electron coordinates with spin. |
| Execution backend | Present | `QuantumBackend`, request/options/result separation, and capability selection provide the execution boundary. |

The audit therefore rejects reusing evidence, strategy, or molecular-geometry
classes as if they were quantum-state mathematics.

## Added minimal mathematical boundary

The new `totah.lab.prometheus.variational` package provides only representation-
independent contracts and immutable values:

- `QuantumCoordinates`: ordered particle coordinates in bohr with explicit spin.
- `QuantumAmplitude`: complex wavefunction value.
- `QuantumState`: evaluate a representation at one configuration.
- `ParameterizedQuantumState`: immutable parameter vector and functional update.
- `DifferentiableQuantumState`: spatial gradient, Laplacian, and parameter gradient.
- `Hamiltonian`: operator action independent of trial representation.
- `CollocationPointSet`: immutable weighted points with provenance checksum.
- `VariationalFunctional` / `ResidualFunctional`: objective boundary.
- `ParameterOptimizer`: stateless optimizer boundary.
- `VariationalProblem` / `VariationalResult`: immutable input/output and gates.

## Numerical intermediate reuse

`totah.lab.prometheus.numerics` adds an immutable dependency graph evaluated in a
single `NumericalStateIdentity` scope. Every node declares one of:

- `MANDATORY_REUSE`: evaluate once and retain for the state;
- `CACHE_IF_BENEFICIAL`: retain only when the cost/memory policy approves;
- `RECOMPUTE_IF_CHEAPER`: deliberately avoid retention.

This avoids indiscriminate caching. Large geometry-dependent tensors such as ERIs
may be recomputed when retention would cause greater aggregate cost through memory
pressure. No cache entry crosses a state identity. A new geometry, Hamiltonian,
state parameter vector, protocol, or other validity input requires a new state hash.

`DifferentiableQuantumState.evaluateWithDerivatives` returns a
`DifferentiableStateEvaluation` bundling the wavefunction value, coordinate
gradient, Laplacian, and parameter gradient from one forward evaluation. A future
Java autodiff implementation must construct these outputs from the shared graph or
tape instead of rerunning a neural network independently for each derivative.

No `GaussianOrbitalState`, `SlaterDeterminantState`, `NeuralTrialState`, or
`NeuralFermionicState` is implemented yet. Those names imply scientific behavior
that must be separately specified and validated.

## Three solver modes remain distinct

`QuantumSolverMode` separates:

1. `CONVENTIONAL_ELECTRONIC_STRUCTURE`: orbitals/basis plus SCF/DFT.
2. `ANN_ASSISTED_CONVENTIONAL`: ANN supplies an initial density/orbital guess but
   the locked conventional solver produces the converged result.
3. `POTENTIAL_ENERGY_SURROGATE`: geometry directly predicts approximate energy/forces.
4. `VARIATIONAL_QUANTUM_STATE`: a parameterized state is optimized through a Hamiltonian functional.

The mode participates in the new request scientific identity and backend
capability check. A backend cannot silently cross modes.

The new request identity deliberately excludes executor/backend id, software
implementation name, resource options, initial guess, cost estimate, and working
directory. Those remain execution provenance. It includes molecule and atom-map
identity, geometry, electronic state, method/basis/dispersion/environment,
constraints, requested observables, acceptance gates, and solver mode.

## Smallest clean production architecture

```text
CalculationSpecification (frozen scientific intent)
        +
QuantumExecutionRequest (geometry, atom map, solver mode, observables)
        |
QuantumBackendSelector (injected capabilities; no global enum registry)
        |
QuantumBackend (stateless/thread-safe Java contract)
        |
QuantumResult (deeply immutable, units + checksums + provenance)
        |
Prometheus validation/persistence/reuse
        |
Frozen read-only QM targets

ForceBalance consumes targets only; it cannot reach a QuantumBackend.
```

Conventional Java electronic structure implements `QuantumBackend` directly.
ANN-assisted conventional DFT may provide a checksum-bound initial guess but may
not alter the final protocol. A PES surrogate is a separate solver mode/backend.
A neural variational solver composes the types in `variational` and exposes its
validated result through a `VARIATIONAL_QUANTUM_STATE` backend.

## Safeguards still genuinely missing

Before any variational implementation is accepted, Prometheus still needs typed,
testable contracts for:

- normalization measure and tolerance;
- exact or auditable boundary-condition construction;
- fermionic permutation/antisymmetry tests;
- spin-sector identity and constraints;
- local/nonlocal Hamiltonian semantics;
- sampling/collocation convergence and bias;
- numerical stability and singularity handling;
- optimizer stopping and reproducibility;
- uncertainty/out-of-domain/failure detection;
- validation against conventional accepted references.

These are not represented by optimistic booleans. Until implemented as explicit
scientific requirements and validation evidence, no neural variational backend
should advertise production capability.

## Quantum4J lessons retained

Prometheus retains Quantum4J's small stateless/thread-safe backend idea and
pluggable request-to-result boundary. It strengthens that model with immutable
options, deeply immutable results, capability selection rather than an enum/global
factory, explicit units, scientific identity, artifact checksums, acceptance
gates, persistence, and reuse.
