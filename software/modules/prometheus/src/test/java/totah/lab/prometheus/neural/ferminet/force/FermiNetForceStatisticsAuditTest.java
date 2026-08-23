package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;

final class FermiNetForceStatisticsAuditTest {
    @Test void handComputableEqualChainMeanVarianceAndStandardError()
            throws Exception {
        Class<?> type = Class.forName(SwctFermiNetForceEstimator.class.getName()
                + "$ComponentStatistics");
        Method compute = type.getDeclaredMethod("compute", double[].class,
                int[].class, int.class, int.class);
        compute.setAccessible(true);
        Object value = compute.invoke(null, new double[]{1,3,5,7},
                new int[]{0,0,1,1}, 2, 4);
        Method mean = type.getDeclaredMethod("mean"); mean.setAccessible(true);
        Method variance = type.getDeclaredMethod("variance"); variance.setAccessible(true);
        Method se = type.getDeclaredMethod("chainStandardError"); se.setAccessible(true);
        assertEquals(4.0, (double) mean.invoke(value), 0.0);
        assertEquals(20.0 / 3.0, (double) variance.invoke(value), 1e-15);
        assertEquals(2.0, (double) se.invoke(value), 0.0);
    }

    @Test void unequalFiniteChainLengthsRefuseMisleadingStandardError()
            throws Exception {
        Class<?> type = Class.forName(SwctFermiNetForceEstimator.class.getName()
                + "$ComponentStatistics");
        Method compute = type.getDeclaredMethod("compute", double[].class,
                int[].class, int.class, int.class);
        compute.setAccessible(true);
        Object value = compute.invoke(null, new double[]{1,Double.NaN,5,7},
                new int[]{0,0,1,1}, 2, 3);
        Method se = type.getDeclaredMethod("chainStandardError"); se.setAccessible(true);
        assertTrue(Double.isNaN((double) se.invoke(value)));
    }

    @Test void marginalImportanceWeightEssIsHandComputable() throws Exception {
        Class<?> type = Class.forName(
                FermiNetCorrelatedFiniteDifferenceForceReference.class.getName()
                + "$LogWeights");
        var constructor = type.getDeclaredConstructor(); constructor.setAccessible(true);
        Object weights = constructor.newInstance();
        Method add = type.getDeclaredMethod("add", double.class); add.setAccessible(true);
        Method ess = type.getDeclaredMethod("ess"); ess.setAccessible(true);
        add.invoke(weights, 0.0); add.invoke(weights, Math.log(2.0));
        assertEquals(9.0 / 5.0, (double) ess.invoke(weights), 1e-15);
    }
}
