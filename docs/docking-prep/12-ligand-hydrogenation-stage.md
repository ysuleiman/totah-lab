# Ligand Hydrogenation Stage

This work completes the hydrogens explicitly defined by the selected CCD
chemical component. It does not choose a different protonation or tautomeric
state.

## Work performed

- Validates element-specific valence against CCD bond orders and formal charge.
- Plans only hydrogens present in the CCD but absent from the deposited ligand.
- Generates deterministic coordinates from deposited and CCD reference frames.
- Appends generated hydrogens after all deposited atoms, preserving deposited
  atom order.
- Adds the corresponding graph properties and single bonds.
- Preserves existing valid hydrogens.

The implementation is split across `LigandValenceValidator`,
`LigandHydrogenPlanner`, `CcdHydrogenCoordinateGenerator`, and
`LigandHydrogenator`.

## Scientific invariants

- The CCD chemical state is preserved.
- No deposited heavy atom is moved.
- Every generated hydrogen has exactly one CCD-defined heavy-atom parent.
- Invalid or unsupported valence fails before output is prepared.

## Limitations

- There is no pH-dependent protonation, tautomer enumeration, or microstate
  selection.
- Coordinate generation is deterministic geometry reconstruction, not quantum
  optimization or force-field minimization.
- No ligand-wide hydrogen clash optimization is currently applied.
- A CCD entry without usable local reference geometry can fail coordinate
  generation.
