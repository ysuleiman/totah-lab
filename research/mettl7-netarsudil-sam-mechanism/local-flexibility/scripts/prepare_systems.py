#!/usr/bin/env python3
"""Prepare matched starting poses and Meeko flexible-receptor partitions."""
from __future__ import annotations
import hashlib, json, subprocess, sys
from pathlib import Path
import numpy as np
from rdkit import Chem
sys.path.insert(0,str(ROOT/'analysis/dcmb')) if 'ROOT' in globals() else None

ROOT=Path(__file__).resolve().parents[4]
BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism/local-flexibility'
PREP=BASE/'prepared'; PREP.mkdir(parents=True,exist_ok=True)
VINA_BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism/vina-matched'
PY=BASE/'.venv/bin/python'
MEEKO='meeko.cli.mk_prepare_receptor'
CANONICAL={e:ROOT/f'analysis/dcmb/sam_state/validated/WT_METTL{e}_SAM_BOUND.pdb' for e in ('7A','7B')}
FLEX=[29,33,149,150,151,192,196,197,198,200,202,203,204,205,206,207,211]

sys.path.insert(0,str(ROOT/'analysis/dcmb'))
import same_site_pose_analysis as site

def protein_only(src,dst):
    lines=[]
    for line in src.read_text().splitlines():
        if line.startswith('HETATM') and line[17:20].strip()=='SAM': continue
        if line.startswith('TER') and lines and lines[-1].startswith('TER'): continue
        lines.append(line)
    dst.write_text('\n'.join(lines)+'\n')

def accepted_pose():
    src=VINA_BASE/'raw/7B_neutral_seed483271.pdbqt'; out=[]; take=False
    for line in src.read_text().splitlines():
        if line.startswith('MODEL'): take=int(line.split()[1])==5; continue
        if take and line.startswith('ENDMDL'): break
        if take: out.append(line)
    dst=PREP/'netarsudil_neutral_start.pdbqt'; dst.write_text('\n'.join(out)+'\n'); return dst

def pdbqt_atoms(path):
    rows=[]
    for line in path.read_text().splitlines():
        if line.startswith(('ATOM','HETATM')):
            p=line.split(); rows.append({'line':line,'name':line[12:16].strip(),'res':line[17:20].strip(),
                'xyz':np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])]),'type':p[-1],'charge':float(p[-2])})
    return rows

def make_plus1(neutral_pose):
    source_sdf=VINA_BASE/'prepared/netarsudil_CID66599893_monocation_pH7.4.sdf'
    mol=Chem.SDMolSupplier(str(source_sdf),removeHs=False)[0]
    heavy_ids=[a.GetIdx() for a in mol.GetAtoms() if a.GetAtomicNum()>1]
    source_conf=mol.GetConformer(); pose_heavy=[a for a in pdbqt_atoms(neutral_pose) if a['type'] not in ('H','HD')]
    prepared_heavy=[a for a in pdbqt_atoms(VINA_BASE/'prepared/netarsudil_monocation.pdbqt') if a['type'] not in ('H','HD')]
    # Map prepared PDBQT order to SDF by its unchanged input coordinates.
    unused=set(heavy_ids); order=[]
    for row in prepared_heavy:
        idx=min(unused,key=lambda i:np.linalg.norm(row['xyz']-np.array(source_conf.GetAtomPosition(i))))
        unused.remove(idx); order.append(idx)
    if len(order)!=len(pose_heavy): raise RuntimeError('neutral/+1 heavy atom count mismatch')
    for row,idx in zip(pose_heavy,order):
        x,y,z=row['xyz']; source_conf.SetAtomPosition(idx,(float(x),float(y),float(z)))
    # Regenerate hydrogens at the accepted heavy-atom geometry.
    heavy_mol=Chem.RemoveHs(mol); heavy_conf=heavy_mol.GetConformer()
    plus=Chem.AddHs(heavy_mol,addCoords=True)
    sdf=PREP/'netarsudil_plus1_start.sdf'; w=Chem.SDWriter(str(sdf)); w.write(plus); w.close()
    out=PREP/'netarsudil_plus1_start.pdbqt'
    heph=ROOT/'software/modules/hephaestus/target/hephaestus-1.0-SNAPSHOT-standalone.jar'
    subprocess.run(['java','-jar',str(heph),'prepare-ligand','--input',str(sdf),'--output',str(out),'--overwrite'],check=True)
    return out

def append_canonical_sam(enzyme):
    rigid=PREP/f'METTL{enzyme}_localflex_rigid.pdbqt'
    sam=[a['line'] for a in pdbqt_atoms(VINA_BASE/f'prepared/METTL{enzyme}_SAM_receptor.pdbqt') if a['res']=='SAM']
    if len([x for x in sam if x.split()[-1] not in ('H','HD')])!=27: raise RuntimeError(f'{enzyme} SAM count')
    final=PREP/f'METTL{enzyme}_localflex_rigid_with_SAM.pdbqt'
    final.write_text(rigid.read_text().rstrip()+'\n'+'\n'.join(sam)+'\n')
    return final

def transfer_b_to_a(src,dst):
    seq_b,ca_b=site.ca_sequence(CANONICAL['7B']); seq_a,ca_a=site.ca_sequence(CANONICAL['7A'])
    pairs=site.align_sequences(seq_b,seq_a)
    mobile=np.array([ca_b[i][5] for i,j in pairs]); reference=np.array([ca_a[j][5] for i,j in pairs])
    r,t=site.kabsch(mobile,reference)
    out=[]
    for line in src.read_text().splitlines():
        if line.startswith(('ATOM','HETATM')):
            xyz=np.array([float(line[30:38]),float(line[38:46]),float(line[46:54])])@r+t
            line=line[:30]+f'{xyz[0]:8.3f}{xyz[1]:8.3f}{xyz[2]:8.3f}'+line[54:]
        out.append(line)
    dst.write_text('\n'.join(out)+'\n')
    fitted=mobile@r+t; return float(np.sqrt(np.mean(np.sum((fitted-reference)**2,axis=1)))),len(pairs)

def main():
    neutral=accepted_pose(); plus1=make_plus1(neutral)
    neutral_a=PREP/'netarsudil_neutral_start_7A_transfer.pdbqt'; align_rmsd,n_pairs=transfer_b_to_a(neutral,neutral_a)
    plus1_a=PREP/'netarsudil_plus1_start_7A_transfer.pdbqt'; transfer_b_to_a(plus1,plus1_a)
    for enzyme in ('7A','7B'):
        pdb=PREP/f'METTL{enzyme}_protein_only.pdb'; protein_only(CANONICAL[enzyme],pdb)
        basename=PREP/f'METTL{enzyme}_localflex'
        cmd=[str(PY),'-m',MEEKO,'--read_pdb',str(pdb),'-o',str(basename),'-p','-f','A:'+','.join(map(str,FLEX))]
        subprocess.run(cmd,check=True)
        append_canonical_sam(enzyme)
    manifest={'flexible_residues_requested':FLEX,'selection_basis':'within 5 A of accepted 7B pose plus side-chain-bearing residues 149-151 and 196-207',
              'neutral_start_7B':str(neutral.relative_to(ROOT)),'plus1_start_7B':str(plus1.relative_to(ROOT)),
              'neutral_start_7A_transfer':str(neutral_a.relative_to(ROOT)),'plus1_start_7A_transfer':str(plus1_a.relative_to(ROOT)),
              'structural_transfer':{'method':'sequence-matched CA Kabsch, METTL7B to METTL7A','ca_pairs':n_pairs,'ca_rmsd_a':align_rmsd},'meeko_version':'0.8.0',
              'charge_model_override':'Meeko 0.8 Gasteiger protein partition required for internally consistent Vina flexible-receptor topology; canonical Hephaestus SAM charges/types retained unchanged',
              'sam_heavy_atoms':27}
    (BASE/'preparation_manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')

if __name__=='__main__': main()
