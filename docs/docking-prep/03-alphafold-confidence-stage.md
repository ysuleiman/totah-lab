# AlphaFold Confidence Stage

`AlphaFoldFilterStage` is the third docking-prep stage. It applies an optional
pLDDT cutoff to AlphaFold-style structures whose confidence scores are stored in
the B-factor column.

## Contract

- No-ops when `ContextKeys.PLDDT_CUTOFF` is absent.
- Requires `ContextKeys.PROTEIN_RESIDUES` when a cutoff is configured.
- Accepts numeric cutoff values or numeric strings.
- Requires cutoff values to be in `[0, 100]`.
- Keeps a residue when at least one backbone atom (`N`, `CA`, `C`) has
  `B-factor >= cutoff`.
- Drops a residue when no backbone atom meets the cutoff.
- Retained residues keep every atom; this stage never trims individual atoms.
- Fails if all residues would be removed.
- Publishes an `AlphaFoldConfidenceReport` to
  `ContextKeys.ALPHAFOLD_CONFIDENCE_REPORT`.

## Scientific Boundary

This stage is a confidence gate, not a structure editor. Atom-level pLDDT
trimming can leave chemically invalid partial side chains, which breaks later
Amber-template validation, hydrogenation, topology, and charge assignment.

The chosen policy is whole-residue keep/drop. A residue with a trusted backbone
is kept intact so later template validation can decide whether its chemistry is
complete enough for docking.

## Test Coverage

`AlphaFoldFilterStageTest` covers:

- No-op behavior without a cutoff.
- Whole-residue retention when any backbone atom passes.
- Whole-residue dropping when no backbone atom passes.
- Preservation of low-confidence side-chain atoms in retained residues.
- Numeric string cutoff parsing.
- Non-numeric and out-of-range cutoff failures.
- Missing and empty input residue handling.
- Failure when all residues would be removed.
- Defensive-copy behavior in the report.
- Fixture residue counts at a representative cutoff.
