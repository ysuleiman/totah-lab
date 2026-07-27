# Ligand Preparation Integration

`LigandPreparer` is the native ligand sub-pipeline. It connects the completed
CCD graph, hydrogenation, Gasteiger charge, AutoDock4 typing, torsion-tree, and
PDBQT export services without adding ligand branches to receptor stages.

## Input and output

The caller must provide exactly one explicitly selected deposited `Residue`.
`LigandPreparer` can resolve its component through an injected BioJava
`ChemCompProvider`, or the caller can use the explicit `ChemComp` overload for
deterministic operation. `prepare` returns one typed `LigandPreparationResult`;
`prepareToPath` additionally writes the validated PDBQT using `Path` and
propagates checked I/O failures.

Receptor cleanup now exposes candidates through `StructureCleanupResult`.
Its `ClassifiedResidue` entries keep component identity separate from cleanup
disposition. Selection policy must choose one extracted free-ligand residue
before invoking `LigandPreparer`; the preparer does not reinterpret receptor
cleanup policy.

`LigandPreparationOrchestrator` implements this handoff. It supports automatic
handling only when zero or one extracted component exists and requires
`LigandSelection` for ambiguous multi-candidate structures. See
[19-ligand-selection-orchestration.md](19-ligand-selection-orchestration.md).

The result contains the final molecular graph, graph validation,
hydrogenation, charge, AD4 typing, torsion results, and the exact emitted PDBQT
text.

Ordinary ligand preparation requires a complete, connected, non-monatomic CCD
component with both atom and bond definitions. Missing definitions fail with
`UnsupportedLigandException` and reason `INCOMPLETE_CCD`. Monatomic components
must be handled by classification or ion policy rather than this workflow.

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
- Complete CCD atom and bond chemistry is required.
- Covalent ligands, multi-residue ligands, and receptor-linked cofactors are
  unsupported.
- Protonation and tautomer selection are not performed; the CCD state is used.
- Default Gasteiger parameter coverage excludes metal-containing ligands.
- Ligand preparation is not automatically inserted into the receptor
  `PipelineFactory`; callers invoke the explicit ligand orchestrator when
  ligand preparation is requested.

The machine-readable support boundary and stable rejection mapping are
documented in [18-ligand-capability-contract.md](18-ligand-capability-contract.md).
