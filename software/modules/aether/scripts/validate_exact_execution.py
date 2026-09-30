"""Serial, bounded-heap M14 driver. Never writes M13 receipts or starts concurrent scientific JVMs."""
from pathlib import Path
import argparse, hashlib, json, os, subprocess, time, xml.etree.ElementTree as ET

root=Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser()
p.add_argument('--mode',choices=['RHF','LDA','CP','density','contractions','historical'],required=True)
p.add_argument('--systems',nargs='+',required=True)
p.add_argument('--policy',choices=['DIRECT_EXACT','PACKED_REFERENCE'],default='DIRECT_EXACT')
p.add_argument('--heap',default='512m')
p.add_argument('--label',default='validation')
p.add_argument('--output-subdirectory',default='parallel')
a=p.parse_args()
evidence=root/'validation/milestone-14'
out=evidence/a.output_subdirectory;out.mkdir(parents=True,exist_ok=True)
java='/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home/bin/java'
cp=None
for report in (root/'target/surefire-reports').glob('TEST-*.xml'):
    for prop in ET.parse(report).findall('./properties/property'):
        if prop.get('name')=='java.class.path':cp=prop.get('value');break
    if cp:break
if not cp:raise SystemExit('Compile and run a focused test to establish the classpath first')
for name in a.systems:
    label=f'{a.label}-{a.mode}-{name}-{a.policy}'
    args=[java,'-Xmx'+a.heap,'-Daether.exact.output='+str(out),'-cp',cp,'totah.lab.aether.ExactExecutionProbe',a.mode,name,a.policy]
    if a.mode=='historical':
        method,system=name.split(':')
        args=[java,'-Xmx'+a.heap,'-cp',cp,'totah.lab.aether.Def2ReceiptReplay',method,system]
    print('START',label,flush=True);start=time.monotonic()
    with (out/(label+'.log')).open('w') as stdout,(out/(label+'.stderr')).open('w') as stderr:
        with subprocess.Popen(args,cwd=root,stdout=stdout,stderr=stderr) as child:
            _,status,usage=os.wait4(child.pid,0);child.returncode=os.waitstatus_to_exitcode(status)
    record={'command':args,'exit_code':child.returncode,'elapsed_seconds':time.monotonic()-start,
            'peak_rss_bytes':int(usage.ru_maxrss),'heap':a.heap}
    if a.mode=='historical' and child.returncode==0:
        key='validation/milestone-13/'+system+'-'+{'RHF':'rhf','LDA':'lda','CP':'cp'}[method]+'.receipt'
        expected=json.loads((evidence/'m13-frozen-manifest.json').read_text())[key]
        record['receipt_sha256']=hashlib.sha256((out/(label+'.log')).read_bytes()).hexdigest()
        record['historical_receipt_unchanged']=record['receipt_sha256']==expected
        if not record['historical_receipt_unchanged']:child.returncode=1;record['exit_code']=1
    (out/(label+'.json')).write_text(json.dumps(record,indent=2)+'\n')
    print('FINISH',label,child.returncode,record['elapsed_seconds'],flush=True)
    if child.returncode:raise SystemExit(child.returncode)
