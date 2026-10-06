# V03 explicit source radical representation

The [approved property contract](../../../../software/qualification/v03-representation-boundary-20261006/DESIGN.txt) and [implementation checkpoint](../../../../software/qualification/v03-explicit-radical-20261006/CHECKPOINT.txt) are the definition and execution authorities. This supplements the [historical representation boundary](V03_REPRESENTATION.md); no historical rejection or certificate is rewritten.

## Scientific meaning and supporting material

The versioned atom property `athena.ocl.atomRadicalState/1` records exact OCL2026.7.2 atom radical constants `NONE`, `S`, `D`, `T`. The existing OCL implementation, mapper source and permanent synthetic tests are pinned by the checkpoint. These are toolkit representation states, not experimentally established total spin, reactivity, stability, population or biological function. No empirical threshold or structural distribution is used. No new chemistry literature claim is introduced.

ADOPT OCL's exact per-atom source-state encoding, existing authoritative graph properties and faithful canonical round-trip guard. MODIFY only the opt-in mapper: transfer radical state rather than discard it. REJECT converting missing metadata into an explicit NONE assertion, inferring molecule-wide multiplicity, normalizing charges/H or using successful representation as scientific-operation qualification. UNSUPPORTED operations fail checked as described below. Typed atom-record evolution was considered and rejected for this bounded milestone because the reviewed property preserves historical API and bytes.

Missing property is NOT_SUPPLIED. OCL's default zero during ordinary SMILES decoding does not create an explicit NONE property. Explicit NONE on a supplied graph survives. Positive parser states are attributed to that pinned source decoder/model. Invalid tokens remain in original evidence and raise checked failure. Contradictory toolkit/source radical states are rejected without overwrite. Original isotope, charge, supplied H, stereo, coordinates, IDs, ordering and unrelated properties are preserved or conversion fails. Existing positive-H consistency checks remain independent; zero H annotation retains historical unspecified meaning.

## Operation qualification

| Entry point on opt-in instance | Bounded disposition |
|---|---|
| `decodeStructure(SMILES, ...)` | Source representation under existing faithful IDCode round-trip guard; unsupported formats/stereo remain checked failures |
| `identify(...)` | Pinned OCL canonical identity with explicit-radical implementation provenance; preserves source graph, checks supplied H |
| `correspondence`, inherited `associate` | Explicitly unsupported; no legacy graph equivalence that ignores radical properties |
| `sanitize`, `validate`, `validateDimensions` | Explicitly unsupported; no inherited valence/stereo/neutrality qualification |
| `match` | Explicitly unsupported; no B00 radical-query domain implied |
| `generate`, `minimize` | Explicitly unsupported; no conformer/force-field fallback |
| Package-level `absoluteStereo` overloads | Explicitly unsupported |

Factory: `OclMolecularBackend.forExplicitRadicalState()`. Canonical evidence version: `2026.7.2/explicit-radical-representation/1`. The default constructor and `forChemicalStateValidation()` retain historical implementation paths. The canonical OCL key is not an evidence-state digest: source NOT_SUPPLIED and explicit NONE may share an OCL key but remain different graph/property snapshots and deltas. They are never conflated by supported correspondence because that operation is unavailable here.

This qualifies representation only. No `ATHENA.*` interaction manifest, Research Gate receipt, element whitelist, arbitrary isotope-validity domain or radical-reactivity rule is added. V03's wider inventory row remains explicitly partial pending remaining scope reconciliation.
