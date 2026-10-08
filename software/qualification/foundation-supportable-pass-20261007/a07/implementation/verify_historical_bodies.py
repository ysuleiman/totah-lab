from pathlib import Path
import subprocess,hashlib,json
base='0567e44f9';root=Path.cwd();q=root/'software/qualification/foundation-supportable-pass-20261007/a07/implementation'
checks={}
def method(s,marker):
 start=s.index(marker);brace=s.index('{',start);depth=1;i=brace+1
 # Existing methods contain balanced braces in comments/strings as well.
 while depth:
  depth+=(s[i]=='{')-(s[i]=='}');i+=1
 return s[start:i]
for path,marker in [('software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclOccurrenceMatcher.java','    static SubstructureMatcher.Result match(String query,'),('software/modules/athena-openchemlib/src/main/java/totah/lab/athena/design/backend/ocl/OclMolecularBackend.java','    public SubstructureMatcher.Result match(String query,')]:
 old=subprocess.check_output(['git','show',base+':'+path],text=True);new=(root/path).read_text();a=method(old,marker);b=method(new,marker);assert a==b,path;checks[path]={'historicalMethodUnchanged':True,'sha256':hashlib.sha256(a.encode()).hexdigest()}
(q/'HISTORICAL_METHOD_PRESERVATION.json').write_text(json.dumps({'baseline':base,'checks':checks},indent=2)+'\n');print('Historical two-argument implementation bodies unchanged')
