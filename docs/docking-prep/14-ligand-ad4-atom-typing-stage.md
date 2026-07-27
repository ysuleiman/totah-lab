# Ligand AutoDock4 Atom Typing Stage

This work assigns legal AutoDock4 atom-type symbols from molecular graph
chemistry. It does not use residue names, protein atom names, or Amber types.

## Work performed

- Distinguishes aliphatic (`C`) from aromatic (`A`) carbon.
- Types donor (`HD`) and non-donor (`H`) hydrogen from its bonded parent.
- Distinguishes nitrogen acceptor (`NA`) from non-acceptor (`N`) using formal
  charge, aromatic hydrogen, valence, and amide/thioamide context.
- Distinguishes acceptor and non-acceptor oxygen and sulfur.
- Handles phosphate/sulfonate graph environments, halogens, and locally
  supported AutoDock metals.
- Preserves graph and atom order by copying atoms with their assigned types.
- Rejects unsupported elements, invalid hydrogen topology, non-finite charges,
  and illegal AutoDock symbols.

`LigandAd4AtomTyper` produces `LigandAd4TypingResult` with deterministic type
counts. The shared `AutoDockType` enumeration includes phosphorus (`P`).

## Scientific invariants

- Typing uses CCD-derived graph evidence.
- Every output atom has a legal local `AutoDockType`.
- Charges, coordinates, bonds, and atom order are unchanged.

## Limitations

- Rules are explicit graph rules, not the full Meeko SMARTS rule library.
- Exotic resonance systems and elements outside the local AutoDock enum are
  rejected and may require future explicit rules.
- Supported metal AD4 symbols do not imply that the current Gasteiger model can
  charge a metal-containing ligand.
- Exact atom-type parity against Meeko still requires a broader reference
  ligand panel.
