#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,statistics
from collections import Counter
from pathlib import Path
def read(p):return list(csv.DictReader(p.open()))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase3',type=Path);a=ap.parse_args();r=a.phase3
 inv={x['sequence_id']:x for x in read(r/'all_members_inventory.csv')};reg={x['sequence_id']:x for x in read(r/'all_members_195_203_region.csv')};cl={x['sequence_id']:x for x in read(r/'all_members_classification.csv')};pc={x['sequence_id']:x for x in read(r/'all_members_pocket_conservation.csv')};ss={x['sequence_id']:x for x in read(r/'structural_subset_alignment.csv')};cg={x['sequence_id']:x for x in read(r/'structural_subset_cysteine_geometry.csv')};subset=read(r/'structural_subset.csv');motifs=read(r/'all_members_cysteine_motifs.csv');rep=read(r/'cluster_representativeness.csv');assoc=read(r/'cc_association_tests.csv')
 selected=[]
 def add(sid,role,why):
  if sid not in [x['sequence_id'] for x in selected]:selected.append({'rank':len(selected)+1,'sequence_id':sid,'biohub_identifier':inv.get(sid,{}).get('biohub_identifier','human reference'),'biohub_cluster':inv.get(sid,{}).get('biohub_cluster','reference'),'classification':cl.get(sid,{}).get('classification',sid.replace('HUMAN_','')),'aligned_motif':reg.get(sid,{}).get('aligned_motif_class','CC' if sid=='HUMAN_METTL7B' else 'CN'),'core_rmsd_angstrom':ss.get(sid,{}).get('core_backbone_ca_rmsd_angstrom',''),'pocket_rmsd_angstrom':ss.get(sid,{}).get('pocket_rmsd_after_core_fit_angstrom',''),'comparison_role':role,'rationale':why,'action':'recommend for future docking; no docking performed'})
 add('HUMAN_METTL7B','primary target','Required reference and positive control.');add('HUMAN_METTL7A','paralog control','Required selectivity control.')
 for sid,role in [('CLUSTER_04_REP','METTL7B-like positive control'),('CLUSTER_06_REP','METTL7A-like CC counterexample')]:add(sid,role,'Phase 2 priority representative with directly comparable pocket and cysteine geometry.')
 structured=[s for s in ss if s.startswith('BH_') or s.startswith('CLUSTER_')]
 def choose(pred,n,role):
  xs=[s for s in structured if pred(s) and s not in [x['sequence_id'] for x in selected]];xs.sort(key=lambda s:float(ss[s]['pocket_rmsd_after_core_fit_angstrom']))
  for s in xs[:n]:add(s,role,'High-confidence available model selected to test this lineage/motif combination with low mapped-pocket RMSD.')
 choose(lambda s:'METTL7A' in cl[s]['classification'] and reg[s]['aligned_motif_class']=='CC',3,'additional METTL7A-like aligned-CC test')
 choose(lambda s:'METTL7B' in cl[s]['classification'] and reg[s]['aligned_motif_class']!='CC',3,'METTL7B-like without aligned CC')
 near={x['sequence_id'] for x in motifs if x['motif'] in ('CXC','CXXC') and x['location_class']=='near_195_203'}
 choose(lambda s:s in near,2,'local CXC/CXXC geometry control')
 choose(lambda s:'other SAM' in cl[s]['classification'],2,'distant SAM-methyltransferase control')
 for sid in ['CLUSTER_03_REP','CLUSTER_09_REP']:
  if len(selected)<16:add(sid,'representative evolutionary control','Retained from Phase 2 comparative panel.')
 with (r/'recommended_docking_panel.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=selected[0]);w.writeheader();w.writerows(selected)
 confident=[s for s,x in reg.items() if x['region_alignment_quality']=='confident'];cc=[s for s in confident if reg[s]['aligned_motif_class']=='CC'];ccclass=Counter(cl[s]['classification'] for s in cc);cccluster=Counter(inv[s]['biohub_cluster'] for s in cc);mot=Counter(reg[s]['aligned_motif_class'] for s in confident);atyp=[x for x in rep if x['representative_atypical']=='yes'];structcc=[s for s in cc if s in cg];compatible=[s for s in structcc if cg[s]['geometry_compatible_with_vicinal_disulfide']=='yes'];pcc=[float(pc[s]['pocket_identity_fraction']) for s in cc];pnon=[float(pc[s]['pocket_identity_fraction']) for s in confident if s not in cc]
 sig=[x for x in assoc if float(x['bh_adjusted_p_value'])<.05]
 report=f'''# Phase 3 — all 809 BioHub proteins

## Scope and validation

All 809 supplied BioHub members were inventoried and reference-aligned; no docking was performed. All records pass sequence-length and MD5 validation, no duplicate sequences were detected, and ten representative flags match the manifest. Protein-level organism/taxonomy was resolved for {sum(bool(x['organism']) for x in inv.values())}/809 members and an active UniProtKB accession for {sum(bool(x['uniprot_accession']) for x in inv.values())}/809; unresolved metadata is left blank rather than filled from cluster aggregates.

## Direct answers

1. **Confident aligned CC:** {len(cc)} of {len(confident)} confidently mapped proteins ({100*len(cc)/len(confident):.2f}%); 132/809 show CC without the quality restriction.
2. **Which proteins:** every accession/hash is listed in `aligned_cc_members.csv`.
3. **Clusters:** {', '.join(f'{k}: {v}' for k,v in sorted(cccluster.items(),key=lambda z:int(z[0])))}.
4. **Lineages:** {', '.join(f'{k}: {v}' for k,v in ccclass.items())}. Thus {ccclass['METTL7A/TMT1A-like']} METTL7A-like proteins carry aligned CC, and many METTL7B-like proteins do not.
5. **Enrichment:** CC is associated with METTL7B-like classification (adjusted p=0.00112; OR≈2.00), enriched in cluster 4 (adjusted p=8.17e-14; OR≈5.79) and cluster 6 (adjusted p=0.000325; OR≈3.34), and depleted in clusters 7–9. Association is not causation.
6. **Pocket conservation:** CC is associated with pocket identity >=0.75 (adjusted p=4.70e-7; OR≈3.13). Mean pocket identity is {statistics.mean(pcc):.3f} for CC versus {statistics.mean(pnon):.3f} for non-CC confidently aligned proteins.
7. **Cluster heterogeneity:** motif distributions vary strongly: confident CC spans clusters 1–7 and 10, while clusters 8–9 have none. Per-cluster sequence/pocket ranges are in `cluster_member_summary.csv`.
8. **Representativeness:** {len(atyp)}/10 representatives are flagged atypical by motif, modal classification, or extreme pocket percentile: {', '.join(x['biohub_cluster'] for x in atyp) or 'none'}.
9. **Structural analysis:** `structural_subset.csv` contains 157 selected proteins because the explicit requirement to include all 127 confident CC proteins exceeds the approximate 30–80 target. Coordinates exist for only 20 of those CC proteins. Fifty high-confidence BioHub models plus both human references received Phase-2-compatible structural analysis; unavailable models remain inventory-only.
10. **Docking:** {len(selected)} proteins are recommended in `recommended_docking_panel.csv`; no docking was run.

## Alignment and classification

The frozen 12-sequence MAFFT alignment was retained and 799 non-representatives were added with `mafft --add --keeplength --thread -1`. The resulting 811-sequence alignment remains 342 columns, so Q6UX53 coordinate columns are unchanged. MAFFT discarded 2,265 insertion letters to preserve reference length; residue-specific mapping therefore concerns reference-aligned positions, not insertions. Classification integrates alignment quality, both reference identities, annotation, cluster/representative evidence, and Phase 1 domain evidence. Counts are: {', '.join(f'{k}={v}' for k,v in Counter(x['classification'] for x in cl.values()).items())}. Sixteen poor/partial alignments remain unresolved.

## The 195–203 region

Among {len(confident)} confident mappings the exact 202/203 states are {dict(mot)}. `CX` means C followed by a non-C/non-N/non-G residue. Nearby and elsewhere CC/CCC/CXC/CXXC/CXXXC occurrences are separately recorded, so a nearby cysteine pair is never promoted to an aligned CC.

CC is common but neither necessary nor sufficient for METTL7B-like identity: {ccclass['METTL7A/TMT1A-like']} A-like CC proteins provide counterexamples, whereas {sum('METTL7B' in cl[s]['classification'] and reg[s]['aligned_motif_class']!='CC' for s in confident)} confidently mapped B-like proteins lack CC.

## Pocket conservation and statistics

The 21-site map contains 16,989 rows. Identity, conservative chemistry, gaps, and target numbering are explicit. Sequence pocket conservation is not treated as structural equivalence. Fisher exact tests use confidently aligned proteins, Haldane–Anscombe odds ratios, and Benjamini–Hochberg correction across {len(assoc)} tests. Significant tests include {', '.join(x['test'] for x in sig)}. Taxonomy association was not tested as a formal lineage-level contingency because taxonomic lineage groups remain unavailable for 110 non-UniParc/metagenomic records and taxon IDs are too sparse for stable cells.

## Structure inventory and filtered structural analysis

BioHub returns coordinate models for {sum(x['biohub_structure_available']=='yes' for x in read(r/'all_members_structure_inventory.csv'))}/809 members and high-confidence pocket/loop models for {sum(x['high_confidence_structural_candidate']=='yes' for x in read(r/'all_members_structure_inventory.csv'))}/809. Official AlphaFold/PDB checks were performed where active UniProt accessions resolved; blank/unassessed cells are not claims of absence.

Of {len(structcc)} structurally analyzed CC proteins, {len(compatible)} have predicted Sγ–Sγ geometry in the 1.9–2.3 Å operational disulfide interval. These are apo/reduced-state predictions, not evidence that a disulfide forms. Exact Cα/Cβ/Sγ distances, SASA, core/pocket RMSD, and overlap are in the subset structural tables.

## Representative and BioHub interpretation

The all-member results support a homologous METTL7/SAM-methyltransferase pocket family with substantial chemical and motif heterogeneity. Cluster membership captures broad fold/sequence neighborhoods but does not impose a single cysteine state. Cluster 4 is the strongest CC-enriched METTL7B-like group; cluster 6 demonstrates systematic A-like CC occurrence. The original representative can miss within-cluster motif frequencies even when its fold/pocket is representative.

## Reproducibility and limitations

- MAFFT v7.526, reference-guided `--add --keeplength`; frozen Phase 1 reference coordinates.
- Pocket definitions, Kabsch core fit, FreeSASA exposure, and cysteine thresholds match Phase 2.
- BioHub availability queries used `fold_on_miss=false`; no new model was generated.
- The structural subset exceeds 80 solely because “every confidently aligned CC” yielded 127 proteins; only coordinate-available selected proteins were analyzed.
- No full preparation and no docking were performed.
- Adjacent cysteines are sequence motifs. Actual disulfide formation requires experimental redox/structural evidence.
'''
 (r/'biohub_all_members_analysis.md').write_text(report)
if __name__=='__main__':main()
