"""Task-specific bounded Amber endpoint preflight; writes only under this report."""
import hashlib,json,os,shutil,subprocess,time
from pathlib import Path
import psutil
from rdkit import Chem
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).resolve().parent
ENV=ROOT/'analysis/dcmb/selectivity_validation/.conda-md'
BASE=ROOT/'research/mettl7-netarsudil-sam-mechanism/phase2-matched-sar'
OUT=HERE/'parameter-preflight'; OUT.mkdir(exist_ok=True)
sha=lambda p:hashlib.sha256(Path(p).read_bytes()).hexdigest()
env=dict(os.environ,AMBERHOME=str(ENV),OMP_NUM_THREADS='1',OPENBLAS_NUM_THREADS='1',MKL_NUM_THREADS='1',PYTHONDONTWRITEBYTECODE='1')
env['PATH']=str(ENV/'bin')+os.pathsep+env.get('PATH','')
manifest=json.loads((BASE/'run_manifest.json').read_text())
receipts=[]
def run(args,cwd,label):
    assert shutil.disk_usage(OUT).free>=100*1024**2,'insufficient free space'
    start=time.monotonic(); peak=0; reason=None
    with (cwd/(label+'.log')).open('w') as log:
        p=subprocess.Popen(args,cwd=cwd,env=env,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
        while p.poll() is None:
            try:
                tree=[psutil.Process(p.pid)]+psutil.Process(p.pid).children(recursive=True)
                peak=max(peak,sum(x.memory_info().rss for x in tree if x.is_running()))
            except psutil.Error: pass
            size=sum(x.stat().st_size for x in OUT.rglob('*') if x.is_file())
            if time.monotonic()-start>180:reason='TIME_LIMIT'
            if peak>1024**3:reason='MEMORY_LIMIT'
            if size>16*1024**2 or shutil.disk_usage(OUT).free<100*1024**2:reason='DISK_LIMIT'
            if reason:
                import signal
                os.killpg(p.pid,signal.SIGTERM);p.wait(timeout=10);break
            time.sleep(.2)
    receipt=dict(command=args,cwd=str(cwd.relative_to(ROOT)),returncode=p.returncode,wall_seconds=time.monotonic()-start,peak_tree_rss_bytes=peak,stop_reason=reason)
    receipts.append(receipt)
    (OUT/'COMMANDS.json').write_text(json.dumps(receipts,indent=2)+'\n')
    assert p.returncode==0 and reason is None,receipt

for compound,n in [('parent_netarsudil',61),('ar_13503_deesterified',43)]:
    work=OUT/compound;work.mkdir(exist_ok=True)
    entry=next(x for x in manifest['ligands'] if x['compound']==compound)
    source=ROOT/entry['sdf'];assert sha(source)==entry['sdf_sha256']
    mol=Chem.SDMolSupplier(str(source),removeHs=False)[0];assert mol is not None and mol.GetNumAtoms()==n
    assert sum(a.GetFormalCharge() for a in mol.GetAtoms())==0
    assert all(a.GetNumRadicalElectrons()==0 for a in mol.GetAtoms())
    Chem.AssignAtomChiralTagsFromStructure(mol,replaceExistingTags=True)
    identity=Chem.MolToSmiles(Chem.RemoveHs(mol),isomericSmiles=True)
    # Exact source copy: source is never rewritten by the charge engine.
    shutil.copyfile(source,work/'input.sdf')
    (work/'INPUT.json').write_text(json.dumps(dict(source=str(source.relative_to(ROOT)),sha256=sha(source),neutral_reference_only=True,source_3d_stereochemical_smiles=identity),indent=2)+'\n')
    run([str(ENV/'bin/antechamber'),'-i','input.sdf','-fi','mdl','-o','candidate.mol2','-fo','mol2','-c','bcc','-nc','0','-m','1','-at','gaff2','-rn','LIG','-pf','n','-s','2','-ek',"qm_theory='AM1', scfconv=1.d-10, grms_tol=0.0005, maxcyc=2000,"],work,'antechamber')
    sqm=(work/'sqm.out').read_text();assert 'Calculation Completed' in sqm
    run([str(ENV/'bin/parmchk2'),'-i','candidate.mol2','-f','mol2','-o','candidate.frcmod','-s','2'],work,'parmchk2')
    assert 'ATTN' not in (work/'candidate.frcmod').read_text()
    (work/'leap.in').write_text('source leaprc.gaff2\nLIG = loadmol2 candidate.mol2\nloadamberparams candidate.frcmod\ncheck LIG\nsaveamberparm LIG candidate.prmtop candidate.inpcrd\nquit\n')
    run([str(ENV/'bin/tleap'),'-f','leap.in'],work,'tleap')
    assert (work/'candidate.prmtop').is_file()
    assert sha(source)==entry['sdf_sha256']
(OUT/'STATUS.json').write_text(json.dumps(dict(status='CANDIDATE_GENERATION_ONLY',production_qualified=False,commands=len(receipts)),indent=2)+'\n')
