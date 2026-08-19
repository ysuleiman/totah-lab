package totah.lab.prometheus.neural.ferminet.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.variational.QuantumCoordinates;

final class FermiNetDirectionalDerivativeTest {

    @Test
    void frozenIteration17NetworkMatchesFiniteDifferencesOnFixedConfigurations()
            throws Exception {
        Path root = Path.of("../../..").toAbsolutePath().normalize();
        Path checkpointFile = root.resolve("artifacts/prometheus/h2o/ferminet/sr/"
                + "qualified-best-7988-n1024-checkpointed-8step/iteration-017/"
                + "continuation-checkpoint.bin");
        Path configurationsFile = root.resolve("artifacts/prometheus/h2o/ferminet/"
                + "forces/correlated-fd-iteration-017-n1024/configurations.csv");
        Assumptions.assumeTrue(Files.exists(checkpointFile)
                && Files.exists(configurationsFile));

        Molecule molecule = water();
        FermiNetOptimizationCheckpoint checkpoint =
                FermiNetOptimizationCheckpoint.read(checkpointFile);
        assertEquals("dfa88d8f0714ea9f9cf45fd3f735a0b198f1f5eef42e6b0a96f2dc7e40341d20",
                checkpoint.parameterChecksum());
        FermiNetV1Configuration configuration = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(configuration, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, configuration,
                FermiNetParameters.fromArray(layout, checkpoint.parameters()));
        List<QuantumCoordinates> configurations = new ArrayList<>();
        FermiNetCorrelatedFdConfigurationFile.forEach(
                configurationsFile, 64, (sample, chain, retained, coordinates) -> {
                    if (sample < 3) configurations.add(coordinates);
                });
        assertEquals(3, configurations.size());

        double[] nuclear = {0.07, -0.03, 0.02, -0.04, 0.01, 0.05,
                -0.03, 0.02, -0.01};
        double[] electron = direction(30);
        double h = 1.0e-5;
        double maxLogAbsolute = 0.0;
        double maxLogRelative = 0.0;
        double maxLaplacianAbsolute = 0.0;
        double maxLaplacianRelative = 0.0;
        for (QuantumCoordinates coordinates : configurations) {
            var actual = FermiNetStateAccess.directional(state, coordinates,
                    new FermiNetStateAccess.NuclearDirection(nuclear),
                    new FermiNetStateAccess.ElectronDirection(electron));
            FermiNetV1State plusState = FermiNetStateAccess.withGeometry(
                    state, moveNuclei(molecule, nuclear, h));
            FermiNetV1State minusState = FermiNetStateAccess.withGeometry(
                    state, moveNuclei(molecule, nuclear, -h));
            var plus = FermiNetStateAccess.spatial(
                    plusState, moveElectrons(coordinates, electron, h));
            var minus = FermiNetStateAccess.spatial(
                    minusState, moveElectrons(coordinates, electron, -h));
            double expectedLog = (plus.logAbsoluteWavefunction()
                    - minus.logAbsoluteWavefunction()) / (2.0 * h);
            double expectedLaplacian = (plus.laplacianOverWavefunction()
                    - minus.laplacianOverWavefunction()) / (2.0 * h);
            double logError = Math.abs(actual.directionalLogAbsoluteWavefunction()
                    - expectedLog);
            double laplacianError = Math.abs(actual.directionalLaplacianOverWavefunction()
                    - expectedLaplacian);
            maxLogAbsolute = Math.max(maxLogAbsolute, logError);
            maxLogRelative = Math.max(maxLogRelative,
                    relative(logError, expectedLog));
            maxLaplacianAbsolute = Math.max(maxLaplacianAbsolute, laplacianError);
            maxLaplacianRelative = Math.max(maxLaplacianRelative,
                    relative(laplacianError, expectedLaplacian));
            assertEquals(expectedLog, actual.directionalLogAbsoluteWavefunction(), 2.0e-6);
            assertEquals(expectedLaplacian,
                    actual.directionalLaplacianOverWavefunction(), 2.0e-4);
        }
        System.out.printf(java.util.Locale.ROOT,
                "FERMINET_ITERATION17_DIRECTIONAL_LOG_MAX_ABS_ERROR=%.16e%n"
                        + "FERMINET_ITERATION17_DIRECTIONAL_LOG_MAX_REL_ERROR=%.16e%n"
                        + "FERMINET_ITERATION17_DIRECTIONAL_LAPLACIAN_MAX_ABS_ERROR=%.16e%n"
                        + "FERMINET_ITERATION17_DIRECTIONAL_LAPLACIAN_MAX_REL_ERROR=%.16e%n",
                maxLogAbsolute, maxLogRelative,
                maxLaplacianAbsolute, maxLaplacianRelative);
    }

    private static double[] direction(int length) {
        double[] result = new double[length];
        for (int i = 0; i < length; i++) result[i] = 0.006 * ((i % 9) - 4);
        return result;
    }

    private static double relative(double error, double expected) {
        return error / Math.max(1.0e-12, Math.abs(expected));
    }

    private static Molecule moveNuclei(Molecule molecule, double[] direction, double scale) {
        List<NuclearCenter> nuclei = new ArrayList<>();
        for (int nucleus = 0; nucleus < molecule.nuclei().size(); nucleus++) {
            NuclearCenter old = molecule.nuclei().get(nucleus);
            CartesianPosition p = old.position().inBohr();
            nuclei.add(new NuclearCenter(old.orderedIndex(), old.element(), old.charge(),
                    new CartesianPosition(
                            p.x() + scale * direction[3 * nucleus],
                            p.y() + scale * direction[3 * nucleus + 1],
                            p.z() + scale * direction[3 * nucleus + 2],
                            LengthUnit.BOHR)));
        }
        return new Molecule(molecule.moleculeId(), nuclei, molecule.charge(),
                molecule.electrons(), molecule.spin());
    }

    private static QuantumCoordinates moveElectrons(
            QuantumCoordinates coordinates, double[] direction, double scale) {
        List<QuantumCoordinates.ParticleCoordinate> particles = new ArrayList<>();
        for (int electron = 0; electron < coordinates.particles().size(); electron++) {
            var old = coordinates.particles().get(electron);
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    electron,
                    old.xBohr() + scale * direction[3 * electron],
                    old.yBohr() + scale * direction[3 * electron + 1],
                    old.zBohr() + scale * direction[3 * electron + 2],
                    old.spin()));
        }
        return new QuantumCoordinates(particles);
    }

    private static Molecule water() {
        return new Molecule("ferminet-v1-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8),
                        new CartesianPosition(0, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(1.7952398191849366, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(2, "H", new NuclearCharge(1),
                        new CartesianPosition(-0.46464225035067114,
                                1.7340684963325879, 0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10), new SpinSector(5, 5, 1));
    }
}
