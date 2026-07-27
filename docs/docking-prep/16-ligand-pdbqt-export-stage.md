# Ligand PDBQT Export Stage

This stage serializes a prepared ligand and its validated torsion tree as
AutoDock-compatible ligand PDBQT.

## Work performed

- Assigns deterministic one-based serials in torsion-tree traversal order.
- Writes `ROOT`, `ENDROOT`, nested `BRANCH`/`ENDBRANCH`, and `TORSDOF`.
- Uses the shared rigid-PDBQT atom-line formatter.
- Omits receptor-only `BEGIN_RES`, `END_RES`, `TER`, and `END` records.
- Validates finite coordinates and charges, legal AD4 types, branch endpoints,
  unique atom coverage, serial references, and `TORSDOF` parity.

The implementation is `LigandPDBQTWriter` in the `io` module. It consumes only
domain objects and does not perform chemistry or modify atoms.

## Scientific invariants

- Every prepared ligand atom is emitted exactly once.
- Charges, coordinates, atom types, and torsional degrees of freedom are
  serialized without reinterpretation.
- Invalid or incomplete preparation fails before output is accepted.

## Limitations

- The writer emits one connected ligand and one model only.
- Covalent receptor-ligand complexes and multiple independent components are
  unsupported.
- PDBQT text parity with every Meeko version is not guaranteed; semantic
  records, serial references, charges, types, and torsions are the compatibility
  target.
- File-path orchestration belongs to the ligand preparation integration layer,
  not the writer.
