"""Read-only artifact/header characterization; no refinement or experimental interpretation."""
from pathlib import Path
import hashlib,json,struct,subprocess
q=Path(__file__).resolve().parent
base=Path('analysis/mettl7-phase2/execution-unit-05O/literature-comparator-sources/AmberClassic')
paths=['examples/4tut/4tut_final.mtz','examples/4tut/README','examples/9ewk/9ewk.mtz','examples/9ewk/README','test/cryoem/emap/1gb1.map','test/cryoem/emap/Run.emap','test/rdc/1pqx.nef','test/rdc/1pqx.pdb','test/rdc/1pqx_renum.txt','test/rdc/Run.nef']
result={'upstreamCheckout':str(base),'upstreamCommit':subprocess.check_output(['git','-C',str(base),'rev-parse','HEAD'],text=True).strip(),'purpose':'Presence/provenance characterization only. No METTL7 experiment interpreted; no refinement or new validation metric run. Upstream example outcomes are not Athena qualification.','artifacts':[]}
for path in paths:
 p=base/path;b=p.read_bytes();r={'path':str(p),'sha256':hashlib.sha256(b).hexdigest(),'bytes':len(b)}
 if p.suffix=='.mtz':
  assert b[:4]==b'MTZ '
  offset=(struct.unpack('<i',b[4:8])[0]-1)*4
  assert 80<=offset<len(b)
  header=[]
  for pos in range(offset,len(b)-79,80):
   line=b[pos:pos+80].decode('ascii').rstrip();header.append(line)
   if line.strip()=='END':break
  r['mtzHeader']=[line for line in header if line.startswith(('VERS','TITLE','NCOL','CELL','SYMINF','RESO','COLUMN','PROJECT','CRYSTAL','DATASET','DCELL','DWAVEL'))]
 elif p.suffix=='.map':
  r['magicAt208']=b[208:212].decode('ascii',errors='replace');r['dimensionsAndMode']=list(struct.unpack('<4i',b[:16]));r['limitation']='Amber emap regression input; no experimental half-map/mask provenance established merely by map presence.'
 elif p.suffix=='.nef' and b.lstrip().startswith(b'data_'):
  text=b.decode();r['saveFrames']=[line.strip() for line in text.splitlines() if line.startswith('save_') and line.strip()!='save_'];r['limitation']='NEF1.1/PDBStat5.20.4-exp metadata, shift/restraint data and companion model present. Exact atom ambiguity, averaging, units/protocol and model correspondence require separate reviewed admission.'
 result['artifacts'].append(r)
(q/'LOCAL_EXPERIMENTAL_ARTIFACTS.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({a['path']:{k:v for k,v in a.items() if k in ['mtzHeader','dimensionsAndMode','saveFrames']} for a in result['artifacts'] if any(k in a for k in ['mtzHeader','dimensionsAndMode','saveFrames'])},indent=2))
