import unittest, tempfile, json
from pathlib import Path
from unittest.mock import patch
import foundation as f

class HarnessTests(unittest.TestCase):
    def test_known_leaf_is_narrow(self):
        s=f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/ClPheCandidateRules.java'],2)
        self.assertEqual(s['tier'],2);self.assertIn('totah.lab.daedalus.system.BatchCCurrentPipelineTest',s['classes']);self.assertFalse(any('WaterBridge' in c for c in s['classes']))
    def test_unknown_change_escalates(self):self.assertEqual(3,f.select(['unknown/new.java'])['tier'])
    def test_empty_scope_escalates(self):self.assertEqual(3,f.select([])['tier'])
    def test_shared_geometry_escalates(self):self.assertEqual(3,f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/ContinuousGeometryRules.java'])['tier'])
    def test_source_hash_mismatch_escalates(self):
        with patch.object(f,'digest',return_value='changed'):self.assertEqual(3,f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/ClPheCandidateRules.java'])['tier'])
    def test_focused_excludes_governed_pipeline(self):
        s=f.select(['software/modules/athena/src/main/java/totah/lab/athena/system/rules/ClPheCandidateRules.java'],1);self.assertFalse(any('CurrentPipeline' in c for c in s['classes']))
    def test_selection_deterministic(self):
        a=['software/modules/athena/src/main/java/totah/lab/athena/system/rules/ClPheCandidateRules.java','software/modules/athena/src/main/java/totah/lab/athena/system/rules/SourceSiteMetadata.java'];self.assertEqual(f.select(a),f.select(a[::-1]))
    def test_cache_key_binds_changed_bytes(self):self.assertNotEqual(f.hash_object({'source':'a'}),f.hash_object({'source':'b'}))
    def test_cache_key_binds_configuration(self):self.assertNotEqual(f.hash_object({'compiler':'a'}),f.hash_object({'compiler':'b'}))
    def test_cache_key_ignores_dictionary_order(self):self.assertEqual(f.hash_object({'a':1,'b':2}),f.hash_object({'b':2,'a':1}))
    def test_missing_test_report_fails(self):
        with tempfile.TemporaryDirectory() as d:
            with self.assertRaises(AssertionError):f.results(Path(d),['expected.Test'])
    def test_duplicate_test_identity_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'job/junit';p.mkdir(parents=True);p.joinpath('TEST-one.xml').write_text('<testsuite><testcase classname="C" name="a"/><testcase classname="C" name="a"/></testsuite>')
            with self.assertRaises(RuntimeError):f.results(Path(d),['C'])
    def test_skipped_is_not_success(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'job/junit';p.mkdir(parents=True);p.joinpath('TEST-one.xml').write_text('<testsuite><testcase classname="C" name="a"><skipped/></testcase></testsuite>');self.assertTrue(f.results(Path(d),['C'])['failuresErrorsOrSkips'])
    def test_compiled_cache_reuse_and_tamper_rejection(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);one=root/'one';two=root/'two';three=root/'three'
            for out in [one,two,three]:out.mkdir()
            inputs=({'classes':'OLD'},['/bin/true','-d','OLD'],{'A.java':'abc'},{},'javac21')
            with patch.object(f,'compiler_inputs',return_value=inputs),patch.object(f,'timed',return_value={'seconds':0.01,'exitCode':0}) as compiler:
                a=f.build(root,one,root/'cache');b=f.build(root,two,root/'cache');self.assertFalse(a['cacheHit']);self.assertTrue(b['cacheHit']);self.assertEqual(compiler.call_count,1)
                Path(b['classes']).joinpath('injected.class').write_bytes(b'changed')
                with self.assertRaisesRegex(RuntimeError,'corruption'):f.build(root,three,root/'cache')
    def test_changed_source_cannot_reuse_compiled_cache(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);one=root/'one';two=root/'two';one.mkdir();two.mkdir()
            with patch.object(f,'timed',return_value={'seconds':0.01,'exitCode':0}):
                with patch.object(f,'compiler_inputs',return_value=({'classes':'OLD'},['javac','OLD'],{'A':'old'},{},'v')):a=f.build(root,one,root/'cache')
                with patch.object(f,'compiler_inputs',return_value=({'classes':'OLD'},['javac','OLD'],{'A':'changed'},{},'v')):b=f.build(root,two,root/'cache')
                self.assertNotEqual(a['cacheKey'],b['cacheKey']);self.assertFalse(b['cacheHit'])
    def test_release_never_reuses_development_cache(self):
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);one=root/'one';two=root/'two';one.mkdir();two.mkdir()
            with patch.object(f,'compiler_inputs',return_value=({'classes':'OLD'},['javac','OLD'],{'A':'same'},{},'v')),patch.object(f,'timed',return_value={'seconds':0.01,'exitCode':0}) as compiler:
                f.build(root,one,root/'cache');b=f.build(root,two,root/'cache',clean=True);self.assertFalse(b['cacheHit']);self.assertEqual(compiler.call_count,2)
if __name__=='__main__':unittest.main()
