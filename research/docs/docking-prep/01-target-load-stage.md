# Target Load Stage

`TargetLoadStage` is the first docking-prep stage. Its only scientific job is to
load the receptor structure faithfully; it must not clean, protonate, type, or
reinterpret chemistry.

## Contract

- Requires `ContextKeys.TARGET_PDB_PATH`.
- Accepts regular, readable PDB, CIF, or mmCIF files through `StructureIO`.
- Uses BioJava's reduced local chemical-component provider; target loading must
  not attempt network downloads for HETATM component dictionaries.
- Publishes loaded residues to `ContextKeys.PROTEIN_RESIDUES`.
- Preserves BioJava residue and atom ordering.
- Collapses alternate-location atoms to one representative atom per atom name,
  preferring highest occupancy and then altloc `A` on ties.
- Fails before parsing when the configured path is missing, unreadable, or not a
  regular file.
- Fails after parsing when no residues are loaded.

## Scientific Boundary

This stage intentionally does not decide whether waters, metals, HETATM groups,
modified residues, missing atoms, or low-confidence regions should be kept.
Those policies belong to later cleanup and validation stages.

Alternate-location records are the exception because keeping duplicate atom
positions would create an impossible receptor before cleanup, topology, charges,
or PDBQT export can run. The loader chooses one representative conformation per
atom name while preserving the residue's first-seen atom order.

## Test Coverage

`TargetLoadStageTest` covers:

- PDB load success and residue/atom order preservation.
- CIF load success.
- Alternate-location representative selection.
- Alternate-location tie handling, preferring altloc `A`.
- Missing context key.
- Missing target file.
- Directory supplied as target path.
- Unsupported file format.
- Parseable PDB with no residues.
