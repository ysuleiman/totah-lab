import sqlite3,pathlib,json,hashlib,importlib.metadata
from rdkit import Chem
import argparse
parser=argparse.ArgumentParser(description='Offline parity fixture export; no production Python dependency')
parser.add_argument('database',type=pathlib.Path)
parser.add_argument('output',type=pathlib.Path)
args=parser.parse_args()
out=args.output
out.mkdir(parents=True,exist_ok=True)
c=sqlite3.connect(args.database.resolve().as_uri()+'?mode=ro',uri=True)
q='select distinct c1.public_id,c2.public_id,s1.smiles,s2.smiles,cs.smiles from pair p join compound c1 on c1.id=p.compound1_id join compound c2 on c2.id=p.compound2_id join rule_environment re on re.id=p.rule_environment_id join rule r on r.id=re.rule_id join rule_smiles s1 on s1.id=r.from_smiles_id join rule_smiles s2 on s2.id=r.to_smiles_id join constant_smiles cs on cs.id=p.constant_id'
allrows=c.execute(q).fetchall(); rows=[]
for a,b,l,r,core in allrows:
 def heavy(s):return sum(x.GetAtomicNum()>1 for x in Chem.MolFromSmiles(s).GetAtoms())
 if heavy(core)>=4 and 1<=heavy(l)<=8 and 1<=heavy(r)<=8:
  rows.append((a,b,l,r,core))
pairs=sorted({tuple(sorted((a,b))) for a,b,*_ in rows})
(out/'mmpdb-single-cut-pairs.tsv').write_text('left\tright\n'+''.join(a+'\t'+b+'\n' for a,b in pairs))
(out/'mmpdb-reference-transformations.tsv').write_text('left\tright\tvariableLeft\tvariableRight\tconstant\n'+''.join('\t'.join(x)+'\n' for x in sorted(rows)))
print(len(allrows),'all reference paths;',len(rows),'in overlapping scope;',len(pairs),'molecular pairs')
