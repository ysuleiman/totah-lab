import unittest
from audit_m17_1 import parse, split_runs, enrich, number


class AuditTest(unittest.TestCase):
    def test_optional_and_hex(self):
        self.assertIsNone(number('OptionalDouble.empty'))
        self.assertEqual(number('OptionalDouble[0.25]'), .25)
        self.assertEqual(number('0x1.0p-2'), .25)

    def test_modern_and_plain_receipts(self):
        for header in ('Iteration[number=', 'IterationReceipt[iterationNumber='):
            rows=parse(header+'1, totalEnergy=-1.0, deltaEnergy=OptionalDouble.empty, densityResidual=2.0, orbitalEnergies=[-1, 2], tracePS=2, extrapolated=false]')
            self.assertEqual(len(rows),1)
            self.assertEqual(rows[0]['homo_lumo_gap'],3)
            self.assertIsNone(rows[0]['diis_error'])
            self.assertFalse(rows[0]['diis_active'])

    def test_cp_iteration_restarts_are_separate_trajectories(self):
        rows=[dict(iteration=i,energy=-1.,density_residual=.1,delta_energy=None) for i in (1,2,3,1,2)]
        runs=split_runs(rows)
        self.assertEqual([len(x) for x in runs],[3,2])
        for run in runs:
            enrich(run)
            self.assertEqual(run[0]['iterations_since_one_percent_best_improvement'],0)

    def test_legacy_converged_row_is_not_lost(self):
        text='1\n-0x1p1\n-0x1p0\nUNAVAILABLE\n0x1p0\n'+'hash\n'*4+'false\nfalse\nDiisUpdate[errorMaximum=0.5, extrapolated=false]\n'
        text+='2\n-0x1p1\n-0x1p0\n0x0p0\n0x0p0\n'+'hash\n'*4+'true\ntrue\nCONVERGED_NO_UPDATE\n'
        rows=parse(text)
        self.assertEqual(len(rows),2)
        self.assertEqual(rows[-1]['density_residual'],0)
        self.assertIsNone(rows[-1]['diis_active'])


if __name__=='__main__': unittest.main()
