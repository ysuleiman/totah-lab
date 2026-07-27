# Target Load Stage

`TargetLoadStage` is the first docking-prep stage. Its only scientific job is to
load the receptor structure faithfully; it must not clean, protonate, type, or
reinterpret chemistry.

## Contract

- Requires `ContextKeys.TARGET_PDB_PATH`.
- Accepts regular, readable PDB, CIF, or mmCIF files through `StructureIO`.
- Publishes loaded residues to `ContextKeys.PROTEIN_RESIDUES`.
- Preserves BioJava residue and atom ordering.
- Fails before parsing when the configured path is missing, unreadable, or not a
  regular file.
- Fails after parsing when no residues are loaded.

## Scientific Boundary

This stage intentionally does not decide whether waters, metals, HETATM groups,
alternate locations, modified residues, missing atoms, or low-confidence regions
should be kept. Those policies belong to later cleanup and validation stages.

## Test Coverage

`TargetLoadStageTest` covers:

- PDB load success and residue/atom order preservation.
- CIF load success.
- Missing context key.
- Missing target file.
- Directory supplied as target path.
- Unsupported file format.
- Parseable PDB with no residues.
