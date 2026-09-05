# Athena Interaction Implementation Plan

**Date:** 2026-09-05
**Status:** Proposal only — derived from `ATHENA_INTERACTION_CAPABILITY_INVENTORY.md`, `ATHENA_PLIP_GAP_MATRIX.csv`, `ATHENA_INTERACTION_CODE_MAP.csv`. No production code has been written.

Guiding constraints (from inventory and `AGENTS.md`):

- Extend existing packages; do not create a parallel framework.
- Athena depends only on gaia (+ commons-math3). Keep it that way — no CDK/RDKit/Open Babel dependency; perception stays hand-rolled Java, consistent with the existing `GasteigerModel`/`QEqModel` precedent.
- Immutable objects, builders where appropriate, JUnit 5 tests for everything new.
- No evidence-dimension merging; the profiler reports typed interactions, it does not score.
- Functional equivalence with PLIP where useful — not a source port, no copied code.

---

## 1. Architecture (smallest sufficient)

### 1.1 `gaia.geometry` — one new primitive

- `Plane3D` — immutable least-squares plane over ≥3 points: centroid, unit normal (canonical sign), `distanceTo(Point3D)`, `project(Point3D)`, `angleTo(Plane3D)` (acute, degrees). Implement on top of the existing eigendecomposition approach already proven in `athena PrincipalComponents` / `WallGeometryAnalyzer.localPlane()` — extract, don't reinvent.
- Test: `Plane3DTest` (known planes, symmetric rings, degenerate collinear input).

This is the single primitive that unblocks all pi-interaction detection. Everything else geometric already exists (`Point3D`, `Vector3D`, `Dihedral`, centroids).

### 1.2 `athena.interaction.perception` — three new perception classes

All operate on `gaia` types (`Structure`, `Atom`, `Bond`, `Element`) and consume the AD4 typing that hephaestus already assigns during preparation. No new dependencies.

- `HydrophobicAtomPerception` — PLIP rule: carbon whose bonded neighbors ⊆ {C, H}. Requires the bond graph; where connectivity is `ABSENT`/`PARTIAL` (per gaia `ConnectivityMetadata`), fall back to AD4 type `C`/`A` and record the degraded provenance on the result.
- `AromaticRingPerception` — ligand side: smallest 5/6-membered cycles over the bond graph + the existing Hückel logic (reuse/promote `hephaestus KekuleAromaticity` — either move it to gaia/athena or expose its result on the prepared ligand; moving a class changes a public-ish API, so prefer exposing ring sets as output of the prep pipeline and consuming them). Protein side: PLIP's own approach — PHE/TYR/TRP/HIS ring atoms by residue-name template (AD4 `A`-typed atoms already mark them). Returns `Ring` records (atoms, centroid, `Plane3D`).
- `ChargedGroupPerception` — PLIP `is_functional_group` connectivity rules: carboxylate, guanidinium, quaternary/protonated amine, sulfonium (positive); carboxylate, phosphate, sulfate/sulfonate (negative). Protein side may shortcut via residue names (ARG/LYS/HIS sidechain N; ASP/GLU sidechain O) exactly as PLIP does. Returns charged-group records with charge-center centroid.

Tests: one per class, using small synthetic structures plus METTL7 fixtures already under `athena/src/test/resources/mettl7/`.

### 1.3 `athena.interaction` — detectors behind the existing interface

Keep `LigandInteractionAnalyzer` as the entry point. Extend, don't replace:

- Unify the two `InteractionType` enums (`ligand.interaction` {HB, SALT_BRIDGE} and `pocket.evidence` annotation vocabulary) into one detector-backed enum: `HYDROGEN_BOND, SALT_BRIDGE, HYDROPHOBIC_CONTACT, PI_STACK_PARALLEL, PI_STACK_T_SHAPED, PI_CATION, HALOGEN_BOND`. Keep `pocket.evidence.InteractionType` as the *evidence annotation* vocabulary if its semantics differ — do not conflate detector output with evidence records (AGENTS.md evidence rules).
- `HydrophobicContactDetector` — pairs of perceived hydrophobic atoms, d ≤ 4.0 Å; PLIP refinements: exclude pairs already in a pi-stack; keep closest per (ligand atom, residue).
- `PiStackingDetector` — ring pairs; centroid ≤ 5.5 Å; normal angle 0±30° → PARALLEL, 90±30° → T_SHAPED; offset (mutual center-into-plane projection via `Plane3D`) ≤ 2.0 Å. Logic mirrors the proven `stage12j run_stage12j.py classify_pi()` but with PLIP's 2.0 Å offset (stage12j used 2.5 Å) and offset enforced for T-shaped too.
- `PiCationDetector` — ring centroid ↔ charge center ≤ 6.0 Å + offset ≤ 2.0 Å; tertiary-amine guard (ring-normal vs substituent-normal > 30°); exclude pairs already pi-stacked.
- `HalogenBondDetector` — C–X donors (X ∈ {Cl,Br,I}; F per PLIP halocarbon rule) vs O/N/S acceptors; O···X ≤ 4.0 Å; acceptor angle 120±30°; donor angle 165±30°. Justified for METTL7: DCMB is dichlorinated and its Cl contacts are currently miscounted as plain hydrophobic.
- Salt-bridge upgrade in place: replace the whole-residue charge-sum heuristic in `DefaultLigandInteractionAnalyzer.saltBridge()` with charged-group centers ≤ 5.5 Å. Keep the old behavior behind the existing constants only if a regression consumer needs it; otherwise change deliberately and update `DefaultLigandInteractionAnalyzerTest`.
- `InteractionProfiler` — thin orchestrator: runs the detectors over a prepared receptor + ligand (+ optional fixed cofactor, see §3), returns immutable `Interaction` records (type, residue, ligand atoms, protein atoms, distance, angles where relevant). Composition over the existing `DefaultContactAnalyzer` for the residue contact layer.
- H-bond cutoff decision: current 3.5 Å/120° is stricter than PLIP's 4.1 Å/100°. Keep ours as default (it is the validated in-house convention reproduced by stage12j) and make thresholds injectable; add PLIP's one-H-bond-per-donor refinement.

### 1.4 `athena.interaction.fingerprint` — typed fingerprint + matrix

- `InteractionFingerprint` — per-residue map `ResidueId -> Set<InteractionType>` (+ the atom-pair detail from `Interaction` records), with Jaccard/Tanimoto-style set comparison. Supersedes `tools/scripts/vina_vs_biohub_interaction_fingerprint.py` (which is distance-only) and gives `ContactStringRenderer` a typed backend.
- `ContactMatrix` — residue × ligand (or residue × interaction-type) matrix view for campaign export; supersedes the SQL/Python 74-ligand matrix pipeline's geometry side (aggregation/DB stays where it is).

### 1.5 `athena.geometry` — free volume / overlap (consolidation)

- `GridVolume` — parameterized voxel-grid utility: local free volume around a ligand (grid spacing, clearance), shared/overlap volume of two ligand envelopes. Consolidates the 4 Python copies (`reciprocal_mutation_geometry.py`, `analyze_interference.py:120-127`, `analyze_dcmb_campaign.py:118-122`, `displacement_field_analysis.py:44-52`) and complements the package-private `ShellFreeVolume` (leave that in place; do not refactor `pocket.architecture` now).
- Consider wiring `gaia AtomCellIndex` into `StericClashAnalysis` to remove the documented O(n²) — only if profiling shows it matters; add the missing `StericClashAnalysisTest` regardless.

### 1.6 What does NOT get built

- Water bridges, metal coordination detectors (no waters/metals in the METTL7 campaign; PLIP rules recorded in the gap matrix for future reference).
- MOL2 reader; SDF V3000.
- Escape-vector/flood-fill connectivity (`stage8_11 run_structural_design.py`) — defer; candidate for `athena.pocket.architecture` later.
- Any PLIP framework machinery (XML reports, PyMOL, composite-ligand splitting).

---

## 2. METTL7-specific placement

| Need | Home | Mechanism |
|---|---|---|
| SAM as cofactor, distinct from ligand | already done (hermes `LigandClassifier`, hephaestus `FixedCofactor`) | `InteractionProfiler` accepts an explicit cofactor/exclusion list so SAM is profiled as environment, not as "the ligand" |
| Ligand–SAM contacts, acceptor→methyl-C distance, approach angle | `athena.tmt` (already exists) | `NearAttackGeometry`/`NearAttackAssessor` stay the single source; the ≥8 inconsistent Python variants get retired |
| F43/Y47/F199 network; sectors 39–47 / 144–175 / 195–207 / 228–237 | `mettl7` module | new `Mettl7Sectors` immutable config: sector name → set of `ResidueId`; detectors stay generic and receive these as input. Replaces hardcoded Python sets (`LEADING`, `FOCUS_7A`, `CORRIDORS`, `KEY_POCKET`, …) |
| Productive-state overlap, molecular-volume overlap | `athena.geometry GridVolume` + `athena.tmt` composition | generic overlap primitive, campaign-specific gating in tmt |
| Productive-state family fingerprints | `athena.tmt` | composition of `InteractionFingerprint` + NAC gate; supersedes stage12j family-fingerprint CSV |
| Local free volume | `athena.geometry GridVolume` | supersedes 4 Python copies |

Nothing METTL7-numbered goes into athena main code (today's only hardcoded range, `LoopRegionOptions.defaults()` 225–236, should move to mettl7 config when touched).

---

## 3. Consolidation (promotion list, in priority order)

1. `run_stage12j.py ring_geom()/classify_pi()/hbonds()/donor_sites()` → `Plane3D` + `PiStackingDetector` + existing HB detector. (Python file becomes a thin report generator or is archived.)
2. The 5 hydrophobic-contact Python copies → `HydrophobicContactDetector`.
3. The 12+ contact-list copies (analysis/research/tools/SQL) → `DefaultContactAnalyzer` (geometry side only; DB import stays).
4. The 4 free-volume copies → `GridVolume`.
5. The ≥8 near-attack Python variants → `athena.tmt` (already parameterized with provenance; this removes a real reproducibility hazard — gates currently disagree: 2.7–3.5 Å, 145–150°).
6. The 4 Python SASA copies → `ShrakeRupleySasa`.
7. `vina_vs_biohub_interaction_fingerprint.py` → `InteractionFingerprint` (with real geometry).
8. The 12+ hand-rolled PDB/PDBQT parsers → hermes readers. Python scripts that remain should shell out to Java or consume pipeline DB output rather than re-parse.

No blind rewrites: each promotion ports the *validated logic and its recorded constants* (quoted in the code map) and deletes the duplicate only after the Java path reproduces the published number on the same inputs.

---

## 4. Validation plan against PLIP

**Test set:** a fixed public set of PDB complexes, one or more per interaction type, checked into `athena/src/test/resources/plip-reference/` (e.g., well-known PLIP validation structures: 1CST/1HVR-class H-bond + hydrophobic cases, an aromatic-rich kinase complex for parallel + T-shaped pi, a cation-pi case such as an acetylcholinesterase inhibitor complex, a salt-bridge-dominated complex, a halogenated ligand complex for halogen bonds). Final list fixed at implementation time; the point is a frozen, public, versioned fixture set.

**Procedure:**

1. Run PLIP 3.0.1 (external CLI, Open Babel backend) on each structure; store its XML output as a fixture.
2. Prepare the same structures through the existing hephaestus pipeline (protonation, AD4 typing, charges) and run `InteractionProfiler`.
3. Compare per interaction: residue, interaction type, ligand atom/group, protein atom/group, distance, angle where relevant.
4. Report agreement counts and every discrepancy with its cause. Expected systematic discrepancies (to be explained, not tuned away):
   - H-bond thresholds (ours 3.5/120° vs PLIP 4.1/100°) — run the comparison with PLIP thresholds injected to isolate logic differences from threshold choice.
   - Perception differences: Open Babel donor/acceptor vs AD4 typing; ring perception Kekulé-5/6 vs Open Babel aromaticity.
   - Salt-bridge center definition once upgraded (group centroids vs closest pair).
5. **Do not tune thresholds for METTL7 to force agreement.** Threshold policy remains explicit, provenance-tagged (as in `NearAttackCriteria`), and injectable.

**Regression gate:** existing METTL7 regression fixtures (`Mettl7ContactAlignmentRegressionTest`, pocket evidence tests) must stay green; new detectors get unit tests on synthetic geometry where the expected interaction is constructed by hand (known distances/angles), independent of PLIP.

---

## 5. Estimated implementation scope

| Package | New/changed main classes | New/changed test classes |
|---|---|---|
| gaia.geometry | 1 (`Plane3D`) | 1 |
| athena.interaction.perception | 3 (`HydrophobicAtomPerception`, `AromaticRingPerception`, `ChargedGroupPerception`) | 3 |
| athena.interaction | 6 (`InteractionProfiler`, `Interaction`, unified `InteractionType`, 4 new detectors + salt-bridge upgrade in `DefaultLigandInteractionAnalyzer`) | 5–6 |
| athena.interaction.fingerprint | 2 (`InteractionFingerprint`, `ContactMatrix`) | 2 |
| athena.geometry | 1 (`GridVolume`) | 1 |
| athena.clash | 0 new (add missing test; optional AtomCellIndex wiring) | 1 |
| mettl7 module | 1 (`Mettl7Sectors`) | 1 |
| **Total** | **~14 main classes** | **~15 test classes** |

New external dependencies: **0**. Reused foundations: gaia model/geometry/graph, euclid k-d tree (optional), hephaestus AD4 typing + Kekulé aromaticity, hermes parsing, athena contact/SASA/clash/PCA machinery.

Sequencing: (1) `Plane3D` → (2) perception trio → (3) pi-stack detector (highest campaign value: Y47/F43/F199 network) → (4) hydrophobic + halogen → (5) pi-cation + salt-bridge upgrade → (6) fingerprint/matrix → (7) `GridVolume` → (8) `Mettl7Sectors` + Python consolidation sweeps → (9) PLIP validation set.

---

## 6. Explicit non-goals

- No full PLIP port, no Open Babel/RDKit/CDK dependency.
- No water-bridge or metal-coordination detectors in this campaign.
- No merging of interaction evidence into any master score (per AGENTS.md pocket-evidence rules).
- No threshold tuning to force PLIP agreement or METTL7-specific calibration inside generic detectors.
