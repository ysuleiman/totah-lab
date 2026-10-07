from pathlib import Path
from rdkit import Chem,rdBase
import csv,json,hashlib,re
q=Path(__file__).resolve().parent
source=Path('software/qualification/scientific-rule-knowledge-audit-20261004/reference/rdkit--Data__BaseFeatures.fdef')
queries=dict(re.findall(r'^DefineFeature (ZnBinder\d) (.+)$',source.read_text(),re.M))
results=[]
for row in csv.DictReader((q/'REFERENCE_PANEL.tsv').open(),delimiter='\t'):
 p=Chem.SmilesParserParams();p.removeHs=False;m=Chem.MolFromSmiles(row['smiles'],p)
 assert m is not None,row
 results.append({**row,'explicitAtoms':m.GetNumAtoms(),'matches':{name:len(m.GetSubstructMatches(Chem.MolFromSmarts(query),uniquify=True)) for name,query in queries.items()}})
(q/'RDKIT_REFERENCE.json').write_text(json.dumps({'version':rdBase.rdkitVersion,'fdefSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'queries':queries,'scope':'Reference comparison only, supplied synthetic SMILES; explicit graph H retained; no binding authority','cases':results},indent=2)+'\n')
(q/'queries.tsv').write_text(''.join(name+'\t'+query+'\n' for name,query in queries.items()))
print(rdBase.rdkitVersion,len(results))
