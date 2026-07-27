# TODO

## Open

- Move `domain-model/src/main/java/totah/lab/docking` to a better-fitting module/package. The torsion tree model is docking/flex-receptor behavior, not core protein domain state; likely homes are `pocket-api` for now or a future receptor/prep module after boundaries settle.
- Decide charge fallback policy for residues/atoms not covered by bundled Amber templates. Current docking prep uses Amber charges and atom types when Amber covers the residue and fails when it does not. We need a scientific design for special residues, ligands, cofactors, metals, modified residues, and missing template atoms before enabling computed charges as a fallback; that design should define when fallback is allowed, which charge model is acceptable, how total charge/protonation is chosen, how AD4 atom typing is assigned, and how the report makes mixed Amber/computed output explicit.
- Evaluate explicit TYS support from `/Users/yazan/Downloads/biology-12-00824-s001`.
  Do not use Forcefield_PTM as the assumed TYS source. The downloaded 2023
  package provides ff14SB-compatible O-sulfo tyrosine files:
  `MTYS.prepi`, `NTYS.prepi`, `CTYS.prepi`, `TYS.frcmod`,
  `leaprc.tys.ff14SB`, and `tys3.leap`. The README states RESP charges were
  generated via RED at HF/6-31G* using two conformations, missing bonded
  parameters were transferred from gaff2, and one sulfur atom type `SO` was
  added. Principal Java-loader need is PREPI residue definitions with atom
  names, ff14SB atom types, RESP partial charges, connectivity, and residue
  charges: mid-chain `TYS` -1, N-terminal `NTYS` 0, C-terminal `CTYS` -2.
  `TYS.frcmod` is needed for Amber minimization/MD, but may not be needed for
  current PDBQT template/charge assignment. Caveats before enabling: PREPI
  parser support, PDB `HETATM` to amino-acid `ATOM`/polymer handling, terminal
  state mapping, total-charge tests, atom-name compatibility with RCSB TYS, and
  explicit documentation that the authors say the parameter set is not
  extensively tested.
- Revisit docking-prep pocket-center policy. `PocketGeometry.calculateCenter(Pocket)` now computes a resolved receptor heavy-atom centroid first. This is the P2Rank path because P2Rank pockets have residue refs but no alpha spheres. fpocket may fall back to alpha spheres, then stored parser center; confirm whether this priority should remain for missing-heavy-atom proximity and grid-box generation.
- Decide whether to support an Open Babel compatibility export mode. Open Babel
  PDBQT references are compatibility references, not scientific
  source-of-truth references. Use them to validate rigid PDBQT shape and
  receptor heavy-atom preservation, but do not treat them as authority for
  Amber charges, protonation, missing-atom repair, or silent heavy-atom flips.
  A compatibility mode would need an explicit policy for polar hydrogens only,
  Gasteiger-style charges, B-factor zeroing, and Open Babel-like AD4 typing.
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
