# Quantum4J execution-architecture adaptation

## Scope

Prometheus uses architectural ideas from Quantum4J's backend layer only. It does not use or
copy Quantum4J's circuit simulator, quantum algorithms, numerical code, or domain model.
Quantum4J is a gate/circuit SDK and is not a molecular electronic-structure engine.

Source studied: <https://github.com/quantum4j/quantum4j/> (official repository), commit
`e0c42140cb23a2b7d68044dfc82ef1cb91e77eb1`.

Authoritative source checksums inspected:

- `Backend.java`: `8836fa9cb719052a2c6a47c1a012cdfa03b360c37a6ffffaead6121579a3002f`
- `RunOptions.java`: `f582cbe1d3393c9d694eba41d61e4334ca95b945bac7cb1361fe981ac2adec7b`
- `Result.java`: `50dba200c697ada5f735d259c808921033a4c3dc55ef472dba396c26de8a90a4`
- `BackendFactory.java`: `31f9e90e5fda2a77f799f6d7269549f2057d0a111efa529c5261f05269b6e229`

## Adopted ideas

- A small pluggable backend interface.
- Backend implementations are contractually stateless and thread-safe.
- Domain inputs, operational options, execution, and results are separate objects.
- Domain code depends on an execution abstraction rather than a concrete engine.

## Deliberately not adopted

- No global/static backend factory.
- No `BackendType` enum registry.
- No mutable fluent execution options.
- No shallowly immutable result collections.
- No nullable scientific result fields.
- No backend authority to change scientific settings.

## Prometheus types

- `CalculationSpecification`: existing frozen scientific intent.
- `QuantumExecutionRequest`: specification, atom-ordered geometry, atom-map hash,
  required observables, and operational options.
- `QuantumExecutionOptions`: immutable resource/backend preferences and an optional
  checksum-bound initial guess. These do not change scientific identity.
- `QuantumBackendCapabilities`: immutable calculation/output/constraint capabilities.
- `QuantumBackend`: stateless/thread-safe execution contract.
- `QuantumBackendSelector`: constructor-injected, capability-based selection.
- `QuantumResult`: deeply immutable, unit-explicit energy/derivatives, artifact checksums,
  execution provenance, and convergence state.
- `QuantumExecutionService`: verifies that a backend result retains the request identity.

## Zero-Python boundary

The new `execution.quantum` pathway contains no subprocess, Python, PySCF, or scripting
integration. No production Java-native molecular-QM backend is claimed by this change.
A future backend must implement `QuantumBackend`, demonstrate its capabilities, and pass
scientific qualification before it can generate accepted evidence.

Existing historical Python/PySCF readers and executors remain isolated legacy code so old
evidence remains readable and existing public APIs are not broken. They are not registered
with or reachable through the new Java-native selector.
