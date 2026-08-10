#!/usr/bin/env python3
"""Validate deterministic Stage 6 adapter artifacts without training."""

import csv
import hashlib
import json
from pathlib import Path

HERE=Path(__file__).resolve().parent; OUT=HERE/'export'
def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    manifest=json.loads((OUT/'export-manifest.json').read_text())
    graphs=[json.loads(line) for line in (OUT/'graph-envelopes.jsonl').read_text().splitlines()]
    residues=list(csv.DictReader((OUT/'site-residue-annotations.csv').open()))
    spheres=list(csv.DictReader((OUT/'alpha-spheres.csv').open()))
    assert manifest['status']=='PASS' and manifest['training_started'] is False
    assert len(graphs)==manifest['graphs']==697
    assert len({g['graph_id'] for g in graphs})==697
    assert [g['graph_id'] for g in graphs]==sorted(g['graph_id'] for g in graphs)
    assert sum(g['physical_site_group_id'] is not None for g in graphs)==394
    assert len({g['physical_site_group_id'] for g in graphs if g['physical_site_group_id']})==108
    assert all(g['canonical_adapters']['protein']['type']=='GaiaResidueGraphView' for g in graphs)
    assert all(g['canonical_adapters']['ligand']['type']=='HephaestusLigandTopology' for g in graphs)
    assert all(g['canonical_adapters']['cavity']['type']=='GaiaAlphaSphereSet' for g in graphs)
    assert len(residues)==manifest['site_residue_annotation_rows']==20906
    assert len(spheres)==manifest['alpha_sphere_references']==129501
    assert len({int(row['site_id']) for row in spheres})==697
    assert all(float(row['radius'])>0 for row in spheres)
    assert sha(OUT/'graph-envelopes.jsonl')==manifest['graph_envelopes_sha256']
    assert sha(OUT/'alpha-spheres.csv')==manifest['alpha_spheres_sha256']
    assert sha(OUT/'site-residue-annotations.csv')==manifest['site_residue_annotations_sha256']
    ablations=json.loads((HERE/'ablation-plan.json').read_text())
    assert len(ablations['variants'])==4 and ablations['shared_example_set']==697
    print('Stage 6 deterministic domain-adapter export validation: PASS')

if __name__=='__main__': main()
