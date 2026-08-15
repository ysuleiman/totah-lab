# Pre-execution Amendment 001 — Audit Fixture Size

Status: `LOCKED_BEFORE_ANY_SCIENTIFIC_RESULT`

The base protocol specified 96 deterministic points per geometry for the
finite-difference objective audit. During construction, before the preflight
produced any scientific result, Prometheus correctly rejected that fixture:
`HydrogenMoleculeImportanceBatches` enforces a minimum of 100 points.

The audit fixture is therefore increased from 96 to 100 deterministic points
per geometry. This is a strictly stronger numerical fixture using the same
generator, exponent, skip, geometries, objective, finite-difference step, and
acceptance thresholds. No gate or scientific model changes. The sampler's
existing public behavior is preserved.
