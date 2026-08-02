#!/usr/bin/env python3
from __future__ import annotations
import argparse,csv,hashlib
from collections import defaultdict
from pathlib import Path

def fasta(path):
 out=[];h=None;s=[]
 for l in path.read_text().splitlines():
  if l.startswith('>'):
   if h is not None:out.append((h,''.join(s)))
   h=l[1:];s=[]
  else:s.append(l.strip())
 if h is not None:out.append((h,''.join(s)))
 return out
def main():
 ap=argparse.ArgumentParser();ap.add_argument('clusters',type=Path);ap.add_argument('phase1',type=Path);ap.add_argument('phase3',type=Path);a=ap.parse_args();a.phase3.mkdir(parents=True,exist_ok=True);(a.phase3/'tmp').mkdir(exist_ok=True)
 members=list(csv.DictReader((a.clusters/'cluster-members.csv').open()));seqrecs=fasta(a.clusters/'all-clusters.fasta')
 byhash={}
 malformed=[]
 for h,s in seqrecs:
  f=h.split('|');
  # Some SPIRE accession strings contain additional pipe-delimited components.
  # Cluster and hash remain the first three fields; source is preserved in CSV.
  if len(f)<5:malformed.append(h);continue
  bh=f[2];byhash[bh]=(h,s)
 reps={r['representative_hash']:f"CLUSTER_{int(r['cluster_id']):02d}_REP" for r in members}
 rows=[]
 for m in members:
  bh=m['member_hash']; rec=byhash.get(bh);issues=[]
  if not rec: issues.append('missing_fasta_record');s=''
  else:_,s=rec
  md5=hashlib.md5(s.encode('ascii')).hexdigest() if s else ''
  if md5!=bh:issues.append('checksum_mismatch')
  if len(s)!=int(m['sequence_length']):issues.append('length_mismatch')
  if any(x not in 'ABCDEFGHIKLMNPQRSTVWXYZUO' for x in s):issues.append('nonstandard_residue')
  rows.append({'sequence_id':reps[bh] if bh in reps else 'BH_'+bh,'biohub_cluster':m['cluster_id'],'biohub_category':m['category'],'biohub_identifier':bh,'uniparc_identifier':m['accession'] if m['accession'].startswith('UPI') else '','uniprot_accession':'','organism':'','taxonomy_id':'','sequence_length':len(s),'representative_flag':'yes' if bh in reps else 'no','annotation':m['category'],'source':m['source'],'checksum_md5':md5,'sequence':s,'validation_status':'valid' if not issues else ';'.join(issues)})
 with (a.phase3/'all_members_inventory.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=rows[0]);w.writeheader();w.writerows(rows)
 groups=defaultdict(list)
 for r in rows:groups[r['checksum_md5']].append(r)
 dup=[]
 for checksum,g in groups.items():
  if checksum and len(g)>1:
   for x in g:dup.append({'checksum_md5':checksum,'duplicate_count':len(g),'sequence_id':x['sequence_id'],'biohub_identifier':x['biohub_identifier'],'cluster':x['biohub_cluster']})
 with (a.phase3/'duplicate_sequences.csv').open('w',newline='') as f:
  fields=['checksum_md5','duplicate_count','sequence_id','biohub_identifier','cluster'];w=csv.DictWriter(f,fieldnames=fields);w.writeheader();w.writerows(dup)
 # Add only non-representatives to the frozen 12-sequence reference alignment.
 with (a.phase3/'tmp'/'remaining_799.fasta').open('w') as f:
  for r in rows:
   if r['representative_flag']=='no':f.write(f">{r['sequence_id']}\n{r['sequence']}\n")
 valid=sum(x['validation_status']=='valid' for x in rows)
 report=f'''# Phase 3 input validation\n\n- Source records: {len(members)}\n- FASTA records: {len(seqrecs)}\n- Valid records: {valid}\n- Invalid records: {len(rows)-valid}\n- Representative records: {sum(x['representative_flag']=='yes' for x in rows)}\n- Remaining records prepared for reference-guided addition: {sum(x['representative_flag']=='no' for x in rows)}\n- Duplicate-sequence memberships: {len(dup)} rows across {sum(len(g)>1 for g in groups.values())} checksum groups\n- Malformed FASTA headers: {len(malformed)}\n\nIdentifiers, cluster assignments, accessions, source labels, lengths, sequences, and MD5 checksums are preserved from the BioHub export. Organism/taxonomy and active UniProt accessions remain blank unless resolved by later authoritative metadata enrichment; cluster-level aggregate taxonomy is not substituted for protein-level identity.\n'''
 (a.phase3/'validation_report.md').write_text(report)
if __name__=='__main__':main()
