# Hydrogen Optimization Stage

`HydrogenOptimizationStage` is the sixth docking-prep stage. It performs a
second pass over already hydrogenated residues to improve rotatable hydrogen
geometry while preserving the chemistry chosen by earlier stages.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Requires `ContextKeys.HYDROGENATION_REPORT` from
  `ReceptorHydrogenationStage`.
- Requires `ContextKeys.RESIDUE_STATES` from `ResidueStateAssignmentStage`.
- Requires every residue in `PROTEIN_RESIDUES` to have a matching residue
  state.
- Passes each residue's Amber template name into the optimizer.
- Uses bundled Amber Lennard-Jones parameters by default.
- Uses `ContextKeys.AMBER_PARM_PATH` when provided as a `Path` or classpath
  resource string.
- Fails if Amber Lennard-Jones parameters cannot be loaded.
- Accepts only finite non-negative `ContextKeys.HYDROGEN_CLASH_CUTOFF`
  values.
- Preserves heavy-atom identity and heavy-atom order for every residue.
- Preserves heavy-atom coordinates for every residue.
- Preserves hydrogen count and hydrogen atom names for every residue.
- Disables ASN/GLN/HIS heavy-atom flips in the docking-prep pipeline.
- Fails if optimization would add, remove, or rename hydrogens.
- Publishes `HydrogenOptimizationReport` to
  `ContextKeys.HYDROGEN_OPTIMIZATION_REPORT`.

## Scientific Boundary

This stage is hydrogen geometry optimization, not protonation assignment or
heavy-atom model editing. It may rotate hydroxyl, thiol, ammonium, and acidic
hydrogens, but the docking-prep pipeline keeps receptor heavy atoms fixed.
ASN/GLN amide flips and HIS ring flips are scientifically meaningful choices,
but they are disabled here because they change the receptor heavy-atom geometry
relative to the loaded structure.

Histidine is the clearest boundary. The low-level optimizer can still evaluate
histidine alternatives when used directly with heavy-atom flips explicitly
allowed, but the pipeline passes the Amber state from Stage 4 (`HID`, `HIE`, or
`HIP`) and constrains optimization to that state. If input hydrogens disagree
with the assigned Amber state, the pipeline fails instead of silently changing
tautomer identity.

This keeps Amber residue-state assignment as the source of truth and prevents
the optimization pass from undoing curated or externally supplied protonation
decisions.

## Test Coverage

`HydrogenOptimizationStageTest` covers:

- Missing hydrogenation report failure.
- Empty input failure.
- Missing residue-state failure.
- Hydrogen movement with heavy-atom identity, order, and coordinate
  preservation.
- Report publication and defensive-copy behavior.
- Fixed histidine template preservation even when the environment favors a
  different tautomer.
- Heavy-atom coordinate preservation for ASN/GLN/HIS residues.
- Failure when optimization would change hydrogen identities.
- Failure on invalid hydrogen clash cutoff.
- Failure when configured Amber Lennard-Jones parameters cannot be loaded.

`HydrogenOptimizerTest` remains the low-level topology test suite and covers:

- ASN/GLN flips when heavy-atom flips are explicitly allowed.
- Histidine tautomer/ring optimization when the optimizer is used directly with
  heavy-atom flips explicitly allowed.
- Hydroxyl, thiol, ammonium, and acidic-H rotations.
- Metal guard behavior.
- Pass-through behavior for fixed groups.
