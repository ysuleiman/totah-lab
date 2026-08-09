# Hermes I/O refactor plan — files (Part 1) and remote/HTTP (Part 2)

Status: PROPOSED — not started. Do not implement until the other in-flight
agent sessions working in `hermes/src/main/java/totah/lab/hermes/file` have
landed their changes (as of 2026-08-08 they created the `file/pdbqt`,
`file/vina`, `file/meeko`, `file/writer/pdb` packages and left empty
`file/{sdf,pdb,pdbqt}/{reader,writer}` directories; re-verify this plan
against the tree before starting).

Two halves, same disease: `file/` is a half-finished reorganization with
split models; the remote layer (`http`, `uniprot`, `rcsb`, `alphafold`,
`biohub`, `fasta`, `structure`, `metadata`) is two parallel generations
of clients with no shared transport. Part 1 = files, Part 2 = remote.
They meet at the structure-acquisition boundary (Part 2, Phase H3).

## Ground rules (apply to every phase)

1. **API changes are approved — but references travel with them.**
   Many modules depend on hermes (daedalus, hephaestus, web-api,
   pocket-viewer). Changing signatures/packages is fine PROVIDED every
   caller is fixed in the same pass and the whole repo builds green.
   No orphan references, no half-migrated modules. "Needs approval" in
   the phases below now means: confirm the caller-migration scope before
   starting that phase, not whether the change is allowed.
2. **DO NOT CHANGE SCIENCE. Behavior-preserving refactor only.** Parsed
   values, atom ordering, charge handling, AD4 typing, coordinate
   precision/rounding, output column positions, line endings, model
   ordering, and validation verdicts must be byte-for-byte /
   value-for-value identical before and after each phase. If existing
   behavior looks like a bug (e.g. the PDB vs PDBQT 3-char-name column
   13/14 difference), it is PRESERVED, documented, and flagged — fixed
   only in a separate, explicitly-scoped change with domain
   verification (Vina/Meeko/Open Babel compatibility). Dedup steps must
   prove equivalence with tests before deleting any copy.
3. Per AGENTS.md: minimal diffs, immutable records, checked exceptions
   for I/O, JUnit 5 tests for new functionality, no unrelated rewrites.

# Part 1 — file I/O

## Goal

One entry point for reading molecular files:

```java
MolecularFile file = registry.read(path);          // auto-detect format
Ligand ligand       = file.as(Ligand.class);
Structure structure = file.as(Structure.class);

Structure s = registry.read(path, Format.PDBQT)    // forced format
                      .as(Structure.class);
```

Two axes kept separate on purpose: **format** (SDF/PDB/PDBQT/mmCIF/pocket
files) vs **target type** (`Ligand`, `Structure`, `Pocket`, ...). `.as()`
is a view request over an already-parsed model, not a re-read.

## Design principles

1. **One shared model family per format.** Reader and writer of a format
   both consume/produce the SAME model types — never one model for
   reading and a parallel `*Input` family for writing. Today PDBQT
   violates this (read-side `PdbqtAtom`/`PdbqtModel` vs write-side
   `PdbqtAtomInput`/`PdbqtLigandInput`/... with no bridge); unifying it
   is a core phase, not optional (Phase 5).
2. **Gaia is the cross-format model — use it before inventing anything.**
   Anything two formats share (`Atom`, `Structure`, `Chain`, `Residue`,
   `Bond`, `AtomReference`, `Ligand`, `Element`, `Point3D`,
   `FormalCharge`, `BondOrder`, `Pocket`, `ResidueId`) lives in gaia,
   not in hermes. Format models only hold what the format adds (bonds/
   charges in `SdfLigand`, AD4 type/serial/torsion tree in the PDBQT
   model), and should REFERENCE gaia types for the rest instead of
   copying fields (today `PdbqtAtom` re-derives element/position; the
   write-side `*Input` records re-declare atom fields gaia already has).
   If a shared concept is missing from gaia, prefer adding it to gaia
   over duplicating it in hermes — gaia is the kernel, hermes is I/O.
3. **Models carry no I/O.** Records + derived values only; parsing,
   formatting, and mapping to gaia live in the format package's
   reader/writer/mapper classes.
4. No duplicated format logic: one atom-line formatter, one line parser,
   one AD4 type set (Phases 0-1).
5. Package layout format-centric; exception policy per repo AGENTS.md
   (checked exceptions for I/O).

## Current state (investigation findings, 2026-08-08)

### Layout problems

- Half-finished reorganization: per-format dirs
  `file/{sdf,pdb,pdbqt}/{reader,writer}` exist but are empty; real code
  sits in per-role packages (`file/reader`, `file/writer/{pdb,pdbqt}`)
  and flat format packages (`file/pdbqt`, `file/vina`, `file/meeko`,
  `file/pocket`).
- PDBQT has two incompatible model families with no bridge:
  - read-side: `file/pdbqt`: `PdbqtAtom`, `PdbqtModel`, `PdbqtFile`,
    `PdbqtBranch`, `PdbqtTorsionTree`, `AtomRecordType`
  - write-side: `file/writer/pdbqt`: `PdbqtAtomInput`, `PdbqtRigidAtomInput`,
    `PdbqtLigandInput`, `PdbqtLigandFragmentInput`, `PdbqtFragmentInput`,
    `PdbqtFlexibleReceptorInput`, `PdbqtFlexibleResidueInput`,
    `PdbqtRotatableBondInput`
- Models trapped in behavior packages: `SdfLigand` (pure record, used
  across modules) lives in `file/reader`; `StructureReaderOptions` lives
  in `hermes.structure`, outside `file/` entirely.

### Duplication

- Atom-line formatting/parsing in 4 places: `PdbAtomFormatter`
  (writer/pdb), `PdbqtAtomFormatter` (writer/pdbqt), `PdbqtReader`
  fixed-column parse (PdbqtReader.java:203-338), `PdbqtStructureValidator`
  whitespace re-parse (PdbqtStructureValidator.java:30-39). The two
  formatters are near-copies with a subtle difference (3-char atom names
  start col 14 in PDB vs col 13 in PDBQT) — resolve deliberately.
- AD4 20-type set copied 3x: `PdbqtWriter.LEGAL_AD4_TYPES` (line 20),
  `PdbqtSerializerValidator.TYPES` (line 14),
  `PdbqtStructureValidator.TYPES` (line 9).
- `PdbWriteOptions`/`PdbqtWriteOptions` isomorphic; result records
  inconsistently named (`output` vs `rigidOutput`).

### Cycles and validation sprawl

- Package cycle `pdbqt ↔ vina/meeko` via two unused imports in
  `PdbqtRemarks.java`.
- Bidirectional dependency `writer.pdbqt ↔ writer.pdbqt.validation`.
- Validation in 4 shapes for one format: inline in `PdbqtWriter`,
  inline in `PdbqtLigandSerializer`, report-based `validation/` package,
  dead private `PdbqtFlexibilitySerializer.validatePartition()` (lines
  179-189).

### Dead code

`PdbqtPose` (unreferenced), `PdbqtValidationOptions` (unreferenced),
`validatePartition()`, `PdbqtRemarks` marker (implemented by Vina, not
Meeko, consumed by nothing), duplicate `import java.util.List` in
`MeekoResult.java`, 8 empty dirs. `MeekoResultParser` does NOT implement
`PdbqtRemarkParser` despite an identical signature.

### Exception policy (violates AGENTS.md "checked exceptions for I/O")

- SDF/BioJava readers: checked `IOException` — OK.
- `PdbqtReader`: unchecked `PdbqtFormatException` — violates policy.
- Writer-side: `IllegalArgumentException` inline, and
  `PdbqtValidationException` extends `IllegalStateException`.

### De-facto public API (external callers — daedalus, hephaestus,
    apps/web-api, apps/pocket-viewer)

Top tier: `BioJavaStructureReader`, `StructureReader`, `PdbqtWriteResult`,
`SdfLigand`, `SdfLigandReader`, `PdbqtModel`, `PdbqtValidator`,
`PdbqtValidationReport`, `PdbqtWriter`, `PdbqtReader`, `PdbqtWriteOptions`.
Second tier: the `writer.pdbqt` `*Input` records,
`PdbqtFlexibilitySerializer`, `PdbqtLigandSerializer`, `FPocketParser`,
`AutoDetectingPocketReader`, `PocketReader`, `PdbqtAtom`, `PdbqtFile`,
`PdbqtGaiaMapper`, `PdbWriter`, `PdbWriteOptions`.
Per AGENTS.md: never change these without asking. Maven deps on hermes:
daedalus, hephaestus, web-api, pocket-viewer. athena/euclid/gaia/proteus
do NOT depend on hermes.

## Target package structure

```
totah.lab.hermes.file
├── api/                          ← abstractions + the registry (new front door)
│   ├── MolecularFileRegistry     ← registry.read(path[, format])
│   ├── MolecularFile             ← parsed model + .as(Class<T>) views
│   ├── Format                    (SDF, PDB, MMCIF, PDBQT, FPOCKET, P2RANK, ...)
│   ├── FormatHandler             ← SPI, one per format package
│   ├── MolecularFileException    (checked)
│   ├── FileReader<T>, StructureReader, PocketReader, LigandReader
│   └── FileValidator<R>
├── pdb/
│   ├── PdbFormatHandler
│   ├── PdbStructureReader        ← renamed BioJavaStructureReader
│   ├── PdbWriter, PdbWriteOptions, PdbWriteResult
│   └── internal/AtomLineFormatter  ← ONE column formatter (pdb + pdbqt)
├── pdbqt/
│   ├── PdbqtFormatHandler
│   ├── PdbqtAtom, PdbqtModel, PdbqtFile, PdbqtBranch, PdbqtTorsionTree,
│   │   AtomRecordType              ← THE pdbqt model (see Phase 5: after
│   │                                 unification this single family serves
│   │                                 reader AND writers; the *Input records
│   │                                 are absorbed/deprecated)
│   ├── PdbqtTypes                ← AD4 20-type set, defined once
│   ├── PdbqtReader, PdbqtGaiaMapper
│   ├── PdbqtWriter, PdbqtLigandSerializer, PdbqtFlexibilitySerializer
│   ├── PdbqtWriteOptions, PdbqtWriteResult
│   └── validation/               (PdbqtValidator + 3 validators + report records)
├── sdf/
│   ├── SdfLigand                 ← moved out of file.reader
│   ├── SdfLigandReader
│   └── SdfFormatHandler
├── vina/                         (VinaResult, VinaResultParser, VinaPose)
├── meeko/                        (MeekoResult, MeekoResultParser — implements PdbqtRemarkParser)
├── pocket/                       (as-is; already clean, gaia-only deps)
└── chemcomp/                     (ChemCompProviders, OnlineFallbackChemCompProvider)
```

`StructureReaderOptions` moves from `hermes.structure` into `file/api`
(or `file/pdb`).

## The registry design (the centerpiece)

```java
public interface FormatHandler {
    Format format();
    boolean supports(Path path);                 // extension first, content sniff second
    ParsedModel parse(Path path) throws IOException;
    Set<Class<?>> targets();                     // what .as() can return
    <T> T as(ParsedModel model, Class<T> target);
}
```

- `MolecularFileRegistry.read(path)`: handlers in deterministic priority
  order; first handler whose `supports()` returns true parses.
  No match → `MolecularFileException` listing what was tried.
- `MolecularFile.as(Class<T>)`: delegates to the handler; unsupported
  target → `MolecularFileException` listing supported targets.
  Convenience constants allowed (`MolecularFile.LIGAND = Ligand.class`)
  but class-based underneath so it's extensible.
- Handlers:
  - `sdf/SdfFormatHandler` → targets: `Ligand`, `SdfLigand`
  - `pdb/PdbFormatHandler` → targets: `Structure`
  - `pdbqt/PdbqtFormatHandler` → targets: `Ligand`, `Structure`,
    `PdbqtFile` (raw), later `VinaResult`, `MeekoResult` (finally gives
    the remark SPI a real consumer)
  - `pocket/PocketFormatHandler` → targets: pocket list; delegates to
    existing `AdapterRegistry` (fpocket/P2Rank)
- Open decision: `.as(Ligand.class)` on SDF returns the plain gaia
  `Ligand` (lossy: drops bonds/formal charges) — recommended, with
  `SdfLigand` available as the rich view.
- This generalizes what `file/pocket/AdapterRegistry` +
  `AutoDetectingPocketReader` already do for pockets.

## Phases

### Phase 0 — hygiene, zero API risk

1. Wait for in-flight sessions to land; delete the 8 empty per-format
   dirs if still empty.
2. Delete dead code: `PdbqtPose`, `PdbqtValidationOptions`,
   `PdbqtFlexibilitySerializer.validatePartition()`, unused imports in
   `PdbqtRemarks.java` (breaks pdbqt↔vina/meeko cycle), duplicate import
   in `MeekoResult.java`.
3. Extract `PdbqtTypes` (AD4 20-type set); point `PdbqtWriter`,
   `PdbqtSerializerValidator`, `PdbqtStructureValidator` at it.
   Same visibility, no signature changes.
4. Make `MeekoResultParser` implement `PdbqtRemarkParser<MeekoResult>`.

### Phase 1 — dedupe behavior, no public API change

5. Unify atom-line formatting into `pdb/internal/AtomLineFormatter`
   with an explicit PDB vs PDBQT column mode; resolve the col 13/14
   3-char-name difference deliberately (verify against Open Babel/Meeko
   output per AGENTS.md domain rules). Keep the old formatters as thin
   delegates first, then inline.
6. Make `PdbqtStructureValidator` use the same line parser as
   `PdbqtReader` (no more whitespace re-tokenization).
7. Align `PdbWriteResult`/`PdbqtWriteResult` naming where possible
   without breaking callers (add accessors, deprecate old ones).

### Phase 2 — registry first (the new front door), additive only

8. Create `file/api` with `Format`, `FormatHandler`, `MolecularFile`,
   `MolecularFileRegistry`, `MolecularFileException`.
9. Implement the 4 handlers on top of today's classes IN THEIR CURRENT
   PACKAGES. No moves yet. Zero breakage.
10. JUnit 5 tests per AGENTS.md: detection per format (extension +
    content sniff, fixtures already under src/test/resources),
    `.as()` per supported target, unsupported-target error message,
    unknown-format error, forced-format read.

### Phase 3 — migrate callers to the registry (needs approval)

11. Switch daedalus / hephaestus / web-api / pocket-viewer entry points
    to `registry.read(...)` where it fits (`BioJavaStructureReader`,
    `SdfLigandReader`, `PdbqtReader`, pocket reader call sites).
    Incremental, one module at a time, tests green per module.

### Phase 4 — package reorganization behind the facade (needs approval)

12. Move classes to the target structure above. Every move is an FQN
    change; only remaining direct users of moved classes need updates
    after Phase 3. Update tests to mirror; fix misplaced
    `writer/PdbqtWriterTest`; move vina/meeko parser tests out of
    `PdbqtReaderTest`.
13. Move `StructureReaderOptions` into `file/`.
14. Exception policy fix on PDBQT read path: checked exception per
    AGENTS.md (API-visible — bundle with this phase).

### Phase 5 — one shared PDBQT model family (core, needs approval)

Per design principles 1-2: reader and writers must operate on the same
model, built on gaia.

15. Audit `PdbqtAtom` and the write-side `*Input` records field by field
    against gaia: anything that is just an atom (name, element,
    position, charge) becomes a gaia `Atom`/`AtomReference` reference;
    the model keeps only format extras (serial, AD4 type, record type,
    branch/torsion structure).
16. Extend the read-side family (`PdbqtAtom`/`PdbqtModel`/`PdbqtBranch`/
    `PdbqtTorsionTree`) to express everything the writers need —
    ligand fragments, rotatable bonds, flexible-residue partitions —
    since it already models ROOT/BRANCH/TORSDOF.
17. Provide the reverse mapping next to `PdbqtGaiaMapper`:
    gaia `Structure`/`Ligand` → shared PDBQT model (today hephaestus
    adapters hand-build the `*Input` records; that logic moves into
    hermes where it belongs).
18. Switch `PdbqtWriter`, `PdbqtLigandSerializer`,
    `PdbqtFlexibilitySerializer` and the validators to consume the
    shared model; deprecate the `Pdbqt*Input` records, then remove them
    once hephaestus (`PdbqtLigandAdapter`,
    `PdbqtFlexibleReceptorAdapter`) and daedalus are migrated.
19. Apply the same rule to every other format: SDF writer (when added)
    consumes `SdfLigand`; vina/meeko remark models are shared by their
    parsers and any future writers; new formats must land model-first
    with reader and writer against it.

## Risks / decisions needed

- Caller blast radius: Phases 3-5 change the de-facto public API — under
  ground rule 1 every caller in daedalus / hephaestus / web-api /
  pocket-viewer is updated in the same pass; confirm the scope list
  before starting each phase. Phases 0-2 are additive/internal only.
- Column-mode difference between PDB/PDBQT formatters: PRESERVE both
  behaviors exactly (ground rule 2). Unify the code, not the output —
  the unified formatter takes a column mode that reproduces today's
  bytes. Any future behavior change is a separate, domain-verified task.
- Other sessions have uncommitted work in `hermes/file` — re-run this
  plan's assumptions against the landed tree before Phase 0.
- Cross-module duplication noted: `daedalus.docking.VinaPose` /
  `VinaOutputParser` vs `hermes.file.vina` — same record name, different
  shape. Decide ownership when the registry starts serving vina results;
  out of scope for Phases 0-4.

## Verification

- FIRST, before Phase 0: characterization tests pinning today's
  behavior — golden files for writer outputs (PDB/PDBQT/SDF fixtures
  written byte-for-byte), parse-result equality on existing test
  resources, validation-report equality on the validation fixtures.
  These are the safety net for ground rule 2 and stay green through
  every phase.
- `mvn -pl hermes test` green after every phase.
- After any phase touching call signatures: full builds + tests of
  daedalus, hephaestus, web-api, pocket-viewer in the same pass —
  no commit leaves a consumer broken.
- Add a formatter round-trip consistency test (PDB write → read back,
  PDBQT write → `PdbqtReader` read back, atom ordering preserved).

---

# Part 2 — remote / HTTP layer

## Goal

Same treatment as `file/`: one organized `http` tree with shared
transport plumbing and one client per service (UniProt, RCSB, AlphaFold,
BioHub), so callers stop constructing ad-hoc clients. Symmetric with the
file registry, the long-term entry point is a structure-acquisition
facade (Phase H3):

```java
ResolvedStructures refs = structures.resolve(accession);   // UniProt -> RCSB/AlphaFold refs
Structure s = structures.fetch(accession)                  // resolve + download + parse
                        .as(Structure.class);              // via the file registry
```

## Current state (investigation findings, 2026-08-08)

### Layout / generations

- Two parallel generations, both compiled and tested:
  - rcsb: `RestRcsbClient` + `internal/RcsbSearchJson` (current) vs
    `RcsbGeneralSearcher` + `RcsbQueryFactory` (legacy — own
    `HttpClient.newHttpClient()`, no timeouts, raw exceptions,
    ZERO production callers).
  - biohub: `BiohubEsmFold2Client` (current, typed model) vs
    `EsmFold2Client` + `config/EsmHttpClientConfig` (legacy — raw PDB
    string, `main()` demo, ZERO callers; sole reason hermes pom carries
    `spring-context`).
- `http/` package mixes plumbing (`HttpClientFactory`,
  `HttpRequestBuilder`) with an orchestrator
  (`DefaultStructureResolutionClient`) and two dead interfaces
  (`ProteinMetadataClient`, `ProteinStructureClient` — no
  implementations; `metadata/ProteinMetadata` exists only for them).
- `alphafold/` and `exception/` are EMPTY directories; AlphaFold URL
  building is inlined in `DefaultStructureResolutionClient` (:30-33,
  :112-131). No AlphaFold client exists.
- `fasta/` (FastaParser/FastaWriter/FastaRecord) is fully orphaned —
  zero callers in main code repo-wide. It is a text format: it belongs
  under `file/` as `Format.FASTA` with a `FastaFormatHandler` (Part 1
  registry), NOT in the http tree.
- `biohub/artifact/` is file I/O (JSON persistence), not remote —
  candidate for `file/` long-term, but it is de-facto public API
  (`BiohubPocketEvidenceReader` is web-api's heaviest hermes dependency),
  so leave in place until Phase H2.
- `structure/StructureReaderOptions` is a file-I/O record trapped in the
  remote layer (already flagged in Part 1).

### Duplication

- Five ways to build an HttpClient: `HttpClientFactory`,
  `JdkBiohubHttpTransport` (:17-20), `BiohubAtlasClient` (:34-37),
  `EsmFold2Client` (:28-31), `RcsbGeneralSearcher` (:24). Only uniprot +
  rcsb use the shared factory.
- Two request idioms: `HttpRequestBuilder` (uniprot, rcsb) vs raw
  `HttpRequest.newBuilder` (all biohub). Two incompatible transport
  abstractions: public `http.*` vs package-private `BiohubHttpTransport`.
- Retry exists ONLY in `RestUniProtClient.sendWithRetry` (:258-322).
- Copy-pasted helpers: error-body truncation x4 (`responseMessage` in
  RestUniProtClient :431-445 / RestRcsbClient :186-192, `abbreviate` in
  BiohubEsmcClient :204-207 / BiohubEsmFold2Client :442-445),
  `normalizeBaseUri` x2, accession/PDB-id regex validation x3.
- `BiohubAtlasClient` sends no Authorization header while every other
  biohub client does — against the same host.
- Structure acquisition fragmented: resolution (http → uniprot, URIs
  only, no download), `RestRcsbClient.downloadCif` (downloads), no
  AlphaFold downloader, biohub folding as a third source outside the
  `StructureSource` enum.
- Exception idioms x3: `UniProtException`/`RcsbException` extend
  IOException (OK per AGENTS.md), `StructureResolutionException` extends
  Exception, biohub throws raw IOException, legacy throws RuntimeException.

### De-facto public API (external callers)

web-api: `BiohubPocketEvidence`(+`ResidueContact`) (7 files),
`BiohubPocketEvidenceReader` (5), uniprot stack (`UniProtClient`,
`RestUniProtClient` ctor, `UniProtEntry`, `UniProtAnnotation`,
`UniProtCrossReference`, `UniProtException`).
daedalus: `BiohubAtlasClient`, `BiohubClientConfig`,
`BiohubEsmFold2Client`, `BiohubEsmFold2Config`, `BiohubComplexMapper`,
`MolecularComplexPrediction(ArtifactWriter)`,
`structure.StructureReaderOptions`.
Zero external callers: entire rcsb, fasta, http, metadata,
`BiohubEsmcClient` + `ResidueConstraint*`.
hephaestus/pocket-viewer use only `hermes.file.*`.

### Test hygiene

- Misplaced test tree: `src/test/java/totah/lab/http/biohub/**` declares
  `package totah.lab.hermes.biohub` (path/package mismatch).
- `structure/StructureResolutionClientTest` is a live-network smoke test
  against the real UniProt API masquerading as a unit test.
- Two fake-HTTP idioms (JDK HttpServer vs hand-rolled fake transport).
- No tests: `HttpClientFactory`, `RestRcsbClient`, `BiohubComplexMapper`,
  `BiohubClientConfig`.
- README.md is stale (documents only readers/writers; the whole remote
  layer is undocumented).

## Target package structure

```
totah.lab.hermes.http
├── transport/                  ← the ONLY place HTTP plumbing lives
│   ├── HttpClientFactory       (moved from http/)
│   ├── HttpRequestBuilder      (moved from http/)
│   ├── HttpRetry               (generalized from RestUniProtClient.sendWithRetry)
│   └── HttpErrors              (error-body truncation, status helpers)
├── uniprot/                    (UniProtClient, RestUniProtClient, DTOs, internal/ parser)
├── rcsb/                       (RcsbClient, RestRcsbClient, criteria + DTOs, internal/)
├── alphafold/                  (NEW AlphaFoldClient — URL building extracted
│                                from DefaultStructureResolutionClient + download)
├── biohub/
│   ├── BiohubEsmcClient, BiohubEsmFold2Client, BiohubAtlasClient
│   ├── BiohubClientConfig, BiohubEsmFold2Config
│   ├── BiohubComplexMapper
│   ├── model/                  (as-is)
│   └── artifact/               (as-is for now; see Phase H2 note)
└── structure/                  (resolution model + StructureResolutionClient
                                 + DefaultStructureResolutionClient)
```

Cross-tree moves: `fasta/` → `file/fasta` (+ `FastaFormatHandler` in the
Part 1 registry); `structure/StructureReaderOptions` → `file/` (Part 1).
Delete `metadata/` and the empty `exception/` dir.

## Phases

### Phase H0 — hygiene, zero production-API risk

1. Delete legacy stacks + their tests: `RcsbGeneralSearcher`,
   `RcsbQueryFactory`, `EsmFold2Client`, `EsmHttpClientConfig`
   (zero production callers). This drops the `spring-context` dependency
   from hermes pom — verify nothing else imports spring first.
2. Delete dead abstractions: `http/ProteinMetadataClient`,
   `http/ProteinStructureClient`, `metadata/ProteinMetadata`.
3. Delete empty `alphafold/` / `exception/` dirs (alphafold returns in
   Phase H2 with real content).
4. Move `totah/lab/http/biohub` test tree to the correct path; quarantine
   the live-network `StructureResolutionClientTest` (tag or delete).

### Phase H1 — one transport, no API change

5. Create `http/transport/`: move `HttpClientFactory` +
   `HttpRequestBuilder`, generalize `sendWithRetry` into `HttpRetry`,
   extract `HttpErrors` (single error-body helper). uniprot + rcsb keep
   working unchanged.
6. Migrate biohub clients onto the shared transport (internal change;
   keep `BiohubHttpTransport` as a thin adapter or inline it). Add retry
   to rcsb + biohub. Add the missing Authorization header to
   `BiohubAtlasClient`.
7. Centralize identifier validation (accession / PDB-id regexes) in one
   package-private helper.

### Phase H2 — package reorganization (needs approval)

8. Move uniprot/rcsb/biohub under `http/` per the tree above; move
   `fasta` → `file/fasta` with a `FastaFormatHandler`; move
   `StructureReaderOptions` → `file/`. FQN changes — update callers
   (web-api uniprot + pocket-evidence services, daedalus biohub batches).
9. Extract `AlphaFoldClient` from `DefaultStructureResolutionClient`'s
   inlined URL building; give it a real download method mirroring
   `RestRcsbClient.downloadCif`.
10. Align exception policy: one checked `HttpServiceException` family
    (or keep per-service exceptions extending a common checked base) —
    API-visible, bundle with this phase.
11. Decide `biohub/artifact/` ownership (stays under http/biohub vs
    moves to file/) — it is web-api's heaviest dependency, so default to
    staying.

### Phase H3 — structure-acquisition facade (optional, the "like file" part)

12. `http/structure` facade tying resolution → download → the Part 1
    file registry: resolve(accession) returns ranked references
    (RCSB experimental preferred over AlphaFold, as today);
    fetch(accession) downloads and parses via
    `MolecularFileRegistry.read(path).as(Structure.class)`.
    Register BioHub ESMFold as a third `StructureSource` or keep it a
    separate prediction path — open decision.

## Risks / decisions needed (Part 2)

- Deleting the legacy stacks is safe per caller analysis, but they are
  committed code with tests — confirm nothing reflective/Spring wires
  them (textual search says no).
- Dropping `spring-context` from hermes pom: verify downstream modules
  do not rely on it transitively.
- `BiohubAtlasClient` auth-header addition changes runtime behavior
  against production biohub — verify against the real service.
- The live-network test currently provides the only end-to-end coverage
  of the resolution path; replace with a fake-transport test before
  deleting.

## Verification (Part 2)

- Before H0/H1: pin current client behavior with recorded-response tests
  (fixed JSON/TSV payloads through the existing parsers and clients via
  fake transport) so transport consolidation provably changes nothing
  on the wire or in parsed output (ground rule 2).
- `mvn -pl hermes test` green after every phase; repo-root + web-api +
  daedalus builds green in the same pass as any signature change
  (ground rule 1).
- Fake-transport unit tests for every migrated client (pick ONE idiom —
  the hand-rolled fake transport is simpler than JDK HttpServer).
- No live-network tests in the unit suite; if an end-to-end smoke test
  is wanted, gate it behind an explicit flag/profile.
- After H1: grep for `HttpClient.newHttpClient` / `HttpRequest.newBuilder`
  in hermes main — only `http/transport` may contain them.
- Update README.md to document the remote layer (currently undocumented).
