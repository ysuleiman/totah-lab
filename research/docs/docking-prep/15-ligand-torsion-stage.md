# Ligand Torsion Stage

This stage identifies ring bonds, classifies ligand bonds, and constructs the
torsion tree required for ligand PDBQT.

## Work performed

- Detects whether each bond belongs to a graph cycle using CCD connectivity.
- Classifies every bond deterministically as rotatable or gives one rigid
  reason.
- Allows explicit rigid overrides by bond index.
- Treats non-single, aromatic, hydrogen, ring, amide/thioamide,
  metal-coordination, and terminal-heavy-atom bonds as rigid.
- Reports rotatable bond indices in original CCD/deposited bond order.
- Divides the graph into rigid fragments by removing active rotatable bonds.
- Selects the fragment with the most heavy atoms as root, breaking ties by
  total atom count and then lowest deposited atom index.
- Orients deterministic nested branches away from the root.
- Validates branch endpoints, exact atom coverage, duplicate rejection, and
  `TORSDOF` parity with the active rotatable-bond count.

The current components are `LigandRingDetector`,
`RotatableBondClassifier`, `LigandRotatableBondReport`, and
`LigandTorsionTreeBuilder`.

## Scientific invariants

- Ring detection and bond classification never use distances.
- Only heavy-atom single bonds can be rotatable.
- Original atom and bond ordering determines stable report ordering.
- Resonance-restricted amide and thioamide C-N bonds are not rotatable.
- Every ligand atom occurs exactly once in the resulting torsion tree.
- A disconnected molecular graph is rejected rather than emitted as one
  molecule.

## Limitations

- The terminal-bond policy requires both endpoints to have more than one heavy
  neighbor. This deliberately suppresses terminal methyl and heteroatom bonds.
- Explicit rigid overrides currently use bond indices; a higher-level
  atom-identifier configuration has not been added.
- Ring detection favors clarity and determinism over asymptotic optimization;
  very large ligands may later benefit from a bridge-finding implementation.
- Additional conjugated systems beyond amide/thioamide C-N bonds need explicit
  future policies.
- The shared mutable `TorsionTree` and `TorsionBranch` APIs remain for receptor
  compatibility. The ligand builder validates its completed output, but callers
  could still mutate a returned branch afterward.
