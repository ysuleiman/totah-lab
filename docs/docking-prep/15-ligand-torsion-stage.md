# Ligand Torsion Stage

This stage identifies ring bonds and classifies ligand bonds for future PDBQT
torsion-tree construction.

## Work performed

- Detects whether each bond belongs to a graph cycle using CCD connectivity.
- Classifies every bond deterministically as rotatable or gives one rigid
  reason.
- Allows explicit rigid overrides by bond index.
- Treats non-single, aromatic, hydrogen, ring, amide/thioamide,
  metal-coordination, and terminal-heavy-atom bonds as rigid.
- Reports rotatable bond indices in original CCD/deposited bond order.

The current components are `LigandRingDetector`,
`RotatableBondClassifier`, and `LigandRotatableBondReport`.

## Scientific invariants

- Ring detection and bond classification never use distances.
- Only heavy-atom single bonds can be rotatable.
- Original atom and bond ordering determines stable report ordering.
- Resonance-restricted amide and thioamide C-N bonds are not rotatable.

## Limitations

- Torsion-tree construction, rigid-fragment selection, root selection, and
  `TORSDOF` output are the next sub-stage and are not yet implemented.
- The terminal-bond policy requires both endpoints to have more than one heavy
  neighbor. This deliberately suppresses terminal methyl and heteroatom bonds.
- Explicit rigid overrides currently use bond indices; a higher-level
  atom-identifier configuration has not been added.
- Ring detection favors clarity and determinism over asymptotic optimization;
  very large ligands may later benefit from a bridge-finding implementation.
- Additional conjugated systems beyond amide/thioamide C-N bonds need explicit
  future policies.
