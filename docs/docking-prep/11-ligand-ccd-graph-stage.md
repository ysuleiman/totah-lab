# Ligand CCD Graph Stage

This work converts one explicitly selected deposited ligand residue and its
complete Chemical Component Dictionary (CCD) entry into a validated
`MolecularGraph`.

## Work performed

- Reconciles deposited atom names with CCD atom identifiers.
- Preserves deposited heavy-atom order, atom objects, coordinates, and bond
  endpoint order.
- Transfers CCD formal charges, aromatic flags, leaving-atom flags, and bond
  orders.
- Separates missing hydrogens from fatal missing heavy atoms.
- Rejects duplicate names, extra heavy atoms, missing heavy atoms, duplicate
  bonds, and unsupported CCD bond orders.
- Records CCD model and ideal coordinates for deterministic hydrogen placement.

The implementation is `CcdLigandGraphBuilder`. It produces
`CcdLigandGraphResult` and `LigandGraphValidationReport`. It is currently a
ligand-domain service, not a receptor `Stage`.

## Scientific invariants

- CCD connectivity is authoritative.
- Bond order is never inferred from interatomic distance.
- Deposited heavy-atom coordinates and ordering are unchanged.
- Missing heavy atoms are not silently generated.

## Limitations

- A complete CCD entry is required; BioJava's reduced provider is insufficient
  for components such as QWE.
- Atom matching is by normalized exact atom identifier. Alternate-name and
  ambiguous mappings are not supported.
- Only one residue-level ligand is handled. Covalent ligands, multi-residue
  ligands, and cofactors connected to the receptor are not supported.
- CCD evidence provenance (reduced, cached, or freshly downloaded) is not yet
  carried in the result.
