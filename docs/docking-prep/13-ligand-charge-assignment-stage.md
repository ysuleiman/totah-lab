# Ligand Charge Assignment Stage

This work assigns ligand partial charges with the project's native Gasteiger
implementation. It is separate from receptor Amber charge assignment.

## Work performed

- Adapts `MolecularGraph` to `ChargeSystem`.
- Exposes CCD formal charge, bond order, aromaticity, coordinates, and
  connectivity to the charge model.
- Selects element and hybridization-specific Gasteiger parameters.
- Seeds calculation from CCD formal atom charges.
- Normalizes and validates the partial-charge sum against total formal charge.
- Copies atoms with assigned charges while preserving ordering, coordinates,
  graph properties, and bonds.

`LigandChargeAssigner` produces `LigandChargeAssignmentResult`. Receptor Amber
charges remain unchanged and authoritative.

## Scientific invariants

- Total ligand partial charge equals the CCD total formal charge within the
  configured numerical tolerance.
- Unsupported elements fail instead of receiving carbon fallback parameters.
- Every output charge must be finite.

## Limitations

- This is Gasteiger charging, not AM1-BCC, RESP, or another quantum-derived
  scheme.
- Parameter coverage is intentionally limited; metal-containing ligands are
  rejected by the default model.
- Charge parity with Meeko or Open Babel is not guaranteed because iteration,
  parameter, and normalization details may differ.
- There is not yet a ligand pipeline orchestration `Stage`; the service is
  called directly by the native ligand preparation flow.
