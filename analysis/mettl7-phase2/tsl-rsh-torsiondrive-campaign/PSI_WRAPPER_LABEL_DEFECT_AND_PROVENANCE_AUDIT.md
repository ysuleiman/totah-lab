# PSI wrapper label defect and provenance audit

## Disposition

The defect is confirmed and is confined to wrapper metadata/status behavior. The
immutable PSI scientific archive remains the authoritative raw record and is not
rewritten. Its SHA-256 is
`1e339fc04bf495521095f8f6e6ff93286b0da7f2252fc27b0a90c450ddd55818`.

The historical `results/PSI/COMPLETION_RECEIPT.json` is intentionally preserved
with the erroneous status `PHI_COMPLETE_PERSISTED`. The corrected interpretation
is `PSI_COMPLETE_PERSISTED`, supported by the PSI state, task identities,
torsion definitions, constraints, candidate paths, results, and checksums.

## Exact defect and root cause

The executed PSI package's `run_multigpu_psi.py` contained two PHI literals:

- line 202 built the completion receipt with `PHI_COMPLETE_PERSISTED`;
- line 210 made `status()` read `PHI/WAVEFRONT_STATE.json`.

The same file's scientific execution path was PSI-specific:

- line 143 selected `results/PSI`;
- line 169 constructed task identities with `task_id("PSI", ...)`;
- candidate specifications used the PSI atom tuple `[9, 8, 7, 1]`.

The root cause was a blind PHI-to-PSI package transformation that did not replace
those two hard-coded literals. This is a copy/transform defect, not an enum
mapping defect and not shared mutable scientific state.

## Affected and unaffected behavior

Affected:

- the text label in the final PSI completion receipt;
- historical CLI status/shutdown lookup, which could inspect PHI state if PHI
  was colocated at the supplied results root.

Unaffected, as positively verified from the archive:

- PSI candidate IDs are hashes of `PSI|source_id|target_degrees`;
- every task carries the PSI atom indices `[9, 8, 7, 1]` (zero-based);
- every constraint uses the corresponding one-based atoms `10 9 8 2`;
- every candidate and state path is under `results/PSI`;
- the state identifies torsion `PSI` and contains 47 completed PSI tasks,
  zero failures, 14 authoritative cells, and an empty queue;
- the archive contains no CHI or PHI candidate tree;
- the receipt's state hashes resolve exactly to the PSI state and PSI state
  checksum manifest;
- all 9,051 root-manifest checksums verify.

Therefore the erroneous PHI lookup did not supply data to candidate generation,
QM, wavefront reduction, or persistence, and it did not overwrite CHI or PHI.
Cross-axis scientific contamination is `NONE`.

## Correction

Commit `656723aa4122dc81140b93b26dc7a5c9b3d4be41` introduced one authoritative
`TorsionAxis` value from which result directories, completion labels, receipts,
and status paths are derived. Status reads only the selected axis and rejects a
state whose internal identity differs.

Regression coverage proves:

- correct directory and receipt mapping for CHI, PHI, and PSI;
- all six cross-axis status lookups do not resolve another axis;
- mismatched internal state identity fails closed;
- unknown axes fail closed.

The historical raw receipt is not repaired in place. This document, the
machine-readable contamination audit, and the corrected code provide the
permanent correction record.

## Commit lineage

- preceding sealed scientific state: `0160c1fb6f510c6ea7d290bc8c25c945684b9899`;
- wrapper identity correction: `656723aa4122dc81140b93b26dc7a5c9b3d4be41`;
- publication audit/document commit: recorded in repository history containing
  this file.

No QM calculation or torsional fit was run for this correction.
