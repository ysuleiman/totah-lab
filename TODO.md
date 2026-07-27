# TODO

## Open

- Move `domain-model/src/main/java/totah/lab/docking` to a better-fitting module/package. The torsion tree model is docking/flex-receptor behavior, not core protein domain state; likely homes are `pocket-api` for now or a future receptor/prep module after boundaries settle.
- Decide charge fallback policy for residues/atoms not covered by bundled Amber templates. Current docking prep uses Amber charges and atom types when Amber covers the residue and fails when it does not. We need a scientific design for special residues, ligands, cofactors, metals, modified residues, and missing template atoms before enabling computed charges as a fallback; that design should define when fallback is allowed, which charge model is acceptable, how total charge/protonation is chosen, how AD4 atom typing is assigned, and how the report makes mixed Amber/computed output explicit.
- Revisit full Maven test failures separately from compile checks.

## Done

- Removed duplicate `TargetId` and `PocketSource` classes from `pocket-api`; `domain-model` is now the source of truth.
- Moved `Dimensions` to `domain-model` and updated `pocket-api` imports.
- Added shared `ElementResolver` in `domain-model` and wired topology/charge stages through it.
- Extracted flex receptor export helpers: `FlexResidueSelector` and `FlexTorsionTreeBuilder`.
- Extracted hydrogen optimization scoring into `HydrogenScorer`.
- Extracted backbone hydrogenation into `BackboneHydrogenator`.
- Removed placeholder/demo `Main` entrypoints and the remaining production `main` method.
- Fixed `PipelineFactory` run-directory context so `ContextKeys.RUN_DIRECTORY` matches `PipelineContext#getRunDirectory()`.
- Normalized production pipeline context access through `ContextKeys` for shared stage/config keys.
