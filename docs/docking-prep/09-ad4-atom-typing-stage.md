# AD4 Atom Typing Stage

`AD4AtomTypingStage` is the ninth docking-prep stage. It translates the prepared
and charged receptor atoms into AutoDock4 atom types for PDBQT export.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Requires `ContextKeys.PROTEIN_TOPOLOGY`.
- Requires `ContextKeys.CHARGE_ASSIGNMENT_REPORT` from
  `ChargeAssignmentStage`.
- Requires `ContextKeys.RESIDUE_STATES` from `ResidueStateAssignmentStage`.
- Requires every residue to have a matching residue state.
- Requires every atom to have a finite charge.
- Requires every atom to have an Amber atom type from charge assignment.
- Uses topology neighbors to determine hydrogen parent atoms.
- Uses topology neighbors to determine whether histidine ring nitrogens are
  protonated.
- Assigns only legal AutoDock4 atom type symbols.
- Rejects unsupported elements instead of falling back to carbon.
- Leaves residue and atom order intact.
- Publishes `AD4AtomTypingReport` to
  `ContextKeys.AD4_ATOM_TYPING_REPORT`.

## Scientific Boundary

This stage performs receptor typing for AutoDock4; it does not assign charges,
change protonation, infer missing bonds, or reinterpret Amber residue state.

Hydrogen donor typing depends on the topology graph. A hydrogen near a nitrogen
but bonded to carbon is non-polar `H`; a hydrogen bonded to nitrogen, oxygen, or
sulfur is donor `HD`. This avoids distance-only mistakes in compact structures.

Histidine ring nitrogen typing also follows topology. A ring nitrogen with a
bonded hydrogen is donor `N`; an unprotonated ring nitrogen is acceptor `NA`.
The tautomer itself remains the Stage 4/5 residue-state decision.

Sulfur typing follows residue state: `CYX` disulfide sulfur and methionine
thioether sulfur are `S`, while deprotonated `CYM` sulfur is `SA`.

## Test Coverage

`AD4AtomTypingStageTest` covers:

- Missing charge-assignment report failure.
- Empty input failure.
- Missing Amber atom type failure.
- Topology-based hydrogen parent typing.
- Histidine donor/acceptor typing from topology-bonded hydrogens.
- Cysteine sulfur typing from Amber residue state.
- Unsupported element rejection.
- Report publication and defensive-copy behavior.
