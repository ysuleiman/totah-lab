package totah.lab.prometheus.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.neural.CubicChebyshevGeometryEncoder;
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

class ExactFiniteObjectiveDifferentiatorTest {
    private static final double[] RADII = {.8, 1, 1.2};
    private static final double[] REFERENCES = {-1.0200566663601389, -1.1245397195465791, -1.1649352434400281};

    @Test void mixedStateMatchesFrozenStateAndDifferentiatesItsLaplacian() {
        ParameterVector parameters = CubicChebyshevGeometryEncoder.coldStart();
        var exact = new ExactFiniteObjectiveDifferentiator(RADII, REFERENCES, 100);
        QuantumCoordinates coordinates = coordinates(.23, -.17, .41, -.32, .28, -.54);
        var actual = exact.state(parameters, 1.4, coordinates);
        var expected = new GeometryConditionedHydrogenMoleculeState(1.4, parameters)
                .evaluateWithGeometryDerivatives(coordinates).stateEvaluation();

        assertThat(actual.value()).isCloseTo(expected.value().real(), within(1e-12));
        assertThat(actual.laplacian()).isCloseTo(expected.coordinateLaplacian().value().real(), within(1e-11));
        double step = 2e-6;
        for (int index : List.of(0, 5, 13, 19)) {
            var plus = exact.state(perturb(parameters, index, step), 1.4, coordinates);
            var minus = exact.state(perturb(parameters, index, -step), 1.4, coordinates);
            assertThat(actual.parameterDerivative()[index])
                    .isCloseTo((plus.value() - minus.value()) / (2 * step), within(2e-9));
            assertThat(actual.laplacianParameterDerivative()[index])
                    .isCloseTo((plus.laplacian() - minus.laplacian()) / (2 * step), within(2e-7));
        }
    }

    @Test void exactFiniteObjectiveGradientMatchesIndependentFiniteDifferences() {
        var exact = new ExactFiniteObjectiveDifferentiator(RADII, REFERENCES, 100);
        ParameterVector parameters = CubicChebyshevGeometryEncoder.coldStart();
        var analytic = exact.evaluate(parameters); double step = 2e-6, maximum = 0, square = 0;
        for (int index = 0; index < parameters.values().size(); index++) {
            double finite = (exact.evaluate(perturb(parameters, index, step)).loss()
                    - exact.evaluate(perturb(parameters, index, -step)).loss()) / (2 * step);
            double error = Math.abs(finite - analytic.gradient()[index]);
            maximum = Math.max(maximum, error); square += error * error;
        }
        assertThat(maximum).isLessThanOrEqualTo(3e-5);
        assertThat(Math.sqrt(square / parameters.values().size())).isLessThanOrEqualTo(1e-5);
        assertThat(analytic.stateEvaluations()).isEqualTo(5L * 100);
        assertThat(analytic.localEnergyEvaluations()).isEqualTo(analytic.stateEvaluations());
    }

    private static ParameterVector perturb(ParameterVector source, int index, double delta) {
        List<Double> values = new ArrayList<>(source.values()); values.set(index, values.get(index) + delta);
        return new ParameterVector(values);
    }
    private static QuantumCoordinates coordinates(double... xyz) {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0, xyz[0], xyz[1], xyz[2], SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1, xyz[3], xyz[4], xyz[5], SpinProjection.BETA)));
    }
    private static org.assertj.core.data.Offset<Double> within(double value) { return org.assertj.core.data.Offset.offset(value); }
}
