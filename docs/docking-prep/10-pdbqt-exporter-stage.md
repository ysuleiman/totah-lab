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
- With `ContextKeys.FLEX_RESIDUES`, requires `ContextKeys.PROTEIN_TOPOLOGY`.
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

## Test Coverage

`PdbqtExporterStageTest` covers:

- Rigid-only export of every atom.
- Missing AD4 typing report failure.
- Missing AutoDock4 type failure.
- Flex residue root atom selection.
- Nested chi-branch generation for lysine.
- Exactly-once flex side-chain atom emission.
- Protection against spurious hydrogen contacts in flex trees.
- Backbone retention in the rigid receptor.
- No `TORSDOF` output.
- Unknown, malformed, and non-standard flex residue rejection.
- Export report publication.
