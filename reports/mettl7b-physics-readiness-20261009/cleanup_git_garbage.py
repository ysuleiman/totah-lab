"""User-authorized removal of Git-reported, unused temporary pack garbage only."""
import json,re,subprocess,time,shutil
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).resolve().parent
def git(*args): return subprocess.check_output(['git',*args],cwd=ROOT)
head=git('rev-parse','HEAD'); refs=git('show-ref'); status=git('status','--porcelain','-uno')
check=subprocess.run(['git','count-objects','-vH'],cwd=ROOT,capture_output=True,text=True,check=True)
paths=[ROOT/p for p in re.findall(r'^warning: garbage found: (.+)$',check.stderr,re.M)]
assert len(paths)==32
records=[]
for p in paths:
    assert p.parent==ROOT/'.git/objects/pack' and re.fullmatch('tmp_pack_[A-Za-z0-9]+',p.name)
    assert not p.is_symlink() and p.is_file()
    s=p.stat();assert time.time()-s.st_mtime>600
    records.append(dict(path=str(p.relative_to(ROOT)),size=s.st_size,mtime_ns=s.st_mtime_ns))
opened=subprocess.run(['lsof',*[str(p) for p in paths]],capture_output=True,text=True)
assert opened.returncode==1 and not opened.stdout and not opened.stderr,opened
before=shutil.disk_usage(ROOT).free
receipt=dict(head=head.decode().strip(),before=check.stdout,files=records,total_bytes=sum(r['size'] for r in records),free_before=before)
(HERE/'GIT_GARBAGE_CLEANUP.json').write_text(json.dumps(receipt,indent=2)+'\n')
for p,r in zip(paths,records):
    s=p.stat();assert s.st_size==r['size'] and s.st_mtime_ns==r['mtime_ns']
    p.unlink()
assert git('rev-parse','HEAD')==head and git('show-ref')==refs and git('status','--porcelain','-uno')==status
receipt.update(free_after=shutil.disk_usage(ROOT).free,after=git('count-objects','-vH').decode(),head_refs_tracked_status_unchanged=True)
(HERE/'GIT_GARBAGE_CLEANUP.json').write_text(json.dumps(receipt,indent=2)+'\n')
print(json.dumps({k:v for k,v in receipt.items() if k!='files'},indent=2))
