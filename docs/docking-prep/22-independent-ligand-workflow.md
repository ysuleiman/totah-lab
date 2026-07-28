# Independent Ligand Workflow and Docking Handoff

`LigandPreparationPipelineFactory` prepares a ligand structure supplied
separately from the receptor:

```java
Pipeline pipeline = new LigandPreparationPipelineFactory(workspace)
        .create(
                Map.of(
                        ContextKeys.LIGAND_SELECTION,
                        new LigandSelection("LIG", "A", 1, ' ')),
                ligandPdb);
pipeline.run();

Path ligandPdbqt =
        pipeline.getContext().require(ContextKeys.LIGAND_PDBQT_PATH);
```

The workflow is:

```text
LIGAND_PATH
  -> LigandInputStage
  -> LigandSelectionStage
  -> LigandGraphBuilderStage
  -> LigandHydrogenationStage
  -> LigandChargeAssignmentStage
  -> LigandAtomTypingStage
  -> LigandTorsionTreeStage
  -> LigandPdbqtExporterStage
  -> prepared_ligand.pdbqt
```

Every chemistry boundary is published in `PipelineContext`, allowing callers
to inspect, test, or stop after graph validation, hydrogenation, charging,
typing, or torsion-tree construction.

`LigandInputStage` accepts PDB, CIF, and mmCIF files and uses the configured
reduced or online/cache-backed CCD provider for both parsing and preparation.
If the file contains multiple residue groups, `LIGAND_SELECTION` is required.
Independent input is an explicit user choice, so automatic crystallization
additive exclusions are not applied; identity classification and all chemistry
validation still apply.

## Docking handoff

`DockingInputAssemblyStage` validates the prepared receptor and ligand PDBQT
files and publishes one typed `DockingInput` under `DOCKING_INPUT`. Optional
flex-receptor PDBQT is preserved in the same record.

This is intentionally an artifact handoff, not a docking executor. The project
does not yet contain a docking-engine invocation, and this change does not add
an external tool.

## Limitations

- SDF and MOL2 parsing are not implemented.
- Only one free, connected CCD-backed residue is prepared at a time.
- Multi-residue and covalent ligands remain unsupported.
- A downstream docking executor and grid/exhaustiveness configuration remain
  separate future work.
