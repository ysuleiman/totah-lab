import math,unittest
from m17_metrics import summarize,spearman,ranks,classify,d3_change,sign_correct,diagnosed_iteration_cap

class MetricsTest(unittest.TestCase):
    def test_signed_errors(self):
        s=summarize([-3.,-2.,-1.],[-2.,-4.,-1.])
        self.assertEqual(s['MAE'],1.);self.assertAlmostEqual(s['RMSE'],math.sqrt(5/3))
        self.assertEqual(s['MAX_ERROR'],2.);self.assertAlmostEqual(s['BIAS'],-1/3)
    def test_rank_reversal(self):self.assertEqual(spearman([1,2,3],[3,2,1]),-1)
    def test_ties(self):self.assertEqual(ranks([4,1,1,9]),[3,1.5,1.5,4])
    def test_undefined(self):
        self.assertEqual(spearman([1,2],[2,1]),-1);self.assertIsNone(spearman([1],[2]))
        self.assertIsNone(spearman([1,1,1],[2,3,4]))
    def test_nonfinite(self):
        with self.assertRaises(ValueError):summarize([1.],[math.nan])
    def test_missing_fail_closed(self):self.assertEqual(classify({'n':0},[],0,True),'INSUFFICIENT_EVIDENCE')
    def test_small_sample_not_quantitative(self):
        r=[-2.,-1.];self.assertEqual(classify(summarize(r,r),r,2,False),'QUALITATIVE_ONLY')
    def test_wrong_sign(self):
        r=[-2.,-1.];self.assertEqual(classify(summarize(r,[2.,1.]),r,2,False),'UNRELIABLE')
    def test_near_zero_indeterminate(self):self.assertIsNone(sign_correct(-.001,.001))
    def test_d3_all_three(self):
        self.assertEqual(d3_change(-3,-1,-2),'IMPROVES')
        self.assertEqual(d3_change(-3,-3,-4),'WORSENS')
        self.assertEqual(d3_change(-3,-2,-2.001),'NEUTRAL')
    def test_iteration_cap_is_not_a_success_or_harness_failure(self):
        self.assertTrue(diagnosed_iteration_cap(['MAX_ITERATIONS']+['CONVERGED']*5))
        for statuses in (['CONVERGED']*6,['MAX_ITERATIONS']*5,
                         ['MAX_ITERATIONS',None]+['CONVERGED']*4,
                         ['MAX_ITERATIONS','NUMERICAL_FAILURE']+['CONVERGED']*4):
            self.assertFalse(diagnosed_iteration_cap(statuses))

if __name__=='__main__':unittest.main()
