# METTL7 netarsudil/SAM local-flexibility validation

**Run key:** `METTL7_NETARSUDIL_LOCAL_FLEX_VALIDATION_2026_08_30`  
**Branch:** follow-up validation of `7B / neutral / family 5`  
**Method:** bounded Vina `local_only` ligand/flexible-side-chain optimization; no global redocking

## Classification

`SURVIVES_LOCAL_FLEXIBLE_REFINEMENT = YES`

`7B_NEUTRAL_LOCAL_REFINEMENT = PASS`

`7B_PLUS1_LOCAL_REFINEMENT = FAIL`

`7A_NEUTRAL_TRANSFER_LOCAL_REFINEMENT = FAIL`

`7A_PLUS1_TRANSFER_LOCAL_REFINEMENT = FAIL`

`NETARSUDIL_7B_SAM_ADJACENT_STRUCTURAL_HYPOTHESIS = UNCHANGED`

The accepted neutral 7B family survives modest local accommodation, but its MMFF94s strain decreases by only 0.17 kcal/mol rather than the preregistered 2.0 kcal/mol required to strengthen the hypothesis. The result therefore validates local geometric persistence without upgrading the mechanistic claim.

## Frozen gate and protocol

The gate was written to `local-flexibility/frozen_protocol.json` before outcome inspection.

- Starting 7B pose: neutral family 5, seed 483271, mode 5.
- METTL7A starts: the accepted 7B coordinates transferred using 244 sequence-matched Cα atoms; alignment RMSD 1.01 Å.
- +1 starts: same accepted heavy-atom conformation/neighborhood with the primary-amine protonation state regenerated explicitly.
- Ligand: all eight PDBQT rotatable bonds flexible.
- Protein: backbone and distant atoms rigid. Side chains at positions 29, 33, 149, 150, 151, 192, 196–198, 200, 202–207 and 211 were requested as flexible. METTL7A position 211 is alanine and has no movable torsion, so Meeko correctly omitted it.
- SAM: explicit and rigid at the validated canonical coordinates; all 27 heavy atoms retained.
- Search: Vina `local_only`, autobox around the supplied starting ligand, one output, CPU 1.
- Technical seeds: 314159, 271828 and 161803.
- No thermal MD was run.

A replicate passed only if centroid displacement was ≤3.0 Å, symmetry-aware heavy-atom RMSD was ≤4.0 Å, at least three starting-neighborhood residue contacts remained, protein and SAM severe-clash counts stayed zero, SAM RMSD was ≤0.25 Å, and MMFF94s ligand strain was ≤15 kcal/mol. Evidence dimensions were not combined into a score.

Vina `local_only` produced identical results across the three seed values. These are seed-labelled technical confirmations of a deterministic local optimizer, not independent thermodynamic samples.

## Results

Values below are medians; each system gave the same value to reported precision in all three attempts.

| System | Gate | Strain, start → final (kcal/mol) | Ligand RMSD (Å) | Centroid shift (Å) | Ligand–SAM minimum (Å) | Protein/SAM severe clashes | Retained contacts | Burial |
|---|---|---:|---:|---:|---:|---:|---:|---:|
| 7B neutral | PASS, 3/3 | 14.04 → 13.87 | 0.13 | 0.08 | 3.82 | 0 / 0 | 10 | 60.7% |
| 7B +1 | FAIL, 0/3 | 26.23 → 25.58 | 0.12 | 0.03 | 3.76 | 0 / 0 | 10 | 61.3% |
| 7A neutral transfer | FAIL, 0/3 | 14.03 → 17.29 | 7.81 | 6.82 | 6.84 | 0 / 0 | 2 | 49.9% |
| 7A +1 transfer | FAIL, 0/3 | 26.23 → 34.36 | 7.90 | 6.85 | 6.94 | 0 / 0 | 3 | 50.7% |

SAM RMSD was exactly 0 Å by construction because SAM remained in the explicit rigid receptor partition. No ligand–SAM pair fell below 2.0 Å in any system.

## 7B neutral: local survival

The ligand remained in the original SAM-adjacent neighborhood with negligible translation or conformational change. All ten starting contacts persisted: Q29, S149, Q151, K196, D200, G201, C203, T205, R206 and E207. Flexible-side-chain RMS displacement was 0.31 Å and the maximum individual side-chain-atom displacement was 1.33 Å.

Geometrically assigned hydrogen bonds were:

- ligand N5 → Q151 backbone O: 3.01 Å donor–acceptor distance, 158.5°;
- E207 backbone N → ligand N6: 3.06 Å, 175.4°;
- K196 NZ → ligand O2: 3.14 Å, 121.8°.

The Vina local engine output was -7.131 kcal/mol. It is recorded only as an engine output and is not interpreted as affinity or free energy.

The strain remains close to the frozen 15 kcal/mol boundary. Local flexibility therefore supports persistence, but does not resolve the marginal-strain concern.

### C202/C203 tracking

The fpocket wall/contact distinction was tracked explicitly during refinement:

| System | Position 202 before → after (Å) | Position 202 SG before → after (Å) | Position 203 before → after (Å) | Position 203 side-chain atom | Interpretation |
|---|---:|---:|---:|---|---|
| 7B neutral | 5.502 → 5.499 | 8.865 → 8.901 | 3.442 → 3.484 | C203 SG | outside-pocket C203 contact persists; C202 remains a wall noncontact |
| 7B +1 | 5.502 → 5.465 | 8.865 → 8.890 | 3.442 → 3.436 | C203 SG | same geometric outside-pocket contact despite failure of the strain gate |
| 7A neutral transfer | 5.769 → 10.400 | 9.364 → 12.213 | 3.171 → 5.862 | position 203 is ASN, not CYS | both homologous-position proximities are lost with ligand migration |
| 7A +1 transfer | 5.769 → 10.051 | 9.364 → 11.847 | 3.171 → 5.572 | position 203 is ASN, not CYS | both homologous-position proximities are lost with ligand migration |

After-values are identical to reported precision across the three deterministic local-only attempts. METTL7B C203 SG remains a direct ≤4 Å contact in both ligand protonation states, while C202 is never a direct contact. In METTL7A the homologous position 203 is asparagine; it begins close after structural transfer but loses proximity when the ligand leaves the transferred site.

This supports `C203_NETARSUDIL_ROLE = OUTSIDE_POCKET_CONTACT`, not promotion to a pocket-wall or mechanistic residue. The local-refinement survival decision for neutral 7B is unchanged and does not depend on treating C203 as mechanistic.

## 7B +1: preserved placement but failed physical gate

The +1 state also stayed in the same neighborhood: 0.12 Å ligand RMSD, 0.03 Å centroid displacement, all ten contacts retained, and no severe protein/SAM clash. However, its strain remained 25.58 kcal/mol after refinement, well above the frozen threshold. No carboxylate–protonated-amine ionic contact formed within 4.0 Å. It is therefore `FAIL`, driven specifically by strain rather than migration or steric incompatibility.

## METTL7A transfers

Both transferred METTL7A states left the homologous starting neighborhood during local optimization.

- Neutral: 6.82 Å centroid migration, 7.81 Å ligand RMSD, only two starting contacts retained, and strain increased to 17.29 kcal/mol.
- +1: 6.85 Å centroid migration, 7.90 Å ligand RMSD, three starting contacts retained, and strain increased to 34.36 kcal/mol.

The final 7A neighborhood shifted toward residues around E128 and positions 151–155/206–207. This is classified as catastrophic migration under the frozen gate, not as a stable transferred homologous pose. It is structural evidence from this bounded model only and does not establish biochemical paralog selectivity.

## Preparation qualification

Meeko 0.8 was required to create a valid Vina rigid/flexible receptor partition. Its internally consistent Gasteiger protein charges replace the earlier Hephaestus protein PDBQT charges for this follow-up only. Canonical SAM coordinates, atom types and charges were copied unchanged into the rigid receptor. This preparation change is documented and limits direct energetic comparison with the frozen rigid-receptor Vina study; structural before/after metrics are the primary evidence.

Local pocket volume was not calculated because the flexible PDBQT partition does not provide a validated, directly comparable pocket-surface definition. No proxy volume was invented.

## Interpretation boundary

This result means only that the neutral 7B Vina family is a stable local minimum under this bounded Vina-flex model. It does not demonstrate affinity, catalytic activation, allostery, biochemical selectivity, or simultaneous RNA engagement. No RNA docking was reopened, the frozen Vina report was not modified, and Kimi's TSL-RSH work was not touched.

## Reproduction and artifacts

- Frozen protocol: [`local-flexibility/frozen_protocol.json`](local-flexibility/frozen_protocol.json)
- Preparation manifest: [`local-flexibility/preparation_manifest.json`](local-flexibility/preparation_manifest.json)
- Exact run commands and hashes: [`local-flexibility/run_manifest.json`](local-flexibility/run_manifest.json)
- Prepared receptors, flex partitions and starting ligands: [`local-flexibility/prepared`](local-flexibility/prepared)
- Vina configs: [`local-flexibility/configs`](local-flexibility/configs)
- Raw relaxed ligand/side-chain coordinates: [`local-flexibility/raw`](local-flexibility/raw)
- Execution logs: [`local-flexibility/logs`](local-flexibility/logs)
- Replicate metrics: [`local-flexibility/analysis/replicate_metrics.csv`](local-flexibility/analysis/replicate_metrics.csv)
- Before/after contacts: [`local-flexibility/analysis/contacts.csv`](local-flexibility/analysis/contacts.csv)
- C202/C203 matched-system tracking: [`local-flexibility/analysis/c202_c203_refinement_tracking.csv`](local-flexibility/analysis/c202_c203_refinement_tracking.csv)
- Machine-readable classification: [`local-flexibility/analysis/classification.json`](local-flexibility/analysis/classification.json)
- Reproduction scripts: [`local-flexibility/scripts`](local-flexibility/scripts)
