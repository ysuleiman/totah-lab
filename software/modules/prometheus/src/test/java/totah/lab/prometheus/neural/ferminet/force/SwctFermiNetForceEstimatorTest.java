package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import totah.lab.prometheus.neural.GeneralSlaterJastrowDirectionalEvaluator;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.force.GeneralAnalyticDifferentialSwctForceEstimator;
import totah.lab.prometheus.variational.force.GeneralMolecularSpaceWarp;

final class SwctFermiNetForceEstimatorTest {

    /**
     * The SWCT term assembly, Coulomb directional derivative, and warp
     * divergence used by the FermiNet plugin must reproduce the validated
     * general analytic SWCT estimator on the same deterministic fixture.
     */
    @Test
    void assemblyMatchesGeneralSwctEstimatorOnDeterministicFixture() {
        Molecule molecule = h2();
        GeneralSlaterJastrowState state =
                GeneralSlaterJastrowState.cuspInitialized(molecule);
        List<QuantumCoordinates> samples = fixtureSamples();
        var reference = new GeneralAnalyticDifferentialSwctForceEstimator()
                .evaluate(state, consumer -> samples.forEach(
                        sample -> consumer.accept(1, sample)));
        assertEquals(16, reference.localEnergyEvaluations());

        var evaluator = new GeneralSlaterJastrowDirectionalEvaluator();
        int nuclei = molecule.nuclei().size();
        int components = 3 * nuclei;
        double[] localEnergies = new double[samples.size()];
        double[] importanceWeights = new double[samples.size()];
        var evaluations = new ArrayList<GeneralSlaterJastrowDirectionalEvaluator.Evaluation>();
        for (int s = 0; s < samples.size(); s++) {
            var e = evaluator.evaluate(molecule, state.parameters(), samples.get(s));
            evaluations.add(e);
            assertEquals(e.potential(),
                    SwctFermiNetForceEstimator.coulombPotential(
                            molecule, samples.get(s)),
                    1e-12, "Coulomb potential");
            localEnergies[s] =
                    -0.5 * e.electronicLaplacian() / e.value() + e.potential();
            importanceWeights[s] = e.value() * e.value();
        }
        // The general fixture uses arbitrary quadrature points, so the general
        // estimator importance-weights by |Psi|^2; mirror that here. The
        // production estimator uses plain means because the frozen dataset is
        // already drawn from |Psi|^2.
        double norm = 0.0, meanLocalEnergy = 0.0;
        for (int s = 0; s < samples.size(); s++) {
            norm += importanceWeights[s];
            meanLocalEnergy += importanceWeights[s] * localEnergies[s];
        }
        meanLocalEnergy /= norm;
        assertEquals(reference.energyHartree(), meanLocalEnergy, 1e-12,
                "importance-weighted mean local energy");

        double[][] assembled = new double[components][samples.size()];
        for (int s = 0; s < samples.size(); s++) {
            var e = evaluations.get(s);
            double psi = e.value();
            var weights = SwctFermiNetForceEstimator.warpWeights(
                    molecule, samples.get(s));
            for (int nucleus = 0; nucleus < nuclei; nucleus++) {
                double[] electronWeights = new double[weights.length];
                for (int i = 0; i < weights.length; i++) {
                    electronWeights[i] = weights[i][nucleus].value();
                }
                for (int axis = 0; axis < 3; axis++) {
                    int component = 3 * nucleus + axis;
                    int total = GeneralSlaterJastrowDirectionalEvaluator.total(
                            nucleus, axis);
                    double directionalLog =
                            e.valueDirectionalDerivatives()[total] / psi;
                    double directionalLaplacianOver =
                            (e.laplacianDirectionalDerivatives()[total] * psi
                                    - e.electronicLaplacian()
                                            * e.valueDirectionalDerivatives()[total])
                                    / (psi * psi);
                    double directionalCoulomb = SwctFermiNetForceEstimator
                            .coulombDirectionalDerivative(molecule,
                                    samples.get(s), nucleus, axis,
                                    electronWeights);
                    assertEquals(e.potentialDirectionalDerivatives()[total],
                            directionalCoulomb, 1e-12,
                            "Coulomb directional derivative " + component);
                    double halfDivergence = SwctFermiNetForceEstimator
                            .halfWarpDivergence(weights, nucleus, axis);
                    assertEquals(0.5 * e.warpDivergence()[component],
                            halfDivergence, 1e-12,
                            "warp divergence " + component);
                    assembled[component][s] = SwctFermiNetForceEstimator.forceSample(
                            localEnergies[s], meanLocalEnergy,
                            -0.5 * directionalLaplacianOver + directionalCoulomb,
                            directionalLog + halfDivergence);
                }
            }
        }
        double[] total = new double[3];
        for (int nucleus = 0; nucleus < nuclei; nucleus++) {
            var expected = reference.forces().get(nucleus);
            double[] perNucleus = {expected.fx(), expected.fy(), expected.fz()};
            for (int axis = 0; axis < 3; axis++) {
                double mean = 0.0;
                for (int s = 0; s < samples.size(); s++) {
                    mean += importanceWeights[s]
                            * assembled[3 * nucleus + axis][s];
                }
                mean /= norm;
                assertEquals(perNucleus[axis], mean, 1e-12,
                        "SWCT force nucleus " + nucleus + " axis " + axis);
                total[axis] += mean;
            }
        }
        for (int axis = 0; axis < 3; axis++) {
            assertEquals(0.0, total[axis], 2e-12,
                    "translational consistency axis " + axis);
        }
    }

    /** End-to-end smoke on the frozen H2O network with a small subset dataset. */
    @Test
    void frozenH2oNetworkProducesFiniteCanonicalResult() throws Exception {
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
        Path directory = Files.createTempDirectory("swct-smoke");
        Path subsetFile = directory.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                subsetFile, subset, 8);
        writeDiagnosticFdReference(directory, identity,
                checkpoint.parameterChecksum());

        var context = new FermiNetForceEvaluationContext(
                state, checkpoint.parameterChecksum(), checkpoint.geometryIdentity(),
                subsetFile, identity, "0".repeat(64),
                checkpoint.rootParameterChecksum());
        long start = System.nanoTime();
        NuclearForceResult result = new SwctFermiNetForceEstimator()
                .estimate(context, NuclearForceConfiguration.swct());
        long elapsed = System.nanoTime() - start;

        assertEquals(NuclearForceEstimatorType.SWCT, result.estimatorType());
        assertEquals(9, result.components().size());
        assertEquals(SwctFermiNetForceEstimator.VARIANCE_REDUCED,
                result.classification());
        var diagnostics =
                (NuclearForceResult.SwctDiagnostics) result.estimatorDiagnostics();
        assertTrue(Double.isFinite(diagnostics.meanLocalEnergyHartree()));
        for (int component = 0; component < 9; component++) {
            var c = result.components().get(component);
            var d = diagnostics.components().get(component);
            assertEquals(16, c.finiteCount(), "finite " + component);
            assertEquals(0, c.nonfiniteCount(), "nonfinite " + component);
            assertTrue(Double.isFinite(c.meanHartreePerBohr()));
            assertTrue(Double.isFinite(c.chainStandardError()));
            assertTrue(Double.isFinite(c.variance()));
            assertEquals(64, c.rawSampleChecksum().length());
            // Decomposition identity: mean = direct + covariance/Pulay.
            assertEquals(c.meanHartreePerBohr(),
                    d.meanDirectForceTermHartreePerBohr()
                            + d.meanCovariancePulayTermHartreePerBohr(),
                    1e-9, "decomposition " + component);
            assertEquals(d.meanCovariancePulayTermHartreePerBohr(),
                    d.meanWavefunctionLogTermHartreePerBohr()
                            + d.meanJacobianDivergenceTermHartreePerBohr(),
                    1e-12, "covariance split " + component);
            assertEquals(c.meanHartreePerBohr()
                            - d.correlatedFdMeanHartreePerBohr(),
                    d.meanDifferenceHartreePerBohr(), 1e-12);
        }
        System.out.printf(java.util.Locale.ROOT,
                "SWCT_SMOKE_16_SAMPLES_9_COMPONENTS_NANOS=%d%n", elapsed);
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

    private static Molecule h2() {
        return new Molecule("H2-force", List.of(
                nucleus(0, 0, 0, -0.7), nucleus(1, 0, 0, 0.7)),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static NuclearCenter nucleus(int index, double x, double y, double z) {
        return new NuclearCenter(index, "H", new NuclearCharge(1),
                new CartesianPosition(x, y, z, LengthUnit.BOHR));
    }

    /** Same deterministic 16-sample fixture as the general SWCT estimator test. */
    private static List<QuantumCoordinates> fixtureSamples() {
        List<double[]> base = List.of(
                new double[] {.35, .20, .40, -.45, -.15, -.30},
                new double[] {-.35, -.20, -.40, .45, .15, .30},
                new double[] {.60, -.25, -.10, -.55, .30, .15},
                new double[] {-.60, .25, .10, .55, -.30, -.15});
        List<QuantumCoordinates> points = new ArrayList<>();
        for (double[] v : base) {
            for (int turn = 0; turn < 4; turn++) {
                double angle = turn * Math.PI / 2;
                double c = Math.cos(angle), s = Math.sin(angle);
                points.add(new QuantumCoordinates(List.of(
                        new QuantumCoordinates.ParticleCoordinate(0,
                                c * v[0] - s * v[1], s * v[0] + c * v[1], v[2],
                                SpinProjection.ALPHA),
                        new QuantumCoordinates.ParticleCoordinate(1,
                                c * v[3] - s * v[4], s * v[3] + c * v[4], v[5],
                                SpinProjection.BETA))));
            }
        }
        return points;
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
