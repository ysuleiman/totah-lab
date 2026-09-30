"""Read-only historical receipt audit; writes only new M17.1 evidence.

Missing fields stay null. Scalar recurrence is diagnostic, not proof of a density
limit cycle. No rule produced here controls the production SCF solver.
"""
import hashlib
import json
import math
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'validation/milestone-17.1'


def number(value):
    if value is None or value in ('UNAVAILABLE', 'OptionalDouble.empty'):
        return None
    value = value.removeprefix('OptionalDouble[').removesuffix(']')
    return float.fromhex(value) if '0x' in value else float(value)


def field(text, name):
    match = re.search(r'\b' + name + r'=(OptionalDouble\[[^]]+\]|[^,\]\s]+)', text)
    return match.group(1) if match else None


def extra(text):
    orbital = re.search(r'orbitalEnergies=\[([^]]*)\]', text)
    energies = [float(x) for x in orbital.group(1).split(',')] if orbital else None
    trace = number(field(text, 'tracePS'))
    occupied = round(trace / 2) if trace is not None and abs(trace-round(trace)) < 1e-7 else None
    gap = energies[occupied] - energies[occupied-1] if energies and occupied and occupied < len(energies) else None
    active = field(text, 'extrapolated')
    return dict(diis_error=number(field(text, 'errorMaximum')),
                diis_active=None if active is None else active == 'true',
                orbital_energies=energies, homo_lumo_gap=gap)


def parse(text):
    rows = []
    for part in re.split(r'(?:Iteration\[number=|IterationReceipt\[iterationNumber=)', text)[1:]:
        rows.append(dict(iteration=int(part.split(',')[0]),
                         energy=number(field(part, 'totalEnergy')),
                         delta_energy=number(field(part, 'deltaEnergy')),
                         density_residual=number(field(part, 'densityResidual')),
                         **extra(part)))
    if rows:
        return rows
    # Legacy canonical DIIS receipts encode scalar fields as hexadecimal doubles.
    pattern = r'(?m)^(\d+)\n(-?0x[^\n]+)\n(-?0x[^\n]+)\n([^\n]+)\n(0x[^\n]+)\n((?:[^\n]*\n){6})(DiisUpdate\[[^\n]+|CONVERGED_NO_UPDATE)'
    for m in re.finditer(pattern, text):
        rows.append(dict(iteration=int(m[1]), energy=number(m[3]),
                         delta_energy=number(m[4]), density_residual=number(m[5]), **extra(m[7])))
    return rows


def enrich(rows, window=16):
    best = math.inf
    last_improvement = 0
    for i, row in enumerate(rows):
        r = row['density_residual']
        if r < best * .99:
            last_improvement = i
        best = min(best, r)
        current = rows[max(0, i-window+1):i+1]
        previous = rows[max(0, i-2*window+1):max(0, i-window+1)]
        rs = [x['density_residual'] for x in current]
        es = [x['energy'] for x in current]
        logs = [math.log10(max(x, 1e-300)) for x in rs]
        meanx = (len(logs)-1)/2
        denom = sum((j-meanx)**2 for j in range(len(logs)))
        slope = sum((j-meanx)*v for j,v in enumerate(logs))/denom if denom else None
        old_best = min(x['density_residual'] for x in previous) if previous else None
        old_span = max(x['energy'] for x in previous)-min(x['energy'] for x in previous) if previous else None
        recurrence = {}
        for period in (2,3,4):
            if i+1 >= 3*period:
                recurrence[str(period)] = max(abs(rows[j]['energy']-rows[j-period]['energy'])
                                             for j in range(i-period+1,i+1))
        row.update(running_best_residual=best, window_best_residual=min(rs),
                   residual_envelope_ratio=min(rs)/old_best if old_best else None,
                   log_residual_slope=slope, energy_envelope=max(es)-min(es),
                   energy_envelope_ratio=(max(es)-min(es))/old_span if old_span else None,
                   scalar_energy_recurrence=recurrence,
                   iterations_since_one_percent_best_improvement=i-last_improvement,
                   divergence_trend=slope is not None and slope > 0)


def split_runs(rows):
    """CP receipts may concatenate several independently numbered SCF runs."""
    runs = []
    for row in rows:
        if not runs or row['iteration'] <= runs[-1][-1]['iteration']:
            runs.append([])
        runs[-1].append(row)
    return runs


def main():
    OUT.mkdir(exist_ok=True)
    unique, inventory = {}, []
    def add_run(run, source):
        scalar=[{k:r[k] for k in ('iteration','energy','delta_energy','density_residual')} for r in run]
        key=hashlib.sha256(json.dumps(scalar,sort_keys=True).encode()).hexdigest()
        if key not in unique:
            enrich(run)
            unique[key]=dict(sources=[],iterations=run,criteria_passed=run[-1]['delta_energy'] is not None and
                abs(run[-1]['delta_energy'])<=1e-12 and run[-1]['density_residual']<=1e-10)
        unique[key]['sources'].append(source)
    for milestone in ('10.2','13','14','14.1','15','16','17'):
        for path in sorted((ROOT/'validation'/('milestone-'+milestone)).rglob('*.receipt')):
            text = path.read_text()
            rows = parse(text)
            inventory.append(dict(path=str(path.relative_to(ROOT)), iterations=len(rows)))
            if not rows:
                continue
            for component, run in enumerate(split_runs(rows)):
                add_run(run,str(path.relative_to(ROOT))+'#'+str(component+1))
    log_inventory=[]
    log_pattern=re.compile(r'^(.*?)iteration=(\d+) energy=(\S+) delta=(\S+) residual=(\S+)\s*$')
    for milestone in ('13','14','14.1','15','16','17'):
        for path in sorted((ROOT/'validation'/('milestone-'+milestone)).rglob('*.log')):
            groups={}
            for line in path.read_text(errors='replace').splitlines():
                match=log_pattern.match(line)
                if match:
                    groups.setdefault(match[1],[]).append(dict(iteration=int(match[2]),energy=number(match[3]),
                        delta_energy=number(match[4]),density_residual=number(match[5]),**extra('')))
            count=0
            for prefix, rows in groups.items():
                for component, run in enumerate(split_runs(rows)):
                    add_run(run,str(path.relative_to(ROOT))+'#'+prefix+str(component+1))
                    count+=1
            log_inventory.append(dict(path=str(path.relative_to(ROOT)),runs=count))
    # Diagnostic sweep, never a production policy: require a sustained envelope
    # plateau AND scalar recurrence, tested against every converged control.
    candidates=[]
    for start in (48,64,80,96):
        for duration in (16,32):
            for recurrence_tolerance in (1e-6,1e-3,.1):
                hits=[]
                for key, run in unique.items():
                    for row in run['iterations']:
                        if (row['iteration'] >= start and row['density_residual'] > 1e-6 and
                            row['iterations_since_one_percent_best_improvement'] >= duration and
                            row['scalar_energy_recurrence'].get('2',math.inf) < recurrence_tolerance and
                            row['residual_envelope_ratio'] is not None and row['residual_envelope_ratio'] > .99):
                            hits.append(dict(trajectory=key, iteration=row['iteration'],
                                             converged_control=run['criteria_passed']))
                            break
                candidates.append(dict(start=start,duration=duration,recurrence_tolerance=recurrence_tolerance,hits=hits,
                                       false_terminations=sum(h['converged_control'] for h in hits)))
    result=dict(status='SCREENING_ONLY', unique_trajectories=len(unique),
                receipt_inventory=inventory,log_inventory=log_inventory, trajectories=unique, diagnostic_candidates=candidates,
                limitations=['Scalar recurrence does not prove density recurrence.',
                             'Missing orbital energies/DIIS fields are not reconstructed from hashes.',
                             'A retrospective zero-false-positive rule is not prospectively validated.'])
    (OUT/'trajectory-audit.json').write_text(json.dumps(result,indent=2,allow_nan=False)+'\n')
    print(json.dumps(dict(unique_trajectories=len(unique), parsed_receipts=sum(x['iterations']>0 for x in inventory),
                         receipt_count=len(inventory), total_unique_iterations=sum(len(x['iterations']) for x in unique.values()),
                         candidate_hits=[(c['start'],c['duration'],len(c['hits']),c['false_terminations']) for c in candidates])))


if __name__ == '__main__':
    main()
