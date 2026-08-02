#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,math,re,statistics
from collections import Counter,defaultdict
from pathlib import Path
POCKET=[33,36,44,55,77,78,79,127,144,145,148,149,151,175,195,196,199,200,201,202,203]
CONS=[set('AVLIM'),set('FWY'),set('STNQ'),set('KRH'),set('DE')]
REP_CLASS={1:'METTL7A/TMT1A-like',2:'METTL7A/TMT1A-like',3:'METTL7B/TMT1B-like',4:'METTL7B/TMT1B-like',5:'METTL7B/TMT1B-like',6:'METTL7A/TMT1A-like',7:'METTL7A/TMT1A-like',8:'other SAM-dependent methyltransferase',9:'other SAM-dependent methyltransferase',10:'METTL7B/TMT1B-like'}
def fasta(p):
 d={};n=None
 for l in p.read_text().splitlines():
  if l.startswith('>'):n=l[1:].split()[0];d[n]=''
  else:d[n]+=l.strip()
 return d
def write(p,rows,fields=None):
 fields=fields or list(dict.fromkeys(k for row in rows for k in row));
 with p.open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(rows)
def ident(a,b):
 pairs=[(x,y) for x,y in zip(a,b) if x!='-' and y!='-'];return sum(x==y for x,y in pairs)/len(pairs) if pairs else 0,len(pairs)
def subst(a,b):
 if b=='-':return 'gap'
 if b==a:return 'identical'
 return 'conservative' if any(a in g and b in g for g in CONS) else 'non-conservative'
def fisher(a,b,c,d):
 n=a+b+c+d;r=a+b;k=a+c;lo=max(0,k-(n-r));hi=min(r,k)
 def prob(x):return math.comb(k,x)*math.comb(n-k,r-x)/math.comb(n,r)
 p0=prob(a);return min(1,sum(prob(x) for x in range(lo,hi+1) if prob(x)<=p0+1e-15))
def main():
 ap=argparse.ArgumentParser();ap.add_argument('phase3',type=Path);a=ap.parse_args();root=a.phase3
 seqs=fasta(root/'all_members_alignment.fasta');inv=list(csv.DictReader((root/'all_members_inventory.csv').open()));meta={x['sequence_id']:x for x in inv};ref=seqs['HUMAN_METTL7B'];refa=seqs['HUMAN_METTL7A'];cols={};p=0
 for i,x in enumerate(ref):
  if x!='-':p+=1;cols[p]=i
 assert len(cols)==244 and all(ref[cols[x]]!='-' for x in POCKET)
 mapping=[];quality=[];classes=[];regions=[];motifs=[];pmap=[];pcons=[]
 per={}
 for sid,m in meta.items():
  s=seqs[sid];ung=s.replace('-','');target_at={};q=0
  for i,x in enumerate(s):
   if x!='-':q+=1
   if i in cols.values():target_at[next(k for k,v in cols.items() if v==i)]=q if x!='-' else None
  idb,compb=ident(s,ref);ida,compa=ident(s,refa);coverage=sum(s[cols[x]]!='-' for x in cols)/244;gap=1-sum(x!='-' for x in s)/len(s);localcov=sum(s[cols[x]]!='-' for x in range(195,204))/9;pockcov=sum(s[cols[x]]!='-' for x in POCKET)/21
  flag='high' if coverage>=.75 and localcov>=8/9 and pockcov>=.9 else 'moderate' if coverage>=.5 and localcov>=6/9 and pockcov>=.7 else 'poor'
  quality.append({'sequence_id':sid,'biohub_cluster':m['biohub_cluster'],'aligned_coverage':f'{coverage:.4f}','gap_fraction':f'{gap:.4f}','identity_to_mettl7b':f'{idb:.4f}','identity_to_mettl7a':f'{ida:.4f}','comparable_sites_mettl7b':compb,'comparable_sites_mettl7a':compa,'region_195_203_coverage':f'{localcov:.4f}','pocket_coverage':f'{pockcov:.4f}','alignment_quality_flag':flag})
  for rp in range(1,245):mapping.append({'sequence_id':sid,'mettl7b_position':rp,'alignment_column_one_based':cols[rp]+1,'target_residue_number':target_at[rp] or 'gap','aligned_residue':s[cols[rp]]})
  cluster=int(m['biohub_cluster']);annotation=m['annotation'].lower();base=REP_CLASS[cluster]
  if flag=='poor' and max(ida,idb)<.15:cl='likely unrelated or erroneous BioHub member';conf='low'
  elif flag=='poor':cl='unresolved';conf='low'
  elif 'ubie' in annotation or 'coq5' in annotation:cl='other SAM-dependent methyltransferase';conf='high' if max(ida,idb)<.35 else 'moderate'
  elif cluster==8:cl='other SAM-dependent methyltransferase' if max(ida,idb)<.38 else 'other METTL7-family protein';conf='moderate'
  else:
   cl=base;delta=(idb-ida) if '7B' in cl else (ida-idb);conf='high' if delta>=.08 and flag=='high' else 'moderate' if delta>=0 or flag=='high' else 'low'
  classes.append({'sequence_id':sid,'biohub_cluster':cluster,'classification':cl,'confidence':conf,'identity_to_mettl7b':f'{idb:.4f}','identity_to_mettl7a':f'{ida:.4f}','alignment_quality':flag,'annotation':m['annotation'],'domain_architecture_evidence':'cluster/representative Pfam evidence from Phase 1; not individually re-annotated','evidence_notes':f'cluster prior={base}; identity delta B-A={idb-ida:.3f}; coverage={coverage:.3f}'})
  rseq=''.join(s[cols[x]] for x in range(195,204));rnums=';'.join(str(target_at[x] or 'gap') for x in range(195,204));pair=rseq[-2:]
  motifclass=pair if pair in ('CC','CN','CG') else 'gap-containing' if '-' in pair else 'CX' if pair.startswith('C') else 'other'
  regionqual='confident' if flag!='poor' and localcov>=8/9 else 'unresolved'
  start=min([target_at[x] for x in range(195,204) if target_at[x]] or [1]);context=ung[max(0,start-6):min(len(ung),start+14)]
  regions.append({'sequence_id':sid,'biohub_cluster':cluster,**{f'mettl7b_{x}_aligned_residue':s[cols[x]] for x in range(195,204)},**{f'mettl7b_{x}_target_position':target_at[x] or 'gap' for x in range(195,204)},'aligned_195_203_sequence':rseq,'local_sequence_context':context,'aligned_202_203_motif':pair,'aligned_motif_class':motifclass,'local_alignment_coverage':f'{localcov:.3f}','region_alignment_quality':regionqual})
  pats=[('CCC','CCC'),('CC','CC'),('CXC','C.C'),('CXXC','C..C'),('CXXXC','C...C')]
  aligned_positions={target_at[202],target_at[203]}-{None};near=set(range(max(1,start-5),start+15))
  for label,pat in pats:
   for z in re.finditer(f'(?=({pat}))',ung):
    st=z.start()+1;en=st+len(z.group(1))-1;loc='exactly_aligned' if {st,en}==aligned_positions else 'near_195_203' if any(x in near for x in range(st,en+1)) else 'elsewhere'
    motifs.append({'sequence_id':sid,'biohub_cluster':cluster,'motif':label,'matched_sequence':z.group(1),'start_one_based':st,'end_one_based':en,'location_class':loc,'sequence_context':ung[max(0,st-7):min(len(ung),en+6)]})
  identical=conservative=mapped=0;chem=0
  for rp in POCKET:
   obs=s[cols[rp]];kind=subst(ref[cols[rp]],obs);mapped+=obs!='-';identical+=kind=='identical';conservative+=kind=='conservative';chem+=1 if kind=='identical' else .5 if kind=='conservative' else 0
   pmap.append({'sequence_id':sid,'biohub_cluster':cluster,'mettl7b_position':rp,'mettl7b_residue':ref[cols[rp]],'target_residue_number':target_at[rp] or 'gap','aligned_residue':obs,'substitution_class':kind,'mapping_quality':flag if obs!='-' else 'unresolved'})
  pc={'sequence_id':sid,'biohub_cluster':cluster,'identical_pocket_site_count':identical,'conservative_pocket_site_count':conservative,'mapped_pocket_site_count':mapped,'pocket_identity_fraction':f'{identical/21:.4f}','pocket_chemistry_similarity_score':f'{chem/21:.4f}','pocket_region_alignment_coverage':f'{mapped/21:.4f}'};pcons.append(pc)
  per[sid]={'cluster':cluster,'class':cl,'quality':flag,'idb':idb,'ida':ida,'pair':pair,'motifclass':motifclass,'regionqual':regionqual,'pocket':identical/21,'chem':chem/21}
 write(root/'all_members_alignment_mapping.csv',mapping);write(root/'alignment_quality_summary.csv',quality);write(root/'all_members_classification.csv',classes);write(root/'all_members_195_203_region.csv',regions);write(root/'all_members_cysteine_motifs.csv',motifs);write(root/'all_members_pocket_residue_map.csv',pmap);write(root/'all_members_pocket_conservation.csv',pcons)
 write(root/'aligned_cc_members.csv',[x for x in regions if x['aligned_motif_class']=='CC' and x['region_alignment_quality']=='confident'],list(regions[0]))
 # cluster aggregation
 cc=[];cs=[];cp=[];cm=[];cr=[]
 for cluster in range(1,11):
  ids=[s for s,v in per.items() if v['cluster']==cluster];n=len(ids);ccount=Counter(per[s]['class'] for s in ids);mot=Counter(per[s]['motifclass'] for s in ids);vals=[per[s]['pocket'] for s in ids];idvals=[per[s]['idb'] for s in ids]
  cs.append({'biohub_cluster':cluster,'protein_count':n,**{f'classification_{k}':v for k,v in sorted(ccount.items())},'identity_to_mettl7b_min':f'{min(idvals):.4f}','identity_to_mettl7b_median':f'{statistics.median(idvals):.4f}','identity_to_mettl7b_max':f'{max(idvals):.4f}','pocket_identity_min':f'{min(vals):.4f}','pocket_identity_median':f'{statistics.median(vals):.4f}','pocket_identity_max':f'{max(vals):.4f}','aligned_cc_count':mot['CC'],'aligned_cc_percent':f'{100*mot["CC"]/n:.2f}','aligned_cn_count':mot['CN'],'aligned_cn_percent':f'{100*mot["CN"]/n:.2f}','high_quality_alignment_count':sum(per[s]['quality']=='high' for s in ids)})
  cm.append({'biohub_cluster':cluster,'protein_count':n,**{f'motif_{k}':v for k,v in sorted(mot.items())}})
  rep=next(s for s in ids if meta[s]['representative_flag']=='yes');rank=sum(per[s]['pocket']<=per[rep]['pocket'] for s in ids)/n
  cr.append({'biohub_cluster':cluster,'representative_sequence_id':rep,'representative_pocket_identity':f'{per[rep]["pocket"]:.4f}','cluster_median_pocket_identity':f'{statistics.median(vals):.4f}','representative_percentile':f'{rank:.3f}','representative_motif':per[rep]['motifclass'],'cluster_modal_motif':mot.most_common(1)[0][0],'representative_classification':per[rep]['class'],'cluster_modal_classification':ccount.most_common(1)[0][0],'representative_atypical':'yes' if per[rep]['motifclass']!=mot.most_common(1)[0][0] or per[rep]['class']!=ccount.most_common(1)[0][0] or rank<.1 or rank>.9 else 'no'})
  cp.append({'biohub_cluster':cluster,'protein_count':n,'pocket_identity_mean':f'{statistics.mean(vals):.4f}','pocket_identity_sd':f'{statistics.stdev(vals) if n>1 else 0:.4f}','pocket_identity_min':f'{min(vals):.4f}','pocket_identity_max':f'{max(vals):.4f}','chemistry_similarity_mean':f'{statistics.mean(per[s]["chem"] for s in ids):.4f}'})
 write(root/'cluster_classification_summary.csv',cs);write(root/'cluster_member_summary.csv',cs);write(root/'cluster_motif_distribution.csv',cm);write(root/'cluster_representativeness.csv',cr);write(root/'cluster_pocket_conservation_summary.csv',cp)
 # Fisher tests, CC restricted to confidently mapped site.
 eligible=[s for s,v in per.items() if v['regionqual']=='confident'];tests=[]
 features={'METTL7B-like classification':lambda s:'METTL7B' in per[s]['class'],'METTL7A-like classification':lambda s:'METTL7A' in per[s]['class'],'high pocket conservation >=0.75':lambda s:per[s]['pocket']>=.75}
 for cl in range(1,11):features[f'BioHub cluster {cl}']=lambda s,cl=cl:per[s]['cluster']==cl
 for rp in [196,199,200,201]:features[f'Q6UX53-identical residue at {rp}']=lambda s,rp=rp:seqs[s][cols[rp]]==ref[cols[rp]]
 for name,fn in features.items():
  A=sum(per[s]['pair']=='CC' and fn(s) for s in eligible);B=sum(per[s]['pair']!='CC' and fn(s) for s in eligible);C=sum(per[s]['pair']=='CC' and not fn(s) for s in eligible);D=sum(per[s]['pair']!='CC' and not fn(s) for s in eligible);pv=fisher(A,B,C,D);odds=((A+.5)*(D+.5))/((B+.5)*(C+.5));tests.append({'test':name,'eligible_n':len(eligible),'cc_feature_yes':A,'noncc_feature_yes':B,'cc_feature_no':C,'noncc_feature_no':D,'odds_ratio_haldane_anscombe':f'{odds:.4g}','p_value_fisher_two_sided':f'{pv:.6g}','bh_adjusted_p_value':'','interpretation':'association only; not causation'})
 order=sorted(range(len(tests)),key=lambda i:float(tests[i]['p_value_fisher_two_sided']));m=len(tests);adj=[0]*m;running=1
 for rank,i in reversed(list(enumerate(order,1))):running=min(running,float(tests[i]['p_value_fisher_two_sided'])*m/rank);adj[i]=running
 for i,x in enumerate(tests):x['bh_adjusted_p_value']=f'{adj[i]:.6g}'
 write(root/'cc_association_tests.csv',tests)
 sig=[x for x in tests if float(x['bh_adjusted_p_value'])<.05]
 (root/'motif_association_summary.md').write_text(f"# Aligned CC association tests\n\nEligible confidently aligned proteins: {len(eligible)}. Confident aligned CC: {sum(per[s]['pair']=='CC' for s in eligible)}. Tests use two-sided Fisher exact tests, Haldane–Anscombe odds ratios, and Benjamini–Hochberg correction across {len(tests)} tests. Taxonomy association was not tested because authoritative per-protein taxonomy is unavailable for many BioHub/SPIRE/MGnify records.\n\nSignificant adjusted associations: {', '.join(x['test'] for x in sig) or 'none'}. Association is not evidence of causation or disulfide formation.\n")
if __name__=='__main__':main()
