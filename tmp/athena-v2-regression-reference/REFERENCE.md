# Athena v2 regression reference — harvested historical METTL7 analysis outputs

Harvested 2026-09-05 from `/Users/yazan/totah-lab`. All paths verified to exist on disk unless flagged otherwise.
"Repo-relative" paths are under `/Users/yazan/totah-lab/`. Nothing here was recomputed; every number is copied verbatim
from the frozen historical output file cited.

---

## 1. DCMB F43 + F199 minimum distances

**Metric:** minimum heavy-atom distance from each frozen DCMB WT-7A family medoid to residue F43 and F199 in the
original (fixed) receptor; `contact_le_4p5A` boolean.

**Producing script:** `analysis/dcmb/dcmb_tsl_interference/analyze_interference.py`
- Contact cutoff convention: 4.5 A, `contact_residues(..., cutoff: float = 4.5)` at line 130.
- F43/F199 loop at lines 290-294: min over all heavy-atom pairs between ligand pose atoms and residue atoms
  (`distance = float(pair_distances(ligand, np.array(residue_atoms)).min())`, `contact_le_4p5A = distance <= 4.5`).
- Hydrogens excluded at parse (line 31-32). Protein atoms exclude SAM/TSL/SAH/MTS (line 138-139).
- Ligand pose = representative model `representative_mode` (1-based MODEL index) of the raw docking PDBQT selected by
  `family_results.csv` row (lines 225-231).

**Historical output:** `analysis/dcmb/dcmb_tsl_interference/f43_f199_original_contacts.csv` (22 data rows, 11 families).
Exact values (family, F43 min A, F199 min A — all `contact_le_4p5A=True`):

| family | F43 min A | F199 min A |
|---|---|---|
| R1 | 3.3911182521404357 | 3.356203062986505 |
| R2 | 3.2821730606413793 | 3.3070828535130476 |
| R3 | 3.4421032814254713 | 3.5154523179812864 |
| R4 | 3.303485129374734 | 3.304553373755673 |
| R5 | 3.4793571245274606 | 3.005262384551472 |
| S1 | 3.345602636297383 | 3.3400899688481447 |
| S2 | 3.423347776665409 | 3.0064740145226607 |
| S3 | 3.3068596885867416 | 3.387273092031406 |
| S4 | 3.1530596251894765 | 3.278704164757779 |
| S5 | 3.448870249806449 | 2.9893400609499077 |
| S6 | 3.042610392409781 | 3.098925136236756 |

Aggregate (`analysis/dcmb/dcmb_tsl_interference/summary.json`): `families_contacting_F43=11`, `families_contacting_F199=11`
(of 11 families).

**Input structure files (verified):**
- Receptor: `analysis/dcmb/sam_state/validated/WT_METTL7A_SAM_BOUND.pdb` (protein frame; SAM atoms used only for corridor metrics).
- Pose sources: `analysis/dcmb/controlled_campaign/raw/7A_WT_SAM_BOUND_{R,S}_s{1,7,42}.pdbqt` (6 files exist);
  per-family (enantiomer, seed, mode) mapping from `analysis/dcmb/controlled_campaign/family_results.csv`:
  R1=(R,s42,mode1), R2=(R,s7,mode2), R3=(R,s1,mode3), R4=(R,s7,mode4), R5=(R,s7,mode6),
  S1=(S,s42,mode1), S2=(S,s42,mode2), S3=(S,s1,mode3), S4=(S,s42,mode3), S5=(S,s1,mode5), S6=(S,s1,mode6).
- Frame QC: `analysis/dcmb/controlled_campaign/prepared/7A_WT_SAM_BOUND.pdbqt` matched 244 CA at RMSD 0.0
  (`frame_summary.json`).

**Stage 7 candidate subspace comparison (same metric, different cohort):**
`analysis/mettl7-closure/stage7_candidates/compare_dcmb_candidate_subspaces.py` ->
`stage7c_dcmb_comparison/family-dcmb-subspace-matrix.csv` (164 candidate families, columns
`F43_min_distance_A`, `F199_min_distance_A`, `W195_min_distance_A`, `L232_min_distance_A`).
Report-level aggregates (`stage7c_dcmb_comparison/DCMB_SUBSPACE_REPORT.md`): at the 4.5 A cutoff across 164 candidate
families, **2 families contact F199; 0 contact F43; 0 contact W195; 0 contact L232**. Nearest DCMB-centroid distance
12.94-19.16 A (median 15.15 A); minimum ligand-DCMB atom distance never below 7.16 A; max shared occupied volume and
max alpha-sphere Jaccard both 0 for every family. Inputs: `stage7b/raw/{identity}_{target}_s{seed}.pdbqt`
(240 pdbqt files present) + Stage 4 raw medoids. Pose coordinates only in these PDBQTs — recomputable in Java from files.

**Feasibility:** Fully recomputable in JUnit from in-repo PDB/PDBQT files + the family mapping CSV. All inputs in-repo.

---

## 2. Tyr47 pi-contact classification + aromatic/hydrophobic contact counts (Stage 12J)

**Producing script:** `analysis/mettl7-closure/stage12j_static_dcmb_chemistry/run_stage12j.py`
- Pi classification at lines 49-54 (`classify_pi`): ligand ring = atom serials 3-8; residue ring atom name lists at line 12
  (`AROM`); centroid distance <= 5.5 A AND normal angle <= 30 deg AND lateral offset <= 2.5 A -> `PARALLEL_PI`;
  centroid <= 5.5 A AND normal angle >= 60 deg -> `EDGE_FACE_PI`; else no pi label (line 53).
- Hydrophobic pairs at line 76: residue in HYDRO set, non-backbone, element in {C,S}, ligand element in {C,CL},
  distance <= 4.5 A (`static_contact.close_contact_A`).
- H-bonds at lines 39-46: donor HD within 1.35 A of heavy donor; H..acceptor <= 2.5 A; donor..acceptor <= 3.5 A;
  D-H-A angle >= 120 deg; acceptor AD4 types {NA, OA, SA}.
- Constants frozen in `PROTOCOL.json` (verbatim):
  `hydrogen_acceptor_cutoff_A=2.5`, `hydrogen_bond_heavy_cutoff_A=3.5`, `hydrogen_bond_min_DHA_angle_deg=120.0`,
  `donor_bond_cutoff_A=1.35`, `salt_bridge_cutoff_A=4.0`, `ionic_charge_threshold=0.5`,
  `static_contact.close_contact_A=4.5`, `aromatic_geometry: centroid_cutoff_A=5.5, parallel_normal_angle_max_deg=30.0,
  edge_face_normal_angle_min_deg=60.0, parallel_lateral_offset_max_A=2.5`.

**Historical outputs (verified numbers):**
- `SUMMARY.json`: families=30 (7A=11, 7B broad=16, 7B escape=3); fingerprint_rows=353;
  `Athena_hydrogen_bonds=8`; `salt_bridges=0`; `families_with_valid_pi_geometry: 7A=8, 7B_broad=2, 7B_escape=0`;
  `aromatic_environment_rates: 7A_multi_aromatic=1.0, 7B_broad=0.625, 7B_escape=0.0`;
  classification=`COUPLED_GEOMETRY_CHEMISTRY`.
- Pi rows in `family-interaction-fingerprints.csv`: exactly **1 PARALLEL_PI** (7B_S4 / TRP195: centroid 4.217949196332206 A,
  normal 27.214054624977397 deg, offset 2.239539163081844 A) and **11 EDGE_FACE_PI** rows
  (7A_R2/HIS175 4.962 A/76.17 deg; 7A_R5/HIS175 4.856/79.15; 7A_R4/PHE199 4.980/67.63; 7A_S1/PHE199 5.009/84.18;
  7A_S2/PHE36 5.272/71.81; 7A_S4/PHE43 4.492/64.34; 7A_S4/HIS175 5.221/88.17; 7A_S5/PHE199 5.493/81.65;
  7A_S6/HIS175 5.181/81.03; 7A_S6/TRP195 5.492/81.21; 7B_R4/TRP195 5.152/60.42).
- **Tyr47 rows (all four, no pi classification accepted — pi centroid 6.43-8.61 A exceeds the 5.5 A cutoff):**

| family | interaction_types | receptor atoms | min heavy A | hydrophobic pairs | pi centroid A | pi normal deg | pi offset A |
|---|---|---|---|---|---|---|---|
| 7A_R4 | HYDROPHOBIC | CE2 | 4.4120763819317546 | 1 | 8.219506838409874 | 72.92 | 5.469 |
| 7A_S1 | HYDROPHOBIC | CE2 | 4.453370184478268 | 1 | 8.609365801265502 | 82.86 | 7.949 |
| 7A_S4 | CLOSE_GEOMETRIC_CONTACT | CE2 | 4.4683476811904415 | 0 | 6.428329578254888 | 82.08 | 5.572 |
| 7A_S6 | HYDROPHOBIC | CE2 | 4.125473790972378 | 1 | 6.980068091040054 | 51.37 | 2.469 |

- H-bond geometry rows (8 total, verbatim `hbond_geometry` field):
  `7A_R2/PHE199 O-N:3.347A/144.4deg/LIGAND_DONOR`; `7A_S2/PHE199 O-N:3.056A/135.7deg/LIGAND_DONOR`;
  `7B_R1/GLU128 OE1-N:3.053A/140.3deg/LIGAND_DONOR`; `7B_R1/GLN151 N-N:3.204A/134.0deg/RECEPTOR_DONOR`;
  `7B_S5/SER149 O-N:3.057A/168.6deg/LIGAND_DONOR`; `7B_S5/ASP200 O-N:2.950A/172.7deg/LIGAND_DONOR`;
  `7B_S7/GLN29 OE1-N:2.969A/124.3deg/LIGAND_DONOR`; `7B_S9/GLY199 O-N:2.883A/150.9deg/LIGAND_DONOR`.
- Per-family engaged/hydrophobic/aromatic/pi residue sets + SAM metrics: `family-chemistry-summary.csv` (30 rows).
  Examples: 7A_R1 engaged=36;39;40;43;145;175;195;199;202;231;234;237, hydrophobic=36;39;40;43;145;195;199;231;234,
  aromatic_hydrophobic=36;39;43;195;199;231, pi=none, SAM_min=3.374229097142042 A, SAM_hbond_count=0;
  7A_S6 engaged includes 47, pi=175;195, SAM_min=3.1953885522734162 A, amine-to-methyl angle 27.76263136365402 deg.
  7B escape families (7B_R1, 7B_S5, 7B_S7) engaged=29;33;99;126;128;149;150;151;200;201 with no hydrophobic/aromatic
  residues and SAM_hbond_count=1 for 7B_R1 only.

**Input structure files (verified):**
- `analysis/mettl7-closure/stage4/prepared/7A_WT_SAM_BOUND.pdbqt`, `.../7B_WT_SAM_BOUND.pdbqt` (receptors with AD4 types/charges; hydrogens retained for donor detection).
- Family medoids: `analysis/mettl7-closure/stage4/raw/7{A,B}_WT_{R,S}_s{1,7,42}.pdbqt` (12 files exist) selected via
  `stage4/family_results.csv` + classification from `stage4/interference_family_matrix.csv`.
- Cavity-facing map: `analysis/mettl7-closure/stage12_cavity_chemistry/cavity-facing-atom-map.csv`;
  substitution effects `aligned-cavity-feature-changes.csv`.
- Productive TSL states via `stage8_11_design/run_structural_design.py:382` `productive_states()` -> Stage 3 artifact
  PDBs listed in `analysis/mettl7-closure/stage3/manifest.json` + 7B->7A transform `stage0/superpocket_transfer.json`.
- Freeze inputs also hash `stage12i_dcmb_chemical_retention/FREEZE.json` (exists).

**Feasibility:** Recomputable in Java from in-repo PDBQT/CSV files only, provided Java reproduces AD4-type parsing,
HD donor pairing, and the exact pi gates. 7B states require the frozen rigid transform in `stage0/superpocket_transfer.json`.

---

## 3. Local free volume

### 3a. `local_cavity_volume` (analyze_interference.py:120-127)
Convention: 0.5 A grid over ligand bounding box +/- 3.0 A; voxel counts if min distance to ligand <= 3.0 A AND
min distance to protein >= 2.0 A; volume = count * 0.125 A3.

**Historical values** (`state_compatibility.csv`, `fixed_local_cavity_A3` = original receptor; per-family constant
across the 5 TSL states):
R1 292.75 | R2 295.0 | R3 290.375 | R4 301.375 | R5 284.0 | S1 293.375 | S2 281.375 | S3 298.125 | S4 302.375 |
S5 284.5 | S6 293.625. Relaxed-state values per (family, TSL state 1-5) in the same CSV (`relaxed_local_cavity_A3`,
e.g. R1: 292.25, 294.25, 294.875, 294.25, 294.5).

**Inputs:** same receptor PDB + 6 raw medoid PDBQTs as item 1; relaxed receptors
`analysis/dcmb/tsl_conformational_response/WT_METTL7A_SAM_TSL_relaxed_{1..5}.pdb` (5 files exist).

**Feasibility:** recomputable in Java; note results are exact multiples of 0.125 A3 — grid alignment is sensitive to
the `np.arange(lo, hi + spacing/2, spacing)` endpoint convention.

### 3b. `accessible_volume` (reciprocal_mutation_geometry.py:16-26)
Convention: 0.5 A grid; voxel kept if within `radius=6.0` A of the segment between the DCMB-7A rank-1 centroid and the
Kabsch-fitted DiffDock 7B rank-2 centroid, and >= 2.0 A (`clearance_cutoff`) from every protein heavy atom;
volume = count * 0.125 A3.

**Historical values** (`analysis/dcmb/reciprocal_mutation/pre_docking_geometry.csv`,
`local_accessible_grid_volume_A3`): WT_METTL7A **544.875** | METTL7A_F43L **616.25** | WT_METTL7B **703.125** |
METTL7B_L43F **713.0**. Same file: corridor bottleneck diameters 6.849499289630725 / 8.319870399896526 /
8.514365882664919 / 8.514365882664919 A and side43 sidechain centroids + neighbor-centroid distances
(e.g. WT 7A side43->side199 7.983107560751678 A; 7B side43->side199 is `nan` because 7B position 199 is GLY).

**Inputs (verified):** `resources/shared-resources/src/main/resources/Q9H8H3/Q9H8H3_TMT1A_HUMAN.pdb`;
`experiments/METTL7B-v6_diffdock/target_protein.pdb`; `analysis/dcmb/artifacts/diffdock/DCMB_diffdock_7A_rank1.pdbqt`;
`experiments/METTL7B-v6_diffdock/rank2_confidence-0.3576555848121643.sdf`;
`analysis/dcmb/reciprocal_mutation/METTL7A_F43L_fixed_backbone.pdb` and `METTL7B_L43F_fixed_backbone.pdb` (mutant
constructs pre-generated by `ReciprocalMutationPreparation.java`).

**Feasibility:** recomputable in Java, but depends on sequence alignment + Kabsch fit (in `analysis/dcmb/same_site_pose_analysis.py`)
and the two DiffDock pose centroids; the segment endpoints are derived, not frozen constants.

---

## 4. TSL productive-state / NAC geometry

**Producing scripts:** `analysis/dcmb/tsl_catalytic_geometry/reconstruct_tsl.py` (7A WT initial search) and
`analysis/mettl7-closure/stage3/run_tsl_matrix.py` (8-system matrix). Both deprecated headers state they are retained
for historical regression reproduction only (superseded by `athena.tmt.NearAttackGeometry`/`NearAttackAssessor`/`EnsembleNacAnalyzer`).

**Conventions:**
- TSL = 7alpha-thiospironolactone, SMILES `C[C@]12CCC(=O)C=C1C[C@H]([C@@H]3[C@@H]2CC[C@]4([C@H]3CC[C@@]45CCC(=O)O5)C)S`,
  PubChem CID 119472; RDKit ETKDGv3 seed 20260808 + MMFF; S-to-SAM-CE distances {2.8, 3.0, 3.2} A; 600 rotations/distance;
  attack direction sampled uniformly in the 150-180 deg backside-attack cone (reconstruct_tsl.py:14, 51-54).
- Static acceptance gates: `protein_pairs_lt_2A == 0`, `sam_pairs_lt_2A == 0`, `superpocket_atom_fraction >= 0.70`
  (run_tsl_matrix.py:203-206).
- Response gates (`response_pass`, run_tsl_matrix.py:147-152): protein_pairs_lt_2A==0, sam_pairs_lt_2A==0,
  `max_bond_deviation_A <= 0.02`, `backbone_rmsd_A <= 0.25`, `max_atom_displacement_A <= 1.50`.
- NAC metrics: `tsl_s_to_sam_methyl_A` (TSL S to SAM CE) and `attack_angle_TSL_S_Cmethyl_SAM_S_deg`
  (reconstruct_tsl.py:34-38).

**Historical values:**
- `tsl_catalytic_geometry/search_summary.json`: status **FAIL** for the raw 7A WT search: 14400 tested
  (8 conformers x 3 distances x 600 rotations), retained=0, zero_protein_clash=1, zero_sam_clash=8051, zero_both_clash=0.
  Best failed candidates: conf5/rot505 d=3.0 A angle=151.44507528201873 deg protein_min=1.3967548952844409 A (3 pairs<2A);
  conf4/rot173 d=3.2 A angle=153.81937098093493 deg protein_min=1.4061401005054666 A (4 pairs<2A).
- `stage3/matrix_summary.csv` (verbatim; receptor + SAM complex sha256 recorded per row):

| system | tested | static passing | static families | accepted states |
|---|---|---|---|---|
| 7A_WT | 14400 | 0 | 0 | 5 (response required) |
| 7B_WT | 14400 | 7 | 6 | 6 (STATIC) |
| 7A_F43L | 14400 | 0 | 0 | 5 |
| 7A_F199G | 14400 | 0 | 0 | 5 |
| 7A_F43L_F199G | 14400 | 0 | 0 | 5 |
| 7B_L43F | 14400 | 11 | 7 | 7 (STATIC) |
| 7B_G199F | 14400 | 1 | 1 | 1 (STATIC) |
| 7B_L43F_G199F | 14400 | 3 | 2 | 2 (STATIC) |

- `stage3/all_states.csv` (36 PASS rows): per-state `catalytic_distance_A` (S...CE; values 2.8/3.0/3.2 by construction)
  and `attack_angle_deg` ranging 150.49673003310866 (7A_F199G state 3) to 170.9708788573675 (7A_F43L_F199G state 1);
  7B_WT state 2 has the largest static angle 169.97914703321064. 7A_WT states 1,2,5 = LIMITED_LOCAL_BACKBONE
  (mobile atoms 88/97/72; backbone RMSD 0.0422/0.0392/0.0372 A), states 3,4 = SIDECHAIN_ONLY. Response ceilings
  confirmed in `TSL_FEASIBILITY_REPORT.md`: max backbone RMSD 0.0424 A, max displacement 1.4140 A,
  max bond deviation 0.0122 A.
- Constant SAM geometry from the interference analysis: `SAM_SD_to_CE_A = 1.7258971580021796` (all 55 corridor rows,
  `catalytic_corridor.csv`); per-state `attack_length_A` = TSL S to SAM CE: 2.9999849999625 / 3.2001315597956284 /
  2.799656228896683 / 2.7999701784126203 / 2.9996823165128665 (TSL states 1-5, R1 row block).

**Input structure files (verified):**
- `analysis/mettl7-closure/stage2/prepared/{system}_receptor.pdb` and `{system}_SAM_BOUND.pdb` for all 8 systems
  (16 files exist; sha256 values in matrix_summary.csv).
- Superpocket spheres: `analysis/mettl7-closure/stage0/METTL7A_homologous_197_sphere_SAM_superpocket.pqr` (7A) and
  `resources/shared-resources/src/main/resources/Q6UX53/fpocket/pockets/pocket2_vert.pqr` (7B).
- Frozen productive-state PDBs used downstream: `analysis/mettl7-closure/stage3/{system}/{system}_SAM_TSL_{n}.pdb`
  (e.g. 7A_WT states 1-5, 7B_WT states 1-6; exist).
- **Outside repo:** reconstruct_tsl.py's 7B sphere cloud source
  `/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6_pockets/fpocket-human/AF-Q6UX53-F1-model_v6-1472429501895029362/AF-Q6UX53-F1-model_v6_out/pockets/pocket1_vert.pqr`
  (exists on this machine; used only by the original 7A-only search and by the BI-187004/reaction-competence pocket clouds).

**Feasibility:** NAC distances/angles recomputable in Java from the frozen stage3 state PDBs alone (S, CE, SD coordinates).
Re-running the 14400-placement search requires RDKit conformer generation — not Java-feasible; treat search counts as
golden constants, and recompute per-state geometry from the frozen PDBs.

---

## 5. DCMB x TSL overlap (analyze_interference.py)

**Conventions:**
- `shared_volume` (lines 87-96): 0.5 A voxel grid, voxel counts if within `radius` of both point sets
  (radius 1.7 for `shared_occupied_volume_A3`, 1.0 for `steric_core_overlap_A3`).
- `corridor_fraction` (lines 108-117): 0.5 A grid over segment TSL_S -> SAM CE bbox +/- r (r in {1.5, 2.0, 2.5});
  corridor = distance <= r AND projection in [0,1]; occupied = within 1.7 A of a DCMB atom.
- `swept_metrics` (lines 167-198): path distance < 3.4 A = vdw intersection, < 2.0 A = core; swept overlap =
  shared_volume(ligand, densely-sampled moved-atom paths, 1.7).
- Classification gates (lines 365-379): A_DIRECT_SUBSTRATE_OCCLUSION if any state with `atom_pairs_lt_2A > 0`;
  B_CATALYTIC_APPROACH_BLOCKADE if any state `between_and_within_2A > 0`; C_CONFORMATIONAL_GATING_INTERFERENCE if any
  state `swept_overlap_A3 >= 0.5`; D if `fixed_better_states >= 3` (clash_pair_change>0 or min-distance change < -0.25);
  E otherwise.

**Historical values:**
- `summary.json`: families=11, pairings=55, families_with_direct_overlap=**11**, families_with_corridor_blockade=**6**,
  families_with_swept_volume_interference=**8**, families_with_core_path_interference=**0**.
- Mechanism labels per family (`family_classifications.csv`): R1 A;B | R2 A;C | R3 A;B;C | R4 A;B | R5 A;B;C |
  S1 A | S2 A;C | S3 A;C | S4 A;B;C | S5 A;B;C | S6 A;C. All 11 families: direct_overlap_states_of_5=5;
  state_compatibility_class=`equally_compatible_or_mixed` for all 11; `fixed_better_states_of_5`=0 for all;
  `catalytic_better_states_of_5`=1 for R2, 2 for S3, 0 otherwise.
- Per-pair detail: `dcmb_tsl_pairwise.csv` (55 rows; e.g. R1 x TSL1: min 0.5593120774665962 A, 16 pairs<2A,
  shared occupied volume 63.375 A3, steric core 7.625 A3; R1 x TSL3: min 0.2633343881835416 A, 105.75 A3 shared);
  `catalytic_corridor.csv` (55 rows; corridor occupied fractions, e.g. R1 x TSL1 r1.5=0.2345679012345679);
  `swept_volume.csv` (55 rows; max swept_overlap_A3 = 1.125 at S6 x TSL2; moved_atoms per state 103/87/10/21/77).

**Inputs:** same as item 1 (receptor PDB, 6 raw medoid PDBQTs, family_results.csv) plus the 5 relaxed TSL state PDBs
(item 3a). All in-repo.

**Feasibility:** fully recomputable in Java from files. Grid endpoint convention as in 3a.

---

## 6. Netarsudil 196-207 contacts + C202/C203 distances

### 6a. Reaction-competence NAC measurement (research/mettl7-reaction-competence-v1/run_analysis.py)
Gate (line 113): near-attack iff `2.8 <= distance <= 3.2` A AND `angle >= 150` deg; distance = ligand N to SAM CE,
angle = N-CE-SD. Historical output `netarsudil_acceptor_state_analysis.csv` (verbatim):

| paralog | pose family | acceptor | atom | distance A | angle deg | in-pose near-attack |
|---|---|---|---|---|---|---|
| 7B | ACCEPTED_7B_FAMILY5 | PRIMARY_AMINE_N | N5 | 16.8565 | 98.454 | FALSE |
| 7B | ACCEPTED_7B_FAMILY5 | ISOQUINOLINE_N | N6 | 17.0993 | 119.451 | FALSE |
| 7A | MATCHED_7A_LOWEST_STRAIN_CONTROL | PRIMARY_AMINE_N | N5 | 9.282 | 56.181 | FALSE |
| 7A | MATCHED_7A_LOWEST_STRAIN_CONTROL | ISOQUINOLINE_N | N6 | 15.1994 | 97.58 | FALSE |

All rows: `NOT_OBSERVED_IN_ACCEPTED_POSE; BOUNDED_REORIENTATION_NOT_RUN`. Protocol constants in `protocol.json`
(distance [2.8,3.2], angle>=150, protein clash 1.8 A, SAM clash 2.0 A, pocket containment >= 0.70).

**Inputs (verified):** `research/mettl7-netarsudil-sam-mechanism/vina-matched/raw/7B_neutral_seed483271.pdbqt`
(MODEL 5); `research/mettl7-netarsudil-sam-mechanism/local-architecture/prepared/corrected_7A_lowest_strain_mode15.pdbqt`;
SAM from `analysis/dcmb/sam_state/validated/WT_METTL7{A,B}_SAM_BOUND.pdb` (both exist).

### 6b. C202/C203 family geometry (vina-matched campaign)
Historical output `research/mettl7-netarsudil-sam-mechanism/vina-matched/analysis/c202_c203_family_geometry.csv`
(5 poses) and `c202_c203_geometry_summary.json`:

- Representative (seed 483271, mode 5, accepted 7B neutral family): **C202 min 5.501731363852656 A** (ligand O1 to
  backbone N; SG min 8.86536863305751 A), **C203 min 3.4421490380284236 A** (ligand C12 to SG; SG is the minimum atom).
- Family distributions: C202 min 5.288-5.502 A (median 5.371); C203 min 3.442-3.693 A (median 3.517).
  All 3 admissible poses contact C203 within 4.0 A; none contacts C202 within 4.0 A.
- Classifications: `C202_NETARSUDIL_ROLE=WALL_NONCONTACT`, `C203_NETARSUDIL_ROLE=OUTSIDE_POCKET_CONTACT`;
  numbering QC: chain A CYS202 and CYS203 confirmed, C-alpha delta 0.000 A (no numbering shift).
- Per-pose rows: 172904/7 C202 5.370561795566642, C203 3.449108435523592; 483271/13 (inadmissible) 5.287789330145444 /
  3.632011426193481; 806519/10 5.3891699731962435 / 3.5165468857957802; 806519/13 (inadmissible) 5.30240294583503 /
  3.6926112711738286.

### 6c. 196-207 contact sets
- `vina-matched/analysis/representative_local_wall_classification.csv` (accepted 7B representative, min distances):
  GLN151 3.057664141137807 (wall), LYS196 3.0718103782623034, GLU207 3.1681409375215623, ARG206 3.231533382157764,
  GLY201 3.4028896543966876 (wall), THR205 3.4099536653743554, CYS203 3.4421490380284236, GLN29 3.519218379129093 (wall),
  ASP200 3.5216197977635235 (wall), SER149 3.669907083292437 (wall); CYS202 5.501731363852656 WALL_NONCONTACT;
  contact convention: <= 4.0 A per METTL7_NETARSUDIL_SAM_MATCHED_VINA.md line 83 (contacts: Q29, S149, Q151, K196,
  D200, G201, C203, T205, R206, E207).
- `local-architecture/analysis/corrected_7a_vs_accepted_7b_residues.csv` (190-210, direct_contact flag):
  7B accepted representative direct contacts at 196, 200, 201, 203, 205, 206, 207; corrected 7A pose direct contacts
  only at 196 (3.5687894306052845 A), 197 (3.5581166366492254 A), 200 (2.8622840529898497 A), 201 (3.4570559729341954 A);
  7A C202 5.538939338898739 A and 7A position 203 is ASN (7.354455316337165 A) — no contact.
- Report-only historical numbers (not recomputable from CSV): DCMB manuscript amine-to-SAM-methyl distances
  **3.6 A in 7A and 7.6 A in 7B** (`research/mettl7-opposed-anchor-selectivity-v2/dcmb_vs_netarsudil_descriptor_matrix.csv`,
  SAM_RELATIVE_POSITION row); accepted 7B netarsudil pose SAM min 3.683 A and MMFF94s strain 14.04 kcal/mol
  (METTL7_NETARSUDIL_SAM_MATCHED_VINA.md:76-78); corrected 7A strain 19.80 kcal/mol (descriptor matrix LIGAND_STRAIN row).

**Inputs:** vina-matched raw pose PDBQTs `research/mettl7-netarsudil-sam-mechanism/vina-matched/raw/{7A,7B}_{neutral,monocation}_seed{172904,483271,806519}.pdbqt` (exist) and receptors
`vina-matched/prepared/METTL7{A,B}_SAM_receptor.pdbqt` (exist).

**Feasibility:** 6a and 6b/6c distances recomputable in Java from the in-repo PDBQT/PDB files. The "3.6/7.6 A" DCMB
historical numbers exist only in report/CSV prose — treat as golden constants, not recomputable.

---

## 7. BRICS representative residue-contact set (Stage 12D)

**Producing script:** `analysis/mettl7-closure/stage12d_region1_fragments/run_stage12d.py`
- Contact convention (`contacts()`, lines 126-134): any fragment atom within **4.5 A** of a protein atom -> residue
  contact; channels: hydrophobic (fragment C/S vs hydrophobic residue), aromatic (aromatic fragment atom vs aromatic
  residue), polar (N/O/S vs polar residue), ionizable (charged residue environment) — residue sets from
  `stage12_cavity_chemistry/run_cavity_chemistry.py`.
- Placement gates frozen in `PROTOCOL.json`: region1 membership tolerance 0.76 A; 48 farthest-point Region-1 voxel
  centers; 24 proper cube rotations; SAM min distance 2.5 A; required A clash atoms >= 1; B-over-A surface clearance
  advantage >= 0.5 A; bottleneck reachability 4.02 A; TSL interference = direct <2.0 A or corridor <=2.0 A in a frozen
  7B state; max 96 library entries, 3-8 heavy atoms, charge -1..1, ETKDGv3 seed 12012+i, 4 conformers/fragment.

**Historical values (verified):**
- Library: **96** BRICS fragments from 218 frozen Stage 6.1 CCD files (`fragment-library.csv`).
- **Retained Region-1 placements: 0** (`retained-region1-placements.csv` is header-only; REPORT.md: "None passed every
  frozen gate"). Rejection audit: BOTTLENECK_UNREACHABLE 27 fragments/83 states; B_PROTEIN_CLASH 81/42641;
  NO_A_B_DIFFERENTIAL 81/11838; OUTSIDE_REGION1 81/292273.
- Shared-site anchors: **6** (`shared-site-anchor-fragments.csv`): A01 F002 `C1CC1` SAM 3.436 A, 7A/7B clearance
  0.267/0.521; A02 F004 `CNC` 3.367, 0.195/0.578; A03 F005 `C1CCNC1` 3.033, 0.134/0.068; A04 F010 `FC(F)F` 3.095,
  0.166/0.383; A05 F011 `CCC` 2.997, 0.244/0.092; A06 F012 `CCO` 3.475, 0.310/0.080. Anchor pose coordinates are
  embedded in the CSV (`pose_coordinates` column). Anchor-linker hypotheses: 0.
- VALIDATION.json: region1_voxels and counts frozen there.

**Inputs (verified):** `analysis/mettl7-closure/stage6_1/ccd/*.cif` (218 files); stage2 prepared 7A/7B SAM-bound PDBs;
stage3 TSL artifacts via stage3/manifest.json; `stage8_11_design/stage8-clearance-profiles.csv`; stage12_cavity_chemistry
outputs; `stage0/superpocket_transfer.json` (7B transform).

**Feasibility:** NOT recomputable in Java as-is (RDKit BRICS cleavage + ETKDG conformers). Suggested regression:
freeze anchor pose coordinates from the CSV and recompute only the 4.5 A residue-contact sets and surface-clearance
values in Java. Separately, "BRICS representative" contact sets at screening scale exist as the 50 METTL7-BRICS-* rows
in the item-8 contact matrix.

---

## 8. 74-ligand contact matrix (docking selectivity inventory)

**Producing script:** `analysis/docking-selectivity-inventory/build_inventory.py`
- CONTACT_CUTOFF = 4.0 A (line 17); contact = `docking.pose_residue_contact.min_distance <= 4.0` (SQL line 46).
- Runs: 7B = (2087, 2099, 2101), 7A = (2088, 2100, 2102) (line 16); observation unit = lowest-Vina-score pose per
  ligand per paralog; warhead-labeled ligands excluded; both paralogs required.
- Upstream Java convention: `PoseContactCalculator.CUTOFF_ANGSTROM = 4.0`
  (`software/apps/web-api/src/main/java/totah/lab/web/docking/PoseContactCalculator.java:20`); per residue keeps
  atom-pair count and min pair distance. Table schema: `docking.pose_residue_contact(pose_id, residue_id,
  atom_contact_count, min_distance)` with CHECK `min_distance BETWEEN 0 AND 4.0`
  (`software/apps/web-api/src/test/resources/docking_test_schema.sql`).

**Historical values (verified):**
- `summary.json`: run_key `METTL7_DOCKING_CONTACT_SELECTIVITY_NONWARHEAD_2026_08_30`, **paired_ligands=74**.
- `matched_pose_contact_sets.csv`: 148 rows (74 ligands x 2 enzymes), columns
  `ligand,enzyme,pose_id,vina_score_engine_output,contact_positions,contact_count`.
- Top single-residue contact frequencies (size 1): 7A — SER149 72/74 (0.9730), ASP200 70 (0.9459), PRO99 69 (0.9324),
  LYS151 68 (0.9189), LYS33 68 (0.9189), GLY201 63, SER29 63, ALA126 60, HIS196 57, GLU128 43, ASN23 43, VAL150 35,
  LEU197 20. (7B top-20 in the same summary.json.)
- Example rows: `864 / CHEMBL4436028` 7A pose 1040776 (score -12.152) contacts
  36;40;43;44;78;79;83;98;99;103;144;145;146;149;175;195;199;202;231;234 (20); 7B pose 1024117 (-10.44) contacts
  36;40;78;79;80;83;84;98;99;144;145;146;149;199;202;229;232;234 (18). `BIX 01294` 7A 27 contacts (incl. 47, 55, 175,
  195, 199, 200, 202), 7B 21. `METTL7-BRICS-0034` and `-0043` have empty contact sets in both paralogs (0 contacts).

**Best representative ligands for a subset regression:** the four non-BRICS/non-RESCUE ligands —
`864 / CHEMBL4436028`, `BIX 01294`, `MCULE-2135392775`, `MCULE-4144593857` — diverse chemotypes, both paralogs,
11-27 contacts per pose. For BRICS coverage add `METTL7-BRICS-0001` (10/18) and one empty-set control
(`METTL7-BRICS-0034`, 0/0).

**Inputs:** the pose geometry exists only as PostgreSQL rows (`docking.docking_pose`, `docking.pose_residue_contact`,
`docking.residue`) in `totah_lab_db` — **no PDBQT exports in this directory**. `combination_db_rows.csv` (13.7 MB),
`residue_combination_frequency.csv` (8.8 MB), `residue_combination_selectivity.csv` (10.1 MB) are the frozen extracts.

**Feasibility:** a JUnit test can recompute contacts from raw pose+receptor coordinates only if the pipeline is run
through `PoseContactCalculator` against the same poses (requires DB or re-docking). Practical regression: treat
`matched_pose_contact_sets.csv` as the golden file and verify the Java contact calculator reproduces per-pose contact
sets for the representative ligands from their pose coordinates (which must first be exported from the DB — currently
not in-repo).

---

## Cross-cutting input inventory

All structure inputs verified to exist 2026-09-05 except where noted. The only out-of-repo input is the fpocket
7B sphere cloud under `/Users/yazan/artifacts/` (used by reconstruct_tsl.py and the BI-187004/reaction-competence
machinery). Everything else is in-repo under `analysis/`, `research/`, `resources/`, or `experiments/`.
See `inputs-manifest.csv` for the per-metric file list.
