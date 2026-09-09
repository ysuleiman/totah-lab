# METTL7A netarsudil/SAM native-box correction

**Run key:** `METTL7_NETARSUDIL_7A_NATIVE_BOX_CORRECTION_2026_08_30`  
**Status:** complete  
**Scope:** bounded correction of the METTL7A rigid-receptor Vina arm. The original campaign is preserved unchanged.

## Corrected conclusion

`NETARSUDIL_SAM_COMPATIBLE_7A = INDETERMINATE`

`NETARSUDIL_SITE_RELATIVE_TO_SAM_7A = INDETERMINATE`

`NETARSUDIL_SAM_COMPATIBLE_7B = YES`

`NETARSUDIL_SITE_RELATIVE_TO_SAM_7B = ADJACENT`

The 7A classification is no longer confounded by use of the 7B numerical window in the distinct METTL7A native coordinate frame. No corrected 7A family passed the frozen physical and cross-seed admissibility gates. This is not evidence that netarsudil cannot bind METTL7A.

## Reason for correction

The established pocket campaign uses native per-paralog coordinates and distinct boxes:

| Target | Center (Å) | Size (Å) |
|---|---|---|
| METTL7A | `(1.8020, -3.9254, -6.7763)` | `(28.452, 22.000, 26.506)` |
| METTL7B | `(2.8444, -2.1005, -4.2105)` | `(25.334, 22.000, 23.923)` |

The original netarsudil run applied a 7B-centered numerical window to both native-frame receptors without transforming METTL7A into the METTL7B frame. This correction reran only METTL7A using its established native-frame box.

## Frozen protocol retained

- unchanged canonical METTL7A+SAM receptor PDBQT, including all 27 SAM heavy atoms;
- unchanged neutral and monocation netarsudil PDBQT inputs;
- Vina `v1.2.5-17-gda92a68`;
- exhaustiveness 32 and 20 requested modes;
- fixed seeds 172904, 483271 and 806519;
- two CPU threads per serial job;
- symmetry-aware family cutoff of 2.5 Å;
- family acceptance requires at least three physically admissible poses from at least two seeds;
- separate physical gates: no protein heavy-atom pair below 1.8 Å, no ligand–SAM pair below 2.0 Å, MMFF94s strain at most 15 kcal/mol, and burial reduction at least 20%.

No threshold, clustering rule, ligand state, receptor atom, or seed was changed. The original 7B raw outputs were copied into the corrected comparison set and verified by hashes; 7B was not rerun.

## Corrected 7A results

| State | Poses | Protein-clash-free | SAM-clash-free | Burial ≥20% | Minimum strain (kcal/mol) | Physical passes | Admissible families |
|---|---:|---:|---:|---:|---:|---:|---:|
| neutral | 60 | 60 | 60 | 60 | 19.798 | 0 | 0 |
| monocation | 60 | 60 | 60 | 60 | 20.261 | 0 | 0 |

The largest corrected 7A neutral family contained seven poses and the largest monocation family contained nine. Both were SAM-adjacent and free of severe clashes, but their representative MMFF94s strains were 32.95 and 35.05 kcal/mol, respectively. Across all corrected poses, the lowest strain still exceeded the frozen 15 kcal/mol ceiling.

Consequently, the corrected 7A arm remains `INDETERMINATE` because no pose was physically admissible under the preregistered gates. Vina scores are retained only as engine outputs and are not interpreted as affinity.

## Corrected bilateral interpretation

The valid comparison is now corrected native-box 7A versus the unchanged original 7B arm. The sole admissible 7B result remains `7B / neutral / family 5`, adjacent to SAM. The asymmetry survives correction at the level of the frozen structural screen: an admissible neutral family was recovered for 7B but not 7A. It does not establish affinity, biochemical selectivity, catalytic activation, or allostery.

The prior local-flexibility study remains a separate follow-up of the accepted 7B family and its structurally transferred 7A neighborhood. It is not retroactively treated as part of this rigid-docking correction.

## Reproduction and artifacts

- Run script: `research/mettl7-netarsudil-sam-mechanism/scripts/run_7a_native_box_correction.py`
- Frozen-analysis wrapper: `research/mettl7-netarsudil-sam-mechanism/scripts/analyze_7a_native_box_correction.py`
- Manifest: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/run_manifest.json`
- Per-pose metrics: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/analysis/pose_metrics.csv`
- Family results: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/analysis/family_results.csv`
- Contacts: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/analysis/family_contacts.csv`
- Classification: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/analysis/classification.json`
- Artifact hashes: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/artifact_hashes.json`
- Database SQL: `research/mettl7-netarsudil-sam-mechanism/vina-7a-native-box-correction/persist.sql`

## Interpretation boundary

This correction supports only the conclusion that the corrected METTL7A search produced no family satisfying the frozen structural admissibility criteria, while the unchanged METTL7B neutral family did. It does not demonstrate binding affinity, catalytic activation, allostery, or biological selectivity.
