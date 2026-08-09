#!/usr/bin/env python3
"""Prepare both unspecified CONH enantiomers through the established pipeline."""
from __future__ import annotations
import csv,hashlib,json,subprocess
from pathlib import Path
from rdkit import Chem
from rdkit.Chem import AllChem,Descriptors,rdMolDescriptors

ROOT=Path(__file__).resolve().parents[3]; HERE=Path(__file__).resolve().parent
HEPH=ROOT/'software/modules/hephaestus/target/hephaestus-1.0-SNAPSHOT-standalone.jar'
NEUTRAL='C1CCCC(CCC1)C(CN)O'; PUBCHEM_CID=1551; INCHIKEY='NUOYMOJXFODLFN-UHFFFAOYSA-N'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()

def main():
 out=HERE/'ligands';out.mkdir(exist_ok=True); base=Chem.MolFromSmiles(NEUTRAL)
 center=Chem.FindMolChiralCenters(base,includeUnassigned=True)[0][0]; rows=[]
 for tag in (Chem.ChiralType.CHI_TETRAHEDRAL_CCW,Chem.ChiralType.CHI_TETRAHEDRAL_CW):
  m=Chem.Mol(base);m.GetAtomWithIdx(center).SetChiralTag(tag);Chem.AssignStereochemistry(m,cleanIt=True,force=True)
  stereo=Chem.FindMolChiralCenters(m,includeUnassigned=False)[0][1];cid=f'CONH_{stereo}'
  n=next(a for a in m.GetAtoms() if a.GetSymbol()=='N');n.SetFormalCharge(1);n.SetNoImplicit(False)
  prepared=Chem.MolToSmiles(m,isomericSmiles=True);m=Chem.AddHs(m)
  p=AllChem.ETKDGv3();p.randomSeed=20260809
  if AllChem.EmbedMolecule(m,p):raise RuntimeError(f'embed failed {cid}')
  AllChem.MMFFOptimizeMolecule(m,mmffVariant='MMFF94s',maxIters=1000);m.SetProp('_Name',cid)
  sdf=out/f'{cid}.sdf';w=Chem.SDWriter(str(sdf));w.write(m);w.close();pdbqt=out/f'{cid}.pdbqt'
  subprocess.run(['java','-jar',str(HEPH),'prepare-ligand','--input',str(sdf),'--output',str(pdbqt),'--overwrite'],check=True)
  rows.append({'compound_id':cid,'parent_compound_id':'CONH','name':'2-cyclooctyl-2-hydroxyethylamine','canonical_smiles':Chem.MolToSmiles(base),'inchi_key':INCHIKEY,'pubchem_cid':PUBCHEM_CID,'experimental_target':'historical TMT/PNMT','experimental_metric':'literature-supported inhibition','experimental_value':'','experimental_units':'','literature_source':'Glauser et al., Xenobiotica 1993 23:657-669; Liang et al., JPET 1982 223:375-381','evidence_category':'historical TMT/PNMT inhibitor comparator; no direct METTL7A-vs-METTL7B activity located','prepared_smiles':prepared,'stereochemistry':stereo,'formal_charge':Chem.GetFormalCharge(m),'rotatable_bonds':rdMolDescriptors.CalcNumRotatableBonds(m),'molecular_weight':f'{Descriptors.MolWt(m):.3f}','sdf_sha256':sha(sdf),'pdbqt_sha256':sha(pdbqt)})
 with (HERE/'conh_compounds.csv').open('w',newline='') as f:w=csv.DictWriter(f,fieldnames=list(rows[0]));w.writeheader();w.writerows(rows)
 (HERE/'conh_preparation_manifest.json').write_text(json.dumps({'source':{'name':'2-cyclooctyl-2-hydroxyethylamine','pubchem_cid':PUBCHEM_CID,'neutral_canonical_smiles':'C1CCCC(CCC1)C(CN)O','inchi_key':INCHIKEY,'stereochemistry':'unspecified'},'representation':'both R and S alcohol-center enantiomers; primary amine protonated +1; hydroxyl neutral','embedding':'RDKit ETKDGv3 seed 20260809; MMFF94s <=1000 iterations','conversion':'same Hephaestus prepare-ligand pipeline as focused controls','activity_warning':'No direct METTL7A-vs-METTL7B CONH experiment located; do not label METTL7-selective','variants':rows},indent=2)+'\n')
if __name__=='__main__':main()
