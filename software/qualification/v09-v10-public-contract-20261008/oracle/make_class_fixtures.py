import sys
sys.dont_write_bytecode = True
"""Execute unchanged upstream precedence/omega condition AST on supplied symbolic facts.
This probes branching only, NOT residue perception, coordinate geometry or admission.
"""
from pathlib import Path
import ast,json,types,math,hashlib
root=Path(__file__).resolve().parent
p=root.parent.parent/'v09-v10-decision-20261008/reference/cctbx--mmtbx__validation__ramalyze.py'
tree=ast.parse(p.read_text())
branch=next(n for n in ast.walk(tree) if isinstance(n,ast.If) and ast.unparse(n.test)=="main_residue.resname[0:3] == 'GLY'")
cis=next(n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name=='is_cislike_peptide')
condition=next(n.test for n in cis.body if isinstance(n,ast.If) and 'omega >' in ast.unparse(n.test))
cond=compile(ast.Expression(condition),str(p),'eval')
code=compile(ast.Module(body=[branch],type_ignores=[]),str(p),'exec')
const={'RAMA_GENERAL':0,'RAMA_GLYCINE':1,'RAMA_CISPRO':2,'RAMA_TRANSPRO':3,'RAMA_PREPRO':4,'RAMA_ILE_VAL':5}
residues='ALA ARG ASN ASP CYS GLN GLU GLY HIS ILE LEU LYS MET PHE PRO SER THR TRP TYR VAL'.split();rows=[]
for current in residues:
 for nxt in residues:
  for omega in ([None,-180,math.nextafter(-90,-math.inf),-90,math.nextafter(-90,0),0,math.nextafter(90,0),90,math.nextafter(90,math.inf),180] if current=='PRO' else [180]):
   def is_cislike_peptide(ignored):
    # Supply omega directly: source's None=>False branch and exact comparison.
    return False if omega is None else eval(cond,{'omega':omega})
   ns=dict(const,main_residue=types.SimpleNamespace(resname=current),three=[None,None,types.SimpleNamespace(resname=nxt)],is_cislike_peptide=is_cislike_peptide,res_type=0)
   exec(code,ns)
   rows.append({'central':current,'next':nxt,'omega':omega,'upstreamClass':ns['res_type'],'athenaAdmissionOverride':'UNKNOWN_REQUIRED' if omega is None else None})
obj={'status':'BRANCH_ORACLE_ONLY_SUPPLIED_IDENTITIES_NOT_SOURCE_ADMISSION','sourceSha256':hashlib.sha256(p.read_bytes()).hexdigest(),'precedenceLines':[branch.lineno,branch.end_lineno],'cisConditionLine':condition.lineno,'cases':rows}
(root/'CLASS_ORACLE.json').write_text(json.dumps(obj,indent=2)+'\n');print('class branch probes',len(rows))
