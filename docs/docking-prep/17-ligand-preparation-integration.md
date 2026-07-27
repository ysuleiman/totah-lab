# Ligand Preparation Integration

`LigandPreparer` is the native ligand sub-pipeline. It connects the completed
CCD graph, hydrogenation, Gasteiger charge, AutoDock4 typing, torsion-tree, and
PDBQT export services without adding ligand branches to receptor stages.

## Input and output

The caller must provide exactly one explicitly selected deposited `Residue` and
its complete BioJava `ChemComp`. `prepare` returns one typed
`LigandPreparationResult`; `prepareToPath` additionally writes the validated
PDBQT using `Path` and propagates checked I/O failures.

The result contains the final molecular graph, graph validation,
hydrogenation, charge, AD4 typing, torsion results, and the exact emitted PDBQT
text.

## Execution order

1. Reconcile deposited atoms with CCD chemistry.
2. Validate valence and add CCD-defined missing hydrogens.
3. Assign formal-charge-preserving Gasteiger charges.
4. Assign graph-based AutoDock4 atom types.
5. Classify rotatable bonds and construct the torsion tree.
6. Validate and serialize ligand PDBQT.

## Invariants

- Deposited heavy-atom order and coordinates remain unchanged.
- CCD bond definitions and formal charges are authoritative.
- Each stage must succeed before the next one runs.
- The final PDBQT contains every prepared atom exactly once.
- Receptor Amber preparation behavior is not involved or modified.

## Limitations

- The caller must select one ligand; independent residues are never merged.
- Complete CCD chemistry is required.
- Covalent ligands, multi-residue ligands, and receptor-linked cofactors are
  unsupported.
- Protonation and tautomer selection are not performed; the CCD state is used.
- Default Gasteiger parameter coverage excludes metal-containing ligands.
- The service is not automatically inserted into `PipelineFactory`; callers
  invoke the explicit ligand sub-pipeline when ligand preparation is requested.
