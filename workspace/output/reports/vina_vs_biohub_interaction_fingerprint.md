# Vina versus BioHub interaction-fingerprint analysis

## Scientific answer

This analysis asks whether BioHub preserves the METTL7B pocket-interaction hypothesis from Vina even when exact Cartesian poses differ. It separates pocket location, residue interactions, Cys202 anchoring, and pose geometry.

## Results

Analyzed 20 ligands. Same region, different binding mode: 20.

BioHub preserves the broad METTL7B/Cys202 pocket region for 20/20 ligands and the strict <=4 A Cys202-SG anchor for 15/20. However, the maximum typed interaction-fingerprint Tanimoto is 0.19, below the 0.25 moderate-agreement threshold. Therefore BioHub independently supports pocket localization—and often Cys202 anchoring—but does not reproduce the detailed Vina residue-interaction network or binding mode for this set.

| Ligand | Contact Jaccard | Contact Dice | IFP Tanimoto | Pharmacophore overlap | Shared Cys202 SG | Shared key residues | Same pocket | Geometry RMSD | Interpretation |
|---|---:|---:|---:|---:|---|---|---|---:|---|
| MCULE-9917229820 | 0.12 | 0.21 | 0.13 | 0.32 | True | A:LEU145;A:HIS175;A:CYS202 | True | 13.94 | Same region, different binding mode |
| MCULE-5156111111 | 0.10 | 0.18 | 0.09 | 0.19 | True | A:THR144;A:LEU145;A:CYS202 | True | 15.43 | Same region, different binding mode |
| MCULE-5939897708 | 0.12 | 0.21 | 0.07 | 0.07 | False | A:LEU145;A:HIS175;A:CYS202 | True | 16.78 | Same region, different binding mode |
| MCULE-7434666169 | 0.13 | 0.24 | 0.14 | 0.31 | True | A:CYS148;A:SER149;A:HIS175;A:CYS202 | True | 12.65 | Same region, different binding mode |
| MCULE-3953678005 | 0.11 | 0.20 | 0.09 | 0.18 | True | A:THR144;A:LEU145;A:HIS175;A:CYS202 | True | 10.65 | Same region, different binding mode |
| MCULE-9107845978 | 0.12 | 0.22 | 0.13 | 0.25 | True | A:THR144;A:LEU145;A:HIS175;A:CYS202 | True | 12.33 | Same region, different binding mode |
| MCULE-9341439659 | 0.17 | 0.29 | 0.14 | 0.27 | True | A:CYS148;A:SER149;A:HIS175;A:ASP200;A:CYS202 | True | 11.99 | Same region, different binding mode |
| MCULE-1779009715 | 0.11 | 0.19 | 0.09 | 0.21 | False | A:THR144;A:LEU145;A:SER149 | True | 15.10 | Same region, different binding mode |
| MCULE-2481997982 | 0.16 | 0.27 | 0.11 | 0.14 | False | A:LEU145;A:SER149;A:HIS175 | True | 14.84 | Same region, different binding mode |
| MCULE-6290114076 | 0.20 | 0.33 | 0.13 | 0.18 | True | A:THR144;A:LEU145;A:SER149;A:HIS175;A:CYS202 | True | 11.74 | Same region, different binding mode |
| MCULE-9252733589 | 0.09 | 0.17 | 0.09 | 0.25 | False | A:LEU145;A:HIS175 | True | 12.73 | Same region, different binding mode |
| MCULE-1053934938 | 0.10 | 0.18 | 0.10 | 0.25 | False | A:LEU145;A:SER149 | True | 15.76 | Same region, different binding mode |
| MCULE-3499657642 | 0.10 | 0.18 | 0.10 | 0.20 | True | A:HIS175;A:CYS202 | True | 13.26 | Same region, different binding mode |
| MCULE-5052294910 | 0.08 | 0.15 | 0.06 | 0.13 | True | A:THR144;A:LEU145;A:CYS202 | True | 14.51 | Same region, different binding mode |
| MCULE-5957735610 | 0.09 | 0.17 | 0.09 | 0.19 | True | A:HIS175;A:CYS202 | True | 12.52 | Same region, different binding mode |
| MCULE-7671173592 | 0.11 | 0.21 | 0.12 | 0.28 | True | A:LEU145;A:CYS148;A:HIS175;A:CYS202 | True | 12.59 | Same region, different binding mode |
| MCULE-1222960644 | 0.20 | 0.33 | 0.19 | 0.46 | True | A:LEU145;A:CYS148;A:SER149;A:HIS175;A:CYS202 | True | 12.91 | Same region, different binding mode |
| MCULE-2946174076 | 0.20 | 0.33 | 0.18 | 0.27 | True | A:THR144;A:LEU145;A:CYS148;A:HIS175;A:CYS202 | True | 11.94 | Same region, different binding mode |
| MCULE-5337723752 | 0.16 | 0.27 | 0.15 | 0.29 | True | A:THR144;A:LEU145;A:SER149;A:CYS202 | True | 14.32 | Same region, different binding mode |
| MCULE-7938039910 | 0.14 | 0.25 | 0.14 | 0.25 | True | A:THR144;A:LEU145;A:HIS175;A:CYS202 | True | 11.79 | Same region, different binding mode |

## Four distinct agreement questions

- **Pose geometry agreement:** assessed by aligned heavy-atom RMSD and centroid displacement, but not used alone to define interaction agreement.
- **Pocket-location agreement:** requires a shared spatial region, or preservation of the Cys202 neighborhood plus at least three shared contact residues.
- **Residue-interaction agreement:** assessed with contact Jaccard/Dice and a typed residue-level interaction-fingerprint Tanimoto.
- **Cys202-anchor agreement:** requires direct <=4.0 A ligand proximity to Cys202 SG in both structures. A <=6.0 A neighborhood is tracked only for broad pocket localization.

## Interaction definitions

The binary fingerprint consists of `(chain:residue, interaction type)` entries. Types are CONTACT (heavy atoms <=4.0 A), HYDROPHOBIC (<=4.0 A), LIGAND_DONOR and LIGAND_ACCEPTOR hydrogen-bond opportunities (heavy-atom distance <=3.5 A), AROMATIC (aromatic atoms <=5.0 A), LIGAND_CATIONIC and LIGAND_ANIONIC salt-bridge opportunities (<=4.5 A), and CYS202_S_PROXIMITY (ligand heavy atom <=4.0 A from SG). Hydrogen bonds are distance-based opportunities because explicit hydrogen geometry is unavailable.

Pharmacophore-feature overlap is the overlap coefficient for shared non-CONTACT typed residue interactions. Contact Jaccard is intersection/union; Dice is twice the intersection divided by the summed set sizes.

## Classification rules

- Strong: same pocket, shared principal anchor, IFP Tanimoto >=0.50, and at least two shared key-pocket residues.
- Moderate: same pocket, IFP Tanimoto 0.25-0.49, plus a shared anchor or at least two shared key-pocket residues.
- Same region/different mode: broad pocket or Cys202-neighborhood agreement with IFP Tanimoto <0.25 or a substantially changed orientation/network.
- Not reproduced: different pocket without a meaningful shared anchor. Indeterminate: missing structure or invalid molecular-graph mapping.

## Interaction-based clustering

Combined Vina and BioHub poses were clustered as connected components using typed-fingerprint Tanimoto >=0.40, rather than Cartesian RMSD.

- Cluster 1: MCULE-9917229820 (Vina), MCULE-5156111111 (Vina), MCULE-5939897708 (Vina), MCULE-7434666169 (Vina), MCULE-3953678005 (Vina), MCULE-9107845978 (Vina), MCULE-9341439659 (Vina), MCULE-1779009715 (Vina), MCULE-2481997982 (Vina), MCULE-6290114076 (Vina), MCULE-9252733589 (Vina), MCULE-1053934938 (Vina), MCULE-3499657642 (Vina), MCULE-5052294910 (Vina), MCULE-5957735610 (Vina), MCULE-7671173592 (Vina), MCULE-1222960644 (Vina), MCULE-2946174076 (Vina), MCULE-5337723752 (Vina), MCULE-7938039910 (Vina)
- Cluster 2: MCULE-9917229820 (BioHub), MCULE-5156111111 (BioHub), MCULE-5939897708 (BioHub), MCULE-7434666169 (BioHub), MCULE-3953678005 (BioHub), MCULE-9107845978 (BioHub), MCULE-9341439659 (BioHub), MCULE-1779009715 (BioHub), MCULE-2481997982 (BioHub), MCULE-6290114076 (BioHub), MCULE-9252733589 (BioHub), MCULE-1053934938 (BioHub), MCULE-3499657642 (BioHub), MCULE-5052294910 (BioHub), MCULE-5957735610 (BioHub), MCULE-7671173592 (BioHub), MCULE-2946174076 (BioHub), MCULE-5337723752 (BioHub), MCULE-7938039910 (BioHub)
- Cluster 3: MCULE-1222960644 (BioHub)

## Missing inputs, assumptions, and cautions

- The requested `/mnt/data` files were not mounted. The moved first-stage report in `/Users/yazan/totah-lab/reports`, the original BioHub artifacts, exact Vina PDBQT poses, and canonical METTL7B receptor were used.
- Interaction typing is structure-based and distance-based. It does not calculate interaction energies, water mediation, protonation equilibria, or covalent reaction feasibility.
- PHE199 in the requested key list is not present in the canonical METTL7B sequence at residue 199; the receptor contains GLY199. The requested PHE199 key is retained for transparent reporting but cannot be matched.
- BioHub confidence fields are annotations only. BioHub does not validate affinity, inhibition, Vina selectivity, or covalency; it supplies an independent METTL7B structural prediction.
