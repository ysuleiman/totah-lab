package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

final class FermiNetCorrelatedFiniteDifferenceForceReferenceTest {

    @TempDir Path temporary;

    @Test
    void evaluatesAllWaterComponentsWithCommonConfigurationsAndFrozenParameters()
            throws Exception {
        Molecule molecule = water();
        FermiNetV1Configuration configuration = FermiNetV1Configuration.testFixture();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(configuration, molecule);
        FermiNetV1State state = new FermiNetV1State(
                molecule, configuration, FermiNetParameters.initialize(layout, 44017L));
        String parameters = FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state));
        List<QuantumCoordinates> samples = List.of(
                coordinates(0.00), coordinates(0.02),
                coordinates(-0.01), coordinates(0.03));

        Path file = temporary.resolve("configurations.csv");
        var written = FermiNetCorrelatedFdConfigurationFile.write(file, samples, 2);
        assertEquals(written,
                FermiNetCorrelatedFdConfigurationFile.inspect(file, 2));
        var result = new FermiNetCorrelatedFiniteDifferenceForceReference()
                .evaluate(state, file, 2);

        assertEquals(1.0e-3, result.stepBohr(), 0.0);
        assertEquals(4, result.dataset().sampleCount());
        assertEquals(2, result.dataset().walkerCount());
        assertEquals(2, result.dataset().retainedPerWalker());
        assertEquals(9, result.components().size());
        for (var component : result.components()) {
            assertEquals(parameters, component.parameterChecksum());
            assertNotEquals(component.plusGeometryChecksum(),
                    component.minusGeometryChecksum());
            assertEquals(component.forceHartreePerBohr(),
                    -(component.energyPlusHartree() - component.energyMinusHartree())
                            / (2.0 * result.stepBohr()), 1.0e-12);
            double rawMean = java.util.Arrays.stream(component.rawForceSamples())
                    .average().orElseThrow();
            assertEquals(component.forceHartreePerBohr(), rawMean, 1.0e-10);
            assertTrue(Double.isFinite(component.forceStandardError()));
            assertTrue(component.plusEffectiveSampleSize() > 0.0);
            assertTrue(component.minusEffectiveSampleSize() > 0.0);
        }
        assertEquals(parameters, FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state)));
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {
                {0.18, 0.11, 0.27}, {-0.31, 0.42, -0.16},
                {0.57, -0.28, 0.33}, {-0.63, -0.37, 0.21},
                {0.24, 0.71, -0.45}, {-0.22, -0.15, -0.38},
                {0.36, -0.54, 0.19}, {-0.48, 0.26, 0.51},
                {0.69, 0.18, -0.24}, {-0.12, 0.61, 0.37}
        };
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    i, xyz[i][0] + shift, xyz[i][1], xyz[i][2],
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(particles);
    }

    private static Molecule water() {
        return new Molecule(
                "ferminet-v1-water",
                List.of(
                        new NuclearCenter(0, "O", new NuclearCharge(8),
                                new CartesianPosition(0.0, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(1.7952398191849366, 0.0, 0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(2, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.46464225035067114,
                                        1.7340684963325879, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }
}
