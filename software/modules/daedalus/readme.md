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

## Pipeline and CLI

`PipelineFactory.createDockingPipeline(config, targetPdb)` assembles:

```text
TargetLoadStage
ReceptorPreparationStage
LigandPreparationStage   (SDF ligand from the config's ligand_path)
DockingInputAssemblyStage
```

A three-arg overload taking `DockingProperties` appends
`VinaDockingStage` when a vina executable is configured; the stage
requires `vina_docking_options` (the search box) in the config map and
turns execution failures into clear stage errors.

The CLI (`totah.lab.daedalus.cli.DaedalusCli`, no packaged launcher —
run it on the Maven classpath, see below) exposes the pipeline end to
end:

```text
daedalus dock-prep --target <receptor.pdb> --ligand <ligand.sdf> --out <runs-dir>
    (--box cx,cy,cz,sx,sy,sz | --pocket-id <db-pocket-id>)
    [--padding <angstroms, default 8>]
    [--vina <path-to-vina-binary>]
    [--overwrite] [--help]
daedalus compare-ligand-prep [--count 100] [--report <csv>]
    [--source-db <jdbc-url>] [--artifact-root <path>]
daedalus version
daedalus help
```

`compare-ligand-prep` validates hephaestus ligand preparation against
Meeko (`mk_prepare_ligand.py`) reference PDBQTs from chemflow3: it
samples compounds that have a prepared_ligand artifact linked to its
source SDF (`artifact_metadata.source_artifact_id`), re-prepares each
SDF with hephaestus, and compares heavy-atom counts, Gasteiger charge
deltas, AD4 type agreement and torsion counts. Failures (for example
SDFs without explicit hydrogens) are recorded with their reason, not
fatal. Database access is read-only: `--source-db` (default the local
chemflow3), `DB_USERNAME` (default postgres), `PGPASSWORD` (required,
no default). Reports land in `analysis/ligand-prep-comparison/`.

The search box is explicit (`--box`) or derived from a `docking.pocket`
row (`--pocket-id`, via `PocketGridBoxLoader`): alpha spheres are
preferred, pocket atoms otherwise; the center is the point centroid and
each axis is sized extent (+ sphere radii) + 2*padding. Exactly one box
source is required when `--vina` is used; without `--vina` the box is
optional. Database access uses `DB_URL` (default the local dev
database), `DB_USERNAME` (default postgres) and `PGPASSWORD` (required,
no default).

Until daedalus gets packaged launcher, run it as:

```bash
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
java -cp target/classes:$(cat target/classpath.txt) \
    totah.lab.daedalus.cli.DaedalusCli dock-prep --help
```
