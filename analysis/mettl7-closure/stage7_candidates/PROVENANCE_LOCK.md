# Prospective candidate provenance lock

This stage evaluates the exact historical **216-compound METTL7B-favored
computational candidate universe**. Membership is reconstructed from the frozen
historical analysis campaign over 7,716 paired compounds using:

- historical METTL7B engine output `<= -7.5 kcal/mol`; and
- historical `(METTL7A - METTL7B)` engine-output difference `>= 1.0 kcal/mol`.

These are historical docking-screen criteria, not experimental selectivity,
affinity, potency, or biological labels. Members are called
`COMPUTATIONAL_CANDIDATES`.

An alternative rule, METTL7B `<= -7.0` and `(METTL7A - METTL7B) >= 1.5`,
selects 116 compounds from the same paired corpus. Of those, 112 overlap the
historical 216 and four lie outside it because they do not satisfy the historical
`-7.5` METTL7B cutoff. Alternative membership is retained as provenance only. It
does not redefine or filter the 216-candidate universe.

The candidate direction is METTL7B-favored. Canonical reevaluation will compare
SAM-present WT METTL7B and WT METTL7A independently. DCMB remains an
METTL7A-inhibition experimental anchor and mechanistic comparator; its behavior
is not imposed as the expected inverse mechanism for METTL7B candidates.

This lock changes no Stage 0-4 benchmark artifact and no sealed experimental
representation artifact.
