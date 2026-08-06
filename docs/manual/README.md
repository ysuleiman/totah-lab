# Totah Lab manual (DRAFT)

> **Status: DRAFT skeleton.** Sections are intentionally short
> orientation notes; grow them as the project matures. Where a deeper
> document exists it is linked.

## Project overview

Totah Lab is a molecular docking pipeline: AlphaFold/PDB structures are
prepared, pockets are detected (fpocket, P2Rank, BioHub), pockets are
compared and annotated, and docking runs over selected pockets are
analyzed for selectivity. Java 21, Maven multi-module, Spring Boot web
API, React web UI, PostgreSQL.

## Modules (`software/modules/`)

- **gaia** — the immutable domain model: structures, chains, residues,
  atoms, pockets, geometry primitives.
- **hermes** — file I/O: PDB/mmCIF reading (BioJava), fpocket and
  P2Rank output parsing, PDBQT serialization/validation, BioHub
  evidence artifacts, FASTA.
- **hephaestus** — molecular preparation and validation for docking
  (Meeko/Open Babel-compatible behavior where possible). Includes a
  standalone CLI (`mvn package`, then
  `java -jar hephaestus-1.0-SNAPSHOT.jar <command>`) with
  `prepare-receptor`, `prepare-ligand`, `validate-pdbqt`,
  `validate-flex-pdbqt`, `version`, `help`.
- **daedalus** — docking-workflow orchestration (receptor preparation
  through pose analysis); no chemistry or serialization of its own.
- **athena** — structural analysis: pocket shape descriptors,
  multi-hypothesis pocket alignment (PCA+ICP, sequence-seeded Kabsch),
  residue correspondence/chemistry/substitution scoring, SASA, dihedral
  and mutation measurements, sequence alignment, PocketMatch
  signatures, and the pocket-comparison evidence model.
- **euclid** — math toolkit: linear algebra, numerical optimization,
  spatial structures, graphs.

## Apps (`software/apps/`)

- **web-api** — Spring Boot 3 / Spring Data JPA REST API over the
  PostgreSQL `docking` schema; also hosts the import/backfill runners.
- **web-ui** — React + TypeScript + Vite UI (raw WebGL pocket viewer,
  similarity search, comparison evidence pages).
- **lab-report** — reusable report analysis, narrative, validation, and
  rendering (PDF/DOCX/Markdown); independent of HTTP and persistence.
- **pocket-viewer** — desktop (Swing) pocket visualization.

## Database orientation

Schema managed externally (Hibernate runs `ddl-auto=validate`).
Migrations live in `tools/scripts/sql/docking/`.

- `docking.receptor` — one row per protein target (UniProt-keyed).
- `docking.structure` — a structure of a receptor
  (`source` PDB/ALPHAFOLD/…, `preparation_state`,
  `chosen_pocket_id`).
- `docking.residue` — canonical residues of a structure
  (identity: structure + chain + number + insertion code).
- `docking.pocket` — a detected pocket (source is the
  `docking.pocket_source` PG enum: FPOCKET/P2RANK/BIOHUB/MANUAL/
  IMPORTED), with volume/score/druggability.
- `docking.pocket_residue` / `docking.pocket_atom` — pocket membership
  and pocket-lining atoms (`pocket_atom.coords` cube column is
  deliberately unmapped).
- `docking.pocket_alpha_sphere` — fpocket voronoi vertices in parser
  order (`sphere_index`).
- `docking.artifacts` — files on disk; every artifact belongs to a
  `public.targets` row and a `public.pipeline_runs` row (one FINISHED
  run per target by convention).
- `docking.pocket_summary_mv` — materialized view feeding pocket
  search; refresh after bulk imports.

## Docking pipeline (daedalus)

`PipelineFactory.createDockingPipeline(config, targetPdb)` builds:
TargetLoadStage → ReceptorPreparationStage → LigandPreparationStage →
DockingInputAssemblyStage. The ligand SDF path comes from the config map
(`ligand_path`); the prepared ligand PDBQT lands in the run directory.
Vina execution is opt-in: the overload taking `DockingProperties` adds
VinaDockingStage only when `vinaExecutable` is configured, and the stage
requires `vina_docking_options` (search box) in the config map;
execution failures surface as stage errors.

## Running the bulk imports

All runners are `CommandLineRunner`s gated by properties (never active
in normal startup or tests) and meant to run with
`--spring.main.web-application-type=none` from `software/apps/web-api`.

- **AlphaFold/fpocket bulk import**
  (`totah.bulk-import.enabled=true`):
  `totah.bulk-import.pdb-dir` (default the UP000005640 v6 proteome
  dir), `totah.bulk-import.fpocket-dirs` (two comma-separated roots),
  `totah.bulk-import.dry-run` (default `true` — filesystem pairing and
  parse validation only, no DB writes),
  `totah.bulk-import.skip-existing` (default `false`),
  `totah.bulk-import.workers` (0 = min(processors−1, 8)).
  Idempotent find-or-create; per-structure transactions; exit code 1
  on any failed structure. Pockets with fewer than
  `totah.import.min-pocket-residues` (default 8) residues are skipped
  as fpocket noise.
- **Alpha-sphere backfill**
  (`totah.alpha-sphere-backfill.enabled=true`): fills
  `pocket_alpha_sphere` from `pocketN_vert.pqr` files for FPOCKET
  pockets lacking spheres; `totah.alpha-sphere-backfill.dry-run`
  (default `true`), `….structure-accession` to limit to one structure.
  One transaction per structure.
- **Receptor UniProt backfill** (`totah.backfill.enabled=true`):
  `totah.backfill.uniprot-tsv` fills `protein_name`/`gene_name` from a
  UniProt TSV; only null fields are written.

## The pocket-comparison evidence pipeline

Retrieval unions three channels (global shape, PocketMatch — disabled
by default, chosen references), preserves every evidence dimension
without a master score, and derives a rule-based verdict. See
[../../analysis/pocket-evidence/README.md](../../analysis/pocket-evidence/README.md)
for the full model, the assessment thresholds, the worked METTL7
example, and the endpoints (`/evidence`, `/evidence/report.md`).

## Development workflow

- Modules: `mvn install -DskipTests` in `software/modules` to refresh
  snapshots; `mvn test` per module for tests.
- web-api: `mvn test` in `software/apps/web-api`. DB-backed tests use
  a throwaway `docking_test` schema in the dev database (recreated once
  per JVM, truncated per test, dropped never — see
  `DockingTestSchemaSupport`). Never run imports or `spring-boot:run`
  against the live database unless explicitly asked.
- web-ui: `npm run lint && npm test && npm run build` in
  `software/apps/web-ui`.
- Coding conventions: see the root `AGENTS.md`.
