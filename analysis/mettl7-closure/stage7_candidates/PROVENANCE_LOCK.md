# Prospective candidate provenance lock

This stage evaluates the corrected **top-200 non-warhead METTL7B-favored
computational candidate universe**. Membership is reconstructed from the frozen
historical analysis campaign over 7,716 paired compounds by excluding all labels
prefixed `WH`, requiring historical METTL7B engine output `< -5.5 kcal/mol`,
ordering by historical `(METTL7A - METTL7B)` delta descending, and retaining 200.

These are historical docking-screen criteria, not experimental selectivity,
affinity, potency, or biological labels. Members are called
`COMPUTATIONAL_CANDIDATES`.

The candidate direction is METTL7B-favored. Canonical reevaluation will compare
SAM-present WT METTL7B and WT METTL7A independently. DCMB remains an
METTL7A-inhibition experimental anchor and mechanistic comparator; its behavior
is not imposed as the expected inverse mechanism for METTL7B candidates.

This lock changes no Stage 0-4 benchmark artifact and no sealed experimental
representation artifact.
