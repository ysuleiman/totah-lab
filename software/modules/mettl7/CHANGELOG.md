# METTL7 triage changelog

## METTL7_TRIAGE_RULESET_V1 — 2026-09-03

- Created the first-class `mettl7` Maven module.
- Added deterministic, evidence-preserving ligand triage without an opaque score.
- Added the 14-compound retrospective calibration panel.
- Added a compatibility facade over Athena's existing METTL7B enrichment gate; the Athena API remains unchanged.
- Preserved frozen v1.7 and kept Prometheus untouched.

Future scientific rule changes require a new ruleset identifier, a changelog entry,
and regression calibration while preserving this implementation.
