"""Describe sampled motifs geometrically; does not change selection/class labels."""
from pathlib import Path
import json,numpy as np
OUT=Path(__file__).resolve().parents[1]/'validation/milestone-17'
specs=json.loads((OUT/'benchmark.json').read_text());rows=[];ANG_BOHR=1.8897261254578281
for name in ['des_ammonium_benzene','des_methylammonium_benzene','des_methanethiol_benzene','des_h2s_benzene','des_methane_benzene']:
    s=specs[name];a=s['atoms'][:s['fragment_a_count']];b=s['atoms'][s['fragment_a_count']:]
    ring=np.array([p for e,p in b if e=='C']);center=ring.mean(axis=0);_,_,vh=np.linalg.svd(ring-center);normal=vh[-1]
    atom='N' if 'ammonium' in name else 'S' if ('h2s' in name or 'methanethiol' in name) else 'C'
    position=np.array(next(p for e,p in a if e==atom));d=position-center
    rows.append({'system':name,'probe_atom':atom,'plane_height_angstrom':abs(float(d@normal))/ANG_BOHR,
                 'lateral_offset_angstrom':float(np.linalg.norm(d-normal*(d@normal)))/ANG_BOHR,
                 'plane_rms_angstrom':float(np.sqrt(np.mean(((ring-center)@normal)**2)))/ANG_BOHR})
for name in ['nenci_080','nenci_087']:
    atoms=specs[name]['atoms'];cl=np.array(next(p for e,p in atoms if e=='Cl'));o=np.array(next(p for e,p in atoms if e=='O'))
    c=min((np.array(p) for e,p in atoms if e=='C'),key=lambda p:np.linalg.norm(p-cl));u=c-cl;v=o-cl
    rows.append({'system':name,'Cl_O_angstrom':float(np.linalg.norm(v)/ANG_BOHR),
                 'C_Cl_O_degrees':float(np.degrees(np.arccos(np.clip(u@v/np.linalg.norm(u)/np.linalg.norm(v),-1,1))))})
(OUT/'motif-geometry-audit.json').write_text(json.dumps(rows,indent=2)+'\n')
