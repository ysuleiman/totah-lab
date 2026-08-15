package totah.lab.prometheus.variational.force;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

class HydrogenMoleculeSpaceWarpTest {
    @Test
    void normalizedInverseFourthPowerWeightsHaveRequiredLimitsAndSymmetry() {
        var center = electron(0, 0.2, -0.1, 0);
        var reflected = electron(0, 0.2, -0.1, -0.31);
        var original = electron(0, 0.2, -0.1, 0.31);

        var centerWeight = HydrogenMoleculeSpaceWarp.weightAndDerivative(center, 0.7);
        var originalWeight = HydrogenMoleculeSpaceWarp.weightAndDerivative(original, 0.7);
        var reflectedWeight = HydrogenMoleculeSpaceWarp.weightAndDerivative(reflected, 0.7);

        assertThat(centerWeight.weightAtPositiveNucleus()).isEqualTo(0.5);
        assertThat(originalWeight.weightAtPositiveNucleus()
                + reflectedWeight.weightAtPositiveNucleus()).isCloseTo(1.0,
                        org.assertj.core.data.Offset.offset(2e-15));
        assertThat(originalWeight.zDerivative()).isCloseTo(reflectedWeight.zDerivative(),
                org.assertj.core.data.Offset.offset(2e-15));
    }

    @Test
    void exactJacobianMatchesIndependentCoordinateDifference() {
        double radius = 1.4, displacement = 1e-3, coordinateStep = 1e-6;
        var coordinates = new QuantumCoordinates(List.of(
                electron(0, 0.37, -0.21, 0.18), electron(1, -0.44, 0.31, -0.63)));
        double numericalJacobian = 1;
        for (int particle = 0; particle < 2; particle++) {
            var plus = replaceZ(coordinates, particle,
                    coordinates.particles().get(particle).zBohr() + coordinateStep);
            var minus = replaceZ(coordinates, particle,
                    coordinates.particles().get(particle).zBohr() - coordinateStep);
            double transformedPlus = HydrogenMoleculeSpaceWarp.transform(plus, radius, displacement)
                    .coordinates().particles().get(particle).zBohr();
            double transformedMinus = HydrogenMoleculeSpaceWarp.transform(minus, radius, displacement)
                    .coordinates().particles().get(particle).zBohr();
            numericalJacobian *= (transformedPlus - transformedMinus) / (2 * coordinateStep);
        }

        assertThat(HydrogenMoleculeSpaceWarp.transform(coordinates, radius, displacement).jacobian())
                .isCloseTo(numericalJacobian, org.assertj.core.data.Offset.offset(2e-10));
    }

    @Test
    void centeredBondWarpHasNoTransverseDisplacementAndRespectsInterchange() {
        var original = new QuantumCoordinates(List.of(electron(0, 0.2, -0.1, 0.31)));
        var reflected = new QuantumCoordinates(List.of(electron(0, 0.2, -0.1, -0.31)));
        var moved = HydrogenMoleculeSpaceWarp.transform(original, 1.4, 1e-3).coordinates().particles().get(0);
        var reflectedMoved = HydrogenMoleculeSpaceWarp.transform(reflected, 1.4, 1e-3)
                .coordinates().particles().get(0);

        assertThat(moved.xBohr()).isEqualTo(0.2);
        assertThat(moved.yBohr()).isEqualTo(-0.1);
        assertThat(moved.zBohr() - 0.31).isCloseTo(-(reflectedMoved.zBohr() + 0.31),
                org.assertj.core.data.Offset.offset(1e-16));
    }

    private static QuantumCoordinates replaceZ(QuantumCoordinates coordinates, int index, double z) {
        var particles = new java.util.ArrayList<>(coordinates.particles());
        var old = particles.get(index);
        particles.set(index, electron(index, old.xBohr(), old.yBohr(), z));
        return new QuantumCoordinates(particles);
    }

    private static QuantumCoordinates.ParticleCoordinate electron(int index, double x, double y, double z) {
        return new QuantumCoordinates.ParticleCoordinate(index, x, y, z,
                index == 0 ? SpinProjection.ALPHA : SpinProjection.BETA);
    }
}
