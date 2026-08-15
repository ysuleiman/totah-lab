package totah.lab.prometheus.variational;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class VariationalArchitectureTest {

    @Test
    void coordinatesAndParametersAreDeeplyImmutable() {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>(List.of(
                new QuantumCoordinates.ParticleCoordinate(0, 0.0, 0.0, 0.0, SpinProjection.ALPHA)));
        QuantumCoordinates coordinates = new QuantumCoordinates(particles);
        List<Double> values = new ArrayList<>(List.of(1.0, 2.0));
        ParameterVector parameters = new ParameterVector(values);

        particles.clear(); values.clear();

        assertThat(coordinates.particles()).hasSize(1);
        assertThat(parameters.values()).containsExactly(1.0, 2.0);
        assertThatThrownBy(() -> parameters.values().add(3.0))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void collocationPointsRequireOrderedCoordinatesAndProvenance() {
        assertThatThrownBy(() -> new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(1, 0, 0, 0, SpinProjection.BETA))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("contiguous");
        assertThatThrownBy(() -> new CollocationPointSet(List.of(), "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void resultCannotClaimConvergenceWhileFailingAGate() {
        assertThatThrownBy(() -> new VariationalResult("b".repeat(64),
                new ParameterVector(List.of()), -1.0, true, List.of("normalization"),
                List.of("antisymmetry"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failed gates");
    }
}
