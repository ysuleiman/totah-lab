# Topology Builder Stage

`TopologyBuilderStage` is the seventh docking-prep stage. It builds the receptor
bond graph after residue states, hydrogens, and hydrogen optimization are
finished.

## Contract

- Requires non-empty `ContextKeys.PROTEIN_RESIDUES`.
- Requires `ContextKeys.HYDROGEN_OPTIMIZATION_REPORT` from
  `HydrogenOptimizationStage`.
- Requires `ContextKeys.RESIDUE_STATES` from `ResidueStateAssignmentStage`.
- Requires every residue to have a matching residue state.
- Uses the assigned Amber template for each residue.
- Adds intra-residue bonds from Amber template connectivity.
- Requires every non-hydrogen atom in the assigned Amber template to be present.
- Skips Amber template bonds involving absent hydrogens, preserving tolerance
  for hydrogen naming/count decisions made by earlier stages.
- Adds peptide bonds between consecutive residues in the same chain when the
  `C-N` distance is in a narrow peptide-bond window.
- Adds `SG-SG` disulfide bonds between residues marked disulfide by Stage 4 when
  their sulfur distance is in a disulfide-bond window.
- Leaves residues and atoms untouched.
- Publishes `Topology` to `ContextKeys.PROTEIN_TOPOLOGY`.
- Publishes `TopologyBuildReport` to `ContextKeys.TOPOLOGY_BUILD_REPORT`.

## Scientific Boundary

This stage no longer infers intra-residue covalent chemistry from generic
distance cutoffs. Distance-only bonding can create false bonds after hydrogens,
nearby side chains, metals, or compact conformations enter the structure.

Amber templates are the source of truth for residue-local connectivity. Geometry
is used only for cross-residue covalent links whose existence depends on chain
context: peptide bonds and disulfides.

Missing heavy atoms are fatal because downstream charges and atom typing assume
the topology matches the assigned Amber residue template. Missing hydrogens are
not fatal here because previous stages may omit labile hydrogens for valid
state, clash, metal, or disulfide reasons.

## Test Coverage

`TopologyBuilderStageTest` covers:

- Missing hydrogen-optimization report failure.
- Empty input failure.
- Missing residue-state failure.
- Amber template intra-residue bonds.
- Explicit peptide-bond addition.
- Rejection of missing template heavy atoms.
- Rejection of out-of-range peptide-bond geometry.
- Disulfide `SG-SG` bond addition from residue states.
- Report publication and defensive-copy behavior.
