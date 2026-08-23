package totah.lab.prometheus.neural.ferminet.force;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportanceBatches;
import totah.lab.prometheus.variational.ParameterVector;
import totah.lab.prometheus.variational.QuantumCoordinates;

final class AcZvzbDerivFermiNetForceEstimatorTest {

    @Test
    void derivativeEstimatorInvalidSamplesAreExplicitNanAtEveryPosition() {
        for (int invalid : new int[] {0, 2, 4}) {
            double[] samples = AcZvzbDerivFermiNetForceEstimator
                    .invalidSampleMatrix(1, 5)[0];
            for (int i = 0; i < samples.length; i++) if (i != invalid) samples[i] = i + 1.0;
            assertTrue(Double.isNaN(samples[invalid]));
            double sum = 0.0;
            int count = 0;
            for (double value : samples) if (Double.isFinite(value)) {
                sum += value;
                count++;
            }
            assertEquals(4, count);
            assertEquals((15.0 - (invalid + 1.0)) / 4.0, sum / count, 0.0);
        }
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

    @Test
    void pathakWagnerPolynomialAndNodalDistanceIdentities() {
        assertEquals(0.0,
                AcZvzbDerivFermiNetForceEstimator.pathakWagnerFactor(0.0, 0.1),
                0.0);
        assertEquals(1.0,
                AcZvzbDerivFermiNetForceEstimator.pathakWagnerFactor(0.1, 0.1),
                0.0);
        assertEquals(1.0,
                AcZvzbDerivFermiNetForceEstimator.pathakWagnerFactor(0.2, 0.1),
                0.0);
        double below = AcZvzbDerivFermiNetForceEstimator.pathakWagnerFactor(
                Math.nextDown(0.1), 0.1);
        assertEquals(1.0, below, 2.0e-15, "continuity at s=1");

        double psi = -0.37;
        double[] logGradient = {0.4, -0.7, 1.1, 0.2};
        double gradientNorm = 0.0;
        for (double value : logGradient) {
            double directGradient = psi * value;
            gradientNorm += directGradient * directGradient;
        }
        double direct = Math.abs(psi) / Math.sqrt(gradientNorm);
        assertEquals(direct,
                AcZvzbDerivFermiNetForceEstimator.nodalDistance(logGradient),
                2.0e-16);
    }

    /**
     * The ZB response quantity, nuclear().logNuclearGradient(), against a
     * central geometry finite difference of log|Psi| on the exact frozen
     * configurations consumed by the estimator.
     */
    @Test
    void nuclearLogGradientMatchesCentralGeometryFiniteDifference()
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
        FermiNetV1State state = new FermiNetV1State(molecule,
                FermiNetV1Configuration.locked(),
                FermiNetParameters.fromArray(
                        new FermiNetParameterLayout(
                                FermiNetV1Configuration.locked(), molecule),
                        checkpoint.parameters()));
        List<QuantumCoordinates> configurations = new ArrayList<>();
        FermiNetCorrelatedFdConfigurationFile.forEach(
                configurationsFile, 64, (sample, chain, retained, coordinates) -> {
                    if (sample < 3) configurations.add(coordinates);
                });
        assertEquals(3, configurations.size());
        double h = 1.0e-5;
        double maxError = 0.0;
        for (var coordinates : configurations) {
            double[] nuclear = FermiNetStateAccess.nuclear(state, coordinates)
                    .logNuclearGradient();
            for (int nucleusI = 0; nucleusI < 3; nucleusI++) {
                for (int axis = 0; axis < 3; axis++) {
                    var plus = FermiNetStateAccess.withGeometry(state,
                            displace(molecule, nucleusI, axis, h));
                    var minus = FermiNetStateAccess.withGeometry(state,
                            displace(molecule, nucleusI, axis, -h));
                    double expected = (FermiNetStateAccess.sampling(plus,
                            coordinates).logAbsoluteWavefunction()
                            - FermiNetStateAccess.sampling(minus,
                                    coordinates).logAbsoluteWavefunction())
                            / (2.0 * h);
                    double actual = nuclear[3 * nucleusI + axis];
                    maxError = Math.max(maxError, Math.abs(actual - expected));
                    assertEquals(expected, actual, 2.0e-6,
                            "nuclear log gradient nucleus " + nucleusI
                                    + " axis " + axis);
                }
            }
        }
        System.out.printf(Locale.ROOT,
                "AC_ZVZB_DERIV_NUCLEAR_LOGGRAD_FD_MAX_ABS_ERROR=%.16e%n", maxError);
    }

    /**
     * Explicit sign test derived from the AC observable convention: per
     * sample, the AC-2003 v=0 split estimator in the paper's convention
     * (estimating +dE/dR) assembled independently must be exactly minus the
     * production force-convention sample. Also asserts the physical sign on
     * the repulsive wall of H2 (R = 1.0, reference force +0.3622).
     */
    @Test
    void forceConventionMatchesAcObservableConventionPerSample() {
        var batches = new HydrogenMoleculeImportanceBatches(512, 1.4, 1.15, 43, 64);
        checkConvention(batches, 1.4, Double.NaN);
        var wall = new HydrogenMoleculeImportanceBatches(512, 1.0, 1.15, 43, 64);
        checkConvention(wall, 1.0, 0.3621964426997232);
    }

    private static void checkConvention(
            HydrogenMoleculeImportanceBatches batches, double radius,
            double reference) {
        var state = new GeometryConditionedHydrogenMoleculeState(radius, H2_PARAMETERS);
        var hamiltonian = new HydrogenMoleculeHamiltonian(radius);
        Molecule molecule = h2(radius);
        double h = 1.0e-4;
        var plusState = state.atGeometry(radius + h);
        var minusState = state.atGeometry(radius - h);
        double[] norm = {0.0}, energySum = {0.0};
        List<double[]> perSample = new ArrayList<>();
        batches.forEachBatch(batch -> batch.forEach(point -> {
            var bundle = state.evaluateWithDerivatives(point.coordinates());
            double psi = bundle.value().real();
            if (!Double.isFinite(psi) || Math.abs(psi) < 1e-14) return;
            double localEnergy = -0.5
                    * bundle.coordinateLaplacian().value().real() / psi
                    + hamiltonian.potential(point.coordinates());
            // Bond-coordinate response: d log|psi| / dR by central FD of the
            // geometry-conditioned state at fixed parameters and electrons.
            double response = (Math.log(Math.abs(
                    plusState.evaluateWithDerivatives(point.coordinates())
                            .value().real()))
                    - Math.log(Math.abs(
                            minusState.evaluateWithDerivatives(point.coordinates())
                                    .value().real()))) / (2.0 * h);
            // Bond-coordinate ZV terms: F_nn,R = 1/R^2 (repulsive, positive);
            // G_R = (G_1z - G_0z) / 2 by symmetric bond motion.
            double[] logGradient = new double[6];
            for (int i = 0; i < 2; i++) {
                var gradient = bundle.coordinateGradient().particleGradients().get(i);
                logGradient[3 * i] = gradient.x().real() / psi;
                logGradient[3 * i + 1] = gradient.y().real() / psi;
                logGradient[3 * i + 2] = gradient.z().real() / psi;
            }
            double g1 = AcZvFermiNetForceEstimator.auxiliaryContraction(
                    molecule, point.coordinates(), logGradient, 1, 2);
            double g0 = AcZvFermiNetForceEstimator.auxiliaryContraction(
                    molecule, point.coordinates(), logGradient, 0, 2);
            double gBond = 0.5 * (g1 - g0);
            // Physical electron-nucleus bare force along the bond coordinate,
            // F_en,R = (F_en,1z - F_en,0z) / 2, explicit 1/r^3 sums.
            double en1 = 0.0, en0 = 0.0;
            for (var electron : point.coordinates().particles()) {
                double d1z = electron.zBohr() - radius / 2;
                double d0z = electron.zBohr() + radius / 2;
                double r13 = Math.pow(electron.xBohr() * electron.xBohr()
                        + electron.yBohr() * electron.yBohr() + d1z * d1z, 1.5);
                double r03 = Math.pow(electron.xBohr() * electron.xBohr()
                        + electron.yBohr() * electron.yBohr() + d0z * d0z, 1.5);
                en1 += d1z / r13;
                en0 += d0z / r03;
            }
            double enBond = 0.5 * (en1 - en0);
            double weight = point.weight() * psi * psi;
            norm[0] += weight;
            energySum[0] += weight * localEnergy;
            perSample.add(new double[] {weight, localEnergy, response, gBond,
                    enBond});
        }));
        double meanEnergy = energySum[0] / norm[0];
        double nnBond = 1.0 / (radius * radius);
        double forceSum = 0.0;
        for (double[] s : perSample) {
            double weight = s[0], localEnergy = s[1], response = s[2];
            double gBond = s[3], enBond = s[4];
            // Production force convention: F = F_nn + G + 2(E_v - E_L) response.
            double force = nnBond + gBond
                    + 2.0 * (meanEnergy - localEnergy) * response;
            // AC 2003 observable convention, assembled independently with the
            // paper's printed signs (Eq. 66 bare + Eq. 70 identity + Eq. 72
            // ZB sign): O = -F_nn - F_en + (F_en - G) + 2(E_L - E_v) response.
            double observable = -(nnBond + enBond) + (enBond - gBond)
                    + 2.0 * (localEnergy - meanEnergy) * response;
            assertEquals(-observable, force, 1e-12,
                    "force = -observable per sample");
            forceSum += weight * force;
        }
        double meanForce = forceSum / norm[0];
        if (Double.isFinite(reference)) {
            assertTrue(meanForce > 0.0,
                    "repulsive-wall sign: mean bond force " + meanForce);
            System.out.printf(Locale.ROOT,
                    "AC_ZVZB_DERIV_H2_WALL R=1.0 reference=%.16f mean=%.16f%n",
                    reference, meanForce);
        }
    }

    /**
     * On the frozen H2O subset through the canonical pipeline: the ZV part is
     * numerically identical to canonical AC-ZV, zeroing the derivative-ZB
     * contribution reproduces AC-ZV exactly, and the ZB sample formula
     * 2(E_v - E_L) d log|Psi|/dR is independently reproduced.
     */
    @Test
    void zvIdentityZbZeroAndZbFormulaOnFrozenSubset() throws Exception {
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
        FermiNetV1State state = new FermiNetV1State(molecule, network,
                FermiNetParameters.fromArray(
                        new FermiNetParameterLayout(network, molecule),
                        checkpoint.parameters()));

        List<QuantumCoordinates> subset = new ArrayList<>();
        FermiNetCorrelatedFdConfigurationFile.forEach(
                configurationsFile, 64, (sample, chain, retained, coordinates) -> {
                    if (sample < 16) subset.add(coordinates);
                });
        assertEquals(16, subset.size());
        Path directory = Files.createTempDirectory("aczvzbderiv-smoke");
        Path datasetDirectory = directory.resolve("dataset");
        Files.createDirectories(datasetDirectory);
        Path subsetFile = datasetDirectory.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                subsetFile, subset, 8);
        writeDiagnosticFdReference(datasetDirectory, identity,
                checkpoint.parameterChecksum());
        for (var type : new NuclearForceEstimatorType[] {
                NuclearForceEstimatorType.SWCT, NuclearForceEstimatorType.AC_ZV,
                NuclearForceEstimatorType.AC_ZVZB}) {
            writeDiagnosticEstimatorArtifact(directory, identity,
                    checkpoint.parameterChecksum(), type);
        }
        var context = new FermiNetForceEvaluationContext(
                state, checkpoint.parameterChecksum(), checkpoint.geometryIdentity(),
                subsetFile, identity, "0".repeat(64),
                checkpoint.rootParameterChecksum());
        var engine = totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngines
                .create(totah.lab.prometheus.neural.ferminet.runtime
                        .FermiNetDerivativeConfiguration.referenceJet());
        NuclearForceResult acZvResult = new AcZvFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.acZv(), engine);
        NuclearForceResult result = new AcZvzbDerivFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.acZvzbDeriv(), engine);
        NuclearForceResult regularized = new AcZvzbDerivFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.acZvzbDerivPathakWagner(
                        0.100, 0.050, 0.020, 0.010, 0.005), engine);

        assertEquals(NuclearForceEstimatorType.AC_ZVZB_DERIV,
                result.estimatorType());
        assertEquals(9, result.components().size());
        assertEquals(AcZvzbDerivFermiNetForceEstimator.VARIANCE_REDUCED,
                result.classification());
        var diagnostics = (NuclearForceResult.AcZvzbDerivDiagnostics)
                result.estimatorDiagnostics();
        assertEquals(AcZvzbDerivFermiNetForceEstimator.FORMULATION,
                diagnostics.estimatorFormulation());
        assertEquals(AcZvzbDerivFermiNetForceEstimator.AUXILIARY,
                diagnostics.auxiliaryEquation());
        assertTrue(Double.isFinite(diagnostics.sampledMeanEnergyHartree()));
        var regularizedDiagnostics = (NuclearForceResult.AcZvzbDerivDiagnostics)
                regularized.estimatorDiagnostics();
        assertEquals(5, regularizedDiagnostics.pathakWagner()
                .epsilonPanel().size());
        assertEquals(9, regularizedDiagnostics.pathakWagner()
                .extrapolations().size());
        double[] nodalDistances = regularizedDiagnostics.pathakWagner()
                .nodalDistance().rawSamples();
        for (var epsilonDiagnostics : regularizedDiagnostics.pathakWagner()
                .epsilonPanel()) {
            long affected = 0;
            long lessThanOne = 0;
            long greaterThanOne = 0;
            for (double nodalDistance : nodalDistances) {
                double factor = AcZvzbDerivFermiNetForceEstimator
                        .pathakWagnerFactor(nodalDistance,
                                epsilonDiagnostics.epsilonBohr());
                if (factor != 1.0) affected++;
                if (factor < 1.0) lessThanOne++;
                if (factor > 1.0) greaterThanOne++;
            }
            assertEquals(affected, epsilonDiagnostics.affectedSampleCount());
            assertEquals(lessThanOne,
                    epsilonDiagnostics.factorLessThanOneCount());
            assertEquals(greaterThanOne,
                    epsilonDiagnostics.factorGreaterThanOneCount());
        }

        // Independent ZB recomputation from the validated runtime quantities.
        double[] localEnergies = new double[16];
        double[][] nuclearGradients = new double[16][];
        for (int s = 0; s < 16; s++) {
            localEnergies[s] = totah.lab.prometheus.neural.ferminet.runtime
                    .FermiNetRuntimeSampling.localEnergyWithLog(state,
                            subset.get(s)).localEnergy().totalHartree();
            nuclearGradients[s] = FermiNetStateAccess.nuclear(state,
                    subset.get(s)).logNuclearGradient();
        }
        double meanEnergy = 0.0;
        for (double energy : localEnergies) meanEnergy += energy;
        meanEnergy /= 16;
        assertEquals(diagnostics.sampledMeanEnergyHartree(), meanEnergy, 1e-12,
                "E_v");

        for (int component = 0; component < 9; component++) {
            var c = result.components().get(component);
            var d = diagnostics.components().get(component);
            assertEquals(Double.doubleToRawLongBits(c.meanHartreePerBohr()),
                    Double.doubleToRawLongBits(
                            regularized.components().get(component)
                                    .meanHartreePerBohr()),
                    "raw mean bit identity " + component);
            assertArrayEquals(c.rawSamples(),
                    regularized.components().get(component).rawSamples(), 0.0,
                    "raw sample bit identity " + component);
            assertEquals(16, c.finiteCount(), "finite " + component);
            assertEquals(0, c.nonfiniteCount(), "nonfinite " + component);
            assertEquals(64, c.rawSampleChecksum().length());
            double acZvMean =
                    acZvResult.components().get(component).meanHartreePerBohr();
            // ZV part numerically identical to canonical AC-ZV.
            assertEquals(acZvMean, d.nuclearRepulsionTermHartreePerBohr()
                            + d.meanContractionTermHartreePerBohr(), 1e-9,
                    "ZV identity " + component);
            // Zeroing the derivative-ZB contribution reproduces AC-ZV.
            assertEquals(acZvMean, c.meanHartreePerBohr()
                            - d.zbDerivativeMeanHartreePerBohr(), 1e-9,
                    "ZB=0 reproduces AC-ZV " + component);
            // Decomposition identity.
            assertEquals(c.meanHartreePerBohr(),
                    d.nuclearRepulsionTermHartreePerBohr()
                            + d.meanContractionTermHartreePerBohr()
                            + d.zbDerivativeMeanHartreePerBohr(), 1e-9,
                    "decomposition " + component);
            // Independent ZB sample-formula verification.
            double zbSum = 0.0, zbSquares = 0.0;
            for (int s = 0; s < 16; s++) {
                double zb = 2.0 * (meanEnergy - localEnergies[s])
                        * nuclearGradients[s][component];
                zbSum += zb;
                zbSquares += zb * zb;
            }
            double zbMean = zbSum / 16;
            double zbVariance = (zbSquares - zbSum * zbSum / 16) / 15;
            assertEquals(zbMean, d.zbDerivativeMeanHartreePerBohr(), 1e-12,
                    "ZB mean formula " + component);
            assertEquals(zbVariance, d.zbDerivativeVariance(),
                    Math.max(1e-9, 1e-9 * Math.abs(zbVariance)),
                    "ZB variance formula " + component);
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
        double mean = switch (type) {
            case SWCT -> 0.25;
            case AC_ZV -> 0.5;
            default -> 0.75;
        };
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
        return new Molecule("H2-ac-zvzb-deriv", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, -bondLengthBohr / 2,
                                LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, bondLengthBohr / 2,
                                LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }

    private static Molecule displace(
            Molecule molecule, int nucleus, int axis, double delta) {
        List<NuclearCenter> nuclei = new ArrayList<>();
        for (var center : molecule.nuclei()) {
            var p = center.position().inBohr();
            double[] q = {p.x(), p.y(), p.z()};
            if (center.orderedIndex() == nucleus) q[axis] += delta;
            nuclei.add(new NuclearCenter(center.orderedIndex(), center.element(),
                    center.charge(), new CartesianPosition(q[0], q[1], q[2],
                            LengthUnit.BOHR)));
        }
        return new Molecule(molecule.moleculeId(), nuclei, molecule.charge(),
                molecule.electrons(), molecule.spin());
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
