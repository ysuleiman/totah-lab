# Charge Assignment Stage

`ChargeAssignmentStage` is the eighth docking-prep stage. It assigns receptor
partial charges and Amber atom types after topology construction.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Requires `ContextKeys.PROTEIN_TOPOLOGY`.
- Requires `ContextKeys.TOPOLOGY_BUILD_REPORT` from `TopologyBuilderStage`.
- Requires `ContextKeys.RESIDUE_STATES` from `ResidueStateAssignmentStage`.
- Requires every residue to have a matching residue state.
- Uses the assigned Amber template for each residue.
- Requires every present atom to exist in the assigned Amber template.
- Assigns Amber template partial charge to every present atom by default.
- Assigns Amber atom type to every present atom.
- Does not call the configured `ChargeModel` unless
  `ContextKeys.OVERRIDE_CHARGES_WITH_MODEL` is true.
- When override is true, stamps Amber metadata first, then replaces charges with
  the configured model output.
- Fails when override is requested but no `ChargeModel` is configured.
- Publishes `ChargeAssignmentReport` to
  `ContextKeys.CHARGE_ASSIGNMENT_REPORT`.

## Scientific Boundary

Amber charges are the source of truth for the protein receptor. This stage does
not use QEq or another charge equilibration model by default, even though the
pipeline still constructs the stage with a model for explicit override use.

The override path is intentionally explicit because QEq-like models are useful
for experiments and non-Amber workflows, but they should not silently replace
curated Amber residue charges during docking preparation.

An atom present in the receptor but absent from the assigned Amber template is a
hard failure. Without a template atom entry, the stage has no authoritative
Amber charge or Amber type to assign.

## Test Coverage

`ChargeAssignmentStageTest` covers:

- Missing topology-build report failure.
- Empty input failure.
- Missing residue-state failure.
- Default Amber charge and Amber type assignment.
- Proof that the charge model is not called by default.
- Failure when a present atom is absent from the Amber template.
- Explicit model override after Amber metadata assignment.
- Failure when model override is requested without a model.
- Report publication and defensive-copy behavior.
