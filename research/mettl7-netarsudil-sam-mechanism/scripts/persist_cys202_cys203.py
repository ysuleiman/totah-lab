#!/usr/bin/env python3
"""Generate SQL persistence for the C202/C203 geometry clarification."""
import csv,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3];B=ROOT/'research/mettl7-netarsudil-sam-mechanism';V=B/'vina-matched/analysis';L=B/'local-flexibility/analysis';RUN='METTL7_NETARSUDIL_C202_C203_GEOMETRY_2026_08_30'
def q(x):return "'"+str(x).replace("'","''")+"'"
fam=list(csv.DictReader((V/'c202_c203_family_geometry.csv').open()));ref=list(csv.DictReader((L/'c202_c203_refinement_tracking.csv').open()));summary=json.loads((V/'c202_c203_geometry_summary.json').read_text())
s=['BEGIN;','''CREATE TABLE IF NOT EXISTS docking.mettl7_netarsudil_cys_geometry (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), seed integer NOT NULL, mode integer NOT NULL,
physically_admissible boolean NOT NULL, c202_min_a double precision NOT NULL, c202_ligand_atom text NOT NULL, c202_atom text NOT NULL,
c202_sg_min_a double precision NOT NULL, c202_sg_ligand_atom text NOT NULL, c203_min_a double precision NOT NULL,
c203_ligand_atom text NOT NULL, c203_atom text NOT NULL, c203_sg_min_a double precision NOT NULL, c203_sg_ligand_atom text NOT NULL,
PRIMARY KEY(run_key,seed,mode));''','''CREATE TABLE IF NOT EXISTS docking.mettl7_netarsudil_cys_refinement (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), system_id text NOT NULL, phase text NOT NULL,
seed text NOT NULL, position integer NOT NULL, residue_name text NOT NULL, min_distance_a double precision NOT NULL,
ligand_atom text NOT NULL, residue_atom text NOT NULL, sg_min_a double precision, sg_ligand_atom text,
PRIMARY KEY(run_key,system_id,phase,seed,position));''']
conclusion='C202 is an fpocket wall noncontact (representative 5.502 A; SG 8.865 A). C203 is outside the fpocket wall but contacts netarsudil through SG at 3.442 A; the contact persists through 7B local refinement and is not assigned a mechanistic role.'
s.append(f"INSERT INTO docking.mettl7_computational_run(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES ({q(RUN)},{q('METTL7B netarsudil C202/C203 geometric clarification')},{q('Five-pose atom geometry and alpha-sphere boundary analysis')},{q('2026-08-30')},{q('C202 WALL_NONCONTACT; C203 OUTSIDE_POCKET_CONTACT')},{q('research/mettl7-netarsudil-sam-mechanism/METTL7_NETARSUDIL_SAM_MATCHED_VINA.md')},{q('research/mettl7-netarsudil-sam-mechanism/vina-matched/analysis/c202_c203_geometry_summary.json')},'2026-08-30',{q(json.dumps(summary,separators=(',',':')))}::jsonb,{q(conclusion)}) ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,report_path=EXCLUDED.report_path,input_path=EXCLUDED.input_path,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;")
s += [f'DELETE FROM docking.mettl7_netarsudil_cys_geometry WHERE run_key={q(RUN)};',f'DELETE FROM docking.mettl7_netarsudil_cys_refinement WHERE run_key={q(RUN)};']
for r in fam:
 vals=[RUN,int(r['seed']),int(r['mode']),r['physically_admissible'].lower(),float(r['c202_min_a']),r['c202_ligand_atom'],r['c202_atom'],float(r['c202_sg_min_a']),r['c202_sg_ligand_atom'],float(r['c203_min_a']),r['c203_ligand_atom'],r['c203_atom'],float(r['c203_sg_min_a']),r['c203_sg_ligand_atom']]
 s.append('INSERT INTO docking.mettl7_netarsudil_cys_geometry VALUES ('+','.join(str(v) if isinstance(v,(int,float)) or v in ('true','false') else q(v) for v in vals)+');')
for r in ref:
 vals=[RUN,r['system'],r['phase'],r['seed'],int(r['position']),r['residue_name'],float(r['min_distance_a']),r['ligand_atom'],r['residue_atom'],float(r['sg_min_a']) if r['sg_min_a'] else None,r['sg_ligand_atom'] or None]
 def sql(v):return 'NULL' if v is None else str(v) if isinstance(v,(int,float)) else q(v)
 s.append('INSERT INTO docking.mettl7_netarsudil_cys_refinement VALUES ('+','.join(sql(v) for v in vals)+');')
s.append('COMMIT;');out=B/'vina-matched/persist_cys202_cys203.sql';out.write_text('\n'.join(s)+'\n');print(out)
