# Prometheus architecture

Prometheus is the force-field evidence and model-decision peer of Gaia. Gaia owns molecular structure primitives; Prometheus owns calculation identity, evidence, parameter provenance, development plans, frozen candidates, blinded validation, and decisions. Athena and simulation systems consume accepted outputs.

The core is molecule-agnostic. TSL-RSH, TSL-RS−, SAM, and DCMB are molecule profiles and archive adapters, never architectural branches.

The data path is:

`immutable source artifacts → format adapter → canonical JSON generation → verified memory index → inventory/comparability → strategy proposal → costed plan → authorized executor → validated evidence → frozen candidate → one-shot holdout → decision`

Canonical generations are content-addressed by source fingerprint, importer version, and schema version. Each evidence JSON has its own SHA-256. Normal startup loads `current.json` and its canonical generation; it never reparses or recomputes the source archive. Explicit refresh creates a new immutable generation.

Scientific engines are behind executor interfaces. Skeleton adapters fail closed until a real engine integration is configured. Parameterization strategies cannot execute calculations or access sealed holdout values.
