"""Representative setup measurements, separate from qualification; no production instrumentation."""
from pathlib import Path
import csv, json, subprocess, sys, time, shutil
import foundation as f

def main():
    build=f.read(sys.argv[1]);out=Path(sys.argv[2]).resolve();out.mkdir(exist_ok=False);source=Path(build['source']);jdk=Path(f.read(f.BASE/'compile-command.json')[0]).parent
    bb=Path('/Users/yazan/.m2/repository/net/bytebuddy/byte-buddy/1.15.11/byte-buddy-1.15.11.jar');classes=out/'agent-classes';classes.mkdir()
    subprocess.run([str(jdk/'javac'),'-cp',str(bb),'-d',str(classes),str(f.HERE/'ProfileAgent.java')],check=True)
    manifest=out/'AGENT.MF';manifest.write_text('Premain-Class: foundation.harness.ProfileAgent\nClass-Path: '+bb.as_uri()+'\n\n');jar=out/'profile-agent.jar';subprocess.run([str(jdk/'jar'),'cfm',str(jar),str(manifest),'-C',str(classes),'.'],check=True)
    base=f.test_base(source,build['classes']);java=base[0];cp=base[base.index('--class-path')+1];launch=[]
    for i in range(3):launch.append(f.timed([java,'-version'],out/('startup'+str(i)+'.log'),source)['seconds'])
    runs=[];operations=[]
    for name in ['DirectAssessmentExecutionAcceptanceTest','ClPheAcceptanceTest','FoundationV1ConsumerAcceptanceTest']:
        d=out/name;d.mkdir();cmd=[java,'-Xmx512m','-javaagent:'+str(jar)+'='+str(d/'operations.tsv'),'-cp',cp,'totah.lab.daedalus.system.'+name,str(d/'catalog'),str(d/'result.json')]
        r=f.timed(cmd,d/'run.log',source);assert r['exitCode']==0,name;r['name']=name;runs.append(r)
        for row in csv.DictReader((d/'operations.tsv').open(),delimiter='\t'):row['scenario']=name;operations.append(row)
    result={'measurementOnly':True,'productionClassesModified':False,'agentSourceSha256':f.digest(f.HERE/'ProfileAgent.java'),'byteBuddyExistingDependency':{'path':str(bb),'sha256':f.digest(bb)},'javaVersionLaunchSeconds':launch,'compileSeconds':build['compile']['seconds'],'immutableBuildKey':build['cacheKey'],'runs':runs,'operations':sorted(operations,key=lambda x:-float(x['exclusive_seconds'])),'caveat':'Instrumentation adds overhead. Inclusive times overlap; exclusive values partition only measured methods. JVM -version is a startup lower bound, not JUnit discovery time. No eligibility decisions or mutable evidence are cached.'}
    f.write(out/'PROFILE.json',result);print(json.dumps({'profileScenarios':len(runs),'operations':len(operations),'startupSeconds':launch}))
if __name__=='__main__':main()
