# Athena Interaction Profiler V2 — Freeze

**Date:** 2026-09-05
**Git commit SHA:** `4b689ce6bb4f85ac948a0d413c5976a6c97913fd` (HEAD at freeze; **all V2 changes are uncommitted working-tree changes on top of this SHA** — commit them to make the freeze permanent)
**Build state at freeze:** athena module 503 tests green (`mvn -pl athena test`); gaia 158 green; mettl7 37 green.

Companion artifacts: `ATHENA_INTERACTION_V2_REGRESSION_RESULTS.csv` (688 rows), `ATHENA_INTERACTION_V2_PLIP_VALIDATION.csv` (50 rows), `ATHENA_INTERACTION_V2_THRESHOLDS.json`, `ATHENA_NEAR_ATTACK_MIGRATION_NOTE.md`, `tmp/plip-3.0.1-behavioral-reference.md`.

---

## Classes implemented (new)

**gaia (`totah.lab.gaia.geometry`)**
- `Plane3D` — least-squares plane fit (in-class 3×3 Jacobi eigensolver, no new dependency), canonical-sign unit normal, signed/absolute point-to-plane distance, orthogonal projection, acute plane–plane angle, degenerate-input rejection.

**athena (`totah.lab.athena.interaction` + `.perception`)**
- Perception: `HydrophobicAtomPerception`, `AromaticRingPerception`, `ChargedGroupPerception` (+ records `HydrophobicAtoms`, `AromaticRing`, `ChargedGroup`, enums `PerceptionProvenance`, `ChargeSign`, `ChargedGroupType`). All results carry degraded-provenance flags; nothing is guessed silently.
- Detectors: `HydrophobicContactDetector`, `PiStackingDetector` (PARALLEL + T_SHAPED), `PiCationDetector`, `SaltBridgeDetector` (charged-group centers), `HalogenBondDetector`, `HydrogenBondDetector` (re-expresses the legacy AD4 rules with injectable thresholds; verdict-for-verdict equivalence with the legacy analyzer tested).
- Model: `InteractionType` (7 types; water bridges / metal coordination deliberately absent), `Interaction` (immutable; type, residue, atom lists, distance, optional angles, group ids, threshold provenance), `InteractionThresholds` (`athenaDefaults()` / `plipReference()`, provenance-tagged), `InteractionRefinements` (PLIP precedence: salt bridge → HB suppression → pi-stack → HIS pi-cation suppression → hydrophobic refinement).
- Composition: `InteractionProfiler` (`profile(receptor, ligand[, cofactor])`, `profileComplex(complex, ligandSelector)`; SAM passable as distinct fixed cofactor/environment), `InteractionProfile`, `PerceptionSummary`, `InteractionFingerprint` (typed per-residue map, typed/residue Jaccard, records accessible — no master score), `ContactMatrix` (residue × type count + min-distance, `toCsv()`).

**athena (`totah.lab.athena.geometry`)**
- `GridVolume` (+ `FreeVolumeOptions`, `EnvelopeOptions`, `FreeVolume`, `EnvelopeVolume`, `SharedEnvelopeVolume`) — local free volume, ligand-envelope volume, shared/overlap volume. Consolidates 4 Python copies.

**athena (`totah.lab.athena.pocket.architecture`)**
- `EscapeRouteAnalyzer` (+ `EscapeRouteOptions`, `EscapeRouteAnalysis`, `EscapeRouteClassification`, `EscapeRouteComponent`, `OccupancySphere`) — occupancy/clearance grid, 26-connected origin-seeded flood fill, component labeling, boundary destination criterion, widest-path bottleneck metric, reproducible classification. Ported from stage8_11.

**mettl7 (`totah.lab.mettl7.sectors`)**
- `Mettl7Sectors` — frozen config only: 39–47, 144–175, 195–207, 228–237 (METTL7A numbering). No METTL7 numbering in generic athena code.

**Tests added:** `Plane3DTest` (24), perception tests (28), `StericClashAnalysisTest` (10), detector/threshold/refinement tests (52), profiler/fingerprint/matrix tests (11), regression package (8 classes, 15 tests), `GridVolumeTest` (10), `EscapeRouteAnalyzerTest` (6), `Mettl7SectorsTest` (7).

## Classes modified

- `software/modules/athena/pom.xml` — added test-scoped `totah.lab:hermes` (acyclic; documented in AGENTS.md).
- `AGENTS.md` — Testing section: regression-test rule + CSV contract.
- `HydrogenBondDetector` null-AD4-type guard (new code only).
- 13 historical Python near-attack scripts: deprecation headers added (no logic touched, nothing deleted).
- Legacy `ligand.interaction.DefaultLigandInteractionAnalyzer` **untouched**; its salt bridge is superseded in the new layer by `SaltBridgeDetector` (charged-group centers, 5.5 Å). Migrate consumers deliberately later.

## METTL7 regression results (ATHENA_INTERACTION_V2_REGRESSION_RESULTS.csv)

688 compared numbers: **590 REPRODUCED, 93 DELTA_DOCUMENTED, 5 NOT_COMPUTABLE**.
- DCMB F43/F199: all 22 distances bit-exact.
- TSL NAC: all 36 frozen states recompute within PDB rounding (≤1.3e-4 Å, ≤0.003°); SAM SD–CE bit-exact.
- DCMB×TSL overlap: all 276 rows exact (shared/core/swept volumes, path counts).
- Netarsudil: NAC table + all C202/C203 and wall contacts bit-exact/within historical rounding; all CLEARLY_NONPRODUCTIVE via `NearAttackAssessor`.
- Free volume: historical convention reproduces exactly on all grids; GridVolume's ligand-occupied convention shifts values down (~−176 Å³ typical) — documented per row.
- Stage12j: all Tyr47 distances and 8 raw H-bonds exact. Documented deltas: pi-stacking reports 0 on PDBQT inputs (degraded ligand rings refused + stricter 2.0 Å offset vs historical 2.5 Å); refined H-bonds 7 vs 8 raw (PLIP one-per-donor); hydrophobic counts differ (PLIP-style 4.0 Å perceived atoms vs historical residue-set 4.5 Å — 282 vs 1049 pairs).
- BRICS: empty retained set confirmed; 18 anchor values reproduce.
- 74-ligand matrix: geometry DB-only → NOT_COMPUTABLE (subset named for future export); ContactMatrix schema compatibility validated.
- Near-attack: historical Python gates (13 scripts, 2.7–3.5 Å / 145–150° disagreement) mapped to `athena.tmt` in the migration note; regression rows record reproduction status.

## PLIP 3.0.1 validation (ATHENA_INTERACTION_V2_PLIP_VALIDATION.csv)

Oracle: PLIP 3.0.1 (pip, isolated venv) on its own test fixtures 2w0s, 2reg, 3pxf, 1eve — all 7 types covered. Athena run twice (athenaDefaults + plipReference), structures prepared through the real hermes/hephaestus pipeline.
**50 rows: 29 MATCH / 16 PLIP_ONLY / 5 ATHENA_ONLY.**
- Hydrophobic 17/17 (≤0.002 Å); T-shaped pi 1/1; halogen 1/1 exact; parallel pi 2/3; HB 7/10 (3 are pure threshold-policy, vanish under plipReference, distances ≤0.003 Å).
- Systematic causes (not tuned away): phosphate/sulfate/sulfonate ligand groups unimplemented (4 PLIP-only salt bridges + knock-on HB suppression); tertamine guard applied to quaternary amines (7 choline pi-cations rejected); CCD vs OpenBabel aromaticity (1 pi-stack); N-degree-4 vs sp3 amine rule (1 pi-cation); hydrogen-placement differences (2 HB angles).
- Found probable hephaestus bug: terminal ASN/GLN NH2 hydrogenation places both H at identical coordinates (2reg ASN156) — filed as a follow-up, not fixed here (out of scope).

## Remaining known limitations

1. Pi-cation weakest oracle channel (0/8) for identified perception/guard reasons — revisit amine classification if the campaign needs choline-like ligands.
2. Ligand negative groups: carboxylate/guanidinium/amine/sulfonium only; phosphate/sulfate/sulfonate not perceived (documented in `ChargedGroupPerception`).
3. Pi-stack/pi-cation/halogen require a bond graph; PDBQT-only inputs degrade (protein rings still work via templates; degraded ligand rings are refused, never guessed).
4. Salt-bridge duality: legacy 4.0 Å charge-sum method still exists in `DefaultLigandInteractionAnalyzer` (untouched); new layer uses group centers at 5.5 Å.
5. Regression CSV regenerates fully only on a full regression-package run (harness rewrites it on JVM start).
6. `EscapeRouteAnalyzer`/`GridVolume` are O(cells × atoms) brute force — faithful ports; optimize only if profiling demands.
7. Cross-environment precedence (SAM salt bridge suppressing receptor H-bond) not modeled in the separate-cofactor profiler path.
8. 74-ligand matrix geometry requires a PostgreSQL pose export before end-to-end reproduction.
9. Documented PLIP deviations (11 items) are in `ATHENA_PLIP_GAP_MATRIX.csv` and detector javadoc — inclusive bounds, chain-aware keys, kept isolated contacts, no tertamine-break quirk, perfect-parallel accepted.

## Authoritative implementations (V2)

- Interaction thresholds: `InteractionThresholds.athenaDefaults()` — see `ATHENA_INTERACTION_V2_THRESHOLDS.json`. Never tuned to METTL7 outcomes; PLIP set exists only for oracle validation.
- Near-attack: `athena.tmt.NearAttackGeometry` / `NearAttackAssessor` / `EnsembleNacAnalyzer` (13 Python predecessors deprecated, retained for regression only).
- Volume: `athena.geometry.GridVolume`.
- Escape/connectivity: `athena.pocket.architecture.EscapeRouteAnalyzer`.
- Interactions: `athena.interaction.InteractionProfiler`.

## Stop condition met

Narrow V2 layer implemented, tested (503 athena tests), regression-validated against historical METTL7 outputs, oracle-validated against PLIP 3.0.1, frozen. The METTL7 production docking campaign is explicitly NOT started here.
