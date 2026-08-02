# Domain-model migration inventory

Status: Phase 0 and the first Gaia/pocket foundation checkpoints are complete.
Implemented work includes canonical residue/structure identity, immutable Gaia
structure connectivity, BioJava same-chain merging regressions, checked reader
error mapping, the immutable Gaia pocket model, typed fpocket/P2Rank metrics,
and pocket-reader mapping to Gaia. Hephaestus flexibility now uses Gaia
`ResidueId` instead of its duplicate `ResidueReference`. BioHub atom, ligand,
and structure models in `http-client` now use Gaia, and that artifact no longer
depends on `domain-model`. The `domain-model`
artifact remains scheduled for deletion, not rehabilitation.

## Final module ownership

- **Gaia** — what molecules are: molecular and structural value types,
  general chemical connectivity, geometry primitives, target identity, and the
  core pocket model.
- **Hermes** — how molecules enter and leave: external-format readers and
  writers, remote-service clients,
  external request/response DTOs, authentication, transport, artifact download,
  and mapping external payloads into Gaia-compatible representations. During
  migration, `pocket-reader` remains a small adapter module that returns Gaia
  pocket types.
- **Hephaestus** — how molecules are prepared: prepared protein/ligand topology,
  charges, atom typing, torsion, and flexibility models.
- **Athena** — how molecules are analyzed: pocket calculations, comparisons, scoring,
  contacts, and ligand-pocket spatial analysis. Athena does not exist as a Maven
  module yet.
- **Daedalus** — how workflows are executed; orchestration only.
- **Argus / pocket-viewer** — how molecules are visualized.
- **Atlas** — how data is stored: persistence, datasets, repositories, indexing,
  and caches. Atlas never owns canonical domain objects; persisted entities map
  to and from Gaia objects at the boundary.

The dependency direction is:

```text
Gaia <- Hermes
Gaia <- Hephaestus
Gaia <- Athena
Gaia/Hermes/Hephaestus/Athena <- Daedalus
Gaia/Athena <- Argus
Gaia <- Atlas
```

Gaia must not depend on any of the modules to its right.

## Legacy package destinations

| Legacy package or artifact | Destination |
|---|---|
| `totah.lab.io` | Hermes |
| `totah.lab.http-client` | Hermes |
| `totah.lab.pocket-reader` | Hermes after its returned model is migrated |
| `totah.lab.protein` | Gaia |
| `totah.lab.chemistry` | Gaia for general chemistry; Hephaestus for prepared topology |
| `totah.lab.ligand` | Gaia for ligand identity/model; Hephaestus for preparation |
| `totah.lab.docking.torsion` | Hephaestus |
| `totah.lab.pocket` | Gaia |

## Current artifact consumers

Direct Maven dependencies on `domain-model` currently exist in:

- `pocket-reader`
- `io`
- `http-client`
- `daedalus`
- `apps/lab-report`
- `apps/pocket-viewer`

`domain-model` is also present in the modules reactor and dependency management.
The `web-api` application consumes legacy pocket types through its application
dependencies even though it does not declare `domain-model` directly.

Legacy Java imports are concentrated in:

| Namespace | Direct source consumers |
|---|---|
| `totah.lab.protein` | `pocket-reader`, `io`, `http-client`, `daedalus`, `pocket-viewer` |
| `totah.lab.pocket` | `pocket-reader`, `daedalus`, `lab-report`, `web-api`, `pocket-viewer` |
| `totah.lab.chemistry` | `daedalus` |
| `totah.lab.ligand` | `daedalus`, `http-client` |
| `totah.lab.docking.torsion` | `io`, `daedalus` |

## Class-by-class disposition

| Current class | Current consumers | Replacement / destination | Action | Blocking differences |
|---|---|---|---|---|
| `chemistry.AtomChemicalProperties` | Daedalus ligand pipeline | `hephaestus.ligand.topology.LigandAtomProperties` | Reuse Hephaestus; delete old type | Migrate legacy ligand result and graph APIs first |
| `chemistry.ChemicalAtomFactory` | Daedalus ligand hydrogenation | Hephaestus hydrogen atom construction using Gaia `Atom` | Rewrite callers; delete factory | Old factory creates obsolete protein atoms |
| `chemistry.MolecularGraph` | Daedalus ligand charge, typing, torsion | Hephaestus `LigandTopology`; Gaia bonds for unprepared connectivity | Reuse replacements; delete graph | Legacy Daedalus stages expose it in result types |
| `docking.torsion.TorsionBranch` | Daedalus, old `io` writers | Hephaestus `LigandFragment` / `LigandFlexibilityModel` | Reuse Hephaestus; delete | Migrate old PDBQT writers to Hermes and stage results |
| `docking.torsion.TorsionTree` | Daedalus, old `io` writers | Hephaestus `LigandFlexibilityModel` | Reuse Hephaestus; delete | Same compatibility surface as `TorsionBranch` |
| `ligand.Ligand` | Daedalus, Biohub mapper, pocket geometry | Gaia `molecule.Ligand` | Reuse Gaia; delete | Consumer APIs and tests still accept old atom/residue model |
| `pocket.Dimensions` | Daedalus comparison and pocket geometry | Gaia `BoundingBox` dimensions or Athena calculation result | Delete after caller migration | Preserve existing volume semantics in characterization tests |
| `pocket.Pocket` | Readers, Daedalus, report, API, viewer | New immutable `gaia.pocket.Pocket` | Rewrite in Gaia | Current object is mutable, resolver-bound, nullable, and uses `Map<String,Object>` |
| `pocket.PocketBox` | Daedalus and geometry | Gaia `BoundingBox` | Reuse Gaia; delete | Confirm inclusive containment and zero-size behavior; use an Athena result only when derivation context is also returned |
| `pocket.PocketSource` | Pocket consumers and reports | `gaia.pocket.PocketSource` | Migrate | Preserve serialized names used by API/reporting |
| `pocket.ResidueRef` | Readers, reports, Daedalus | Canonical `gaia.structure.ResidueId` | Replace and delete | Identity is chain ID, residue number, and insertion code; residue name is validation evidence, not identity |
| `pocket.Sphere` | fpocket reader, analysis, viewer | `gaia.pocket.AlphaSphere` inside optional `AlphaSphereSet` | Rewrite in Gaia | Old type lacks sphere type and treats fpocket evidence as generic pocket state |
| `pocket.geometry.PocketGeometry` | Daedalus and pocket-viewer | Focused `athena.pocket` services | Characterize, split, then delete | Large static API mixes bounds, overlap, selection, comparison, and ligand analysis |
| `protein.ElementResolver` | Daedalus | Existing Gaia `chemistry.ElementResolver` | Reuse Gaia; delete | Verify calcium/alpha-carbon ambiguity and monoatomic residues |
| `protein.TargetId` | No current direct imports found | New `gaia.structure.StructureId` if instance identity is required | Replace old type | The old class conflates pipeline target, filename, and UniProt accession; Hermes should parse external accessions separately |
| `protein.Topology` | Daedalus receptor preparation | Hephaestus `ProteinTopology` / `TopologyModel` | Reuse Hephaestus; delete | Migrate stage contracts and old writer inputs |

## Already-deleted types still referenced by consumers

The working `domain-model` directory no longer contains its former `Atom`,
`Element`, `Point3D`, `Residue`, `Chain`, `Structure`, `Protein`, `ChemicalBond`,
or `BondOrder` implementations. This is why a clean reactor build fails. Their
remaining consumers must be changed to the existing Gaia equivalents; the old
classes must not be restored.

Before consumer migration, verify these Gaia semantics:

- `BioJavaStructureReader` merges polymer, non-polymer, and water fragments with
  the same normalized chain identifier into one Gaia chain while retaining
  residues in source order;
- structure conversion failures are exposed through the reader's documented
  `IOException` contract and do not leak `IllegalArgumentException`;
- chain IDs remain stable and duplicate BioJava fragments merge correctly;
- residue number plus insertion code uniquely identifies a residue;
- atom serial, input ordering, alternate location, occupancy, and B-factor are
  retained where required;
- `Bond` and `BondOrder` are first-class Gaia types and every Gaia `Structure`
  owns an immutable connectivity collection whose endpoints refer unambiguously
  to its atoms;
- collections returned from structural value objects are immutable;
- equality is intentional and does not accidentally identify atoms only by
  mutable preparation properties.

The BioJava chain-fragment behavior is a hard Phase 1 gate. Pocket readers and
BioHub mappers must not be migrated until ordinary PDB and mmCIF structures load
into the canonical Gaia model reliably.

## Canonical residue identity

Do not introduce a pocket-specific `ResidueRef`. Add one canonical identity
type to Gaia:

```java
public record ResidueId(
        String chainId,
        int residueNumber,
        Character insertionCode) {
}
```

`Character` is used instead of primitive `char` so the normalized absence of an
insertion code is represented as `null`, consistently with the current Gaia
`Residue`. Its constructor must normalize blank insertion codes and reject blank
chain IDs.

Gaia `Residue` should expose its `ResidueId`, and Gaia `Structure` should accept
it consistently in lookup operations:

```java
Residue residue(ResidueId id);
Optional<Residue> findResidue(ResidueId id);
boolean contains(ResidueId id);
```

The throwing `residue` method is appropriate only if Gaia establishes a stable
exception contract; `findResidue` remains the normal optional lookup. A pocket
stores `List<ResidueId>`. This identity must also replace:

- Hephaestus `flexibility.ResidueReference`;
- Daedalus's private flex-selection `ResidueId`;
- manually assembled `chain:number:insertion` string keys where those keys are
  not an external serialization format.

Residue name must not participate in identity or equality. Readers may retain an
`expectedResidueName` as optional source validation evidence and emit a warning
when it differs from the resolved structure. Mutation, engineered variants,
modified residues, and CCD normalization must not make residue resolution fail.

## Structure and external identity

Do not migrate the overloaded old `TargetId` unchanged. Use
`gaia.structure.StructureId` only when the application needs stable identity for
one structure instance. UniProt accessions, PDB IDs, AlphaFold model IDs, assay
targets, and filenames remain explicitly named external identifiers or Hermes
adapter inputs. Do not collapse them into a generic ID merely because they can
all be used to locate a structure.

Gaia connectivity is part of the structure, not preparation metadata. The
canonical structure model must expose immutable bonds alongside chains/atoms,
using Gaia `Bond` and `BondOrder`. Hephaestus may derive richer prepared
topologies from this connectivity, but it must not be the only owner of ordinary
molecular bonds.

## New Gaia pocket model

Introduce under `totah.lab.gaia.pocket`:

- `Pocket`
- `PocketId`
- `PocketSource`
- `PocketMetric`
- `PocketMetricType`
- `AlphaSphere`
- `AlphaSphereSet`

The generic `Pocket` may contain `Optional<AlphaSphereSet>` as optional
fpocket-specific evidence. Do not introduce a sealed feature hierarchy until at
least several genuinely different feature families require a common contract.
P2Rank pockets simply have no alpha-sphere set.

Scores must not be represented by nullable `Double`. Preserve source semantics
with `PocketMetricType` values such as `FPOCKET_DRUGGABILITY`, `FPOCKET_SCORE`,
`P2RANK_PROBABILITY`, `SOURCE_VOLUME`, `SASA`, and `ALPHA_SPHERE_COUNT`.
Pocket collections and metadata must use defensive copies.
Do not put a mutable resolver or bound `Protein`/`Structure` reference in the
pocket record.

Do not add a `PocketResidueResolver`. Gaia owns identity lookup through
`Structure.findResidue(ResidueId)`. Callers can resolve a pocket directly:

```java
pocket.residues().stream()
        .map(structure::findResidue)       // Stream<Optional<Residue>>
        .flatMap(Optional::stream)         // Stream<Residue>, if missing IDs are ignored
```

Athena owns higher-level selection, missing-reference reporting, proximity, and
contact analysis.

## BioHub disposition

| Current area | Destination | Action |
|---|---|---|
| BioHub client interfaces in `http-client` | `hermes.biohub.client` | Migrate |
| BioHub HTTP implementation | `hermes.biohub.internal` | Migrate and keep non-public |
| BioHub request/response JSON models | `hermes.biohub.dto` | Migrate |
| BioHub job IDs and statuses | `hermes.biohub.model` | Migrate |
| BioHub structure/ligand mappers | `hermes.biohub.mapper` | Rewrite against Gaia |
| BioHub workflow polling | Daedalus using the Hermes client | Split transport from orchestration |
| BioHub result persistence | Atlas | Add only when persistence is required |

Generic `Ligand`, `Structure`, and `Pocket` types must remain in Gaia rather than
being duplicated under a BioHub namespace.

## Planned package organization

Hermes separates passive file formats from active remote services:

```text
totah.lab.hermes.file.pdb
totah.lab.hermes.file.mmcif
totah.lab.hermes.file.fasta
totah.lab.hermes.file.pdbqt
totah.lab.hermes.file.fpocket
totah.lab.hermes.file.p2rank
totah.lab.hermes.service.biohub
```

Public BioHub client contracts live under `service.biohub`; transport,
serialization DTOs, and polling implementation details remain internal beneath
that service boundary.

Athena starts with explicit scientific areas rather than a miscellaneous utility
package:

```text
totah.lab.athena.pocket
    PocketComparison
    PocketComparator
    PocketAlignment
    PocketOverlap

totah.lab.athena.contacts
    ContactMap
    ContactCalculator

totah.lab.athena.geometry
    DistanceCalculator
    OverlapCalculator

totah.lab.athena.docking
    PoseAnalysis
```

Hephaestus should converge on domain capability packages (`protein`, `ligand`,
`topology`, `typing`, `charge`, `flexibility`, and `amber`) rather than exposing
pipeline stage structure as its long-term public organization.

## Error contracts

Do not introduce a generic shared-exception module. Exception ownership follows
the operation that can fail:

- Hermes uses checked `IOException` (or a checked format-specific subtype) for
  reading, writing, parsing, downloading, and conversion failures.
- Remote-service lookup failures such as a missing BioHub job or structure are
  Hermes service errors, not Gaia domain errors.
- Gaia constructors reject invalid value objects with stable validation messages;
  add a Gaia exception only when callers can meaningfully recover from a
  domain-specific failure beyond ordinary argument validation.
- Hephaestus retains preparation-specific checked/typed failures such as
  unsupported ligand reasons.
- Athena owns analysis precondition or calculation failures.

This avoids a nominally common exception vocabulary that couples unrelated
storage, transport, domain validation, and scientific operations.

## Compatibility surfaces requiring inventory

Before changing a public model, inventory more than imports:

- public method parameters and return types;
- record components and generic parameters;
- checked exceptions, constructors, and builder methods;
- Jackson type and field names;
- JPA converters, database column values, and serialized `PocketSource` names;
- report templates and CSV headers;
- reflection configuration, service loaders, Spring configuration, scripts, and
  documentation containing legacy class names.

Behavioral fixtures that protect parsing, geometry, ordering, serialization, or
scientific results should be ported. Tests that only enforce mutable pocket
state or resolver binding should be deleted with those intentionally removed
behaviors.

## Migration batches and gates

### Batch 1A: Gaia structural readiness

1. Fix and verify BioJava same-ID chain-fragment merging and exception mapping.
2. Add canonical `ResidueId` and make `Structure` use it consistently.
3. Close the remaining listed Gaia semantic gaps, including general bonds and
   bond order.
4. Add real PDB regressions for chain identity, insertion codes, atom order,
   alternate locations, and coordinates.
5. Add `gaia.target.TargetId`; keep parsing/conversion adapters in Hermes.

Gate: Gaia tests pass and Hermes real-structure tests prove the canonical model
can represent current inputs without loss.

### Batch 1B: Structure consumer migration

1. Replace deleted protein and geometry types in the old `io` module.
2. Migrate surviving readers and writers into Hermes or make them use Gaia
   immediately when relocation is deferred.
3. Migrate structure-returning BioHub mappers to Gaia.
4. Migrate other structure consumers that block pocket-reader compilation.

Gate: the complete structure loading and writing path uses Gaia without relying
on a stale installed `domain-model` artifact.

### Batch 2: Gaia pocket model

1. Add the immutable pocket types and typed metrics.
2. Store canonical `ResidueId` values and use `Structure` identity lookup.
3. Create characterization fixtures for fpocket and P2Rank covering ID, source,
   center, residue references, alpha spheres, metrics, bounds, and metadata.

Gate: new-model tests prove all information required by readers, reports, API,
and viewer is representable without `Map<String,Object>`.

### Batch 3: Readers and Athena analysis

1. Change `pocket-reader` to depend on Gaia and return Gaia pockets.
2. Keep source-format DTOs internal to the reader boundary.
3. Create the Athena module.
4. Characterize and split `PocketGeometry` into focused bounds, volume,
   comparison, residue selection, ligand overlap, contact, and alpha-sphere
   services.
5. Retain a temporary deprecated façade only while callers are being moved.

Gate: explicit `1.0e-6` numerical tolerances preserve bounds, centroid,
dimensions, volume, distances, overlap, intersection volume, IoU, and
pocket-center distance.

### Batch 4: Consumers and preparation models

Migrate in this order:

1. `lab-report`
2. `web-api`, using explicit REST and persistence mappers
3. `pocket-viewer` / Argus
4. Daedalus pocket utilities and orchestration
5. old Biohub/HTTP consumers
6. legacy ligand, topology, torsion, and old PDBQT-writer consumers

Gate: each migrated module builds without a `domain-model` dependency. Daedalus
uses Gaia/Hermes/Hephaestus/Athena types rather than duplicating chemistry.

### Batch 5: deletion and enforcement

1. Confirm no production or test imports remain under the forbidden legacy
   namespaces.
2. Delete `domain-model`.
3. Remove it from reactor modules, dependency management, and all POMs.
4. Verify without the locally installed legacy artifact, using an isolated Maven
   local repository rather than destructively altering the user's normal cache.
5. Run `mvn -U clean verify` for modules and applications.
6. Add architecture enforcement for forbidden packages and module direction.

Forbidden legacy namespaces:

```text
totah.lab.protein..
totah.lab.pocket..
totah.lab.chemistry..
totah.lab.ligand..
totah.lab.docking.torsion..
```

The check must allow the new namespaced packages such as
`totah.lab.gaia.pocket` and `totah.lab.hephaestus.ligand`.

## Deletion criterion

`domain-model` may be deleted only when all of the following are true:

- no module POM declares it;
- no source imports a forbidden legacy namespace;
- the full build succeeds with an isolated Maven repository;
- pocket reader characterization tests pass;
- Athena numerical characterization tests pass;
- native ligand and receptor regressions remain green.
