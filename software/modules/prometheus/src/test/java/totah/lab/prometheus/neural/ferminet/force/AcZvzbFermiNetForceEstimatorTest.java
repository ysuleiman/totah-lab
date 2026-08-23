package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
import totah.lab.prometheus.neural.GeometryConditionedHydrogenMoleculeState;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.force.AssarafCaffarelZvzbForceEstimator;

final class AcZvzbFermiNetForceEstimatorTest {

    private static final double MINIMUM_AMPLITUDE = 1e-14;

    @Test
    void invalidSamplesAtBeginningMiddleAndEndCannotBecomePhantomZeros() throws Exception {
        for (int invalid : new int[] {0, 2, 4}) {
            double[] samples = AcZvzbFermiNetForceEstimator.invalidSampleMatrix(1, 5)[0];
            for (int i = 0; i < samples.length; i++) if (i != invalid) samples[i] = i + 1.0;
            assertTrue(Double.isNaN(samples[invalid]));
            var tails = AcZvzbFermiNetForceEstimator.class.getDeclaredMethod(
                    "tails", double[].class, int.class, double.class, double.class);
            tails.setAccessible(true);
            Object diagnostics = tails.invoke(null, samples, 4, 3.0,
                    Math.sqrt(10.0 / 3.0));
            assertTrue(diagnostics instanceof NuclearForceResult.TailDiagnostics);
        }
        var tails = AcZvzbFermiNetForceEstimator.class.getDeclaredMethod(
                "tails", double[].class, int.class, double.class, double.class);
        tails.setAccessible(true);
        var error = assertThrows(java.lang.reflect.InvocationTargetException.class,
                () -> tails.invoke(null, new double[] {0.0, 2.0, 4.0}, 2, 3.0, 1.0));
        assertTrue(error.getCause() instanceof IllegalArgumentException);

        var summary = AcZvzbFermiNetForceEstimator.summarizeSamplesForTesting(
                new double[] {Double.NaN, 2, 3, 4, 5, Double.NaN},
                new int[] {0, 0, 0, 1, 1, 1}, 2);
        assertEquals(4, summary.finiteCount());
        assertEquals(3.5, summary.mean(), 0.0);
        assertEquals(5.0 / 3.0, summary.variance(), 1e-15);
        assertEquals(1.0, summary.chainStandardError(), 0.0);
        assertEquals(2.0, summary.tails().minimum(), 0.0);
        assertEquals(3.5, summary.tails().median(), 0.0);
        assertEquals(5.0, summary.tails().maximum(), 0.0);
        assertEquals(AcZvzbFermiNetForceEstimator.IMPLEMENTATION_FAILURE,
                summary.classification());
    }

    /** Frozen H2 study parameters from NuclearForceEstimatorCapabilityStudy. */
    private static final ParameterVector H2_PARAMETERS = new ParameterVector(List.of(
            .8576772116910546, .11919655001255025, -.06709570692540537,
            .04370894911240642, -.32732397143757097, .21519667708138937,
            -.06386208428749664, .04232059707741613, .017563345336565027,
            -.12118637444956007, .11444052280585346, .26554487072063354,
            .19811737981250818, .07860098998305089, -.2778578205251936,
            -.16701609069702947, .07580798604963333, -.15755013283163458,
            .22812063643399538, -.1453261891402233));

    /**
     * Per-sample parity on a nonconstant wavefunction: the generalized
     * nn + G + 2(E_v - E_L) Q must equal the surviving H2 ZVZB estimator's
     * exact second-pass per-sample force to tight precision, and the sampled
     * mean energies must agree.
     */
    @Test
    void perSampleParityWithHistoricalZvzbOnNonconstantWavefunction() {
        double radius = 1.4;
        var state = new GeometryConditionedHydrogenMoleculeState(radius, H2_PARAMETERS);
        var hamiltonian = new HydrogenMoleculeHamiltonian(radius);
        var batches = new HydrogenMoleculeImportanceBatches(512, radius, 1.15, 43, 64);
        Molecule molecule = h2(radius);

        var historical = new AssarafCaffarelZvzbForceEstimator();
        var result = historical.evaluate(state, hamiltonian, batches, 1);
        List<AssarafCaffarelZvzbForceEstimator.Contribution> contributions =
                new ArrayList<>();
        historical.forEachContribution(state, hamiltonian, batches, 1,
                result.sampledMeanEnergyHartree(), contributions::add);

        List<double[]> mine = new ArrayList<>();
        double zeroBiasSquares = 0.0;
        Panel pass = computeSamples(batches, state, hamiltonian, molecule);
        mine.addAll(pass.forces);
        double myMeanEnergy = pass.energySum / pass.norm;
        assertEquals(result.sampledMeanEnergyHartree(), myMeanEnergy, 1e-12,
                "sampled mean energy E_v");
        assertEquals(contributions.size(), mine.size(), "accepted sample count");
        for (int s = 0; s < contributions.size(); s++) {
            var expected = contributions.get(s).forceHartreePerBohr();
            double[] actual = mine.get(s);
            assertEquals(expected.x(), actual[0], 1e-9, "sample " + s + " x");
            assertEquals(expected.y(), actual[1], 1e-9, "sample " + s + " y");
            assertEquals(expected.z(), actual[2], 1e-9, "sample " + s + " z");
            zeroBiasSquares += pass.zeroBias.get(s) * pass.zeroBias.get(s);
        }
        double zeroBiasRms = Math.sqrt(zeroBiasSquares / contributions.size());
        assertTrue(zeroBiasRms > 1e-6,
                "fixture must exercise the ZB term: RMS " + zeroBiasRms);
        System.out.printf(Locale.ROOT,
                "AC_ZVZB_PARITY_ZB_RMS=%.16e%n", zeroBiasRms);
    }

    /** Per-sample generalized quantities, mirroring the historical acceptance rule. */
    private record Panel(double norm, double energySum, List<double[]> forces,
            List<Double> zeroBias) {}

    private static Panel computeSamples(
            HydrogenMoleculeImportanceBatches batches,
            GeometryConditionedHydrogenMoleculeState state,
            HydrogenMoleculeHamiltonian hamiltonian, Molecule molecule) {
        List<double[]> forces = new ArrayList<>();
        List<Double> zeroBias = new ArrayList<>();
        // First collect local energies for E_v.
        List<QuantumCoordinates> coordinates = new ArrayList<>();
        List<double[]> logGradients = new ArrayList<>();
        List<Double> localEnergies = new ArrayList<>();
        List<Double> weights = new ArrayList<>();
        batches.forEachBatch(batch -> batch.forEach(point -> {
            var bundle = state.evaluateWithDerivatives(point.coordinates());
            double psi = bundle.value().real();
            if (!Double.isFinite(psi) || Math.abs(psi) < MINIMUM_AMPLITUDE) return;
            double[] logGradient = new double[6];
            for (int i = 0; i < 2; i++) {
                var gradient = bundle.coordinateGradient().particleGradients().get(i);
                logGradient[3 * i] = gradient.x().real() / psi;
                logGradient[3 * i + 1] = gradient.y().real() / psi;
                logGradient[3 * i + 2] = gradient.z().real() / psi;
            }
            coordinates.add(point.coordinates());
            logGradients.add(logGradient);
            localEnergies.add(-0.5
                    * bundle.coordinateLaplacian().value().real() / psi
                    + hamiltonian.potential(point.coordinates()));
            weights.add(point.weight() * psi * psi);
        }));
        double norm = 0.0, energySum = 0.0;
        for (int s = 0; s < weights.size(); s++) {
            norm += weights.get(s);
            energySum += weights.get(s) * localEnergies.get(s);
        }
        double meanEnergy = energySum / norm;
        for (int s = 0; s < weights.size(); s++) {
            double[] force = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                double q = AcZvFermiNetForceEstimator.auxiliaryQ(
                        molecule, coordinates.get(s), 1, axis);
                double g = AcZvFermiNetForceEstimator.auxiliaryContraction(
                        molecule, coordinates.get(s), logGradients.get(s), 1, axis);
                double zb = 2.0 * (meanEnergy - localEnergies.get(s)) * q;
                force[axis] = AcZvFermiNetForceEstimator.nuclearRepulsionForce(
                        molecule, 1, axis) + g + zb;
                if (axis == 2) zeroBias.add(zb);
            }
            forces.add(force);
        }
        return new Panel(norm, energySum, forces, zeroBias);
    }

    /**
     * Nuclear-charge generalization of the complete ZB term: Q, G, and
     * 2(E_v - E_L) Q must all scale linearly with Z_A for Z in {1, 2, 8}.
     */
    @Test
    void zeroBiasTermScalesWithNuclearCharge() {
        QuantumCoordinates coordinates = new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0, 0.7, -0.4, 0.3,
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1, -0.5, 0.9, -0.6,
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(2, 0.2, 0.1, 1.1,
                        SpinProjection.BETA)));
        double[] logGradient = {0.31, -0.27, 0.18, -0.12, 0.42, -0.35,
                0.22, 0.14, -0.29};
        double meanEnergy = -1.13, localEnergy = -1.47;
        for (int charge : new int[] {1, 2, 8}) {
            Molecule molecule = charged(charge);
            Molecule unit = charged(1);
            for (int axis = 0; axis < 3; axis++) {
                double q = AcZvFermiNetForceEstimator.auxiliaryQ(
                        molecule, coordinates, 0, axis);
                double qUnit = AcZvFermiNetForceEstimator.auxiliaryQ(
                        unit, coordinates, 0, axis);
                assertEquals(charge * qUnit, q, 1e-12, "Q scaling Z=" + charge);
                double g = AcZvFermiNetForceEstimator.auxiliaryContraction(
                        molecule, coordinates, logGradient, 0, axis);
                double gUnit = AcZvFermiNetForceEstimator.auxiliaryContraction(
                        unit, coordinates, logGradient, 0, axis);
                assertEquals(charge * gUnit, g, 1e-12, "G scaling Z=" + charge);
                double zb = 2.0 * (meanEnergy - localEnergy) * q;
                double zbUnit = 2.0 * (meanEnergy - localEnergy) * qUnit;
                assertEquals(charge * zbUnit, zb, 1e-12, "ZB scaling Z=" + charge);
            }
        }
    }

    /**
     * Frozen H2 panel: the surviving historical ZVZB class must still
     * reproduce the preserved study values, and the generalized assembly must
     * match the historical class on the full panel.
     */
    @Test
    void frozenH2PanelReproducesPreservedZvzbValues() {
        double[] radii = {1.0, 1.4, 3.0};
        double[] locked = {
                0.3263265186825435, 0.002402101102335822, -0.05185460065425046};
        for (int k = 0; k < radii.length; k++) {
            var state = new GeometryConditionedHydrogenMoleculeState(
                    radii[k], H2_PARAMETERS);
            var hamiltonian = new HydrogenMoleculeHamiltonian(radii[k]);
            var batches = new HydrogenMoleculeImportanceBatches(
                    72000, radii[k], 1.15, 1009, 512);
            Molecule molecule = h2(radii[k]);

            var historical = new AssarafCaffarelZvzbForceEstimator()
                    .evaluate(state, hamiltonian, batches, 1);
            double historicalForce =
                    historical.rawStatistics().meanHartreePerBohr().z();
            assertEquals(locked[k], historicalForce, 1e-12,
                    "preserved ZVZB panel regression R=" + radii[k]);

            Panel panel = computeSamples(batches, state, hamiltonian, molecule);
            double meanEnergy = panel.energySum / panel.norm;
            assertEquals(historical.sampledMeanEnergyHartree(), meanEnergy,
                    1e-12, "panel E_v R=" + radii[k]);
            // Generalized panel mean via weighted accumulation:
            WeightedPanel weighted = computeWeightedPanel(
                    batches, state, hamiltonian, molecule);
            assertEquals(historicalForce, weighted.forceZ, 1e-9,
                    "generalized ZVZB panel R=" + radii[k]);
            assertEquals(locked[k], weighted.forceZ, 1e-9,
                    "generalized vs preserved R=" + radii[k]);
            System.out.printf(Locale.ROOT,
                    "AC_ZVZB_H2_PANEL R=%.1f historical=%.16f generalized=%.16f%n",
                    radii[k], historicalForce, weighted.forceZ);
        }
    }

    private record WeightedPanel(double forceZ) {}

    /** Mirrors the historical streaming accumulation order exactly. */
    private static WeightedPanel computeWeightedPanel(
            HydrogenMoleculeImportanceBatches batches,
            GeometryConditionedHydrogenMoleculeState state,
            HydrogenMoleculeHamiltonian hamiltonian, Molecule molecule) {
        List<Double> weights = new ArrayList<>();
        List<Double> constants = new ArrayList<>();
        List<Double> coefficients = new ArrayList<>();
        List<Double> energies = new ArrayList<>();
        batches.forEachBatch(batch -> batch.forEach(point -> {
            var bundle = state.evaluateWithDerivatives(point.coordinates());
            double psi = bundle.value().real();
            if (!Double.isFinite(psi) || Math.abs(psi) < MINIMUM_AMPLITUDE) return;
            double[] logGradient = new double[6];
            for (int i = 0; i < 2; i++) {
                var gradient = bundle.coordinateGradient().particleGradients().get(i);
                logGradient[3 * i] = gradient.x().real() / psi;
                logGradient[3 * i + 1] = gradient.y().real() / psi;
                logGradient[3 * i + 2] = gradient.z().real() / psi;
            }
            double localEnergy = -0.5
                    * bundle.coordinateLaplacian().value().real() / psi
                    + hamiltonian.potential(point.coordinates());
            double q = AcZvFermiNetForceEstimator.auxiliaryQ(
                    molecule, point.coordinates(), 1, 2);
            double g = AcZvFermiNetForceEstimator.auxiliaryContraction(
                    molecule, point.coordinates(), logGradient, 1, 2);
            double nn = AcZvFermiNetForceEstimator.nuclearRepulsionForce(
                    molecule, 1, 2);
            weights.add(point.weight() * psi * psi);
            constants.add(nn + g - 2.0 * localEnergy * q);
            coefficients.add(2.0 * q);
            energies.add(localEnergy);
        }));
        double norm = 0.0, energySum = 0.0;
        for (int s = 0; s < weights.size(); s++) {
            norm += weights.get(s);
            energySum += weights.get(s) * energies.get(s);
        }
        double meanEnergy = energySum / norm;
        double constantSum = 0.0, coefficientSum = 0.0;
        for (int s = 0; s < weights.size(); s++) {
            constantSum += weights.get(s) * constants.get(s);
            coefficientSum += weights.get(s) * coefficients.get(s);
        }
        return new WeightedPanel(
                (constantSum + meanEnergy * coefficientSum) / norm);
    }

    /** End-to-end smoke on the frozen H2O network through the canonical pipeline. */
    @Test
    void frozenH2oNetworkProducesFiniteCanonicalResultThroughPipeline()
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
        FermiNetV1Configuration network = FermiNetV1Configuration.locked();
        FermiNetParameterLayout layout = new FermiNetParameterLayout(network, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, network,
                FermiNetParameters.fromArray(layout, checkpoint.parameters()));

        List<QuantumCoordinates> subset = new ArrayList<>();
        FermiNetCorrelatedFdConfigurationFile.forEach(
                configurationsFile, 64, (sample, chain, retained, coordinates) -> {
                    if (sample < 16) subset.add(coordinates);
                });
        assertEquals(16, subset.size());
        Path directory = Files.createTempDirectory("aczvzb-smoke");
        Path datasetDirectory = directory.resolve("dataset");
        Files.createDirectories(datasetDirectory);
        Path subsetFile = datasetDirectory.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                subsetFile, subset, 8);
        writeDiagnosticFdReference(datasetDirectory, identity,
                checkpoint.parameterChecksum());
        for (var type : new NuclearForceEstimatorType[] {
                NuclearForceEstimatorType.SWCT, NuclearForceEstimatorType.AC_ZV}) {
            writeDiagnosticEstimatorArtifact(directory, identity,
                    checkpoint.parameterChecksum(), type);
        }

        var context = new FermiNetForceEvaluationContext(
                state, checkpoint.parameterChecksum(), checkpoint.geometryIdentity(),
                subsetFile, identity, "0".repeat(64),
                checkpoint.rootParameterChecksum());
        NuclearForceResult result = new AcZvzbFermiNetForceEstimator()
                .estimate(context, NuclearForceConfiguration.acZvzb(),
                        totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngines
                                .create(totah.lab.prometheus.neural.ferminet.runtime
                                        .FermiNetDerivativeConfiguration.referenceJet()));

        assertEquals(NuclearForceEstimatorType.AC_ZVZB, result.estimatorType());
        assertEquals(9, result.components().size());
        assertEquals(AcZvzbFermiNetForceEstimator.VARIANCE_REDUCED,
                result.classification());
        var diagnostics =
                (NuclearForceResult.AcZvzbDiagnostics) result.estimatorDiagnostics();
        assertEquals(AcZvzbFermiNetForceEstimator.FORMULATION,
                diagnostics.estimatorFormulation());
        assertEquals(AcZvzbFermiNetForceEstimator.AUXILIARY,
                diagnostics.auxiliaryEquation());
        assertTrue(Double.isFinite(diagnostics.sampledMeanEnergyHartree()));
        for (int component = 0; component < 9; component++) {
            var c = result.components().get(component);
            var d = diagnostics.components().get(component);
            assertEquals(16, c.finiteCount(), "finite " + component);
            assertEquals(0, c.nonfiniteCount(), "nonfinite " + component);
            assertTrue(Double.isFinite(c.meanHartreePerBohr()));
            assertTrue(Double.isFinite(c.chainStandardError()));
            assertTrue(Double.isFinite(c.variance()));
            assertEquals(64, c.rawSampleChecksum().length());
            // Decomposition identity: mean = nn + mean G + mean ZB.
            assertEquals(c.meanHartreePerBohr(),
                    d.nuclearRepulsionTermHartreePerBohr()
                            + d.meanContractionTermHartreePerBohr()
                            + d.meanZeroBiasTermHartreePerBohr(),
                    1e-9, "decomposition " + component);
            // Fabricated comparison artifacts are echoed into diagnostics.
            assertEquals(0.25, d.swctMeanHartreePerBohr(), 0.0);
            assertEquals(0.5, d.acZvMeanHartreePerBohr(), 0.0);
            assertEquals(1.0e6 / c.variance(),
                    d.varianceReductionFactorVsCorrelatedFd(), 1e-6);
        }
    }

    /** Diagnostic-only FD stand-in with huge variance; never a scientific reference. */
    private static void writeDiagnosticFdReference(
            Path directory,
            FermiNetCorrelatedFdConfigurationFile.Identity identity,
            String parameterChecksum) throws Exception {
        var tails = new FermiNetCorrelatedFiniteDifferenceForceReference
                .TailDiagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0);
        List<FermiNetCorrelatedFiniteDifferenceForceReference.ComponentResult>
                components = new ArrayList<>();
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                components.add(new FermiNetCorrelatedFiniteDifferenceForceReference
                        .ComponentResult(nucleus, axis,
                                switch (axis) {
                                    case 0 -> "x"; case 1 -> "y"; default -> "z"; },
                                0.0, 0.0, 0.0, 0.0, 1.0, 1.0, 1.0e6,
                                1.0, 1.0, 1.0, parameterChecksum,
                                "0".repeat(64), "0".repeat(64), tails,
                                new double[] {0.0}));
            }
        }
        new ObjectMapper().writeValue(
                directory.resolve("correlated-fd-reference.json").toFile(),
                new FermiNetCorrelatedFiniteDifferenceForceReference.Result(
                        1.0e-3, parameterChecksum, "0".repeat(64), identity,
                        components));
    }

    /** Minimal fabricated estimator artifact; only the fields the loader reads. */
    private static void writeDiagnosticEstimatorArtifact(
            Path root,
            FermiNetCorrelatedFdConfigurationFile.Identity identity,
            String parameterChecksum,
            NuclearForceEstimatorType type) throws Exception {
        double mean = type == NuclearForceEstimatorType.SWCT ? 0.25 : 0.5;
        StringBuilder components = new StringBuilder("[");
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                if (nucleus + axis > 0) components.append(',');
                components.append(String.format(Locale.ROOT,
                        "{\"nucleus\":%d,\"axis\":%d,\"meanHartreePerBohr\":%s,"
                                + "\"chainStandardError\":1.0,\"variance\":1.0e6}",
                        nucleus, axis, mean));
            }
        }
        components.append(']');
        Path directory = root.resolve(identity.sha256()).resolve(type.name());
        Files.createDirectories(directory);
        Files.writeString(directory.resolve("nuclear-force-result.json"),
                "{\"result\":{\"estimatorType\":\"" + type.name()
                        + "\",\"parameterChecksum\":\"" + parameterChecksum
                        + "\",\"datasetChecksum\":\"" + identity.sha256()
                        + "\",\"components\":" + components + "}}");
    }

    private static Molecule h2(double bondLengthBohr) {
        return new Molecule("H2-ac-zvzb", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, -bondLengthBohr / 2,
                                LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, bondLengthBohr / 2,
                                LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static Molecule charged(int firstCharge) {
        return new Molecule("charged-fixture", List.of(
                new NuclearCenter(0, "X", new NuclearCharge(firstCharge),
                        new CartesianPosition(0, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0.3, -0.2, 1.9, LengthUnit.BOHR))),
                new MolecularCharge(firstCharge + 1 - 3), new ElectronCount(3),
                new SpinSector(2, 1, 2));
    }

    private static Molecule water() {
        return new Molecule("ferminet-v1-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8),
                        new CartesianPosition(0, 0, 0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(1.7952398191849366, 0, 0,
                                LengthUnit.BOHR)),
                new NuclearCenter(2, "H", new NuclearCharge(1),
                        new CartesianPosition(-0.46464225035067114,
                                1.7340684963325879, 0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }
}
