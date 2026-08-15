# Prometheus domain model

- `MoleculeIdentity`, canonical atom maps, crosswalks, and `GeometryIdentity` prevent file order from becoming scientific identity.
- `EvidenceIdentity` includes molecule/map/geometry, charge, multiplicity, calculation type, protocol, constraints, and requested outputs.
- `QuantumEvidence` and `ClassicalEvidence` remain separate evidence dimensions.
- `ParameterCandidate` and parameter provenance retain derivation lineage and source evidence.
- `ScientificEvidenceRequirement` adds explicit electronic state, constraints, outputs, and gates without breaking the original public planning API.
- `FrozenCandidate`, sealed holdouts, validation plans, gates, and results make post-validation tuning structurally unavailable.
- Strategy SPI implementations propose and assess; they do not invent scientific values or launch engines.

Mutable collections are confined to construction boundaries. Published domain values and returned collections are immutable.
