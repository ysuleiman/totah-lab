# TODO

## Open

- Connect the pocket Report button to the new database-backed reporting flow.
  Let the user select a docking run, call
  `GET /api/pockets/{pocketId}/report?runId={runId}`, present the structured
  pocket report in the UI, and add a downloadable narrative PDF endpoint.
  Replace or clearly distinguish the older structure report so the button's
  behavior is unambiguous.
- Move `domain-model/src/main/java/totah/lab/docking` to a better-fitting module/package. The torsion tree model is docking/flex-receptor behavior, not core protein domain state; likely homes are `pipeline` for now or a future receptor/prep module after boundaries settle.
- Decide charge fallback policy for residues/atoms not covered by bundled Amber templates. Current docking prep uses Amber charges and atom types when Amber covers the residue and fails when it does not. We need a scientific design for special residues, ligands, cofactors, metals, modified residues, and missing template atoms before enabling computed charges as a fallback; that design should define when fallback is allowed, which charge model is acceptable, how total charge/protonation is chosen, how AD4 atom typing is assigned, and how the report makes mixed Amber/computed output explicit.
- Continue validation of explicit TYS support from
  `/Users/yazan/Downloads/biology-12-00824-s001`. Do not use Forcefield_PTM as
  the assumed TYS source. The bundled 2023 package provides ff14SB-compatible
  O-sulfo tyrosine PREPI templates and `TYS.frcmod`; core template loading,
  terminal mapping, total-charge tests, and current PDBQT assignment are now
  implemented. Remaining review items: compare atom naming against additional
  RCSB TYS examples, decide whether/when `TYS.frcmod` should be parsed for
  Amber minimization/MD, and continue investigating the other unsupported
  `1A4W` chemistry (`QWE`) separately from TYS.
- Revisit docking-prep pocket-center policy. `PocketGeometry.calculateCenter(Pocket)` now computes a resolved receptor heavy-atom centroid first. This is the P2Rank path because P2Rank pockets have residue refs but no alpha spheres. fpocket may fall back to alpha spheres, then stored parser center; confirm whether this priority should remain for missing-heavy-atom proximity and grid-box generation.
- Revisit derived residue annotations after the docking data and score bands are finalized. Migrate annotations to the canonical `residue.id`, make derivation reproducible from the contact materialized views, decide when derived assignments should refresh, and distinguish `DOCKING_ANCHOR` from `PRODUCTIVE_CONTACT` because their current rules select the same residues. Also define and validate rules before assigning the currently unused `STRUCTURAL_HUB`, `SELECTIVITY_HOTSPOT`, `UNFAVORABLE_CONTACT`, and `PERIPHERAL` roles.
- Rebuild the legacy residue pair/triplet materialized views from the compact `pose_residue_contact` table. Their replacements must be scoped by `docking_run.id`, use canonical `residue.id`, count distinct ligands, and share the same score-band policy as the residue-level views.
- Migrate remaining legacy consumers and tests from
  `ContextKeys.EXTRACTED_LIGANDS` to `StructureCleanupResult`. The ligand
  preparation orchestrator now consumes the typed result; keep the legacy key
  until its public API removal is approved.
- Decide whether to support an Open Babel compatibility export mode. Open Babel
  PDBQT references are compatibility references, not scientific
  source-of-truth references. Use them to validate rigid PDBQT shape and
  receptor heavy-atom preservation, but do not treat them as authority for
  Amber charges, protonation, missing-atom repair, or silent heavy-atom flips.
  A compatibility mode would need an explicit policy for polar hydrogens only,
  Gasteiger-style charges, B-factor zeroing, and Open Babel-like AD4 typing.
- Revisit full Maven test failures separately from compile checks.

## Done

- Removed duplicate `TargetId` and `PocketSource` classes from `pipeline`; `domain-model` is now the source of truth.
- Moved `Dimensions` to `domain-model` and updated `pipeline` imports.
- Added shared `ElementResolver` in `domain-model` and wired topology/charge stages through it.
- Extracted flex receptor export helpers: `FlexResidueSelector` and `FlexTorsionTreeBuilder`.
- Extracted hydrogen optimization scoring into `HydrogenScorer`.
- Extracted backbone hydrogenation into `BackboneHydrogenator`.
- Removed placeholder/demo `Main` entrypoints and the remaining production `main` method.
- Fixed `PipelineFactory` run-directory context so `ContextKeys.RUN_DIRECTORY` matches `PipelineContext#getRunDirectory()`.
- Normalized production pipeline context access through `ContextKeys` for shared stage/config keys.
