# PDBQT Exporter Stage

`PdbqtExporterStage` is the final docking-prep stage. It serializes the prepared
receptor to PDBQT for rigid-only docking or Meeko-style flexible-residue docking.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Requires `ContextKeys.AD4_ATOM_TYPING_REPORT` from `AD4AtomTypingStage`.
- Requires finite partial charges on every atom.
- Requires a legal AutoDock4 atom type on every atom.
- Writes `prepared_receptor.pdbqt` under the pipeline run directory.
- Publishes receptor output path to `ContextKeys.RECEPTOR_PDBQT` and
  `ContextKeys.OUTPUT_PDBQT_PATH`.
- Without `ContextKeys.FLEX_RESIDUES`, writes every prepared atom to the rigid
  receptor file and writes no flex file.
- Rigid receptor export keeps explicit prepared hydrogens, including nonpolar
  carbon-bound hydrogens typed as `H`; this pipeline does not merge nonpolar
  receptor hydrogens into parent carbons.
- With `ContextKeys.FLEX_RESIDUES`, requires `ContextKeys.PROTEIN_TOPOLOGY`.
- Flex residue entries use `chain:number`, for example `A:123`; insertion-coded
  residues append the insertion code, for example `A:123A`.
- In flex mode, keeps flex-residue backbone atoms in the rigid receptor and
  writes side-chain torsion trees to `prepared_flex.pdbqt`.
- Publishes flex output path to `ContextKeys.FLEX_PDBQT` and
  `ContextKeys.FLEX_PDBQT_PATH` when flex mode is used.
- Publishes `PdbqtExportReport` to `ContextKeys.PDBQT_EXPORT_REPORT`.

## Scientific Boundary

This stage serializes the already prepared receptor. It does not assign charges,
infer atom types, add hydrogens, change topology, or reinterpret flexible
residue chemistry.

Rigid/flex export follows the existing Meeko/prepare_flexreceptor convention:
the rigid receptor keeps the receptor frame, while flex side-chain atoms move
into a separate torsion-tree file rooted at `CA`. `TORSDOF` is intentionally not
written because Vina rejects it in flexible-residue PDBQT files.

The exporter fails if AD4 types or charges are missing because PDBQT output with
fallback element types or invalid charge fields would be silently wrong for
docking.

Hydrogen retention is an explicit project policy. Some PDBQT workflows use a
unified-atom receptor representation and omit nonpolar hydrogens, but this
pipeline serializes the prepared receptor it was given. Stage 9 decides whether
a hydrogen is nonpolar `H` or donor `HD`; Stage 10 writes that typed atom rather
than merging it away.

Open Babel PDBQT files are useful compatibility references for rigid PDBQT
shape, field layout, and receptor heavy-atom preservation. They are not the
scientific source of truth for this pipeline's receptor preparation choices.
Plain Open Babel conversion should not be interpreted as authority for Amber
charges, protonation-state assignment, missing-atom repair, or silent ASN/GLN/HIS
heavy-atom flips. Exact whole-file matching to Open Babel is therefore not a
goal unless a separate Open Babel compatibility export mode is explicitly
enabled.

## Test Coverage

`PdbqtExporterStageTest` covers:

- Rigid-only export of every atom.
- Rigid-only retention of explicit nonpolar hydrogens.
- Missing AD4 typing report failure.
- Missing AutoDock4 type failure.
- Illegal AutoDock4 type failure.
- Non-finite charge failure.
- Flex residue root atom selection.
- Nested chi-branch generation for lysine.
- Exactly-once flex side-chain atom emission.
- Protection against spurious hydrogen contacts in flex trees.
- Backbone retention in the rigid receptor.
- No `TORSDOF` output.
- Unknown, malformed, and non-standard flex residue rejection.
- Insertion-code-safe flex residue selection.
- Export report publication.

`PipelineTest` and `PipelineOpenBabelVerifierTest` cover Open Babel
compatibility at the agreed boundary:

- Heavy-atom count, identity, order, and coordinates are compared against
  `Q6UX53_TMT1B_HUMAN_3_clean.pdbqt`.
- The curated pipeline verifier set currently contains Open Babel outputs for
  `1CRN`, `1HVR`, `1UBQ`, `4HVP`, and `Q6UX53_TMT1B_HUMAN`.
- All five verifier files are checked against their source PDB heavy atoms so
  the reference resources cannot silently drift.
- Pipeline output is compared against the Open Babel heavy-atom reference only
  for receptors that the current chemistry policy supports end to end:
  `1CRN`, `1UBQ`, and `Q6UX53_TMT1B_HUMAN`.
- Hydrogens, charges, B-factors, and exact whole-file text are intentionally not
  compared because this pipeline uses Amber/full-hydrogen receptor policy, not
  Open Babel compatibility-export policy.
