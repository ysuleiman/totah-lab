#!/usr/bin/env python3
import external_cell_watchdog as w

class Clock:
 def __init__(self):self.value=0
 def __call__(self):return self.value
 def sleep(self,seconds):self.value+=seconds

class Environment:
 def __init__(self,complete_at=None,immutable=None):self.complete_at=complete_at;self.immutable=immutable if immutable is not None else {};self.started=False;self.discarded=False;self.timeout_evidence=[];self.clock=None
 def start(self):self.started=True
 def completed_result(self):return {'cell':'new'} if self.complete_at is not None and self.clock()>=self.complete_at else None
 def verify_completed_result(self,result):assert result=={'cell':'new'}
 def persist_timeout_evidence(self,elapsed):self.timeout_evidence.append(elapsed)
 def discard_entire_execution_environment(self):self.discarded=True

c=Clock();done={'old':'checksum-verified'};hung=Environment(immutable=done);hung.clock=c
result=w.run_attempt(hung,timeout_seconds=20,poll_seconds=1,clock=c,sleeper=c.sleep)
assert result['status']=='RUNTIME_TIMEOUT' and hung.discarded and hung.timeout_evidence==[20]
assert done=={'old':'checksum-verified'}

c=Clock();retry=Environment(complete_at=3,immutable=done);retry.clock=c
result=w.run_attempt(retry,timeout_seconds=20,poll_seconds=1,clock=c,sleeper=c.sleep)
assert result['status']=='COMPLETE' and not retry.discarded and done=={'old':'checksum-verified'}

# A timed-out environment is never reused; the clean retry is a distinct object.
assert hung is not retry and hung.discarded and retry.started

existing={'done':{'checksum':'verified'}};made=[]
def factory(cell,attempt):
 env=Environment(complete_at=None if (cell,attempt)==('hung',0) else 1,immutable=existing);env.clock=c;made.append((cell,attempt,env));return env
c=Clock()
out=w.run_cells(['done','hung','later'],factory,lambda cell:existing.get(cell),max_attempts=2,timeout_seconds=3,poll_seconds=1,clock=c,sleeper=c.sleep)
assert out['done']['status']=='REUSED_VERIFIED' and out['done']['attempts']==0
assert out['hung']['status']=='COMPLETE' and out['hung']['attempts']==2
assert out['later']['status']=='COMPLETE'
assert made[0][2].discarded and made[0][2] is not made[1][2]
assert existing=={'done':{'checksum':'verified'}}
print('EXTERNAL_CELL_WATCHDOG_TESTS_PASS=12')
