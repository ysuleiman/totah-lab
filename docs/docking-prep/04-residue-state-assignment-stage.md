# Residue State Assignment Stage

`ResidueStateAssignmentStage` is the fourth docking-prep stage. It assigns the
Amber residue template name that later chemistry stages must use, while keeping
atom order stable.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Rejects isolated single-residue chains because combined N/C-terminal Amber
  templates are not currently supported.
- Detects chain termini from residue order, chain id, and consecutive residue
  numbers.
- Assigns N-terminal (`Nxxx`), internal (`xxx`), or C-terminal (`Cxxx`) Amber
  template names.
- Verifies every assigned template exists in the bundled Amber library.
- Converts `MSE` to `MET` for Amber lookup by renaming residue `MSE -> MET`,
  atom `SE -> SD`, and element selenium to sulfur at the same coordinates.
- Detects disulfide cysteines when enabled and assigns `CYX`/terminal `CYX`
  templates.
- Applies explicit histidine state from `ContextKeys.HIS_PROTONATION_STATE`
  when it is `HID`, `HIE`, or `HIP`.
- Rejects `hisProtonationState=AUTO`; there is no external pKa/H-bond solver in
  this pipeline stage.
- Applies per-residue overrides from
  `ContextKeys.RESIDUE_PROTONATION_OVERRIDES` using keys like `A:123=HIE`;
  insertion-coded residues append the insertion code, for example
  `A:123A=HIE`.
- Uses simple pH heuristics for internal `ASP/ASH`, `GLU/GLH`, `CYS/CYM`, and
  `LYS/LYN` states.
- Fails when a pH-driven or overridden terminal state has no bundled Amber
  template, for example `NASH`.
- Publishes `ContextKeys.RESIDUE_STATES` and
  `ContextKeys.RESIDUE_STATE_REPORT`.
- Publishes `ContextKeys.DISULFIDE_BONDS` when disulfides are assigned.

## Scientific Boundary

Amber templates are the source of truth for residue names, atom names, and later
charge assignment. This stage does not invent missing atoms, optimize
protonation with a pKa model, resolve histidine tautomer choices automatically,
or parameterize ligands/cofactors.

The only structural conversion here is `MSE -> MET`, a common docking-prep
normalization for selenomethionine in protein structures. The atom order is
preserved; only the residue name, selenium atom name, and selenium element are
changed so downstream Amber-template matching uses methionine.

The pH rules are deterministic defaults, not scientific proof of protonation.
When residue state matters, callers should supply explicit overrides derived
from curated structure knowledge or an external protonation workflow.

## Test Coverage

`ResidueStateAssignmentStageTest` covers:

- N-terminal, internal, and C-terminal Amber template assignment.
- Preservation of unchanged residue object identity and residue order.
- Explicit global histidine state.
- Per-residue overrides from map and comma-separated string forms.
- Insertion-code-safe residue-state keys and overrides.
- Rejection of malformed override entries.
- Rejection of incompatible overrides.
- Rejection of unsupported `AUTO` histidine state.
- `MSE -> MET` conversion with `SE -> SD` and sulfur element assignment.
- Disulfide detection and `CYX` terminal template assignment.
- Internal pH-driven `ASH`, `GLH`, `CYM`, and `LYN` assignments.
- Rejection of unsupported terminal protonation variants.
- Rejection of single-residue chains requiring combined terminal templates.
- Defensive-copy behavior for reports and residue-state maps.
