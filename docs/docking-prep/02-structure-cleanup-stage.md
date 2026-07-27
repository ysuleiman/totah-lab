# Structure Cleanup Stage

`StructureCleanupStage` is the second docking-prep stage. Its job is to apply
coarse receptor-content policy after loading and before any chemistry-sensitive
stage runs.

## Contract

- Requires `ContextKeys.PROTEIN_RESIDUES`.
- Keeps the 20 standard amino-acid residue names.
- Removes waters by default: `HOH`, `WAT`, `DOD`, `H2O`.
- Keeps known modified amino-acid placeholders by default: currently `MSE`
  and `TYS`.
- Removes monoatomic metals and known monoatomic ions by default.
- Keeps monoatomic metals and known monoatomic ions only when
  `ContextKeys.KEEP_METALS` is true.
- Keeps additional configured special residues from
  `ContextKeys.ALLOWED_SPECIAL_RESIDUES`.
- Extracts unknown multi-atom non-polymer residues from the receptor by default
  and publishes them to `ContextKeys.EXTRACTED_LIGANDS`.
- Publishes a `StructureCleanupReport` to
  `ContextKeys.STRUCTURE_CLEANUP_REPORT`.

## Scientific Boundary

This stage does not convert residue names, add atoms, assign protonation, assign
charges, or infer ligand/cofactor chemistry. A residue that survives this stage
may still fail later Amber-template validation.

`MSE` is passed through intentionally because it is a common modified amino-acid
case and should be handled by a later residue-state/template-normalization stage.
`TYS` is passed through because the project bundles explicit O-sulfo tyrosine
Amber PREPI templates from the 2023 ff14SB-compatible parameter package.
Known monoatomic ions use the same coarse keep/remove switch as metals at this
stage. If retained, later stages still decide whether a fixed charge and
AutoDock4 atom type exist.

Unknown multi-atom non-polymer residues are treated as bound ligands by default:
they are removed from `PROTEIN_RESIDUES` and published under
`EXTRACTED_LIGANDS` for ligand-specific preparation. This is the correct path
for PDB `1A4W` residue `QWE H:373` (RWJ-50215): `TYS` remains a modified
protein residue, while `QWE` is extracted from receptor preparation rather than
matched against an Amber amino-acid template. Cofactors, glycans, and covalent
adducts that must remain in the receptor require an explicit keep/parameterize
policy via allowed special residues or a future cofactor policy stage.

## Test Coverage

`StructureCleanupStageTest` covers:

- Standard amino-acid retention and order preservation.
- Default water removal.
- Rejection of water retention.
- Default `MSE` retention for later normalization.
- Default `TYS` retention for explicit Amber-template support.
- Default monoatomic metal and known-ion removal.
- Configured monoatomic metal and known-ion retention.
- Configured special-residue retention by list and comma-separated string.
- Unknown multi-atom ligand extraction.
- Failure when cleanup removes every residue.
- Missing and empty input residue handling.
- Defensive-copy behavior in the report.
