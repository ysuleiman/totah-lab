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

final class AcZvFermiNetForceEstimatorTest {

    private static final double MINIMUM_AMPLITUDE = 1e-14;

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
     * Sign-sensitive parity: on a nonconstant wavefunction (nonzero electron
     * log-gradient, so the sign of the contraction is observable), the
     * generalized AC-ZV per-sample force must equal the zero-variance part of
     * the existing ZVZB formulation, bareForce - operatorRatio, extracted
     * independently as constant + E_L * coefficient from the locked ZVZB
     * streaming decomposition.
     */
    @Test
    void contractionMatchesZvzbZeroVariancePartOnNonconstantWavefunction() {
        double radius = 1.4;
        var state = new GeometryConditionedHydrogenMoleculeState(radius, H2_PARAMETERS);
        var hamiltonian = new HydrogenMoleculeHamiltonian(radius);
        var batches = new HydrogenMoleculeImportanceBatches(512, radius, 1.15, 43, 64);
        Molecule molecule = h2(radius);

        List<AssarafCaffarelZvzbForceEstimator.LinearContribution> zvzb =
                new ArrayList<>();
        new AssarafCaffarelZvzbForceEstimator().evaluate(
                state, hamiltonian, batches, 1, zvzb::add);

        List<double[]> mine = new ArrayList<>();
        List<double[]> logGradients = new ArrayList<>();
        List<QuantumCoordinates> accepted = new ArrayList<>();
        List<Double> localEnergies = new ArrayList<>();
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
            accepted.add(point.coordinates());
            logGradients.add(logGradient);
            localEnergies.add(-0.5
                    * bundle.coordinateLaplacian().value().real() / psi
                    + hamiltonian.potential(point.coordinates()));
            double[] force = new double[3];
            for (int axis = 0; axis < 3; axis++) {
                force[axis] = AcZvFermiNetForceEstimator.nuclearRepulsionForce(
                        molecule, 1, axis)
                        + AcZvFermiNetForceEstimator.auxiliaryContraction(
                                molecule, point.coordinates(), logGradient, 1, axis);
            }
            mine.add(force);
        }));
        assertEquals(zvzb.size(), mine.size(), "accepted sample count");

        double contractionSquares = 0.0;
        for (int s = 0; s < zvzb.size(); s++) {
            var bundle = zvzb.get(s);
            double localEnergy = localEnergies.get(s);
            for (int axis = 0; axis < 3; axis++) {
                double zvzbZvPart = axisValue(bundle.constant(), axis)
                        + localEnergy
                                * axisValue(bundle.sampledMeanEnergyCoefficient(),
                                        axis);
                assertEquals(zvzbZvPart, mine.get(s)[axis], 1e-9,
                        "AC-ZV must equal ZVZB ZV part, sample " + s + " axis " + axis);
            }
            double contraction = AcZvFermiNetForceEstimator.auxiliaryContraction(
                    molecule, accepted.get(s), logGradients.get(s), 1, 2);
            contractionSquares += contraction * contraction;
        }
        double contractionRms = Math.sqrt(contractionSquares / zvzb.size());
        assertTrue(contractionRms > 1e-3,
                "fixture must be sign-sensitive: contraction RMS " + contractionRms);
        System.out.printf(java.util.Locale.ROOT,
                "AC_ZV_SIGN_SENSITIVITY_CONTRACTION_RMS=%.16e%n", contractionRms);
    }

    private static double axisValue(
            totah.lab.prometheus.variational.force.AssarafCaffarelForceStatistics
                    .Vector vector, int axis) {
        return switch (axis) {
            case 0 -> vector.x();
            case 1 -> vector.y();
            case 2 -> vector.z();
            default -> throw new IllegalArgumentException("invalid axis");
        };
    }

    /**
     * Nuclear-charge generalization: Q_A and the contraction must scale
     * linearly with Z_A, and the nucleus-nucleus force must use the actual
     * charges. Oxygen (Z=8) is mandatory because H2O is the production target.
     */
    @Test
    void auxiliaryAndContractionScaleWithNuclearCharge() {
        QuantumCoordinates coordinates = new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(0, 0.7, -0.4, 0.3,
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(1, -0.5, 0.9, -0.6,
                        SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(2, 0.2, 0.1, 1.1,
                        SpinProjection.BETA)));
        double[] logGradient = {0.31, -0.27, 0.18, -0.12, 0.42, -0.35,
                0.22, 0.14, -0.29};
        for (int charge : new int[] {1, 2, 8}) {
            Molecule molecule = charged(charge);
            for (int nucleus = 0; nucleus < 2; nucleus++) {
                for (int axis = 0; axis < 3; axis++) {
                    double q = AcZvFermiNetForceEstimator.auxiliaryQ(
                            molecule, coordinates, nucleus, axis);
                    double qUnit = AcZvFermiNetForceEstimator.auxiliaryQ(
                            charged(1), coordinates, nucleus, axis);
                    double expected = nucleus == 0
                            ? charge * qUnit : qUnit;
                    assertEquals(expected, q, 1e-12,
                            "Q scaling Z=" + charge + " nucleus " + nucleus);
                    double contraction = AcZvFermiNetForceEstimator
                            .auxiliaryContraction(molecule, coordinates,
                                    logGradient, nucleus, axis);
                    double unitContraction = AcZvFermiNetForceEstimator
                            .auxiliaryContraction(charged(1), coordinates,
                                    logGradient, nucleus, axis);
                    assertEquals(nucleus == 0 ? charge * unitContraction
                                    : unitContraction,
                            contraction, 1e-12,
                            "contraction scaling Z=" + charge + " nucleus " + nucleus);
                }
            }
        }
        // Hand-computed nucleus-nucleus force on the Z=8 nucleus of charged(8):
        // 8 * 1 * (R_O - R_H) / |R_O - R_H|^3 with R_O=(0,0,0), R_H=(0.3,-0.2,1.9).
        double dx = -0.3, dy = 0.2, dz = -1.9;
        double r3 = Math.pow(dx * dx + dy * dy + dz * dz, 1.5);
        assertEquals(8.0 * dx / r3, AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(charged(8), 0, 0), 1e-12, "nn x");
        assertEquals(8.0 * dy / r3, AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(charged(8), 0, 1), 1e-12, "nn y");
        assertEquals(8.0 * dz / r3, AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(charged(8), 0, 2), 1e-12, "nn z");
        assertEquals(-8.0 * dx / r3, AcZvFermiNetForceEstimator
                .nuclearRepulsionForce(charged(8), 1, 0), 1e-12, "nn H x");
    }

    /**
     * Frozen H2 panel diagnostic: the Eq.-6-consistent generalized code must
     * reproduce the already observed improved values. The historical
     * printed-Eq.-11 implementation is retired (see
     * AC_ZV_HISTORICAL_IMPLEMENTATION_RETIRED.md); its panel values survive
     * here as locked constants sourced from the preserved study artifacts and
     * git history, and are printed for comparison only.
     */
    @Test
    void frozenH2PanelReportsHistoricalAndConsistentFormulations() {
        double[] radii = {1.0, 1.4, 3.0};
        // Retired printed-Eq-11 (nn-G) implementation, frozen study values.
        double[] historicalLocked = {
                1.7227154464996024, 1.0616424506472109, 0.2833808194705944};
        double[] consistentLocked = {
                0.2772845535003969, -0.0412342873819034, -0.0611585972483748};
        double[] references = {
                0.3621964426997232, 0.009120324827245340, -0.06087135209218764};
        for (int k = 0; k < radii.length; k++) {
            var state = new GeometryConditionedHydrogenMoleculeState(
                    radii[k], H2_PARAMETERS);
            var batches = new HydrogenMoleculeImportanceBatches(
                    72000, radii[k], 1.15, 1009, 512);
            Molecule molecule = h2(radii[k]);

            double historical = historicalLocked[k];

            double[] norm = {0.0}, sum = {0.0};
            batches.forEachBatch(batch -> batch.forEach(point -> {
                var bundle = state.evaluateWithDerivatives(point.coordinates());
                double psi = bundle.value().real();
                if (!Double.isFinite(psi) || Math.abs(psi) < MINIMUM_AMPLITUDE) return;
                double[] logGradient = new double[6];
                for (int i = 0; i < 2; i++) {
                    var gradient = bundle.coordinateGradient()
                            .particleGradients().get(i);
                    logGradient[3 * i] = gradient.x().real() / psi;
                    logGradient[3 * i + 1] = gradient.y().real() / psi;
                    logGradient[3 * i + 2] = gradient.z().real() / psi;
                }
                double weight = point.weight() * psi * psi;
                double force = AcZvFermiNetForceEstimator.nuclearRepulsionForce(
                        molecule, 1, 2)
                        + AcZvFermiNetForceEstimator.auxiliaryContraction(
                                molecule, point.coordinates(), logGradient, 1, 2);
                norm[0] += weight;
                sum[0] += weight * force;
            }));
            double consistent = sum[0] / norm[0];
            assertEquals(consistentLocked[k], consistent, 1e-12,
                    "Eq-6-consistent regression R=" + radii[k]);
            System.out.printf(java.util.Locale.ROOT,
                    "AC_ZV_H2_PANEL R=%.1f reference=%.16f historical(nn-G)=%.16f"
                            + " consistent(nn+G)=%.16f%n",
                    radii[k], references[k], historical, consistent);
        }
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
        Path directory = Files.createTempDirectory("aczv-smoke");
        Path subsetFile = directory.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                subsetFile, subset, 8);
        writeDiagnosticFdReference(directory, identity,
                checkpoint.parameterChecksum());

        var context = new FermiNetForceEvaluationContext(
                state, checkpoint.parameterChecksum(), checkpoint.geometryIdentity(),
                subsetFile, identity, "0".repeat(64),
                checkpoint.rootParameterChecksum());
        NuclearForceResult result = new FermiNetNuclearForcePipeline()
                .estimate(context, NuclearForceConfiguration.acZv());

        assertEquals(NuclearForceEstimatorType.AC_ZV, result.estimatorType());
        assertEquals(9, result.components().size());
        assertEquals(AcZvFermiNetForceEstimator.VARIANCE_REDUCED,
                result.classification());
        var diagnostics =
                (NuclearForceResult.AcZvDiagnostics) result.estimatorDiagnostics();
        assertEquals(AcZvFermiNetForceEstimator.FORMULATION,
                diagnostics.estimatorFormulation());
        assertEquals(AcZvFermiNetForceEstimator.AUXILIARY,
                diagnostics.auxiliaryEquation());
        for (int component = 0; component < 9; component++) {
            var c = result.components().get(component);
            var d = diagnostics.components().get(component);
            assertEquals(16, c.finiteCount(), "finite " + component);
            assertEquals(0, c.nonfiniteCount(), "nonfinite " + component);
            assertTrue(Double.isFinite(c.meanHartreePerBohr()));
            assertTrue(Double.isFinite(c.chainStandardError()));
            assertTrue(Double.isFinite(c.variance()));
            assertEquals(64, c.rawSampleChecksum().length());
            // Decomposition identity: mean = nn + mean contraction.
            assertEquals(c.meanHartreePerBohr(),
                    d.nuclearRepulsionTermHartreePerBohr()
                            + d.meanContractionTermHartreePerBohr(),
                    1e-12, "decomposition " + component);
            assertEquals(c.meanHartreePerBohr()
                            - d.correlatedFdMeanHartreePerBohr(),
                    d.meanDifferenceVsCorrelatedFdHartreePerBohr(), 1e-12);
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

    private static Molecule h2(double bondLengthBohr) {
        return new Molecule("H2-ac-zv", List.of(
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
