#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv
from pathlib import Path
import numpy as np

def read(p):return list(csv.DictReader(p.open()))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase2',type=Path);a=ap.parse_args();r=a.phase2
 inv={x['sequence_id']:x for x in read(r/'structure_inventory.csv')};al={x['sequence_id']:x for x in read(r/'structure_alignment_summary.csv')};po={x['sequence_id']:x for x in read(r/'pocket_structural_comparison.csv')};cy={x['sequence_id']:x for x in read(r/'cysteine_geometry.csv')}
 recs=[
 ('CLUSTER_04_REP',1,'Primary METTL7B control','Near-identical core (0.338 A), pocket (0.273 A), complete 21-site identity, and aligned CC; tests conserved METTL7B chemistry.'),
 ('CLUSTER_06_REP',2,'Vicinal-CC counterexample','METTL7A-like but aligned CC; pocket RMSD 0.815 A. Best discriminator of whether CC geometry rather than lineage controls activity.'),
 ('CLUSTER_01_REP',3,'METTL7A-like comparator','High-confidence model, 0.686 A pocket RMSD and 17/21 sequence identity; tests selectivity within a homologous pocket.'),
 ('CLUSTER_03_REP',4,'Divergent METTL7B-like homolog','Moderate sequence identity but 0.918 A pocket RMSD; separates fold/pocket conservation from exact chemistry.'),
 ('CLUSTER_09_REP',5,'Methyltransferase outgroup','ubiE/COQ5-like, 2.743 A core RMSD and 1.471 A pocket RMSD; negative/control comparison for family specificity.')]
 rows=[]
 for sid,rank,role,why in recs:rows.append({'rank':rank,'sequence_id':sid,'comparison_role':role,'core_rmsd_angstrom':al[sid]['core_backbone_ca_rmsd_angstrom'],'pocket_rmsd_angstrom':al[sid]['pocket_rmsd_after_core_fit_angstrom'],'mean_plddt':inv[sid]['mean_plddt'],'aligned_cc': 'yes' if cy[sid]['position202_residue']==cy[sid]['position203_residue']=='C' else 'no','docking_readiness':'ready for comparative docking; inspect protonation/grid consistency first','rationale':why})
 with (r/'recommended_followup_targets.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
 table='\n'.join(f"| {sid} | {inv[sid]['mean_plddt']} | {al[sid]['core_backbone_ca_rmsd_angstrom']} | {al[sid]['pocket_rmsd_after_core_fit_angstrom']} | {po[sid]['heavy_atom_proximity_overlap_fraction_2A']} | {cy[sid]['sg_sg_distance_angstrom'] or 'n/a'} |" for sid in inv)
 rect='\n'.join(f"| {x['rank']} | {x['sequence_id']} | {x['comparison_role']} | {x['rationale']} |" for x in rows)
 text=f'''# Phase 2 — Structural validation of BioHub representative proteins

**Scope:** the same two human references and ten BioHub representatives used in Phase 1. No member-level analysis of the remaining 809 sequences was performed.

## Executive result

The BioHub representatives overwhelmingly preserve the METTL7 methyltransferase fold and, with one important exception, preserve the mapped 21-residue pocket geometry. After fitting Q6UX53 residues 61–240, nine representatives have pocket Cα RMSD between 0.27 and 1.47 Å. Cluster 8 is the exception (14.01 Å pocket RMSD), associated with the lowest-confidence BioHub model (mean pLDDT {inv['CLUSTER_08_REP']['mean_plddt']}) and a displaced mapped region; it is not docking-ready without an independent model.

These results support **homologous pocket conservation**, not convergence: the proteins share an alignable methyltransferase core, residue correspondence, and local geometry. BioHub's supplied export does not contain an explicit query-pocket atom list or query-to-representative pocket score, so it is not possible to reproduce a proprietary “matched pocket” decision exactly. The operational pocket here is the 21-residue METTL7B pocket from Phase 1 mapped through the sequence alignment. The evidence suggests the grouping is dominated by homologous core/family similarity plus retained pocket geometry rather than an unrelated analogous cavity.

## Structure inventory

BioHub Atlas supplied predicted PDB coordinates and residue confidence for all ten representatives. Official AlphaFold DB models were retained where stable accessions exist; BioHub models were used for representative comparisons because they are directly tied to the BioHub records. Official AlphaFold DB models AF-Q6UX53-F1 and AF-Q9H8H3-F1 were used for the human references. Current UniProt records exposed no experimental PDB cross-references for this panel. BioHub scores stored on a 0–1 scale were normalized to the conventional 0–100 pLDDT scale.

Full provenance, model paths, mean pLDDT, and low-confidence residue counts are in `structure_inventory.csv`.

## Structural alignment and pocket geometry

| Protein | mean pLDDT | core RMSD (Å) | pocket RMSD (Å) | heavy-atom overlap (≤2 Å) | aligned CC S–S (Å) |
|---|---:|---:|---:|---:|---:|
{table}

The fit uses mapped Cα atoms for Q6UX53 residues 61–240, an operational conserved-core boundary chosen to exclude the variable N-terminal targeting segment. Pocket RMSD is evaluated after that core fit, so it measures local displacement rather than allowing the pocket to hide a different global orientation. A second locally fitted pocket RMSD and divergent-loop ranges are recorded in `structure_alignment_summary.csv`.

Pocket centroid, mapped-residue composition, hydrophobic/polar fractions, FreeSASA solvent accessibility (Lee–Richards, 1.4 Å probe), and heavy-atom proximity overlap are in `pocket_structural_comparison.csv`. “Volume” is explicitly the convex-hull envelope of mapped pocket Cα atoms, not a ligand-cavity volume from a cavity-detection algorithm. It is useful for like-for-like geometry comparison but must not be interpreted as an absolute druggable void volume.

Cluster 4 nearly reproduces METTL7B (0.338 Å core; 0.273 Å pocket; 100% heavy-atom proximity overlap). METTL7A is also very close (0.939 Å core; 0.566 Å pocket; 94.5% overlap), explaining why sequence-conserved sites alone are unlikely to provide selectivity. Clusters 1, 2, 3, 5, 6, 7, 9, and 10 retain sub-1.5 Å pocket geometry. Cluster 8's poor local agreement and low confidence make its apparent geometry inconclusive rather than evidence of a genuinely analogous pocket.

## Cys202/Cys203 geometry

Only METTL7B, cluster 4, and METTL7A-like cluster 6 contain CC at the mapped 202/203 columns. Their predicted Sγ–Sγ distances are {cy['HUMAN_METTL7B']['sg_sg_distance_angstrom']}, {cy['CLUSTER_04_REP']['sg_sg_distance_angstrom']}, and {cy['CLUSTER_06_REP']['sg_sg_distance_angstrom']} Å, respectively. All are far outside the 1.9–2.3 Å operational interval for an oxidized S–S bond. Their Cα distances remain near 3.85 Å, but Cβ/Sγ orientations place the sulfurs about 6.6–6.9 Å apart. None of the predicted apo models therefore contains a preformed vicinal disulfide.

This does not disprove redox-dependent formation: AlphaFold/BioHub models are not redox-state experiments and may represent reduced cysteines. The result narrows the hypothesis: disulfide formation would require substantial side-chain reorientation and should be tested experimentally. Cluster 6 remains a crucial lineage counterexample because it shares CC and similar reduced-state geometry despite being METTL7A-like.

## Definitive residue map

`pocket_residue_map.csv` contains 252 rows (21 Q6UX53 positions × 12 proteins), with aligned identity class, target residue number, residue SASA, exposure, structural location, and a cautiously assigned contribution class. Contribution labels distinguish electrostatic/H-bonding, aromatic/hydrophobic packing, pocket-shape glycines, and cysteine sulfur/redox roles; they are mechanistic predictions, not experimental annotations.

## BioHub validation: homologous versus analogous

The evidence favors homologous pockets:

- all structures contain an alignable SAM-dependent methyltransferase core;
- local pocket residues map through common alignment columns;
- most core fits are 0.34–2.74 Å and most pocket fits are 0.27–1.47 Å;
- close TMT1A/TMT1B homologs retain high heavy-atom proximity overlap despite chemical substitutions;
- the Phase 1 tree and reciprocal searches independently support shared ancestry.

BioHub's selection cannot be attributed to pocket volume alone because the export lacks the original query-pocket correspondence and score. The present validation supports a combination of overall fold and local pocket geometry. Cluster 9 demonstrates that a related SAM-methyltransferase scaffold can retain rough local geometry despite divergent chemistry; cluster 8 remains unresolved because model confidence/local topology are inadequate.

## Recommended comparative docking panel

| Rank | Target | Role | Rationale |
|---:|---|---|---|
{rect}

Cluster 4 provides the positive METTL7B control; cluster 1 provides a close METTL7A-like selectivity challenge; cluster 6 tests the CC hypothesis independently of lineage; cluster 3 adds evolutionary divergence while retaining geometry; cluster 9 provides a methyltransferase-family negative control. Cluster 8 should be deferred pending an independently predicted high-confidence structure.

## Reproducibility and limitations

- Structures: BioHub Atlas representative endpoint (`fold_on_miss=false`) and AlphaFold DB API records cached on 2026-08-01.
- Superposition: NumPy {np.__version__} Kabsch least-squares Cα fit.
- Exposure: FreeSASA 2.1.3, Lee–Richards algorithm, 1.4 Å probe, 20 slices.
- Residue correspondence: Phase 1 MAFFT L-INS-i alignment, Q6UX53 numbering.
- The 61–240 core and 21-residue pocket are declared operational definitions.
- Confidence is prediction confidence, not experimental validation.
- No ligand-bound experimental structure was available, so side-chain rotamers and cavity hydration remain uncertain.
- The convex-hull envelope is not a cavity-finder pocket volume.
- BioHub did not export its original pocket match score or atom correspondence; causal attribution to a proprietary search feature cannot be proven from this dataset.
'''
 (r/'biohub_structural_validation.md').write_text(text)
if __name__=='__main__':main()
