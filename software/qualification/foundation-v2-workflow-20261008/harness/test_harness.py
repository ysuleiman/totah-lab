import unittest, importlib.util, json
from pathlib import Path
from unittest.mock import patch
import foundation as f
# Preserve every existing cache/result/harness assertion, using the frozen module.
spec=importlib.util.spec_from_file_location('v1_tests',f.REPO/'software/qualification/foundation-v1-rc-20261008/harness/test_harness.py')
legacy=importlib.util.module_from_spec(spec)
# Legacy tests expect compiler/run utilities. Selection is the reviewed v2 selector.
for name in ['compiler_inputs','timed','build','results']:
    setattr(f,name,getattr(f.engine,name))
# The cache tests patch module globals: retain their original module to avoid
# accidentally testing mocked wrapper aliases instead of the actual implementation.
import sys
with patch.dict(sys.modules,{'foundation':f.engine}):spec.loader.exec_module(legacy)
HarnessTests=legacy.HarnessTests
class V2SelectionTests(unittest.TestCase):
    def test_exact_g06_change_preserves_all_350_test_classes(self):
        m=f.read(f.META);p=m['qualifiedChangePackages']['G06_705a2d78e'];s=f.select(list(p['pins']));self.assertEqual(2,s['tier']);self.assertEqual(set(p['classes']),set(s['classes']))
    def test_shared_water_change_alone_escalates(self):self.assertEqual(3,f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/WaterIdentity.java'])['tier'])
    def test_dispatch_change_alone_escalates(self):self.assertEqual(3,f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/RuleRegistry.java'])['tier'])
    def test_partial_qualified_package_cannot_exempt_shared_change(self):
        p=list(f.read(f.META)['qualifiedChangePackages']['G06_705a2d78e']['pins']);self.assertEqual(3,f.select(p[:-1])['tier'])
    def test_modified_shared_bytes_escalate_even_in_exact_package(self):
        p=f.read(f.META)['qualifiedChangePackages']['G06_705a2d78e']['pins']
        with patch.object(f,'digest',return_value='different'):self.assertEqual(3,f.select(list(p))['tier'])
    def test_unknown_new_test_escalates(self):
        with patch.object(f.engine,'inventory',return_value=f.engine.inventory()+['unreviewed.Consumer']):self.assertEqual(3,f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/SamWaterTetrelRules.java'])['tier'])
    def test_selected_unchanged_helpers_do_not_expand_to_all_consumers(self):
        s=f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/SamWaterTetrelRules.java']);self.assertEqual(12,len(s['classes']));self.assertFalse(any('MetPheSurveyCurrent' in c for c in s['classes']))
    def test_changed_fixture_retains_true_transitive_consumer(self):
        s=f.select(['software/modules/daedalus/src/test/java/totah/lab/daedalus/system/SamG05AcceptanceTest.java']);self.assertIn('totah.lab.daedalus.system.SamWaterTetrelCurrentPipelineTest',s['classes'])
    def test_three_governed_classes_are_isolation_reviewed(self):
        classes=f.read(f.META)['qualifiedChangePackages']['G06_705a2d78e']['classes'];a=f.isolation_eligible(f.REPO,classes);self.assertEqual(3,len(a))
    def test_isolation_hash_drift_disables_parallel(self):
        with patch.object(f,'digest',return_value='changed'):self.assertEqual([],f.isolation_eligible(f.REPO,['totah.lab.daedalus.system.SamWaterTetrelCurrentPipelineTest']))
if __name__=='__main__':unittest.main()
