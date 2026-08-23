#!/usr/bin/env python3
"""Frozen homogeneous A100 PBE-D3(BJ)/def2-SVP campaign runner.

Preparation only: this file is not executed by the repository preparation step.
Each geometry is committed by an atomic directory rename after every numerical
component and checksum has been persisted.
"""
from __future__ import annotations

import csv, hashlib, json, os, platform, shutil, sys, time, uuid
from importlib.metadata import version
from pathlib import Path

import numpy as np

MANIFEST = Path("NEXT_GPU_QM_BATCH_60.csv")
OUTPUT = Path("gpu_qm_results")
EXPECTED_ELEMENTS = ["C"]*5+["O"]+["C"]*16+["O","O","C","S"]+["H"]*30
GEOMETRY_COUNT = 56
ELECTRONS = 202
PARAMETER_DB_SHA256 = "b1d9d1b9882dcad5361a99c34745ad44f8a274d80c907d9d0187255e4323d645"
D3 = {"s6":1.0,"s8":0.7875,"s9":0.0,"a1":0.4289,"a2":4.4407,"alp":14.0}


def sha(path): return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def atomic_text(path, text):
    tmp=Path(str(path)+".tmp");tmp.write_text(text);os.replace(tmp,path)


def read_xyz(path):
    lines=path.read_text().splitlines();n=int(lines[0]);rows=[x.split() for x in lines[2:2+n]]
    elements=[x[0] for x in rows];coords=np.array([[float(v) for v in x[1:4]] for x in rows])
    if n!=56 or elements!=EXPECTED_ELEMENTS:raise RuntimeError("geometry identity/order mismatch")
    return elements,coords


def d3_database():
    import dftd3
    root=Path(dftd3.__file__).resolve().parent
    for path in [root/"parameters.toml",root.parent/"parameters.toml",*root.parent.glob("**/parameters.toml")]:
        if path.is_file() and sha(path)==PARAMETER_DB_SHA256:return path
    raise RuntimeError("frozen dftd3 parameter database not found")


def run_one(row):
    import cupy as cp, pyscf
    from dftd3.interface import DispersionModel,RationalDampingParam
    from gpu4pyscf.dft import gen_grid as gpu_grid
    from gpu4pyscf.dft import radi as gpu_radi
    from pyscf import dft,gto
    from pyscf.data.nist import BOHR

    if pyscf.__version__!="2.14.0" or version("gpu4pyscf-cuda12x")!="1.8.0" or version("dftd3")!="1.5.0":
        raise RuntimeError("software identity mismatch")
    gid=row["campaign_id"];source=Path(row["runner_path"])
    if not source.is_file() or sha(source)!=row["geometry_sha256"]:raise RuntimeError("geometry checksum mismatch")
    final=OUTPUT/gid
    if final.exists():return
    partial=OUTPUT/f".{gid}.partial.{uuid.uuid4().hex}";partial.mkdir(parents=True)
    try:
        shutil.copyfile(source,partial/"geometry.xyz")
        elements,coords=read_xyz(partial/"geometry.xyz")
        mol=gto.M(atom=list(zip(elements,coords.tolist())),basis="def2-svp",charge=0,spin=0,unit="Angstrom",verbose=4,max_memory=24000)
        if mol.nelectron!=ELECTRONS:raise RuntimeError("electron count mismatch")
        cpu=dft.RKS(mol).density_fit(auxbasis="def2-svp-jkfit");cpu.xc="pbe";cpu.grids.level=2
        cpu.conv_tol=1e-8;cpu.max_cycle=160;cpu.init_guess="minao";cpu.chkfile=None
        gpu=cpu.to_gpu();gpu.grids.level=2;gpu.grids.prune=gpu_grid.nwchem_prune
        gpu.grids.becke_scheme=gpu_grid.original_becke;gpu.grids.radi_method=gpu_radi.treutler
        gpu.grids.radii_adjust=gpu_radi.treutler_atomic_radii_adjust
        if gpu_grid.get_C_interface_scheme_id(gpu.grids.becke_scheme)!=100:raise RuntimeError("grid preflight failed")
        cycles={"n":0};gpu.callback=lambda e:cycles.update(n=max(cycles["n"],int(e.get("cycle",-1))+1))
        pool=cp.get_default_memory_pool();free0,totalmem=cp.cuda.runtime.memGetInfo();t0=time.perf_counter();ts=time.perf_counter()
        electronic_energy=float(gpu.kernel());cp.cuda.Stream.null.synchronize();scf_time=time.perf_counter()-ts
        if not gpu.converged:raise RuntimeError("SCF not converged")
        tg=time.perf_counter();eg=gpu.nuc_grad_method().kernel();cp.cuda.Stream.null.synchronize();electronic_gradient=cp.asnumpy(eg) if isinstance(eg,cp.ndarray) else np.asarray(eg);gradient_time=time.perf_counter()-tg
        numbers=np.array([{"H":1,"C":6,"O":8,"S":16}[e] for e in elements],dtype=np.int32)
        model=DispersionModel(numbers,coords/BOHR);param=RationalDampingParam(**D3);disp=model.get_dispersion(param,grad=True)
        d3_energy=float(disp["energy"]);d3_gradient=np.asarray(disp["gradient"]);total_gradient=electronic_gradient+d3_gradient;force=-total_gradient
        free1,_=cp.cuda.runtime.memGetInfo();elapsed=time.perf_counter()-t0
        for name,array in [("electronic_gradient_hartree_per_bohr.txt",electronic_gradient),("d3_gradient_hartree_per_bohr.txt",d3_gradient),("total_gradient_hartree_per_bohr.txt",total_gradient),("force_hartree_per_bohr.txt",force)]:np.savetxt(partial/name,array,fmt="%.17e")
        result={"status":"CONVERGED","campaign_id":gid,"geometry_sha256":sha(source),"atom_count":56,"elements":elements,"charge":0,"spin":0,"electron_count":202,
                "electronic_energy_hartree":electronic_energy,"d3_energy_hartree":d3_energy,"total_energy_hartree":electronic_energy+d3_energy,
                "electronic_gradient_hartree_per_bohr":electronic_gradient.tolist(),"d3_gradient_hartree_per_bohr":d3_gradient.tolist(),"total_gradient_hartree_per_bohr":total_gradient.tolist(),"force_hartree_per_bohr":force.tolist(),"force_definition":"force=-gradient",
                "scf_converged":True,"scf_cycles":cycles["n"],"scf_seconds":scf_time,"gradient_seconds":gradient_time,"total_seconds":elapsed,
                "protocol":{"pyscf":"2.14.0","gpu4pyscf":"1.8.0","method":"PBE","basis":"def2-SVP","auxiliary_basis":"def2-SVP-JKFIT","density_fitting":True,"grid_level":2,"prune":"NWCHEM","partition":"ORIGINAL_BECKE","radial":"TREUTLER_AHLRICHS","radii_adjust":"TREUTLER","conv_tol":1e-8,"max_cycle":160,"d3":"D3(BJ)","d3_parameters":D3,"dftd3":"1.5.0","d3_parameter_database_sha256":sha(d3_database())},
                "gpu":{"name":str(cp.cuda.runtime.getDeviceProperties(0)["name"]),"total_bytes":int(totalmem),"free_before_bytes":int(free0),"free_after_bytes":int(free1),"memory_pool_peak_proxy_bytes":int(pool.total_bytes())},"software":{"python":platform.python_version(),"cupy":cp.__version__}}
        atomic_text(partial/"result.json",json.dumps(result,indent=2,sort_keys=True)+"\n")
        files=sorted(p for p in partial.iterdir() if p.is_file() and p.name!="SHA256SUMS")
        atomic_text(partial/"SHA256SUMS","".join(f"{sha(p)}  {p.name}\n" for p in files))
        os.replace(partial,final)
    except Exception:
        failed=OUTPUT/f"{gid}.FAILED.{uuid.uuid4().hex}";os.replace(partial,failed);raise


def main():
    OUTPUT.mkdir(parents=True,exist_ok=True)
    rows=list(csv.DictReader(MANIFEST.open()))
    if len(rows)!=60:raise RuntimeError("frozen campaign manifest must have 60 rows")
    for row in rows:run_one(row)


if __name__=="__main__":main()
