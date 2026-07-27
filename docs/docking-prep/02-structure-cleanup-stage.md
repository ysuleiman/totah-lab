# Structure Cleanup Stage

`StructureCleanupStage` is the second docking-prep stage. Its job is to apply
coarse receptor-content policy after loading and before any chemistry-sensitive
stage runs.

## Contract

- Requires `ContextKeys.PROTEIN_RESIDUES`.
- Keeps the 20 standard amino-acid residue names.
- Removes waters by default: `HOH`, `WAT`, `DOD`, `H2O`.
- Keeps known modified amino-acid placeholders by default: currently `MSE`.
- Removes monoatomic metals by default.
- Keeps monoatomic metals only when `ContextKeys.KEEP_METALS` is true.
- Keeps additional configured special residues from
  `ContextKeys.ALLOWED_SPECIAL_RESIDUES`.
- Fails on unsupported residues instead of guessing chemistry.
- Publishes a `StructureCleanupReport` to
  `ContextKeys.STRUCTURE_CLEANUP_REPORT`.

## Scientific Boundary

This stage does not convert residue names, add atoms, assign protonation, assign
charges, or infer ligand/cofactor chemistry. A residue that survives this stage
may still fail later Amber-template validation.

`MSE` is passed through intentionally because it is a common modified amino-acid
case and should be handled by a later residue-state/template-normalization stage.
Unknown ligands, cofactors, glycans, and covalent adducts are rejected unless the
caller explicitly configures them as allowed special residues.

## Test Coverage

`StructureCleanupStageTest` covers:

- Standard amino-acid retention and order preservation.
- Default water removal.
- Rejection of water retention.
- Default `MSE` retention for later normalization.
- Default monoatomic metal removal.
- Configured monoatomic metal retention.
- Configured special-residue retention by list and comma-separated string.
- Unknown residue rejection.
- Failure when cleanup removes every residue.
- Missing and empty input residue handling.
- Defensive-copy behavior in the report.
