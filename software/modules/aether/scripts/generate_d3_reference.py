"""Pinned external simple-dftd3 oracle, independent pair energies and C6/C8 recovery."""
from pathlib import Path
import json,hashlib,sys
import numpy as np
from dftd3.interface import DispersionModel,RationalDampingParam
import dftd3
from interaction_benchmarks import systems
from spcl_benchmarks import systems as monomers
root=Path(__file__).resolve().parents[1];out=root/'src/test/resources/totah/lab/aether/reference/d3';out.mkdir(exist_ok=True)
assert dftd3.__version__=='1.2.1'
Z={'H':1,'C':6,'N':7,'O':8,'P':15,'S':16,'Cl':17}
specs={}
for name,s in systems().items():specs[name]={'atoms':s['a']+s['b'],'charge':s['charge_a']+s['charge_b'],'fragment_a_count':len(s['a']),'charge_a':s['charge_a'],'charge_b':s['charge_b'],'source':s['source']}
for name in ['dms','chlorobenzene','h2s','ph3','hcl']:
 s=monomers()[name];specs[name]={'atoms':s['atoms'],'charge':s['charge'],'source':'Frozen M10/M13 geometry'}
# Explicit methane and benzene motifs in bohr; fixed geometries, not equilibrium claims.
a=monomers().get('ch4')
if a is None:
 q=1.09/np.sqrt(3)*1.8897261254578281;a={'atoms':[('C',[0,0,0])]+[('H',(q*np.array(v)).tolist()) for v in [(1,1,1),(1,-1,-1),(-1,1,-1),(-1,-1,1)]]}
b=systems()['ammonium_benzene']['b']
for name,aa,bb,shift in [('methane_dimer',a['atoms'],a['atoms'],[0,0,7.5]),('benzene_dimer',b,b,[2.8,0,6.5]),('benzene_methane',b,a['atoms'],[0,0,7.2])]:
 atoms=aa+[(el,(np.array(p)+shift).tolist()) for el,p in bb]
 specs[name]={'atoms':atoms,'charge':0,'fragment_a_count':len(aa),'charge_a':0,'charge_b':0,'source':'M16 explicit fixed idealized geometry; no published reference energy assigned'}
specs['h2o']={'atoms':systems()['water_dimer']['a'],'charge':0,'source':'Frozen water dimer monomer A'}
# Independently published geometry/energy subset, plus a small explicitly finite-basis cation-pi reference.
selection=root/'validation/milestone-16/nenci-selection.json'
if selection.exists():specs.update(json.loads(selection.read_text()))
bohr=1.8897261254578281
ammonium=[('N',[0,0,3.2]),('H',[0,0,2.16])]
for phi in [0,2*np.pi/3,4*np.pi/3]:ammonium.append(('H',[1.04*np.sqrt(8/9)*np.cos(phi),1.04*np.sqrt(8/9)*np.sin(phi),3.2+1.04/3]))
ethylene=[('C',[-.6695,0,0]),('C',[.6695,0,0]),('H',[-1.232,.928,0]),('H',[-1.232,-.928,0]),('H',[1.232,.928,0]),('H',[1.232,-.928,0])]
specs['cation_pi_small']={'atoms':[(el,(np.array(p)*bohr).tolist()) for el,p in ammonium+ethylene],'charge':1,'fragment_a_count':5,'charge_a':1,'charge_b':0,'category':'CATION_PI','source':'M16 explicit ammonium-ethylene geometry; fresh finite-basis CCSD(T) reference required; not CBS'}
for name,s in specs.items():
 atoms=s['atoms'];z=np.array([Z[e] for e,p in atoms]);xyz=np.array([p for e,p in atoms],dtype=float)
 m=DispersionModel(z,xyz);m.set_realspace_cutoff(60,40,40)
 p=RationalDampingParam(s6=1.,s8=.7875,a1=.4289,a2=4.4407,s9=0.)
 pair=m.get_pairwise_dispersion(p)['additive pairwise energy'];energy=float(m.get_dispersion(p,grad=False)['energy'])
 # Undamped s6-only and s8-only oracle expose independently interpolated C6/C8.
 p6=RationalDampingParam(s6=1.,s8=0.,a1=0.,a2=0.,s9=0.)
 p8=RationalDampingParam(s6=0.,s8=1.,a1=0.,a2=0.,s9=0.)
 raw6=m.get_pairwise_dispersion(p6)['additive pairwise energy'];raw8=m.get_pairwise_dispersion(p8)['additive pairwise energy']
 rows=['i,j,distance,c6,c8,pair_energy']
 for i in range(len(z)):
  for j in range(i):
   r=float(np.linalg.norm(xyz[i]-xyz[j]));rows.append(','.join(map(str,[i,j,r,float(-2*raw6[i,j]*r**6),float(-2*raw8[i,j]*r**8),float(2*pair[i,j])])))
 s.update(d3_energy=energy,oracle='simple-dftd3 1.2.1',three_body=False,scientific_status='SCREENING_ONLY')
 data=('\n'.join(rows)+'\n').encode();(out/(name+'.csv')).write_bytes(data);s['pair_sha256']=hashlib.sha256(data).hexdigest()
(out/'systems.json').write_text(json.dumps(specs,indent=2)+'\n');print(len(specs),'systems')
rows=['name,charge,fragment_a_count,charge_a,charge_b,d3_energy,pair_sha256']
for name,s in specs.items():
 (out/(name+'.atoms')).write_text('\n'.join(','.join(map(str,[Z[e],*p])) for e,p in s['atoms'])+'\n')
 rows.append(','.join(map(str,[name,s['charge'],s.get('fragment_a_count',0),s.get('charge_a',s['charge']),s.get('charge_b',0),s['d3_energy'],s['pair_sha256']])))
(out/'index.csv').write_text('\n'.join(rows)+'\n')
