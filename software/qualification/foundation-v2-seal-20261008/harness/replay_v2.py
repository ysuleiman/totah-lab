"""Replay every new bounded v2 leaf in fresh JVMs against qualified full-report bytes."""
from pathlib import Path
import concurrent.futures

def run_v2_replays(f,source,out,build):
    base=f.test_base(source,build['classes']);java=base[0];classpath=base[base.index('--class-path')+1]
    prefix='software/qualification/'
    specs=[
      ('G05','SamG05AcceptanceTest',None,prefix+'g05-bounded-implementation-20261008/focused-replay-final/1/result.json'),
      ('G06','SamWaterTetrelAcceptanceTest',None,prefix+'g06-water-tetrel-20261008/focused-replay/1/result.json'),
      ('V09_V10','ResidueValidationAcceptanceTest#independentReplayCorpus','athena.residueReplayOutput',prefix+'v09-v10-implementation-20261008/independent-replay-2/replay-1.json'),
      ('V11','CbetaAcceptanceTest','athena.cbetaReplayOutput',prefix+'foundation-v2-closure-20261008/v11/independent-replay/replay-1.json'),
      ('V07','RestraintAcceptanceTest','v07.replay',prefix+'foundation-v2-closure-20261008/v07-public-contract/independent-replay-final/replay-1'),
      ('V08','OrderedSignAcceptanceTest','v08.replay',prefix+'foundation-v2-closure-20261008/v08-ordered-sign/independent-replay/replay-1')]
    def job(spec,n):
        name,cls,prop,golden=spec;work=out/'replay-v2'/f'{name}-{n}';work.mkdir(parents=True);tmp=work/'tmp';tmp.mkdir()
        multiple=name in ['V07','V08'];target=work/('result' if multiple else 'result.json')
        if prop:
            cmd=list(base);cmd.insert(1,'-D'+prop+'='+str(target));cmd.insert(1,'-Djava.io.tmpdir='+str(tmp))
            cmd+=['--select-method' if '#' in cls else '--select-class','totah.lab.daedalus.system.'+cls,'--details','summary','--disable-ansi-colors','--reports-dir',str(work/'junit')]
        else:
            cmd=[java,'-Xmx512m','-Djava.io.tmpdir='+str(tmp),'-cp',classpath,'totah.lab.daedalus.system.'+cls,str(work/'catalog'),str(target)]
        result=f.timed(cmd,work/'run.log',source);assert result['exitCode']==0,(name,n)
        hashes={}
        for suffix in ['-SER.json','-THR.json','-VAL.json'] if multiple else ['']:
            actual=Path(str(target)+suffix);expected=source/(golden+suffix)
            assert actual.read_bytes()==expected.read_bytes(),(name,n,suffix,'qualified golden mismatch')
            hashes[suffix or 'report']=f.digest(actual)
        f.write(work/'RESULT.json',{'status':'PASS','sha256':hashes,'command':result})
        return {'domain':name,'copy':n,'sha256':hashes,'seconds':result['seconds']}
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        futures=[pool.submit(job,spec,n) for spec in specs for n in [1,2]]
        results=[future.result() for future in futures]
    for spec in specs:
        pair=[r for r in results if r['domain']==spec[0]];assert pair[0]['sha256']==pair[1]['sha256']
    summary={'status':'PASS','independentJvmPairs':6,'fullReportPairs':10,'qualifiedGoldenByteComparisons':20,'results':results}
    f.write(out/'V2_REPLAY.json',summary);return summary
