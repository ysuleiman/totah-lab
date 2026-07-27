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
- Preserves hydrogen count and hydrogen atom names for every residue.
- Fails if optimization would add, remove, or rename hydrogens.
- Publishes `HydrogenOptimizationReport` to
  `ContextKeys.HYDROGEN_OPTIMIZATION_REPORT`.

## Scientific Boundary

This stage is geometry optimization, not protonation assignment. It may rotate
hydroxyl, thiol, ammonium, and acidic hydrogens or evaluate amide/ring flips,
but it must not change residue protonation state.

Histidine is the clearest boundary. The low-level optimizer can still evaluate
histidine alternatives when used directly, but the pipeline passes the Amber
state from Stage 4 (`HID`, `HIE`, or `HIP`) and constrains optimization to that
state. If input hydrogens disagree with the assigned Amber state, the pipeline
fails instead of silently changing tautomer identity.

This keeps Amber residue-state assignment as the source of truth and prevents
the optimization pass from undoing curated or externally supplied protonation
decisions.

## Test Coverage

`HydrogenOptimizationStageTest` covers:

- Missing hydrogenation report failure.
- Empty input failure.
- Missing residue-state failure.
- Hydrogen movement with heavy-atom order preservation.
- Report publication and defensive-copy behavior.
- Fixed histidine template preservation even when the environment favors a
  different tautomer.
- Failure when optimization would change hydrogen identities.
- Failure on invalid hydrogen clash cutoff.
- Failure when configured Amber Lennard-Jones parameters cannot be loaded.

`HydrogenOptimizerTest` remains the low-level topology test suite and covers:

- ASN/GLN flips.
- Histidine tautomer/ring optimization when the optimizer is used directly.
- Hydroxyl, thiol, ammonium, and acidic-H rotations.
- Metal guard behavior.
- Pass-through behavior for fixed groups.
