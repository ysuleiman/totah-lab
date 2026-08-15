package totah.lab.prometheus.execution;

import java.util.List;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;

/** Shared specification builder for the execution tests. */
final class ExecutionTestSpecs {

    private ExecutionTestSpecs() {
    }

    static CalculationSpecification withSoftware(String software) {
        return new CalculationSpecification(
                "exec-test-1",
                "executor routing fixture",
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA(),
                0,
                1,
                new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, software, "1.0"),
                List.of(),
                CalculationType.SINGLE_POINT,
                List.of("energy"),
                List.of("convergence=CONVERGED", "acceptance=ACCEPTED"),
                DatasetRole.DEVELOPMENT,
                CostEstimate.zero());
    }
}
