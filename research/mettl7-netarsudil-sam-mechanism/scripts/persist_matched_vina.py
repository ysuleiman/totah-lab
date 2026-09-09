#!/usr/bin/env python3
"""Generate immutable artifact hashes and SQL persistence for the matched Vina study."""
from __future__ import annotations
import csv, hashlib, json
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism'
V=BASE/'vina-matched'; A=V/'analysis'
RUN='METTL7_NETARSUDIL_SAM_MATCHED_VINA_2026_08_29'

def q(v): return "'"+str(v).replace("'","''")+"'"
def sha(p): return hashlib.sha256(p.read_bytes()).hexdigest()

files=[]
for p in sorted(V.rglob('*')):
    if p.is_file() and p.name!='artifact_hashes.json':
        files.append({'path':str(p.relative_to(ROOT)),'sha256':sha(p),'bytes':p.stat().st_size})
report=BASE/'METTL7_NETARSUDIL_SAM_MATCHED_VINA.md'
files.append({'path':str(report.relative_to(ROOT)),'sha256':sha(report),'bytes':report.stat().st_size})
for p in sorted((BASE/'scripts').glob('*.py')):
    files.append({'path':str(p.relative_to(ROOT)),'sha256':sha(p),'bytes':p.stat().st_size})
(V/'artifact_hashes.json').write_text(json.dumps({'run_key':RUN,'artifacts':files},indent=2)+'\n')

manifest=json.loads((V/'run_manifest.json').read_text()); qc=json.loads((V/'sam_receptor_qc.json').read_text()); cls=json.loads((A/'classification.json').read_text())
families=list(csv.DictReader((A/'family_results.csv').open())); contacts=list(csv.DictReader((A/'family_contacts.csv').open()))
s=['BEGIN;', '''CREATE TABLE IF NOT EXISTS docking.mettl7_netarsudil_vina_family (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), enzyme varchar(8) NOT NULL, protonation_state varchar(20) NOT NULL,
family integer NOT NULL, population_all integer NOT NULL, physical_pass_population integer NOT NULL, seed_count integer NOT NULL, seeds text NOT NULL,
admissible boolean NOT NULL, representative_seed integer NOT NULL, representative_mode integer NOT NULL, vina_score_min double precision NOT NULL,
vina_score_mean double precision NOT NULL, sam_min_distance_a double precision NOT NULL, site_relative_to_sam varchar(32) NOT NULL,
burial_reduction_percent double precision NOT NULL, strain_kcal_mol double precision NOT NULL, protein_pairs_lt_1p8 integer NOT NULL,
sam_pairs_lt_2 integer NOT NULL, PRIMARY KEY(run_key,enzyme,protonation_state,family));''', '''CREATE TABLE IF NOT EXISTS docking.mettl7_netarsudil_vina_contact (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), enzyme varchar(8) NOT NULL, protonation_state varchar(20) NOT NULL,
family integer NOT NULL, chain_id varchar(8) NOT NULL, residue_number integer NOT NULL, residue_name varchar(8) NOT NULL,
minimum_distance_a double precision NOT NULL, focus_region boolean NOT NULL,
PRIMARY KEY(run_key,enzyme,protonation_state,family,chain_id,residue_number));''']
protocol={'run_manifest':manifest,'sam_qc':qc,'classification':cls,'artifact_hash_manifest':'research/mettl7-netarsudil-sam-mechanism/vina-matched/artifact_hashes.json'}
conclusion='One neutral-state METTL7B family passed the frozen physical and cross-seed gates and was adjacent to SAM; METTL7A remained indeterminate because no family passed the frozen strain gate. Docking does not establish activation, affinity, or allostery.'
s.append(f"INSERT INTO docking.mettl7_computational_run(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES ({q(RUN)},{q('Matched explicit-SAM Vina docking of netarsudil to METTL7A/B')},{q('AutoDock Vina rigid-receptor matched multi-seed pose-family analysis')},{q('AutoDock Vina v1.2.5-17-gda92a68')},{q('7A INDETERMINATE; 7B YES; 7B site ADJACENT to SAM')},{q('research/mettl7-netarsudil-sam-mechanism/METTL7_NETARSUDIL_SAM_MATCHED_VINA.md')},{q('research/mettl7-netarsudil-sam-mechanism/vina-matched/run_manifest.json')},'2026-08-29',{q(json.dumps(protocol,separators=(',',':')))}::jsonb,{q(conclusion)}) ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,report_path=EXCLUDED.report_path,input_path=EXCLUDED.input_path,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;")
s += [f'DELETE FROM docking.mettl7_netarsudil_vina_contact WHERE run_key={q(RUN)};',f'DELETE FROM docking.mettl7_netarsudil_vina_family WHERE run_key={q(RUN)};']
for r in families:
    vals=[RUN,r['enzyme'],r['state'],int(r['family']),int(r['population_all']),int(r['physical_pass_population']),int(r['physical_pass_seed_count']),r['physical_pass_seeds'],r['admissible'].lower(),int(r['representative_seed']),int(r['representative_mode']),float(r['vina_score_min']),float(r['vina_score_mean']),float(r['sam_min_distance_a']),r['site_relative_to_sam'],float(r['burial_reduction_percent']),float(r['strain_kcal_mol']),int(r['protein_pairs_lt_1p8']),int(r['sam_pairs_lt_2'])]
    sqlvals=','.join(q(x) if isinstance(x,str) and x not in ('true','false') else str(x) for x in vals)
    s.append('INSERT INTO docking.mettl7_netarsudil_vina_family VALUES ('+sqlvals+');')
for r in contacts:
    vals=[RUN,r['enzyme'],r['state'],int(r['family']),r['chain'] or '',int(r['residue_number']),r['residue_name'],float(r['minimum_distance_a']),r['focus_region'].lower()]
    sqlvals=','.join(q(x) if isinstance(x,str) and x not in ('true','false') else str(x) for x in vals)
    s.append('INSERT INTO docking.mettl7_netarsudil_vina_contact VALUES ('+sqlvals+');')
s.append('COMMIT;')
(V/'persist.sql').write_text('\n'.join(s)+'\n')
print(json.dumps({'run_key':RUN,'artifact_count':len(files),'sql':str((V/'persist.sql').relative_to(ROOT))},indent=2))
