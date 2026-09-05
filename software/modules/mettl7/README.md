# METTL7 domain module

Framework-independent METTL7A/B-specific evidence interpretation and incoming-ligand triage.
This is a triage engine, not an affinity predictor and not a replacement for matched experiments.

## Architecture

The module depends on Athena and reuses its ligand liability and enrichment machinery. It does
not duplicate Athena contact, pose, pocket, or chemistry algorithms. Prometheus is untouched.
Database entities and HTTP DTOs belong in `web-api`, not this domain module.

The public entry point is
`totah.lab.mettl7.triage.Mettl7LigandTriageService`. Inputs and results are immutable records.
Every scientific dimension is reported separately with reasons and preserved evidence.

## Rules and evidence boundaries

`METTL7_TRIAGE_RULESET_V1` is frozen in `TRIAGE_RULES_VERSION.json`. Raw docking score,
one pose, one residue, size, hydrophobicity, chlorine count, alpha methylation,
rigidification, stereochemistry, or a single electrophile cannot independently determine
classification. B-side candidates remain prospective until matched A/B experimental testing.
Netarsudil remains B-compatible only. The DCMB wall is not treated as a universal A pharmacophore.

## Build and test

From `software/modules`:

```shell
mvn -pl mettl7 -am test
```

The calibration file contains 14 historical/productive/control/prospective cases. Its feature
columns are inputs to general rules; it is separate from the implementation to avoid hidden
identifier-based decisions.

## Machine-readable use

The JSON codec supports deterministic input/result serialization. The CLI accepts an input and
output path:

```shell
java -cp <reactor-classpath> totah.lab.mettl7.triage.Mettl7TriageCli \
  EXAMPLE_INPUT.json EXAMPLE_OUTPUT.json
```

`Mettl7TriageReportRenderer` provides a concise human-readable rendering. JSON is the durable
machine-readable representation.

## Compatibility migration

`Mettl7bEnrichmentPolicy` is the METTL7-owned facade over Athena's existing
`Mettl7bEnrichmentGate`. The Athena class and public API remain intact. This moves callers toward
the bounded context without reversing dependencies or duplicating its algorithm. Further moves
must follow the same compatibility-first approach.

## Known limitations

- Inputs currently receive computed chemistry/contact features; this module does not calculate
  them from SMILES or poses.
- Cofactor-state generalization beyond DCMB remains untested.
- No experimentally established B-selective ligand exists in the current evidence base.
- Calibration is retrospective and does not constitute prospective validation.
