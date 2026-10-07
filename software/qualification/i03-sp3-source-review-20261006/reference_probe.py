"""External RDKit characterization only. NOT an Athena assignment implementation or authority."""
from pathlib import Path
import hashlib,json,sys
from rdkit import Chem,rdBase
q=Path(__file__).resolve().parent
rows=[]
for line in (q/'REFERENCE_PANEL.tsv').read_text().splitlines():
 name,smiles=line.split('\t')
 try:
  options=Chem.SmilesParserParams();options.sanitize=True;options.removeHs=True
  mol=Chem.MolFromSmiles(smiles,options)
  if mol is None:raise ValueError('parser/sanitization failure')
  atoms=[a for a in mol.GetAtoms() if a.GetAtomMapNum()==1];assert len(atoms)==1
  a=atoms[0];rows.append({'id':name,'smiles':smiles,'status':'PARSED','atomIndex':a.GetIdx(),'hybridization':str(a.GetHybridization()),'formalCharge':a.GetFormalCharge(),'aromatic':a.GetIsAromatic(),'totalH':a.GetTotalNumHs(includeNeighbors=True),'radicalElectrons':a.GetNumRadicalElectrons(),'conjugatedIncidentBond':any(b.GetIsConjugated() for b in a.GetBonds())})
 except Exception as e:rows.append({'id':name,'smiles':smiles,'status':'ERROR','error':str(e)})
binaries=[Path(Chem.rdchem.__file__),Path(rdBase.__file__)]
result={'kind':'EXTERNAL_REFERENCE_CHARACTERIZATION_NOT_ATHENA_AUTHORITY','version':rdBase.rdkitVersion,'build':rdBase.rdkitBuild,'python':sys.version,'protocol':'MolFromSmiles; sanitize=True,removeHs=True; target atom map1; no embedding/coordinates; default other parser/sanitization settings','binaryPins':{str(p):hashlib.sha256(p.read_bytes()).hexdigest() for p in binaries},'rows':rows}
(q/'RDKIT_REFERENCE_RESULTS.json').write_text(json.dumps(result,indent=2)+'\n')
print('RDKit reference rows:',len(rows))
