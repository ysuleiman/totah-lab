# Athena Interaction Capability Inventory

**Date:** 2026-09-05
**Scope:** Full `/Users/yazan/totah-lab` audit — Java modules (`software/modules/*`), apps, `analysis/`, `research/`, `tools/scripts/`, tests, build dependencies, and PLIP (pharmai/plip 3.0.1) reference definitions.
**Purpose:** Determine what already exists for a reusable PLIP-like protein–ligand interaction profiler, and what is genuinely missing. No production code was written.

Companion files: `ATHENA_PLIP_GAP_MATRIX.csv`, `ATHENA_INTERACTION_CODE_MAP.csv`, `ATHENA_INTERACTION_IMPLEMENTATION_PLAN.md`.

---

## 1. Executive summary

The repository already contains a strong, tested foundation in Java:

- A full immutable molecular model (`gaia.structure`, `gaia.molecule`, `gaia.chemistry`).
- Full geometry primitives: `Point3D`, `Vector3D`, `Dihedral`, `RigidTransform`, PCA (`athena PrincipalComponents`), Kabsch/QCP alignment (`euclid RigidSuperposition`, `athena KabschRigidPointAligner`).
- Two spatial indexes: `gaia.graph.AtomCellIndex` (uniform grid, backs `ResidueGraph`) and `euclid.spatial.SimpleKDTree`.
- Residue-level protein–ligand contact analysis (`athena DefaultContactAnalyzer`, 4.5/8.0 Å shells), cross-receptor contact alignment, contact-string fingerprints.
- One real interaction detector: `athena DefaultLigandInteractionAnalyzer` — **hydrogen bonds and salt bridges only**, dependent on prepared PDBQT/AD4 atom types.
- Shrake–Rupley SASA (`athena`), vdW clash detection (`athena StericClashAnalysis`, **untested**), local free-volume proxy (`athena ShellFreeVolume`, package-private).
- Full chemical-preparation stack (`hephaestus`): AD4 atom typing (receptor + ligand), Kekulé aromaticity perception, Gasteiger and QEq charges (hand-rolled Open Babel equivalents), pKa-based protonation, hydrogen placement.
- Full parsing (`hermes`): PDB/mmCIF (BioJava), PDBQT (incl. Vina/Meeko), SDF V2000, CCD-backed ligand classification (SAM/SAH/SIN recognized as cofactors).
- A parameterized SAM methyl-transfer near-attack package (`athena.tmt`): `NearAttackGeometry`, `NearAttackAssessor`, `EnsembleNacAnalyzer` — thresholds caller-supplied with provenance.

The genuine gaps are concentrated and small:

- **No detectors** for hydrophobic contacts, pi stacking (parallel/T-shaped), pi-cation, halogen bonds, water bridges, metal coordination. The richer `athena.pocket.evidence.InteractionType` enum (`HYDROPHOBIC_CONTACT`, `AROMATIC_CONTACT`, `IONIC_CONTACT`, `METAL_COORDINATION_GEOMETRY`) is an annotation vocabulary with no detectors behind it.
- **No reusable plane/ring geometry**: plane fitting, plane normal, plane–plane angle, point-to-plane projection exist only privately inside `WallGeometryAnalyzer.localPlane()` and in Python one-offs. This single missing primitive blocks all pi-interaction detection.
- **No group-level chemical perception**: hydrophobic-atom rule, charged-group (carboxylate/guanidinium/amine) perception, ligand aromatic-ring perception exposed for reuse (`KekuleAromaticity` is ligand-prep-internal), halogen-donor and metal-binding perception.
- **No typed interaction fingerprint / contact matrix** (contact strings and PocketMatch signatures exist; neither is a PLIP-style per-residue typed fingerprint).
- ~30 Python/JS one-off scripts in `analysis/`, `research/`, `tools/scripts/` re-implement contacts, H-bonds, pi geometry, SASA, free volume, and near-attack gates with inconsistent cutoffs — proven logic that should be consolidated into Athena, not rewritten.

---

## 2. Module map

| Module | Role | Java files | PLIP-relevant content |
|---|---|---|---|
| `gaia` | Molecular model + geometry + spatial grid | 74 | Atom/Bond/Residue/Chain/Structure/Ligand/Protein, Element (vdW/covalent radii, isHalogen/isMetal), BondOrder, Point3D/Vector3D/Dihedral/RigidTransform/ResidueGeometry, ResidueGraph + AtomCellIndex |
| `euclid` | Numerics + spatial | 25 | SimpleKDTree, RigidSuperposition (QCP), RmsdClusterer, linear solvers |
| `athena` | Pocket/ligand analysis | 348 | LigandInteractionAnalyzer (HB + salt bridge), DefaultContactAnalyzer, StericClashAnalysis, ShrakeRupleySasa, PocketGeometry, PrincipalComponents, ShellFreeVolume, PocketMatch suite, contact alignment/selectivity, `tmt` near-attack package |
| `hephaestus` | Receptor/ligand preparation | 199 | AD4 typing, KekuleAromaticity, Gasteiger/QEq charges, protonation (pKa), hydrogen placement, SpatialClashChecker, DisulfideDetector, LigandFlexibilityModelBuilder |
| `hermes` | Structure/file I/O + clients | 220 | PdbReader (BioJava), PDBQT reader/writers, SDF V2000 reader, mmCIF readers, LigandClassifier (SAM/SAH cofactors), fpocket/p2rank parsers |
| `daedalus` | Docking pipeline orchestration | 75 | fpocket/Vina runners, pocket evidence assembly, disulfide scan |
| `proteus` | Mutation/rotamer | 29 | Rotamer clash scoring (2.8 Å overlap) |
| `mettl7` | METTL7 rules/panels (data only) | 45 | Triage rulesets, mutant panels, docking windows — **no geometry computation** |
| `prometheus` | Quantum chemistry | 659 | No protein–ligand interaction code (verified) |

Dependency chain: `athena → gaia + commons-math3`; `hephaestus/hermes → gaia (+ euclid for QEq)`; `hermes → biojava-structure 7.2.5`. **No CDK, no RDKit/Open Babel JNI, no Jmol.** Chemical perception is hand-rolled in Java throughout (GasteigerModel and QEqModel are explicit re-implementations of Open Babel behavior). RDKit is used only in Python analysis scripts.

---

## 3. Capability inventory (summary table)

Classification: **FULL** = general, reusable, parameterized; **PARTIAL** = exists but limited; **ONE_OFF** = works but hardcoded to a specific analysis; **MISSING** = not found anywhere. Full per-class listing in `ATHENA_INTERACTION_CODE_MAP.csv`.

### Interaction detection (the core gap)

| Capability | Existing? | Where | Reusable? | Tested? | Notes |
|---|---|---|---|---|---|
| Hydrogen bonds | FULL (AD4-convention) | athena `DefaultLigandInteractionAnalyzer` | Yes, public interface | Yes | H···A ≤2.5, D···A ≤3.5, DHA ≥120°; both directions; requires prepared PDBQT typing |
| Salt bridges | PARTIAL | same class | Yes | Yes | 4.0 Å closest heavy pair + whole-residue charge-sum ≥0.5 e; not PLIP-style group charge centers (PLIP: 5.5 Å between functional-group centroids) |
| Generic protein–ligand contacts | FULL | athena `DefaultContactAnalyzer`, `PocketGeometry` | Yes | Yes | Closest heavy pair per residue; 4.5 Å direct / 8.0 Å shell |
| Residue-level contact aggregation | FULL | `DefaultContactAnalyzer`, `DefaultLigandContactAlignmentAnalyzer` | Yes | Yes | Cross-receptor alignment via sequence alignment; regression tests on METTL7 fixtures |
| Hydrophobic contacts | ONE_OFF (Python ×5+) / MISSING in Java | `analysis/dcmb/*`, `research/*` scripts | No | No | ~4.5 Å C/S↔C/Cl rule re-implemented ≥5 times |
| Parallel pi stacking | ONE_OFF (Python) / MISSING in Java | `analysis/mettl7-closure/stage12j/run_stage12j.py` | No | No | SVD ring normal; ≤5.5 Å, ≤30°, offset ≤2.5 Å (close to PLIP: 5.5/30°/2.0) |
| T-shaped pi stacking | ONE_OFF (Python) / MISSING in Java | same script (`EDGE_FACE_PI`, ≥60°) | No | No | No offset criterion for edge-face |
| Pi-cation | MISSING everywhere | — | — | — | Not in any script |
| Halogen bonds | MISSING (distance-only candidates in Python) | `analysis/docking-functional-group-chemistry` | No | No | "C–X angle not asserted"; relevant because DCMB is dichlorinated |
| Water bridges | MISSING everywhere | — | — | — | Docking poses have no waters |
| Metal coordination | MISSING everywhere | — | — | — | Enum label only; METTL7 has no metals |
| Interaction fingerprints (typed) | PARTIAL | `ContactStringRenderer` (strings), PocketMatch signatures (pocket–pocket), Python `vina_vs_biohub_interaction_fingerprint.py` (typed, distance-only, one-off) | Partial | Yes (Java) | No PLIP-style per-residue typed fingerprint |
| Contact matrices | PARTIAL | `PocketPairComparison` (Jaccard), SQL `pose_residue_contact`, Python 74-ligand matrix | Partial | Partial | Geometry computed upstream by Java/SQL pipeline |

### Chemical perception

| Capability | Existing? | Where | Reusable? | Tested? | Notes |
|---|---|---|---|---|---|
| H-bond donor/acceptor (atom) | FULL within AD4 convention | hephaestus `AD4AtomTyper`, `LigandAD4AtomTypingOperation`; consumed by athena | Yes | Yes | Donor=HD, acceptor∈{NA,OA,SA}; requires prepared structures, no SMILES-level perception in Java |
| Aromaticity (ligand) | PARTIAL | hephaestus `KekuleAromaticity` | Internal to prep | Indirect | Hückel over 5/6-cycles only; no rings >6, no exocyclic; not exposed as a general ring-perception API |
| Aromatic rings (protein) | PARTIAL | AD4 `A` type via atom-name templates (PHE/TYR/TRP/HIS); Python name tables | Indirect | Yes | Name-based, exactly like PLIP's residue-name fallback |
| Hydrophobic atom | MISSING in Java (Python residue-set heuristics) | — | — | — | PLIP rule (C whose neighbors ⊆ {C,H}) is trivial over gaia bond graph |
| Positive/negative charged groups | MISSING in Java | — | — | — | PLIP `is_functional_group` rules (carboxylate, guanidine, quart/tert amine, sulfonium, phosphate, sulfate); Java only sums partial charges |
| Formal charge | FULL as data | gaia `FormalCharge`, hermes CCD/SDF readers | Yes | Yes | Consumed, not perceived, on structures |
| Protonation | FULL (receptor prep) | hephaestus `ProtonationConfig` (pKa table, pH 7.4), His states, termini | Yes | Yes | |
| Halogen donor | MISSING | — | — | — | PLIP: F/Cl/Br/I bonded to exactly one carbon |
| Metal-binding atom | PARTIAL (prep guard only) | hephaestus `MetalIonPolicy`, `isNearMetal` (4.0 Å) | Prep-internal | Indirect | No PLIP-style metal-binding site perception |
| Atom typing (AD4) | FULL | hephaestus | Yes | Yes | Meeko-compatible rules; validated against Meeko |
| Residue typing | FULL (name-based) | gaia `ResidueCategories`; athena `ResidueChemistryClassifier`, `PocketMatchResidueGroup` | Yes | Yes | |
| Functional-group perception | PARTIAL (enum labels only in Java; RDKit-based classifier in Python) | athena `FragmentPocketChemistry` (enum); `analysis/docking-functional-group-chemistry/analyze_functional_groups.py` | No | Partial | |

### Geometry primitives

| Capability | Existing? | Where | Reusable? | Tested? |
|---|---|---|---|---|
| Distance | FULL | gaia `Point3D`, athena `PocketGeometry`, gaia `ResidueGeometry` | Yes | Yes |
| Angle | FULL (vector) / PARTIAL (3-point) | gaia `Vector3D.angle`; private 3-point `angle()` in athena/Python copies | Partial | Yes |
| Dihedral | FULL | gaia `Dihedral` (IUPAC-signed) | Yes | Yes |
| Centroid | FULL | gaia `ResidueGeometry`, athena `ResidueCentroidCalculator`, `PocketShapeStatistics`, `LigandGeometry` | Yes | Yes |
| Plane fitting / plane normal | PARTIAL (private) | athena `WallGeometryAnalyzer.localPlane()` (PCA, k-nearest wall atoms); `PrincipalComponents` public | No (private) | Yes (PCA) |
| Angle between planes | PARTIAL (private) | `WallGeometryAnalyzer.acuteAngleDegrees()` | No | Indirect |
| Point-to-plane distance / projection | PARTIAL (private + Python) | `WallGeometryAnalyzer` roughness RMS; `PrincipalComponents.projection()/offsetAlong()`; Python `run_stage12i` | Partial | Indirect |
| Ring centroid / ring normal | ONE_OFF (Python SVD) / MISSING in Java | `run_stage12j.ring_geom()` | No | No |
| Vector projection | PARTIAL | `PrincipalComponents.projection()` | Yes | Yes |
| Rigid alignment | FULL | `KabschRigidPointAligner`, `euclid RigidSuperposition`, ICP/PCA/composite aligners | Yes | Yes (some aligners untested) |

### Structural analysis

| Capability | Existing? | Where | Reusable? | Tested? |
|---|---|---|---|---|
| Neighbor search / spatial index | FULL | gaia `AtomCellIndex` (grid, 4.0 Å cells) via `ResidueGraph`; euclid `SimpleKDTree` | Yes | Yes |
| Residue contact lists | FULL | gaia `ResidueGraph.withinDistance/atomProximities`, athena `DefaultContactAnalyzer` | Yes | Yes |
| Clash detection | FULL (untested!) | athena `StericClashAnalysis` (vdW×0.7, O(n²)); hephaestus `SpatialClashChecker` (voxel, H-placement scoped) | Yes | **No** — no test class for StericClashAnalysis |
| SASA | FULL | athena `ShrakeRupleySasa` (Fibonacci 96 pts, probe 1.4) | Yes | Yes |
| Burial | PARTIAL | `LoopRegionAnalyzer.burial()` (atom-count proxy, 8.0 Å) | Yes | Yes |
| Pocket/local free volume | PARTIAL | athena `ShellFreeVolume` (package-private, 14 directions); alpha-sphere volume sums; absolute volume delegated to external fpocket/p2rank | Partial | Indirect |
| True cavity volume | MISSING (grid-based Python one-offs ×4) | `analysis/dcmb/reciprocal_mutation_geometry.py` etc. | No | No |

### Parsing

| Format | Existing? | Where | Tested? |
|---|---|---|---|
| PDB | FULL | hermes `PdbReader` (BioJava) | Yes |
| mmCIF | FULL | hermes `Mmcif*Reader` ×7 | Mostly |
| PDBQT | FULL | hermes `PdbqtReader`/writers/validators, Vina/Meeko parsers, `PdbqtGaiaMapper` | Yes |
| SDF | PARTIAL | hermes `SdfLigandReader` (V2000 single-molecule only) | Yes |
| MOL2 | MISSING | — | — |
| Ligand/SAM/cofactor identification | FULL | hermes `LigandClassifier` (COFACTOR set includes SAM, SAH, SIN), hephaestus `FixedCofactor`/`ReceptorAssembly` | Yes |

---

## 4. PLIP comparison (detail in `ATHENA_PLIP_GAP_MATRIX.csv`)

PLIP 3.0.1 (pharmai/plip) uses **Open Babel** for all perception (`IsHbondAcceptor/IsHbondDonor`, ring aromaticity, bond orders) plus hand-rolled connectivity rules for hydrophobic atoms and functional groups. Key defaults: hydrophobic ≤4.0 Å; H-bond D···A ≤4.1 Å, D–H···A ≥100°; pi-stack centroid ≤5.5 Å, normal angle 0±30° (parallel) / 90±30° (T-shaped), offset ≤2.0 Å; pi-cation ≤6.0 Å + offset ≤2.0 Å; salt bridge ≤5.5 Å between charge centers; halogen O···X ≤4.0 Å, angles 120±30°/165±30°; water bridge 2.5–4.1 Å with ω 71–140°; metal ≤3.0 Å.

Functional comparison verdict:

- **H-bonds:** ours (2.5/3.5/120°) is stricter than PLIP (4.1/100°). Both adequate; ours is not PLIP-equivalent numerically — decide convention deliberately, don't silently differ.
- **Salt bridges:** ours (4.0 Å closest-pair + charge sum) is a heuristic; PLIP uses functional-group charge centers at 5.5 Å. Not equivalent; requires charged-group perception to close.
- **Hydrophobic, pi stacking, pi-cation, halogen:** we have nothing reusable; the stage12j Python pi classifier is already near-PLIP (5.5 Å/30°/2.5 Å offset) and is the best consolidation candidate.
- **Water bridges, metal coordination:** missing everywhere and not needed for METTL7 (no waters/metals in the campaign).
- PLIP itself has no spatial index, no SASA, no clash detection, no fingerprints — we are already ahead there.

---

## 5. Duplicated one-off functionality (consolidation list)

Format: `one-off implementation → candidate for promotion` (or `reusable → duplicate`).

**Contacts / distances (≥12 copies):**
- `analysis/dcmb/dcmb_tsl_interference/analyze_interference.py` (`contact_residues`, 4.5 Å) → athena `DefaultContactAnalyzer`
- `analysis/dcmb/same_site_pose_analysis.py` (`contacts`, `residue_min_distances`) → athena `DefaultContactAnalyzer`
- `analysis/mettl7-closure/stage4/analyze_dcmb_campaign.py`, `stage7_candidates/compare_dcmb_candidate_subspaces.py` (43/195/199/232 @ 4.5 Å), `stage12d/run_stage12d.py`, `stage12g` → same
- `research/mettl7-*/analyze_local_refinement.py`, `analyze_matched_vina.py`, `analyze_analog_campaign.py`, `analyze_campaign.py` (4.0–4.5 Å variants) → same
- `tools/scripts/extract_model1_residue_contacts.mjs` (grid-indexed 4.0 Å contacts, MODEL 1) → athena `DefaultContactAnalyzer` + hermes `PdbqtReader`
- `tools/scripts/vina_vs_biohub_pose_comparison.py` `contacts()` → same
- SQL-embedded contact distance in `tools/scripts/prepare_mcule_comparison_import.mjs:311` → same

**Hydrogen bonds (3 copies, two conventions):**
- `analysis/mettl7-closure/stage12j/run_stage12j.py:32-46` (AD4, 2.5/3.5/120° — "Athena rules reproduced") → **already duplicates athena `DefaultLigandInteractionAnalyzer`; delete after consolidation**
- `research/mettl7-reaction-competence-v1/analyze_local_refinement.py:71-88` (same rule, copy) → same
- `analysis/docking-functional-group-chemistry/analyze_functional_groups.py` (distance-only ≤3.5 Å) → same

**Pi geometry (the only near-PLIP implementations, both one-off):**
- `analysis/mettl7-closure/stage12j/run_stage12j.py:47-54` `ring_geom()`/`classify_pi()` → **promote into athena as PiStackingDetector** (SVD ring normal, 5.5 Å/30°/2.5 Å)
- `analysis/dcmb/l43f_rotamer_analysis.py` ring normal → same primitive

**Hydrophobic contacts (≥5 copies):** `run_stage12j.py:76`, `same_site_pose_analysis.py:152`, `run_stage12d.py:132`, `sar_experiment/analyze.py:92`, `displacement_field_analysis.py` → promote once into athena.

**Local free volume (4–5 copies):** `analysis/dcmb/reciprocal_mutation_geometry.py:16-26`, `analyze_interference.py:120-127`, `stage4/analyze_dcmb_campaign.py:118-122`, `displacement_field_analysis.py:44-52`, `research/mettl7-*/analyze_local_architecture.py:90` (0.5 Å grid, 2.0 Å clearance) → promote one grid-based implementation into athena; relate to existing `ShellFreeVolume`.

**Near-attack geometry (≥8 inconsistent variants — a reproducibility hazard):**
- `analysis/dcmb/tsl_catalytic_geometry/reconstruct_tsl.py:31-35`, `research/mettl7-*/run_near_attack.py:21-24`, `run_campaign.py:190`, `run_analysis.py:110`, `analyze_analog_campaign.py:134`, `analyze_sah_campaign.py:178`, `analyze_klf4_final_interface_validation.py:20`, `analyze_haddock_rna_batch.py:54-55` — gates vary (2.7–3.5 Å, 145–150°+) → **athena `tmt/NearAttackGeometry` + `NearAttackAssessor` already exist and are parameterized with provenance; retire the Python variants**

**SASA (4 copies):** `research/.../analyze_local_refinement.py:37` (128 pts), `analyze_matched_vina.py:81` (256 pts), `analyze_local_architecture.py:72`, `analysis/mettl7-closure/stage12_cavity_chemistry/run_cavity_chemistry.py:103-119` → athena `ShrakeRupleySasa`

**PDB/PDBQT parsers (≥12 copies):** ≥7 hand-rolled fixed-column PDB readers in `analysis/`+`research/`, 5–7 PDBQT readers (only `analyze_functional_groups.parse_models` parses Meeko `REMARK SMILES/IDX`), 2 in `tools/scripts/` → hermes `PdbReader`/`PdbqtReader`

**Geometry helpers:** `angle()` ×6+, `dihedral()` ×3, Kabsch ×4, RMSD ×5+, point-to-segment ×3, Rodrigues rotation ×3 → gaia.geometry / euclid

**Fingerprints:** `tools/scripts/vina_vs_biohub_interaction_fingerprint.py` (typed, distance-only, RDKit features, hardcoded KEY_POCKET/Cys202) → candidate pattern for an athena typed fingerprint (but with real geometry)

**vdW tables (3 Python dicts)** → gaia `Element.vanDerWaalsRadius`

---

## 6. Known-result producers (located)

| Known result | Producing code | Status |
|---|---|---|
| DCMB F43/F199 distances | `analysis/dcmb/dcmb_tsl_interference/analyze_interference.py:290-294,351`; also `mettl7-closure/stage7_candidates/compare_dcmb_candidate_subspaces.py:140-143`, `analysis/dcmb/same_site_pose_analysis.py:271-277` | ONE_OFF ×3 |
| Tyr47 pi-contact geometry | `analysis/mettl7-closure/stage12j/run_stage12j.py` (all aromatic residues; 47 in focus list); distance-only Y47 anchor in `analysis/docking-functional-group-chemistry/analyze_functional_groups.py:224` | ONE_OFF |
| Aromatic-contact atom counts | `run_stage12j.py:76-85`, `run_stage12d.py:126-134`, `analyze_functional_groups.py:311-330`, `analysis/docking-box-contact-inventory/LigandChemistryContactAggregator.java:69` (AD4 `A`, ≤4.05 Å) | ONE_OFF ×4 |
| Hydrophobic-contact atom counts | 5+ scripts, ~4.5 Å rule (see §5) | ONE_OFF ×5 |
| Local free volume | 4 grid-based Python copies + athena `ShellFreeVolume` (package-private) | ONE_OFF ×4 + PARTIAL |
| TSL productive-state geometry | `analysis/dcmb/tsl_catalytic_geometry/reconstruct_tsl.py` (2.8–3.2 Å S···CE, cone 150–180°); rerun harness `mettl7-closure/stage3/run_tsl_matrix.py` | ONE_OFF |
| DCMB × TSL overlap | `analysis/dcmb/dcmb_tsl_interference/analyze_interference.py` (`shared_volume`, corridor fractions, swept volume); mirror `mettl7-closure/stage4/analyze_interference.py` | ONE_OFF ×2 |
| SAM-relative near-attack | athena `tmt/NearAttackGeometry` (Java, parameterized) + ≥8 Python variants | Java FULL / Python ONE_OFF |
| Netarsudil 196–207 contacts | `research/mettl7-netarsudil-sam-mechanism/**` (e.g. `run_analysis.py:110`, N5/N6 2.8–3.2 Å ≥150°); only a CSV row in `analysis/` | ONE_OFF |
| BRICS residue-contact analysis | `analysis/mettl7-closure/stage12d/run_stage12d.py:71,126-134`; `stage7_candidates/run_zero_docking_triage.py:50` (RDKit BRICS + 4.5 Å contacts) | ONE_OFF |
| 74-ligand contact matrix | `analysis/docking-selectivity-inventory/build_inventory.py` (CONTACT_CUTOFF 4.0, hardcoded run IDs; geometry from DB `docking.pose_residue_contact`, written by Java pipeline) | ONE_OFF (aggregation), geometry upstream |

---

## 7. METTL7-specific requirements (placement recommendation)

| Requirement | Status today | Recommended home |
|---|---|---|
| SAM as cofactor distinct from ligand | FULL (hermes `LigandClassifier`; hephaestus `FixedCofactor`/`ReceptorAssembly`) | hermes/hephaestus (already there) |
| Ligand–SAM contacts | Possible today via `DefaultContactAnalyzer` pointed at SAM residue | athena `interaction` (generic) |
| Acceptor → SAM methyl-C distance; approach angle | FULL, parameterized, provenance-bound | **athena `tmt`** (already there: `NearAttackGeometry`/`NearAttackAssessor`) |
| F43/Y47/F199 network | Data only (mettl7 triage rulesets, test fixtures) | mettl7 module as **residue-set configuration** over generic detectors |
| Sectors 39–47 / 144–175 / 195–207 / 228–237 | Only as booleans (`context195To203`, `context228To237`) and Python lists | mettl7 module: a `Mettl7Sectors` value object mapping sector name → `ResidueId` set; geometry stays generic |
| Productive-state overlap / molecular-volume overlap | ONE_OFF Python (`shared_volume`, envelope grids) | athena `geometry` (grid volume/overlap utility) — generic, then consumed by tmt |
| Escape-vector / connectivity analysis | ONE_OFF Python (`stage8_11/run_structural_design.py`: flood fill, 26-connected components, widest-path Dijkstra) | athena `geometry` or `pocket.architecture` — **defer** (not needed for the clean mechanistic-state campaign's first pass) |
| Local free volume | PARTIAL (`ShellFreeVolume` private) + 4 Python copies | athena `geometry`: one public grid-based free-volume API |
| Productive-state family fingerprints | ONE_OFF (`run_stage12j` family fingerprints CSV) | athena `tmt` composition of typed interaction fingerprint + NAC gate |
| Generic interaction profiling (HB, hydrophobic, pi, salt bridge, halogen) | Partial | **athena `interaction`** |

Rule of thumb applied: anything expressible as geometry over atoms goes to athena `interaction`/`geometry`; anything defined by SAM/methyl-transfer chemistry goes to athena `tmt`; anything defined by METTL7A/B residue numbers is configuration data in the mettl7 module, never hardcoded in athena.

---

## 8. Verdicts

### Already available and reusable (exact classes)

- Model: `gaia.structure.{Atom,Bond,Residue,Chain,Structure}`, `gaia.molecule.{Ligand,Protein}`, `gaia.chemistry.{Element,BondOrder,ChemicalBond,FormalCharge,ElementResolver}`, `gaia.classification.ResidueCategories`
- Geometry: `gaia.geometry.{Point3D,Vector3D,Dihedral,RigidTransform,ResidueGeometry}`, `euclid.spatial.{SimpleKDTree,RigidSuperposition}`, `athena PrincipalComponents`, `KabschRigidPointAligner`
- Spatial/contacts: `gaia.graph.{ResidueGraph,AtomCellIndex}`, athena `DefaultContactAnalyzer`, `DefaultLigandContactAlignmentAnalyzer`, `PocketGeometry`
- Interactions: athena `DefaultLigandInteractionAnalyzer` (HB, salt bridge) behind `LigandInteractionAnalyzer`
- Prep/perception: hephaestus `AD4AtomTyper`, `LigandAD4AtomTypingOperation`, `KekuleAromaticity`, `GasteigerModel`, `QEqModel`, `ProtonationConfig`
- SASA/clash: athena `ShrakeRupleySasa`, `StericClashAnalysis` (add test)
- Parsing: hermes PDB/mmCIF/PDBQT/SDF readers, `LigandClassifier` (SAM/SAH/SIN)
- METTL7 near-attack: athena `tmt.{NearAttackGeometry,NearAttackAssessor,EnsembleNacAnalyzer}`

### Exists but needs refactoring

- `DefaultLigandInteractionAnalyzer` salt-bridge heuristic → PLIP-style charged-group centers (needs charged-group perception)
- `WallGeometryAnalyzer.localPlane()`/`acuteAngleDegrees()` → extract public plane primitive (gaia.geometry `Plane3D`)
- `ShellFreeVolume` (package-private, 14 directions) → public grid-based local free-volume API
- `KekuleAromaticity` → expose ligand aromatic-ring sets for reuse by pi detectors
- `InteractionType` enums (two of them: `ligand.interaction` with 2 values, `pocket.evidence` with 7 annotation-only values) → unify into one detector-backed enum
- `StericClashAnalysis` → add the missing test class; consider wiring `AtomCellIndex` to remove O(n²)

### Missing and must be implemented

1. `Plane3D` primitive (fit, normal, point-to-plane distance, plane–plane angle) — blocks all pi detection
2. Ring perception for profiling (ligand rings from bond graph via existing Kekulé logic + SSSR; protein rings PHE/TYR/TRP/HIS by template — PLIP does exactly this)
3. Hydrophobic-atom perception (C with neighbors ⊆ {C,H})
4. Charged-group perception (carboxylate, guanidinium, amines, sulfonium, phosphate/sulfate)
5. Detectors: hydrophobic contact, pi stacking (parallel + T-shaped), pi-cation, halogen bond (DCMB is dichlorinated — include), upgraded salt bridge
6. Typed interaction fingerprint (per-residue → set of interaction types) + contact matrix view
7. Grid-based local free volume / volume-overlap utility (consolidating 4 Python copies)

### Not needed for METTL7 (intentionally not built)

- Water bridges (no explicit waters in docking poses/structures in the campaign)
- Metal coordination (METTL7 is not metalloenzyme; no metals in scope)
- MOL2 reader (no MOL2 in the pipeline)
- SDF V3000/multi-molecule (current inputs are V2000/PDBQT)
- Weak C–H donor H-bonds (PLIP perceives but never uses them either)
- Full PLIP framework port (XML reports, PyMOL sessions, composite-ligand handling beyond MAX_COMPOSITE_LENGTH, BioLiP artifact lists)

### Estimated implementation scope

- **gaia.geometry:** 1 new class (`Plane3D`) + 1 test
- **athena perception:** 3 new classes (`HydrophobicAtomPerception`, `ChargedGroupPerception`, `AromaticRingPerception`) + 3 tests
- **athena interaction:** 5 new/refactored detectors (`HydrophobicContactDetector`, `PiStackingDetector`, `PiCationDetector`, `HalogenBondDetector`, salt-bridge upgrade) + `InteractionProfiler` orchestration + unified `InteractionType` + 5–6 tests
- **athena fingerprint:** 2 classes (typed fingerprint + residue×type matrix) + 2 tests
- **athena geometry:** 1 grid free-volume/overlap utility + 1 test; 1 missing `StericClashAnalysisTest`
- **mettl7 module:** 1 `Mettl7Sectors` configuration class + test
- **Total: ~16 new main classes, ~17 new test classes, 0 new dependencies.** Consolidation deletes/neuters ~30 Python one-offs (no Java rewrite risk).

Validation plan, architecture detail, and sequencing: `ATHENA_INTERACTION_IMPLEMENTATION_PLAN.md`.
