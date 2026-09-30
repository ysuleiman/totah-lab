"""80-digit independent incomplete-gamma and quadrature oracle for d-quartet Boys orders."""
from pathlib import Path
import hashlib
import mpmath as mp
mp.mp.dps=80
rows=['order,T,value']
for n in range(5,9):
 for text in ['0','1e-14','1e-5','.1','1','8','15.9999','16','16.0001','40','100','10000']:
  t=mp.mpf(text)
  v=1/mp.mpf(2*n+1) if t==0 else mp.gammainc(n+mp.mpf('.5'),0,t)/(2*t**(n+mp.mpf('.5')))
  q=mp.quad(lambda x:x**(2*n)*mp.exp(-t*x*x),[0,mp.mpf('.01'),mp.mpf('.1'),1])
  assert abs(v-q)<abs(v)*mp.mpf('1e-60')
  rows.append(f'{n},{float(t):.17g},{float(v):.17g}')
p=Path(__file__).resolve().parents[1]/'src/test/resources/totah/lab/aether/reference/def2-boys.csv'
p.write_text('\n'.join(rows)+'\n');print(hashlib.sha256(p.read_bytes()).hexdigest())
