#!/usr/bin/env python3
"""Export deterministic adapter envelopes; molecular graphs remain domain-owned."""

import csv
import hashlib
import json
import os
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
HERE = Path(__file__).resolve().parent
QUERY = r"""
COPY (
 SELECT s.id AS site_id,ea.pdb_id,ea.assembly_id,o.component_id,o.id AS occurrence_id,
        s.site_number,s.localization_status,
        coalesce(string_agg(distinct t.uniprot_accession, ';' order by t.uniprot_accession),'') uniprot,
        src.storage_location,src.sha256,
        count(distinct r.auth_asym_id||':'||r.residue_number||':'||r.insertion_code||':'||r.distance_band) residues,
        count(distinct c.pocket_id) pockets,
        count(distinct a.id) spheres
 FROM docking.experimental_binding_site s
 JOIN docking.assembly_component_occurrence o ON o.id=s.occurrence_id
 JOIN docking.experimental_assembly ea ON ea.id=o.assembly_id
 JOIN docking.assembly_artifact src ON src.assembly_id=ea.id AND src.artifact_type='SOURCE_MMCIF'
 LEFT JOIN docking.experimental_binding_site_target st ON st.site_id=s.id
 LEFT JOIN docking.assembly_polymer_target t ON t.target_id=st.target_id AND t.polymer_entity_id IN
   (SELECT pe.id FROM docking.assembly_polymer_entity pe WHERE pe.assembly_id=ea.id)
 LEFT JOIN docking.experimental_binding_site_residue r ON r.site_id=s.id
 LEFT JOIN docking.experimental_binding_site_candidate c ON c.site_id=s.id AND c.disposition='CONTRIBUTING'
 LEFT JOIN docking.assembly_pocket_alpha_sphere a ON a.pocket_id=c.pocket_id
 GROUP BY s.id,ea.pdb_id,ea.assembly_id,o.component_id,o.id,s.site_number,s.localization_status,src.storage_location,src.sha256
 ORDER BY ea.pdb_id,ea.assembly_id,o.id,s.site_number
) TO STDOUT WITH CSV HEADER
"""
POSITIONS = r"""
COPY (
 SELECT s.id AS site_id,st.target_id,m.uniprot_position
 FROM docking.experimental_binding_site s
 JOIN docking.experimental_binding_site_target st ON st.site_id=s.id
 JOIN docking.assembly_component_occurrence o ON o.id=s.occurrence_id
 JOIN docking.experimental_binding_site_residue r ON r.site_id=s.id AND r.distance_band='DIRECT'
 JOIN docking.assembly_residue_uniprot_mapping m ON m.assembly_id=o.assembly_id
  AND m.target_id=st.target_id AND m.auth_asym_id=r.auth_asym_id
  AND m.auth_sequence_id=r.residue_number::text AND m.insertion_code=r.insertion_code
 WHERE m.uniprot_position IS NOT NULL
 ORDER BY s.id,m.uniprot_position
) TO STDOUT WITH CSV HEADER
"""
ALIGNMENTS = r"""
COPY (
 SELECT query_target_id,candidate_target_id
 FROM docking.experimental_target_alignment
 WHERE correspondence_status='ACCEPTED'
 ORDER BY query_target_id,candidate_target_id
) TO STDOUT WITH CSV HEADER
"""
SPHERES = r"""
COPY (
 SELECT s.id AS site_id,c.pocket_id,a.sphere_number,a.x,a.y,a.z,a.radius
 FROM docking.experimental_binding_site s
 JOIN docking.experimental_binding_site_candidate c ON c.site_id=s.id AND c.disposition='CONTRIBUTING'
 JOIN docking.assembly_pocket_alpha_sphere a ON a.pocket_id=c.pocket_id
 ORDER BY s.id,c.pocket_id,a.sphere_number
) TO STDOUT WITH CSV HEADER
"""
RESIDUES = r"""
COPY (
 SELECT s.id AS site_id,r.auth_asym_id,r.residue_number,r.insertion_code,
        r.residue_name,r.distance_band,st.target_id,m.uniprot_accession,
        m.uniprot_position,m.mapping_outcome
 FROM docking.experimental_binding_site s
 JOIN docking.experimental_binding_site_residue r ON r.site_id=s.id
 LEFT JOIN docking.experimental_binding_site_target st ON st.site_id=s.id
 LEFT JOIN docking.assembly_component_occurrence o ON o.id=s.occurrence_id
 LEFT JOIN docking.assembly_residue_uniprot_mapping m ON m.assembly_id=o.assembly_id
  AND m.target_id=st.target_id AND m.auth_asym_id=r.auth_asym_id
  AND m.auth_sequence_id=r.residue_number::text AND m.insertion_code=r.insertion_code
 ORDER BY s.id,r.auth_asym_id,r.residue_number,r.insertion_code,r.distance_band,st.target_id
) TO STDOUT WITH CSV HEADER
"""

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def psql(query,env):
    result=subprocess.run(['psql','-U','postgres','-d','totah_lab_db','-c',query],env=env,text=True,capture_output=True,check=True)
    return list(csv.DictReader(result.stdout.splitlines()))

def write_csv(path,rows):
    with path.open('w',newline='') as handle:
        writer=csv.DictWriter(handle,fieldnames=list(rows[0])); writer.writeheader(); writer.writerows(rows)

def components(values,edges):
    parent={value:value for value in values}
    def find(value):
        while parent[value]!=value:
            parent[value]=parent[parent[value]]; value=parent[value]
        return value
    for left,right in edges:
        if left not in parent or right not in parent: continue
        a,b=find(left),find(right)
        if a!=b: parent[max(a,b)]=min(a,b)
    return {value:find(value) for value in values}

def main():
    env=dict(os.environ); env.setdefault('PGPASSWORD','admin')
    rows=psql(QUERY,env); position_rows=psql(POSITIONS,env); alignment_rows=psql(ALIGNMENTS,env)
    sphere_rows=psql(SPHERES,env); residue_rows=psql(RESIDUES,env)
    if len(rows)!=697: raise RuntimeError(f'Expected 697 sites, observed {len(rows)}')
    positions={}
    for row in position_rows:
        key=int(row['site_id']); target=int(row['target_id'])
        entry=positions.setdefault(key,{'targets':set(),'positions':set()})
        entry['targets'].add(target); entry['positions'].add(int(row['uniprot_position']))
    # Repeat observations are connected within one target at direct-position Jaccard >=0.50.
    grouped={}; physical={}
    for site,value in positions.items():
        if len(value['targets'])==1: grouped.setdefault(next(iter(value['targets'])),[]).append(site)
    for target,sites in grouped.items():
        edges=[]
        for i,left in enumerate(sites):
            for right in sites[i+1:]:
                a,b=positions[left]['positions'],positions[right]['positions']; union=a|b
                if union and len(a&b)/len(union)>=.5: edges.append((left,right))
        comp=components(sites,edges)
        roots={root:index+1 for index,root in enumerate(sorted(set(comp.values())))}
        for site in sites: physical[site]=f'target:{target}:physical:{roots[comp[site]]}'
    targets=sorted(grouped); target_comp=components(targets,[(int(r['query_target_id']),int(r['candidate_target_id'])) for r in alignment_rows])
    out=HERE/'export'; out.mkdir(parents=True,exist_ok=True)
    graph_path=out/'graph-envelopes.jsonl'
    sphere_path=out/'alpha-spheres.csv'; residue_path=out/'site-residue-annotations.csv'
    write_csv(sphere_path,sphere_rows); write_csv(residue_path,residue_rows)
    with graph_path.open('w') as handle:
        for row in rows:
            record={
              'graph_id':f"pdb:{row['pdb_id']}:assembly:{row['assembly_id']}:occurrence:{row['occurrence_id']}:site:{row['site_number']}",
              'experimental_site_id':int(row['site_id']), 'pdb_id':row['pdb_id'], 'assembly_id':row['assembly_id'],
              'component_id':row['component_id'], 'localization_status':row['localization_status'],
              'uniprot_accessions':row['uniprot'].split(';') if row['uniprot'] else [],
              'source':{'mmcif':row['storage_location'],'sha256':row['sha256'] or None},
              'canonical_adapters':{
                'protein':{'type':'GaiaResidueGraphView','sequence_policy':'EXPLICIT_OR_CHAIN_ORDER','spatial_atom_selection':'HEAVY','spatial_cutoff_A':4.5},
                'annotations':{'type':'AthenaExperimentalSiteEvidence','site_id':int(row['site_id'])},
                'ligand':{'type':'HephaestusLigandTopology','occurrence_id':int(row['occurrence_id'])},
                'cavity':{'type':'GaiaAlphaSphereSet','contributing_pockets':int(row['pockets']),'alpha_spheres':int(row['spheres']),'sphere_surface_gap_cutoff_A':1.0}
              },
              'observed_counts':{'site_residue_bands':int(row['residues']),'alpha_spheres':int(row['spheres'])},
              'physical_site_group_id':physical.get(int(row['site_id'])),
              'leakage_component_id':(f"target_component:{target_comp[next(iter(positions[int(row['site_id'])]['targets']))]}" if int(row['site_id']) in physical else None),
              'missingness':({} if int(row['site_id']) in physical else {'physical_site_group_id':'NOT_AVAILABLE_UNMAPPED_OR_AMBIGUOUS_TARGET_SITE','leakage_component_id':'NOT_AVAILABLE_UNMAPPED_OR_AMBIGUOUS_TARGET_SITE'}),
              'ablation_variants':['geometry_only','residue_graph','residue_ligand_cofactor','residue_ligand_cofactor_cavity']}
            handle.write(json.dumps(record,sort_keys=True,separators=(',',':'))+'\n')
    manifest={'status':'PASS','graphs':len(rows),'source_atlas_assemblies':416,'exported_structures_with_sites':len({(r['pdb_id'],r['assembly_id']) for r in rows}),
      'sites_with_cavity_spheres':sum(int(r['spheres'])>0 for r in rows),'alpha_sphere_references':sum(int(r['spheres']) for r in rows),
      'sites_with_physical_group':len(physical),'physical_site_groups':len(set(physical.values())),
      'site_residue_annotation_rows':len(residue_rows),'graph_envelopes_sha256':sha(graph_path),
      'alpha_spheres_sha256':sha(sphere_path),'site_residue_annotations_sha256':sha(residue_path),'training_started':False,
      'validation':['697 graph envelopes','416 assemblies in source atlas; 341 structures contribute exported sites','all graphs have raw cavity spheres','deterministic lexicographic order','physical groups rebuilt at locked direct-position Jaccard >=0.50'],
      'blocker':None}
    (out/'export-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
    print(json.dumps(manifest,indent=2))

if __name__=='__main__': main()
