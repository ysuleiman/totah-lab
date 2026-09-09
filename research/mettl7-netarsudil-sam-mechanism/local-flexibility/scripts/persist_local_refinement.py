#!/usr/bin/env python3
"""Hash and generate SQL for the local-flexibility validation."""
import csv,hashlib,json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[4];BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism';L=BASE/'local-flexibility';RUN='METTL7_NETARSUDIL_LOCAL_FLEX_VALIDATION_2026_08_30'
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def q(x):return "'"+str(x).replace("'","''")+"'"
files=[]
for p in sorted(L.rglob('*')):
 if p.is_file() and '.venv' not in p.parts and p.name not in ('artifact_hashes.json','persist.sql'):
  files.append({'path':str(p.relative_to(ROOT)),'sha256':sha(p),'bytes':p.stat().st_size})
report=BASE/'METTL7_NETARSUDIL_LOCAL_FLEXIBILITY_VALIDATION.md';files.append({'path':str(report.relative_to(ROOT)),'sha256':sha(report),'bytes':report.stat().st_size})
(L/'artifact_hashes.json').write_text(json.dumps({'run_key':RUN,'artifacts':files},indent=2)+'\n')
protocol={'frozen_protocol':json.loads((L/'frozen_protocol.json').read_text()),'preparation':json.loads((L/'preparation_manifest.json').read_text()),'run':json.loads((L/'run_manifest.json').read_text()),'classification':json.loads((L/'analysis/classification.json').read_text()),'artifact_manifest':str((L/'artifact_hashes.json').relative_to(ROOT))}
rows=list(csv.DictReader((L/'analysis/replicate_metrics.csv').open()))
s=['BEGIN;','''CREATE TABLE IF NOT EXISTS docking.mettl7_netarsudil_local_refinement (
run_key varchar(100) NOT NULL REFERENCES docking.mettl7_computational_run(run_key), system_id varchar(40) NOT NULL, seed integer NOT NULL,
gate_pass boolean NOT NULL, rejection_reasons text NOT NULL, starting_strain double precision NOT NULL, final_strain double precision NOT NULL,
ligand_rmsd_a double precision NOT NULL, centroid_displacement_a double precision NOT NULL, sam_min_distance_a double precision NOT NULL,
protein_severe_clashes integer NOT NULL, sam_severe_clashes integer NOT NULL, sam_rmsd_a double precision NOT NULL,
sidechain_rmsd_a double precision NOT NULL, sidechain_max_displacement_a double precision NOT NULL, retained_contacts integer NOT NULL,
burial_percent double precision NOT NULL, vina_local_energy double precision, hydrogen_bonds text NOT NULL, ionic_interactions text NOT NULL,
output_path text NOT NULL, PRIMARY KEY(run_key,system_id,seed));''']
conclusion='Neutral METTL7B family 5 passed bounded local refinement but strain decreased only 0.17 kcal/mol; hypothesis unchanged. The +1 7B state failed strain and both transferred METTL7A states migrated and failed.'
s.append(f"INSERT INTO docking.mettl7_computational_run(run_key,title,method,method_version,classification,report_path,input_path,completed_on,protocol,conclusion) VALUES ({q(RUN)},{q('METTL7 netarsudil/SAM local-flexibility validation')},{q('Vina local_only with explicit flexible side chains and canonical rigid SAM')},{q('Vina v1.2.5 / Meeko 0.8.0')},{q('7B neutral PASS; 7B +1 FAIL; 7A neutral/+1 transfer FAIL; hypothesis UNCHANGED')},{q(str(report.relative_to(ROOT)))},{q(str((L/'run_manifest.json').relative_to(ROOT)))},'2026-08-30',{q(json.dumps(protocol,separators=(',',':')))}::jsonb,{q(conclusion)}) ON CONFLICT(run_key) DO UPDATE SET classification=EXCLUDED.classification,report_path=EXCLUDED.report_path,input_path=EXCLUDED.input_path,protocol=EXCLUDED.protocol,conclusion=EXCLUDED.conclusion;")
s.append(f'DELETE FROM docking.mettl7_netarsudil_local_refinement WHERE run_key={q(RUN)};')
for r in rows:
 vals=[RUN,r['system'],int(r['seed']),r['gate_pass'].lower(),r['rejection_reasons'],float(r['starting_strain_kcal_mol']),float(r['final_strain_kcal_mol']),float(r['ligand_symmetry_rmsd_a']),float(r['centroid_displacement_a']),float(r['ligand_sam_min_distance_a']),int(r['protein_pairs_lt_1p8']),int(r['sam_pairs_lt_2']),float(r['sam_heavy_rmsd_a']),float(r['sidechain_rms_displacement_a']),float(r['sidechain_max_displacement_a']),int(r['retained_starting_contacts']),float(r['burial_reduction_percent']),float(r['vina_local_final_energy']),r['hydrogen_bonds'],r['ionic_interactions'],str((L/'raw'/f"{r['system']}_seed{r['seed']}.pdbqt").relative_to(ROOT))]
 sql=','.join(str(v) if isinstance(v,(int,float)) or v in ('true','false') else q(v) for v in vals);s.append('INSERT INTO docking.mettl7_netarsudil_local_refinement VALUES ('+sql+');')
s.append('COMMIT;');(L/'persist.sql').write_text('\n'.join(s)+'\n');print(json.dumps({'run_key':RUN,'artifact_count':len(files),'replicates':len(rows)},indent=2))
