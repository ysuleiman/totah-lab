# Daedalus

Daedalus orchestrates molecular-docking workflows. It does not implement
receptor preparation chemistry or PDBQT serialization.

The receptor workflow is:

```text
TargetLoadStage
    -> Hermes StructureReader
    -> Gaia Protein
ReceptorPreparationStage
    -> Hephaestus preparation and validation
    -> Hermes PDBQT writer through the Hephaestus client
```

`TargetLoadStage` owns workflow-level loading configuration and stores the
loaded immutable Gaia `Protein` in the pipeline context.
`ReceptorPreparationStage` passes that protein and request-scoped
`ReceptorPreparationOptions` to Hephaestus, validates the prepared result,
and writes the receptor output.

The former receptor cleanup, hydrogenation, topology, charge-assignment,
atom-typing, and PDBQT-export stages were removed from Daedalus. Their
implementations belong to Hephaestus operations, while file parsing and
writing belong to Hermes.

Ligand and docking stages remain in Daedalus until their corresponding
workflows are migrated separately.
