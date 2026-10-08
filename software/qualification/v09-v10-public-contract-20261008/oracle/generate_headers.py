import sys
sys.dont_write_bytecode = True
"""Invoke unchanged pinned upstream generators; no hand-written header substitutes."""
from pathlib import Path
import scitbx,scitbx.source_generators,sys,json,hashlib
root=Path(__file__).resolve().parent
scitbx.source_generators.__path__[:]=[str(root/'upstream/scitbx/source_generators')]
from scitbx.source_generators.array_family import generate_all
out=root/'upstream/scitbx/array_family'
before=set(out.rglob('*.h'))
generate_all.refresh(str(out))
files=sorted(set(out.rglob('*.h'))-before)
(root/'GENERATED_HEADERS.json').write_text(json.dumps([{'path':str(p.relative_to(root)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()} for p in files],indent=2)+'\n')
