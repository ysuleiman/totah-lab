"""Freeze this task's diagnostics and preservation receipts; no model mutation."""
import hashlib,json,re,shutil
from decimal import Decimal
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]; HERE=Path(__file__).resolve().parent
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
out=HERE/'parameter-preflight'
assert (out/'INDEPENDENT_VALIDATION.json').read_bytes()==(out/'INDEPENDENT_REPLAY.json').read_bytes()
trace=[]
for name in ['parent_netarsudil','ar_13503_deesterified']:
    d=out/name; text=(d/'sqm.out').read_text()
    block=text.split('Atom    Element       Mulliken Charge')[-1].split('Total Mulliken Charge')[0]
    rows=re.findall(r'^\s*\d+\s+[A-Z][a-z]?\s+(-?\d+\.\d+)\s*$',block,re.M)
    assert len(rows)==(61 if name=='parent_netarsudil' else 43)
    mol2=(d/'candidate.mol2').read_text().split('@<TRIPOS>ATOM')[1].split('@<TRIPOS>')[0]
    charges=[Decimal(line.split()[8]) for line in mol2.splitlines() if line.strip()]
    trace.append(dict(compound=name,printed_mulliken_atom_count=len(rows),printed_atomic_sum_e=str(sum(map(Decimal,rows))),printed_total_e=text.split('Total Mulliken Charge =')[-1].split()[0],mol2_charge_sum_e=str(sum(charges)),interpretation='Printed atom rounding is already sufficient to explain the approximately +0.002 e residual; no renormalization performed. This is a serialization/protocol gate failure, not evidence that the molecule is physically charged.'))
(out/'CHARGE_TRACE.json').write_text(json.dumps(trace,indent=2)+'\n')
pins=json.loads((HERE/'SOURCE_PINS.json').read_text())
for path,digest in pins['sources'].items(): assert sha(ROOT/path)==digest,path
pres=json.loads((ROOT/'reports/mettl7b-cap-tsl-coexistence-20261009/PRESERVATION_BEFORE.json').read_text())['sha256']
for path,digest in pres.items(): assert sha(ROOT/path)==digest,path
env=ROOT/'analysis/dcmb/selectivity_validation/.conda-md'
files=[env/'bin'/n for n in ['antechamber','sqm','parmchk2','tleap','sander']]
files += [env/'dat/leap/parm/gaff2.dat',env/'dat/leap/cmd/leaprc.gaff2']
files += list((env/'dat/antechamber').glob('BCC*'))
files += list((env/'conda-meta').glob('ambertools-*.json'))
ref=ROOT/'analysis/mettl7-phase2/execution-unit-05O/literature-comparator-sources/AmberClassic/src'
files += [ref/'antechamber/charge.c',ref/'sqm/qm2_print_charges.F90']
files += [p for p in out.rglob('*') if p.is_file()]
files += [HERE/n for n in ['parameter_preflight.initial.py','parameter_preflight.py','validate_parameters.py','PARAMETER_PREFLIGHT_CONTRACT.txt']]
(HERE/'PARAMETER_PROVENANCE.json').write_text(json.dumps(dict(sha256={str(p.relative_to(ROOT)):sha(p) for p in sorted(files)},source_code_caveat='AmberClassic reference checkout is explanatory source, not a demonstrated build identity for installed AmberTools26 binaries. Executed binaries are separately pinned.'),indent=2)+'\n')
result=dict(source_pins_verified=len(pins['sources']),preservation_pins_verified=len(pres),fresh_process_replay_byte_identical=True,neutral_endpoint_candidates=2,production_qualified=False,charge_precision_gate_pass=False,physical_parameter_validation_complete=False,free_disk_bytes=shutil.disk_usage(HERE).free,assessment='Executed bounded candidate derivations and independent diagnostics; retained charge gate failure and analogy terms. No MD/RBFE/reaction production and no mechanistic advance.')
(HERE/'VALIDATION.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2));print(json.dumps(trace,indent=2))
