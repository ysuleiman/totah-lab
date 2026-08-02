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
- Optionally reads `ContextKeys.POCKET` and
  `ContextKeys.POCKET_PROXIMITY_CUTOFF`.
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
- If required heavy atoms are missing, publishes `MissingHeavyAtomReport` to
  `ContextKeys.MISSING_HEAVY_ATOM_REPORT` before failing.

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

When a pocket is available, missing-heavy-atom reporting marks whether the
affected residue is near the pocket. The pocket center comes from
`PocketGeometry.calculateCenter(pocket)`, which prefers resolved receptor
heavy-atom coordinates and uses fpocket alpha spheres or stored parser metadata
only as fallbacks. Because a missing atom has no coordinates, the reported
distance is from the residue heavy-atom centroid to the pocket center. The
default proximity cutoff is 8.0 angstroms and can be overridden with
`ContextKeys.POCKET_PROXIMITY_CUTOFF`.

Pocket proximity is a review priority signal, not a relaxation of the template
requirement. Missing Amber-template heavy atoms remain fatal everywhere in the
receptor, including outside the binding pocket.

## Test Coverage

`TopologyBuilderStageTest` covers:

- Missing hydrogen-optimization report failure.
- Empty input failure.
- Missing residue-state failure.
- Amber template intra-residue bonds.
- Explicit peptide-bond addition.
- Rejection of missing template heavy atoms.
- Missing-heavy-atom report publication with pocket proximity.
- Rejection of out-of-range peptide-bond geometry.
- Disulfide `SG-SG` bond addition from residue states.
- Report publication and defensive-copy behavior.
