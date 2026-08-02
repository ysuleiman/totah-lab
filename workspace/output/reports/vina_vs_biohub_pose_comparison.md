# Vina versus BioHub pose-consistency analysis

BioHub was treated as an independent pose prediction, not as a docking score. Confidence values are reported only as annotations.

## Summary

Analyzed 20 ligands. Indeterminate: 1, Partially reproduced: 19.

| Ligand | Vina 7B | Vina 7A | Delta | Vina SG | BioHub SG | RMSD | Centroid | Contact Jaccard | Interpretation |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| MCULE-9917229820 | -12.20 | -6.80 | 5.40 | 3.98 | 3.74 | 13.94 | 12.54 | 0.12 | Partially reproduced |
| MCULE-5156111111 | -11.30 | -7.10 | 4.20 | 3.80 | 3.76 | 15.43 | 12.82 | 0.10 | Partially reproduced |
| MCULE-5939897708 | -11.50 | -7.50 | 4.00 | 4.33 | 3.83 | 16.78 | 13.36 | 0.12 | Partially reproduced |
| MCULE-7434666169 | -9.80 | -5.90 | 3.90 | 3.03 | 3.59 | 12.65 | 11.27 | 0.13 | Partially reproduced |
| MCULE-3953678005 | -10.10 | -6.70 | 3.40 | 3.85 | 3.48 | 10.65 | 10.20 | 0.11 | Partially reproduced |
| MCULE-9107845978 | -9.70 | -6.30 | 3.40 | 3.16 | 3.78 | 12.33 | 11.30 | 0.12 | Partially reproduced |
| MCULE-9341439659 | -9.90 | -6.60 | 3.30 | 2.90 | 2.95 | 11.99 | 11.16 | 0.17 | Partially reproduced |
| MCULE-1779009715 | -11.80 | -8.60 | 3.20 | 3.20 | 4.37 | 15.10 | 12.13 | 0.11 | Partially reproduced |
| MCULE-2481997982 | -10.70 | -7.60 | 3.10 | 3.18 | 5.13 | 14.84 | 12.90 | 0.16 | Partially reproduced |
| MCULE-6290114076 | -10.00 | -6.90 | 3.10 | 2.64 | 3.52 | 11.74 | 9.58 | 0.20 | Partially reproduced |
| MCULE-9252733589 | -10.00 | -6.90 | 3.10 | 3.05 | 4.13 | 12.73 | 12.08 | 0.09 | Partially reproduced |
| MCULE-1053934938 | -11.00 | -8.00 | 3.00 | 3.45 | 4.41 | 15.76 | 13.74 | 0.10 | Partially reproduced |
| MCULE-3499657642 | -10.20 | -7.20 | 3.00 | 2.69 | 3.93 | 13.26 | 11.23 | 0.10 | Partially reproduced |
| MCULE-5052294910 | -10.80 | -7.90 | 2.90 | 3.46 | 3.66 | 14.51 | 10.76 | 0.08 | Partially reproduced |
| MCULE-5957735610 | -9.90 | -7.00 | 2.90 | 2.60 | 3.13 | 12.52 | 11.24 | 0.09 | Partially reproduced |
| MCULE-7671173592 | -9.90 | -7.00 | 2.90 | 3.89 | 3.22 | 12.59 | 12.22 | 0.11 | Partially reproduced |
| MCULE-1222960644 | -9.80 | -7.00 | 2.80 | 3.61 | 3.81 | 12.91 | 9.72 | 0.20 | Indeterminate |
| MCULE-2946174076 | -12.30 | -9.50 | 2.80 | 3.39 | 3.67 | 11.94 | 11.00 | 0.20 | Partially reproduced |
| MCULE-5337723752 | -10.30 | -7.50 | 2.80 | 2.67 | 3.91 | 14.32 | 12.72 | 0.16 | Partially reproduced |
| MCULE-7938039910 | -12.40 | -9.60 | 2.80 | 3.39 | 3.75 | 11.79 | 10.90 | 0.14 | Partially reproduced |

## Method and thresholds

- Protein contacts use a 4.0 A heavy-atom cutoff.
- Cys202 distances are measured directly to SG, independently of the contact list.
- BioHub protein coordinates are fitted to matching receptor CA atoms with the Kabsch algorithm; the same transform is applied to its ligand.
- Ligand coordinate connectivity must be graph-isomorphic to the supplied SMILES. Symmetry-equivalent mappings are enumerated and the minimum mapped heavy-atom RMSD is used. Invalid mappings produce no RMSD and an Indeterminate result.
- Same pocket means centroid displacement <= 6.0 A with a shared contact, or both poses within 6 A of Cys202 SG with at least three shared 4 A contact residues. The second rule recognizes a shared anchor while allowing a long ligand to adopt a different orientation.
- Strongly reproduced requires the same pocket, preserved Cys202 geometry, contact Jaccard >= 0.50, and mapped RMSD <= 3.0 A. Partial reproduction requires the same pocket plus contact Jaccard >= 0.20 or preserved Cys202 geometry.

## Missing inputs and assumptions

- The requested `/mnt/data` ZIP and summary CSV were not mounted. Equivalent BioHub artifacts and their manifest under `/Users/yazan/artifacts/targets/Q6UX53/biohub/top20_cys202_delta` were used.
- The manifest's `poseIdPrimary` identifies the best overall METTL7B Vina pose. The report does not silently substitute another Cys202-contacting pose.
- The canonical receptor `Q6UX53_TMT1B_HUMAN.pdb` was used; its Cys202 coordinates were verified against the docking database.
- Contact Jaccard is intersection divided by union. Residue identity is chain, residue name, and residue number.
- BioHub confidence does not establish affinity, inhibition, selectivity, or covalency. Only METTL7B was submitted, so BioHub cannot validate the Vina 7A-versus-7B delta.
