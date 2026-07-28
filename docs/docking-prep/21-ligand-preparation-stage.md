# Ligand Preparation Pipeline Stages

Ligand preparation is exposed as a sequence of thin, independently testable
pipeline stages. Each stage delegates to the existing ligand chemistry
implementation; the refactor does not change algorithms or scientific order.

```text
LigandInputStage
  -> LigandSelectionStage
  -> LigandGraphBuilderStage
  -> LigandHydrogenationStage
  -> LigandChargeAssignmentStage
  -> LigandAtomTypingStage
  -> LigandTorsionTreeStage
  -> LigandPdbqtExporterStage
```

The exporter writes the generated PDBQT to:

```text
<run-directory>/prepared_ligand.pdbqt
```

Selection requires `STRUCTURE_CLEANUP_RESULT`, so it must run after
`StructureCleanupStage` or `LigandInputStage`. Ligand preparation may run
independently of receptor preparation because ligand
chemistry does not depend on the receptor hydrogenation, charge, or AD4 typing
stages.

## Context contract

Optional input:

- `LIGAND_SELECTION`: a `LigandSelection` identifying one extracted residue.
  This is required when cleanup extracted more than one component.

Outputs when a ligand is prepared:

- `SELECTED_LIGAND`: the selected `ClassifiedResidue`;
- `LIGAND_GRAPH_RESULT`: the validated CCD graph;
- `LIGAND_HYDROGENATION_RESULT`: hydrogenation and valence output;
- `LIGAND_CHARGE_ASSIGNMENT_RESULT`: charged molecular graph;
- `LIGAND_AD4_TYPING_RESULT`: AD4-typed graph;
- `LIGAND_TORSION_TREE_RESULT`: rotatable-bond and torsion-tree output;
- `LIGAND_PREPARATION_RESULT`: the typed `SelectedLigandPreparation`;
- `LIGAND_PDBQT`: the generated PDBQT text;
- `LIGAND_PDBQT_PATH`: the `Path` to `prepared_ligand.pdbqt`.

When `LigandSelectionStage` is optional and cleanup extracted no bound ligand,
it publishes no selected ligand. This preserves receptor-only preparation for
workflows that supply a different ligand later. Required standalone ligand
workflows fail instead.

## Usage

For a structure with zero or one extracted ligand, add the ligand stages after
cleanup. The standalone `LigandPreparationPipelineFactory` already assembles
the complete sequence.

```java
pipelineBuilder
        .stage(new LigandSelectionStage())
        .stage(new LigandGraphBuilderStage())
        .stage(new LigandHydrogenationStage())
        .stage(new LigandChargeAssignmentStage())
        .stage(new LigandAtomTypingStage())
        .stage(new LigandTorsionTreeStage())
        .stage(new LigandPdbqtExporterStage());
```

For a structure with multiple extracted components, configure an exact
selection before running the stage:

```java
context.put(
        ContextKeys.LIGAND_SELECTION,
        new LigandSelection("QWE", "H", 373, ' '));
```

The default receptor pipeline factory does not append these stages
automatically. Existing receptor inputs can contain several crystallization
components or cofactors, and choosing one silently would change scientific
meaning. A caller that wants bound-ligand preparation must add the stage and,
when necessary, provide `LIGAND_SELECTION`.

For a ligand supplied independently from the receptor, use
`LigandPreparationPipelineFactory`. It loads PDB or mmCIF through the same
`StructureIO` path, preserves deposited atom order and coordinates, and runs
the selection stage with ligand output required.

## Current limitations

The stages inherit the Version 1 ligand capability contract. They support a
single free, connected residue with complete CCD atoms and bonds and supported
charge/AD4 chemistry. It fails explicitly for unsupported classifications,
missing heavy atoms, incomplete CCD data, disconnected or multi-residue
ligands, unsupported elements, and ambiguous selection.

SDF and MOL2 input are not currently supported.
