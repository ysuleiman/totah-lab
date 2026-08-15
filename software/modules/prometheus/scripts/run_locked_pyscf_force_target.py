#!/usr/bin/env python3
"""One immutable PBE-D3(BJ)/def2-SVP energy+Cartesian-gradient target."""
import argparse, hashlib, json, os, platform, tempfile, time
from pathlib import Path
import numpy as np
import pyscf, dftd3
from pyscf import dft, gto, lib
from pyscf.scf.hf import init_guess_by_chkfile
from dftd3.pyscf import energy as d3_energy


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_xyz(path):
    lines = Path(path).read_text().splitlines(); n = int(lines[0]); atoms=[]; xyz=[]
    for line in lines[2:2+n]:
        fields=line.split(); atoms.append(fields[0]); xyz.append([float(x) for x in fields[1:4]])
    if len(atoms) != n: raise ValueError("XYZ atom count mismatch")
    return atoms, np.asarray(xyz)


def nearest_checkpoint(output, coordinates):
    """Find a completed sibling target and return a projected-SCF starting point."""
    choices=[]
    for directory in sorted(output.parent.parent.glob('force-campaign-*')):
        checkpoint=directory/'scf_checkpoint.chk'; geometry=directory/'input_geometry.xyz'; result=directory/'result.json'
        if directory == output.parent or not (checkpoint.is_file() and geometry.is_file() and result.is_file()):
            continue
        try:
            _, candidate=read_xyz(geometry)
            if candidate.shape != coordinates.shape: continue
            rmsd=float(np.sqrt(np.mean(np.sum((candidate-coordinates)**2,axis=1))))
            choices.append((rmsd,str(directory),checkpoint,geometry))
        except Exception:
            continue
    return min(choices,key=lambda item:(item[0],item[1])) if choices else None


def main():
    p=argparse.ArgumentParser(); p.add_argument('--spec',required=True); p.add_argument('--geometry',required=True); p.add_argument('--output',required=True)
    a=p.parse_args(); spec_path=Path(a.spec); geometry_path=Path(a.geometry); output=Path(a.output)
    spec=json.loads(spec_path.read_text())
    if spec['method']!='PBE' or spec['basis'].lower()!='def2-svp' or spec['dispersion']!='D3(BJ)':
        raise ValueError("protocol is not locked PBE-D3(BJ)/def2-SVP")
    if spec['constraints']: raise ValueError("force targets must be fixed-geometry and unconstrained")
    if digest(geometry_path)!=spec['input_geometry_sha256']: raise ValueError("geometry checksum mismatch")
    threads=int(os.environ.get('PROMETHEUS_PYSCF_THREADS','4')); lib.num_threads(threads); os.environ['OMP_NUM_THREADS']=str(threads)
    atoms, xyz=read_xyz(geometry_path); started=time.time()
    mol=gto.M(atom=list(zip(atoms,xyz.tolist())),basis='def2-svp',charge=int(spec['formal_charge']),spin=int(spec['multiplicity'])-1,unit='Angstrom',verbose=4)
    mf=dft.RKS(mol).density_fit(); mf.xc='pbe'; mf.grids.level=2; mf.conv_tol=1e-8; mf.max_cycle=160
    mf.chkfile=str(output.with_name('scf_checkpoint.chk'))
    guess=nearest_checkpoint(output,xyz); guess_record={'kind':'MINAO','source':None,'source_sha256':None,'geometry_rmsd_angstrom':None}
    dm0=None
    if guess is not None:
        rmsd,_,checkpoint,_=guess
        try:
            dm0=init_guess_by_chkfile(mol,str(checkpoint),project=True)
            guess_record={'kind':'PROJECTED_VERIFIED_CHECKPOINT','source':str(checkpoint.resolve()),
                          'source_sha256':digest(checkpoint),'geometry_rmsd_angstrom':rmsd}
        except Exception as error:
            guess_record={'kind':'MINAO_CHECKPOINT_PROJECTION_FAILED','source':str(checkpoint.resolve()),
                          'source_sha256':digest(checkpoint),'geometry_rmsd_angstrom':rmsd,
                          'fallback_reason':type(error).__name__+': '+str(error)}
    mf=d3_energy(mf,version='d3bj'); energy=float(mf.kernel(dm0=dm0))
    if not mf.converged: raise RuntimeError("SCF did not converge")
    gradient=np.asarray(mf.nuc_grad_method().kernel(),dtype=float); force=-gradient
    np.savetxt(output.with_name('gradient_hartree_per_bohr.txt'),gradient,fmt='%.16e')
    np.savetxt(output.with_name('force_hartree_per_bohr.txt'),force,fmt='%.16e')
    result={'status':'CONVERGED','specification_checksum':spec['specification_checksum'],'scientific_identity':spec['scientific_identity'],
      'geometry_identity':spec['geometry_identity'],'input_geometry_sha256':digest(geometry_path),'calculation_specification_sha256':digest(spec_path),
      'energy_hartree':energy,'gradient_hartree_per_bohr':gradient.tolist(),'force_hartree_per_bohr':force.tolist(),
      'gradient_norm_hartree_per_bohr':float(np.linalg.norm(gradient)),'gradient_force_identity_max_abs':float(np.max(np.abs(gradient+force))),
      'scf_converged':bool(mf.converged),'force_definition':'force = -gradient','units':{'energy':'hartree','gradient':'hartree/bohr','force':'hartree/bohr'},
      'protocol':{'method':'PBE','basis':'def2-SVP','dispersion':'D3(BJ)','density_fitted':True,'environment':'gas phase','grid_level':2,'scf_convergence':1e-8,'max_scf_cycles':160},
      'software':{'python':platform.python_version(),'pyscf':pyscf.__version__,'dftd3':getattr(dftd3,'__version__','unknown'),'numpy':np.__version__},
      'initial_guess_provenance':guess_record,
      'threads':threads,'elapsed_seconds':time.time()-started,'checkpoint_sha256':digest(output.with_name('scf_checkpoint.chk'))}
    output.parent.mkdir(parents=True,exist_ok=True)
    with tempfile.NamedTemporaryFile('w',dir=output.parent,delete=False) as h:
        json.dump(result,h,indent=2,sort_keys=True); h.write('\n'); h.flush(); os.fsync(h.fileno()); tmp=Path(h.name)
    tmp.replace(output)


if __name__=='__main__': main()
