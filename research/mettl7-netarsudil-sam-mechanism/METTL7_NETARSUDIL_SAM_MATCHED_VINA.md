# METTL7 netarsudil–SAM matched Vina study

**Run key:** `METTL7_NETARSUDIL_SAM_MATCHED_VINA_2026_08_29`  
**Status:** complete  
**Scope:** rigid-receptor Vina pose-family evidence only. Scores are engine outputs, not affinities. This study does not establish catalytic activation or allostery.

## Conclusions

`NETARSUDIL_SAM_COMPATIBLE_7A = INDETERMINATE`

`NETARSUDIL_SAM_COMPATIBLE_7B = YES`

`NETARSUDIL_SITE_RELATIVE_TO_SAM_7A = INDETERMINATE`

`NETARSUDIL_SITE_RELATIVE_TO_SAM_7B = ADJACENT`

The positive 7B classification is protonation-state-sensitive: the only admissible family was obtained for the neutral free-base state. The monocation produced no family passing all frozen physical and reproducibility gates. The 7A result is not a negative binding conclusion; it is indeterminate because no family passed the frozen strain gate.

## Frozen matched protocol

- Vina: `AutoDock Vina v1.2.5-17-gda92a68`
- receptor: canonical METTL7A or METTL7B complex with SAM explicitly incorporated in the receptor PDBQT
- center: `(2.844, -2.100, -4.211)` Å
- size: `(25.334, 19.263, 23.923)` Å
- exhaustiveness: 32
- modes requested: 20
- fixed seeds: 172904, 483271, 806519
- CPU: 2 per job; jobs executed serially
- ligand states: neutral free base and pH-7.4-generated monocation, maintained as distinct conditions
- family gate: symmetry-aware heavy-atom RMSD ≤2.5 Å; ≥3 physically admissible poses from ≥2 seeds
- physical gates were evaluated separately: protein severe clashes, SAM severe clashes, MMFF94s strain, and ligand SASA burial. No composite score was calculated.

The initially completed one-CPU pilot was excluded and the entire matrix was rerun uniformly at two CPUs. Only the uniformly generated outputs listed in `run_manifest.json` enter this report.

## Explicit-SAM receptor proof

| Receptor | Canonical SAM heavy atoms | Final PDBQT SAM heavy atoms | Total SAM records | Recognized atom types | SAM charge sum | Coordinate RMSD / maximum delta | QC |
|---|---:|---:|---:|---|---:|---:|---|
| METTL7A | 27 | 27 | 49 | A, C, H, HD, N, NA, OA, S | -0.0004 | 0.000 / 0.000 Å | PASS |
| METTL7B | 27 | 27 | 49 | A, C, H, HD, N, NA, OA, S | -0.0004 | 0.000 / 0.000 Å | PASS |

The 49 records comprise 27 heavy atoms and 22 prepared hydrogens. Coordinate-set comparison is one-to-one because PDBQT torsion-tree ordering differs from source-PDB ordering. Every Vina config names the QC-validated receptor PDBQT, and every execution log repeats that exact receptor path before docking. Receptor hashes are recorded independently in `sam_receptor_qc.json` and per run in `run_manifest.json`.

This directly resolves the Vina question: SAM could affect grid construction, pose generation, and scoring because its typed, charged atoms were part of the receptor actually supplied to Vina. This conclusion does not resolve whether the earlier DiffDock preprocessing used SAM.

## Ligand preparation

The source identity was PubChem CID 66599893 with the reported stereochemistry. Hephaestus generated AutoDock4 atom types and Gasteiger charges.

| State | Atom records | Total charge | Nonzero-charge atoms | Role |
|---|---:|---:|---:|---|
| neutral | 61 | 0.000 | 61 | free-base sensitivity state |
| monocation | 62 | +1.000 | 62 | pH 7.4 primary-amine protonation state |

No states were pooled during clustering or classification.

## Pose and gate results

| Paralog | State | Poses returned | Score range (kcal/mol, Vina output) | No protein <1.8 Å clash | No SAM <2.0 Å clash | Burial ≥20% | Strain ≤15 kcal/mol | Admissible families |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 7A | neutral | 60 | -7.953 to -5.280 | 60 | 60 | 60 | 0 | 0 |
| 7A | monocation | 58 | -8.079 to -5.511 | 58 | 58 | 58 | 0 | 0 |
| 7B | neutral | 60 | -7.555 to -6.432 | 60 | 60 | 60 | 3 | 1 |
| 7B | monocation | 60 | -7.574 to -6.520 | 60 | 60 | 60 | 0 | 0 |

The 7A neutral minimum strain was 17.68 kcal/mol and the 7A monocation minimum was 20.21 kcal/mol. The 7B monocation minimum was 23.37 kcal/mol. Thus the absence of an admissible family in these arms is driven by the prespecified strain criterion, not by SAM overlap, protein severe clashes, burial, or score.

## Admissible METTL7B family

The sole admissible family is `7B / neutral / family 5`:

- 5 clustered poses total
- 3 physically admissible poses from seeds 172904, 483271, and 806519
- representative: seed 483271, mode 5
- Vina score range within the complete family: minimum -7.304; mean -7.133 kcal/mol
- representative SAM minimum heavy-atom distance: 3.683 Å
- representative ligand SASA burial reduction: 60.81%
- representative MMFF94s strain: 14.04 kcal/mol
- protein heavy-atom pairs <1.8 Å: 0
- netarsudil–SAM heavy-atom pairs <2.0 Å: 0
- site relation: `ADJACENT`

Representative residue contacts within 4.0 Å were Q29, S149, Q151, K196, D200, G201, C203, T205, R206, and E207. Of the prespecified A/B architecture positions, only D200 contacted the representative at ≤4.0 Å (3.52 Å). Positions 39–40, 43, 47, 144–145, 173–175, 195, 198–199, and 228–234 were not representative-family contacts. This docking result therefore does not implicate positions 43 or 199 as netarsudil determinants.

## Post-analysis clarification: C202 versus C203 geometry

This clarification was added after the local-flexibility follow-up and does not alter the frozen Vina family classification. It resolves why the 4.0 Å contact table included C203 but not the historically assigned productive-pocket wall residue C202.

The pocket reference is the 197-alpha-sphere METTL7B SAM superpocket: imported-run fpocket pocket 1/DB pocket 3 and checked-in rerun pocket 2. The checked-in `pocket2_atm.pdb` explicitly assigns C202 CB and SG as pocket-wall atoms. It assigns no C203 atom. Canonical-PDB and prepared-receptor checks confirm chain A CYS202 and CYS203 at the intended sequence positions, with 0.000 Å Cα coordinate differences after preparation. There is no numbering shift.

### Five-pose family geometry

| Seed | Mode | Physically admissible | C202 minimum: ligand–protein atoms (Å) | C202 SG: ligand atom–SG (Å) | C203 minimum: ligand–protein atoms (Å) | C203 SG: ligand atom–SG (Å) |
|---:|---:|---|---|---:|---|---:|
| 172904 | 7 | yes | O1–N, 5.371 | 8.818, C14–SG | C11–SG, 3.449 | 3.449 |
| 483271 | 5 | yes; representative | O1–N, 5.502 | 8.865, C15–SG | C12–SG, 3.442 | 3.442 |
| 483271 | 13 | no | C15–N, 5.288 | 8.663, C15–SG | C12–SG, 3.632 | 3.632 |
| 806519 | 10 | yes | O1–N, 5.389 | 8.929, C14–SG | C11–SG, 3.517 | 3.517 |
| 806519 | 13 | no | C15–N, 5.302 | 8.677, C15–SG | C12–SG, 3.693 | 3.693 |

Across the complete family, C202 minimum distances span 5.288–5.502 Å (median 5.371 Å), while C203 minimum distances span 3.442–3.693 Å (median 3.517 Å). C203 SG produces the C203 minimum in every pose. All three physically admissible seeds contact C203 within 4.0 Å; none contacts C202 within 4.0 Å.

This is therefore not a borderline C202 ≈4 Å versus C203 <4 Å cutoff artifact. The ligand genuinely projects around/beyond the C202 wall toward the outside-wall C203 thiol.

### Alpha-sphere boundary and pocket occupancy

For the representative, two of six C202 heavy atoms lie inside the operational alpha-sphere union; C202 CB is only +0.005 Å inside its sphere-union boundary and fpocket explicitly identifies CB/SG as wall atoms. No C203 heavy atom lies inside the union; its closest atom, backbone N, is 2.935 Å outside the operational boundary, and C203 is absent from the fpocket wall-atom file.

Eleven of 34 netarsudil heavy atoms (32.4%) lie inside the alpha-sphere union. O1 is closest to the operational union boundary at +0.241 Å on the inside. Netarsudil therefore does not remain predominantly inside the established pocket: it crosses the boundary and occupies an **adjacent extension**.

Residue roles must consequently be separated:

| Role | Residues around the representative |
|---|---|
| `FPOCKET_WALL_RESIDUE` with direct ≤4 Å ligand contact | Q29, S149, Q151, D200, G201 |
| `DIRECT_LIGAND_CONTACT` but outside the fpocket wall | K196, C203, T205, R206, E207 |
| `PROXIMITY_ONLY` at 4–5 Å | K33, V150, E192, D211, P99 |
| `FPOCKET_WALL_RESIDUE` without direct ligand contact | C202 (5.502 Å minimum; SG 8.865 Å), plus more distant wall residues listed in the machine-readable table |

Final residue classifications:

`C202_NETARSUDIL_ROLE = WALL_NONCONTACT`

`C203_NETARSUDIL_ROLE = OUTSIDE_POCKET_CONTACT`

C203 is a reproducible geometric contact to the ligand extension, but it is not thereby a productive-pocket wall residue or a mechanistic determinant. No causal role is assigned.

## 7A versus 7B

Both paralogs admitted many raw SAM-nonclashing placements, so the result is not simply “netarsudil fits 7B but clashes with 7A.” The bilateral difference appears only after conformational-strain and cross-seed reproducibility gates are imposed: one neutral 7B family survives, while no 7A family survives. Because the surviving strain value (14.04 kcal/mol) lies close to the frozen 15 kcal/mol boundary and the +1 state fails, confidence is **moderate-low** and explicitly state-sensitive.

Vina scores are not used to infer affinity or activation. The lowest raw score occurred in 7A, demonstrating why best-score selection alone would have produced a contradictory and unsupported conclusion.

## Relationship to established DCMB families

The admissible 7B netarsudil representative is not spatially coincident with the established 7B SAM-bound DCMB families. Its centroid is 7.78 Å from the closest populated (population ≥3) DCMB family centroid (DCMB R family 1, population 4); the comparable S family 5 centroid is 8.21 Å away. These centroid distances indicate a different placement within the broader productive-pocket region; they are not binding-energy comparisons.

## Relationship to existing DiffDock hypotheses

DiffDock SAM-awareness remains `INDETERMINATE`, so its outputs are treated only as independent global-site hypotheses.

- The admissible 7B Vina-family centroid is 9.49 Å from the closest 7B DiffDock netarsudil centroid. The channels therefore do not converge on the same 7B placement.
- The closest 7A DiffDock centroid is 4.38 Å away in the aligned coordinate frame, but there is no admissible 7A Vina family and the DiffDock receptor representation remains unresolved. This does not constitute cross-method confirmation.

## Canonical follow-up: corrected 7A window and local architecture

The original shared-window METTL7A arm is superseded by the native-frame correction documented in `METTL7_NETARSUDIL_7A_NATIVE_BOX_CORRECTION.md`. The corrected arm remains `NETARSUDIL_SAM_COMPATIBLE_7A = INDETERMINATE`: no neutral or monocation pose passed the frozen strain gate, although all corrected poses passed severe-clash and burial checks. The valid METTL7B neutral family remains unchanged.

The focused local-architecture follow-up is documented in `METTL7_NETARSUDIL_LOCAL_ARCHITECTURE.md` and concludes:

`NETARSUDIL_7B_LOCAL_SELECTIVITY_MECHANISM = DISTRIBUTED_196_207`

`C203_CAUSAL_SUPPORT = NOT_SUPPORTED`

C203N remains admissible in 7B, N203C does not rescue the transferred 7A geometry, and clash-qualified reciprocal probes at K196, H197/I198, G199 and T208 all remain admissible in the 7B backbone. The supported distinction is therefore collective local structural context—backbone/rotamer geometry, solvent enclosure/electrostatics and extension-contact topology—rather than C203 or another tested side-chain identity acting alone. This remains a structural-compatibility conclusion only.

## Scientific limits

- This is rigid-receptor docking with explicit SAM, not a catalytic assay, free-energy calculation, or demonstration of allostery.
- `YES` for 7B means that a reproducible, frozen-gate-passing pose family is sterically compatible with SAM in this model.
- `INDETERMINATE` for 7A does not mean that netarsudil cannot bind METTL7A.
- The neutral-only 7B result should be tested for sensitivity to higher-quality protonation/tautomer ensembles and protein flexibility only if further computation is justified.
- No RNA docking was performed. The Kimi TSL-RSH force-field work was not modified.

## Reproduction and artifacts

- Runner: [`scripts/run_matched_vina.py`](scripts/run_matched_vina.py)
- Analyzer: [`scripts/analyze_matched_vina.py`](scripts/analyze_matched_vina.py)
- SAM QC: [`vina-matched/sam_receptor_qc.json`](vina-matched/sam_receptor_qc.json)
- Run manifest and exact commands: [`vina-matched/run_manifest.json`](vina-matched/run_manifest.json)
- Frozen gates: [`frozen_admissibility_gates.json`](frozen_admissibility_gates.json)
- Prepared receptors/ligands: [`vina-matched/prepared`](vina-matched/prepared)
- Configs: [`vina-matched/configs`](vina-matched/configs)
- Logs: [`vina-matched/logs`](vina-matched/logs)
- Raw poses: [`vina-matched/raw`](vina-matched/raw)
- Pose metrics: [`vina-matched/analysis/pose_metrics.csv`](vina-matched/analysis/pose_metrics.csv)
- Family results: [`vina-matched/analysis/family_results.csv`](vina-matched/analysis/family_results.csv)
- Contacts: [`vina-matched/analysis/family_contacts.csv`](vina-matched/analysis/family_contacts.csv)
- C202/C203 five-pose geometry: [`vina-matched/analysis/c202_c203_family_geometry.csv`](vina-matched/analysis/c202_c203_family_geometry.csv)
- Representative wall/contact classification: [`vina-matched/analysis/representative_local_wall_classification.csv`](vina-matched/analysis/representative_local_wall_classification.csv)
- C202/C203 geometry summary: [`vina-matched/analysis/c202_c203_geometry_summary.json`](vina-matched/analysis/c202_c203_geometry_summary.json)
- Machine-readable classifications: [`vina-matched/analysis/classification.json`](vina-matched/analysis/classification.json)
- Preserved DiffDock comparison inputs: [`vina-matched/comparison`](vina-matched/comparison)
