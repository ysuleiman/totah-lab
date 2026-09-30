"""Frozen dimer geometries. Coordinates returned in bohr; no geometry optimization.
Water dimer: Q-Chem 6.3 manual Example 12.12 (standard S22 geometry).
Other dimers: explicit idealized motifs; not claimed as published equilibrium structures.
"""
import math
import numpy as np
from spcl_benchmarks import systems as prior_systems, ANGSTROM_TO_BOHR

def systems():
    result={}
    def save(name,a,b,charge_a=0,charge_b=0,source='Idealized fixed motif defined in interaction_benchmarks.py'):
        result[name]=dict(a=[(el,(np.asarray(p)*ANGSTROM_TO_BOHR).tolist()) for el,p in a],b=[(el,(np.asarray(p)*ANGSTROM_TO_BOHR).tolist()) for el,p in b],charge_a=charge_a,charge_b=charge_b,source=source)
    save('water_dimer',[('O',[-1.551007,-.114520,0]),('H',[-1.934259,.762503,0]),('H',[-.599677,.040712,0])],
         [('O',[1.350625,.111469,0]),('H',[1.680398,-.373741,-.758561]),('H',[1.680398,-.373741,.758561])],
         source='Q-Chem 6.3 manual Example 12.12; https://manual.q-chem.com/6.3/qchem_manual.pdf; standard S22 water dimer')
    water=[('O',[0,0,0]),('H',[.96,0,0]),('H',[-.240,.929,0])]
    ammonia=[('N',[2.9,0,0])]
    for phi in [0,2*math.pi/3,4*math.pi/3]:ammonia.append(('H',np.array([2.9,0,0])+1.01*np.array([.37,math.sqrt(1-.37**2)*math.cos(phi),math.sqrt(1-.37**2)*math.sin(phi)])))
    save('water_ammonia',water,ammonia)
    save('hcl_water',[('Cl',[0,0,0]),('H',[1.275,0,0])],[('O',[3.2,0,0]),('H',[3.78,.76,0]),('H',[3.78,-.76,0])])
    prior=prior_systems();thiol=[(el,np.array(p)/ANGSTROM_TO_BOHR) for el,p in prior['ch3sh']['atoms']]
    direction=np.array([math.cos(math.radians(96)),math.sin(math.radians(96)),0]);oxygen=3.3*direction
    save('methanethiol_water',thiol,[('O',oxygen),('H',oxygen+.58*direction+np.array([0,0,.76])),('H',oxygen+.58*direction-np.array([0,0,.76]))])
    benzene=[]
    for element,radius in [('C',1.397),('H',2.477)]:
        for i in range(6):benzene.append((element,[radius*math.cos(i*math.pi/3),radius*math.sin(i*math.pi/3),0]))
    ammonium=[('N',[0,0,3.4]),('H',[0,0,2.36])]
    for phi in [0,2*math.pi/3,4*math.pi/3]:ammonium.append(('H',[1.04*math.sqrt(8/9)*math.cos(phi),1.04*math.sqrt(8/9)*math.sin(phi),3.4+1.04/3]))
    save('ammonium_benzene',ammonium,benzene,charge_a=1)
    chlorine=[(el,np.array(p)/ANGSTROM_TO_BOHR) for el,p in prior['chlorobenzene']['atoms']]
    oxygen=np.array([1.397+1.74+3.1,0,0])
    save('chlorobenzene_water',chlorine,[('O',oxygen),('H',oxygen+[-.96,0,0]),('H',oxygen+[.24,.929,0])])
    return result
