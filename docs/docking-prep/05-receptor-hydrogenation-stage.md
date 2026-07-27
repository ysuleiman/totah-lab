# Receptor Hydrogenation Stage

`ReceptorHydrogenationStage` is the fifth docking-prep stage. It strips
pre-existing hydrogens and rebuilds receptor hydrogens from the residue states
assigned by the Amber-template stage.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Requires `ContextKeys.RESIDUE_STATES` from `ResidueStateAssignmentStage`.
- Requires every residue in `PROTEIN_RESIDUES` to have a matching residue state.
- Uses `ResidueState.amberTemplateName()` as the source of truth for
  side-chain protonation.
- Strips all input hydrogens before adding new hydrogens.
- Preserves heavy-atom residue order and atom order, appending generated atoms
  after the existing heavy atoms.
- Honors explicit Amber states such as `ASH`, `GLH`, `LYN`, `CYM`, `CYX`,
  `HID`, `HIE`, and `HIP`.
- Uses Amber atom names for generated hydrogens; for example glycine alpha
  hydrogens are `HA2` and `HA3`, matching the bundled GLY templates.
- Places methine hydrogens from the tetrahedral center when three heavy-atom
  neighbors are present; this covers backbone `HA`, `VAL HB`, and `LEU HG`.
- Adds terminal caps only when the assigned Amber template is terminal, and
  does not duplicate an existing `OXT`.
- Does not add aromatic `HZ` to tyrosine `CZ`, which is substituted by `OH`.
- Uses global pH and global histidine state only when no more specific Amber
  state is available.
- Suppresses thiol `HG` on `CYX` disulfide cysteines and `CYM` cysteine anions.
- Keeps the metal-neighbor guard from the hydrogenation engine, suppressing
  labile H placement near configured metal atoms.
- Publishes `HydrogenationReport` to `ContextKeys.HYDROGENATION_REPORT`.
- Publishes `ContextKeys.DISULFIDE_BONDS` when disulfides are known from state
  assignment or detected geometrically.

## Scientific Boundary

This stage is a deterministic hydrogen builder, not a protonation solver. It
does not choose histidine tautomers, run pKa prediction, resolve missing heavy
atoms, or parameterize non-protein residues. Those choices must already be
encoded in the Amber residue states from Stage 4.

The key scientific rule is consistency: if Stage 4 assigned `ASH`, `GLH`,
`LYN`, `CYM`, `CYX`, `HID`, `HIE`, or `HIP`, hydrogenation follows that
template even when global pH/HIS settings would imply a different default.
Amber state assignment remains the source of truth.

Hydrogens are added geometrically and clash-guarded. A later optimization stage
may improve rotatable hydrogen positions, but it should not reinterpret residue
state.

For tetrahedral centers with one missing hydrogen and three resolved heavy
neighbors, the missing hydrogen is placed opposite the normalized heavy-neighbor
directions. This avoids false clashes caused by two-anchor dihedral guesses on
protein methine centers.

## Test Coverage

`ReceptorHydrogenationStageTest` covers:

- Missing residue-state failure.
- Empty input failure.
- Failure when any residue lacks an assigned state.
- Existing hydrogen stripping and report publication.
- `ASH` hydrogen placement at physiologic pH.
- `GLH` hydrogen placement at physiologic pH.
- Amber-compatible glycine hydrogen names (`HA2`/`HA3`, not `HA1`).
- `LYN` neutral lysine hydrogen count at physiologic pH.
- `HIP` overriding a conflicting global histidine setting.
- `CYX` suppressing thiol hydrogen and publishing disulfide residues even when
  geometric detection is disabled.
- Q6UX53 receptor-scale coverage at the default clash cutoff:
  - every standard non-glycine residue keeps its backbone `HA`;
  - every atom required by the assigned Amber templates is present after
    hydrogenation.
- Defensive-copy behavior in the report.
