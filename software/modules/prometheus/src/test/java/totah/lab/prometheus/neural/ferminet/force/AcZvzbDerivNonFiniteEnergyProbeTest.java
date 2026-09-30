package totah.lab.prometheus.neural.ferminet.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
import totah.lab.prometheus.neural.ferminet.force.AcZvzbDerivFermiNetForceEstimator;
import totah.lab.prometheus.neural.ferminet.force.FermiNetForceEvaluationContext;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceConfiguration;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceEstimatorType;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceResult;
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Second-instance defect probe: non-finite local energy inside
 * {@link AcZvzbDerivFermiNetForceEstimator#estimate}, mirroring
 * NONFINITE-ZVZB-02 (AcZvzbNonFiniteEnergyProbeTest) for the AC_ZVZB_DERIV
 * estimator.
 *
 * <p>The estimator javadoc (AcZvzbDerivFermiNetForceEstimator.java lines
 * 56-61) documents classification IMPLEMENTATION_FAILURE "when any sample or
 * component is non-finite", implying a non-finite sample is recorded (NaN)
 * like the singularity path. But the sampling callback (lines 134-151)
 * catches only {@link FermiNetPhysicalSingularityException}; the Coulomb
 * distance guard (FermiNetVmc.java lines 782-787) throws
 * IllegalArgumentException("Coulomb singularity"), which propagates out of
 * the forEach callback and aborts the whole estimate().
 *
 * <p>The class declares the runtime package to reach the package-private
 * {@link FermiNetV1Configuration#testFixture} (precedent:
 * FermiNetCorrelatedFiniteDifferenceForceReferenceTest,
 * AcZvzbNonFiniteEnergyProbeTest).
 */
final class AcZvzbDerivNonFiniteEnergyProbeTest {

    @TempDir Path temporary;

    /**
     * NONFINITE-ZVZB-DERIV-01 (documented-intent oracle): a dataset
     * containing one coalesced sample (electron exactly on nucleus 0) must,
     * per the estimator javadoc, be recorded as a NaN sample and classify
     * AC_ZVZB_DERIV_IMPLEMENTATION_FAILURE - not abort the entire estimate().
     *
     * <p>DEFECT (second confirmed instance of NONFINITE-ZVZB-02): the
     * IllegalArgumentException("Coulomb singularity") from FermiNetVmc.java:
     * 785 is not caught by the FermiNetPhysicalSingularityException-only
     * catch at AcZvzbDerivFermiNetForceEstimator.java:147-151, so estimate()
     * throws instead of returning the documented IMPLEMENTATION_FAILURE
     * classification. This test is EXPECTED TO FAIL until the production
     * catch is widened; do not weaken it.
     */
    @Test
    void nonFiniteLocalEnergySampleClassifiesImplementationFailureNotAbort()
            throws Exception {
        Molecule molecule = h2();
        FermiNetV1State state = fixtureState();
        String parameterChecksum = FermiNetOptimizationCheckpoint
                .parameterChecksum(FermiNetStateAccess.parameterSnapshot(state));
        String geometryIdentity = FermiNetPretrainingQualification
                .geometryIdentity(molecule);

        // Fixture sanity: the generic sample evaluates finitely on this
        // seeded network, so the trigger below is the coalescence itself.
        double generic = FermiNetRuntimeSampling.localEnergyWithLog(
                state, genericCoordinates()).localEnergy().totalHartree();
        assertThat(generic)
                .as("generic configuration on the seeded fixture must be finite")
                .isFinite();

        Path dataDirectory = Files.createDirectories(temporary.resolve("dataset"));
        Path configurationFile = dataDirectory.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                configurationFile,
                List.of(coalescedCoordinates(), genericCoordinates()), 2);
        writeDiagnosticFdReference(dataDirectory, identity, parameterChecksum);
        writeEstimatorComparisonArtifact(identity, parameterChecksum,
                NuclearForceEstimatorType.SWCT);
        writeEstimatorComparisonArtifact(identity, parameterChecksum,
                NuclearForceEstimatorType.AC_ZV);
        writeEstimatorComparisonArtifact(identity, parameterChecksum,
                NuclearForceEstimatorType.AC_ZVZB);

        var context = new FermiNetForceEvaluationContext(
                state, parameterChecksum, geometryIdentity, configurationFile,
                identity, "0".repeat(64), parameterChecksum);

        NuclearForceResult result = new AcZvzbDerivFermiNetForceEstimator()
                .estimate(context, NuclearForceConfiguration.acZvzbDeriv(),
                        FermiNetDerivativeEngines.create(
                                FermiNetDerivativeConfiguration.referenceJet()));

        assertThat(result.classification())
                .as("javadoc lines 56-61: any non-finite sample must classify"
                        + " AC_ZVZB_DERIV_IMPLEMENTATION_FAILURE, not abort"
                        + " estimate()")
                .isEqualTo(AcZvzbDerivFermiNetForceEstimator
                        .IMPLEMENTATION_FAILURE);
        for (var component : result.components()) {
            assertThat(component.finiteCount()).isEqualTo(1);
            assertThat(component.nonfiniteCount()).isEqualTo(1);
        }
    }

    private static FermiNetV1State fixtureState() {
        Molecule molecule = h2();
        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();
        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(configuration, molecule);
        return new FermiNetV1State(molecule, configuration,
                FermiNetParameters.initialize(layout, 44017L));
    }

    /** Electron 0 sits exactly on nucleus 0 at (0, 0, -0.7) bohr. */
    private static QuantumCoordinates coalescedCoordinates() {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(
                        0, 0.0, 0.0, -0.7, SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(
                        1, 0.31, -0.22, 0.48, SpinProjection.BETA)));
    }

    private static QuantumCoordinates genericCoordinates() {
        return new QuantumCoordinates(List.of(
                new QuantumCoordinates.ParticleCoordinate(
                        0, 0.42, 0.13, -0.35, SpinProjection.ALPHA),
                new QuantumCoordinates.ParticleCoordinate(
                        1, -0.27, 0.51, 0.19, SpinProjection.BETA)));
    }

    /** Minimal hand-built estimator artifact; only the loader-read fields. */
    private void writeEstimatorComparisonArtifact(
            FermiNetCorrelatedFdConfigurationFile.Identity identity,
            String parameterChecksum,
            NuclearForceEstimatorType type) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ObjectNode result = root.putObject("result");
        result.put("estimatorType", type.name());
        result.put("parameterChecksum", parameterChecksum);
        result.put("datasetChecksum", identity.sha256());
        ArrayNode components = result.putArray("components");
        for (int nucleus = 0; nucleus < 2; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                ObjectNode component = components.addObject();
                component.put("nucleus", nucleus);
                component.put("axis", axis);
                component.put("meanHartreePerBohr", 0.0);
                component.put("chainStandardError", 1.0);
                component.put("variance", 1.0e6);
            }
        }
        Path file = temporary.resolve(identity.sha256()).resolve(type.name())
                .resolve("nuclear-force-result.json");
        Files.createDirectories(file.getParent());
        mapper.writeValue(file.toFile(), root);
    }

    /** Diagnostic-only FD stand-in with huge variance; never a scientific reference. */
    private static void writeDiagnosticFdReference(
            Path directory,
            FermiNetCorrelatedFdConfigurationFile.Identity identity,
            String parameterChecksum) throws IOException {
        var tails = new FermiNetCorrelatedFiniteDifferenceForceReference
                .TailDiagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0);
        List<FermiNetCorrelatedFiniteDifferenceForceReference.ComponentResult>
                components = new ArrayList<>();
        for (int nucleus = 0; nucleus < 2; nucleus++) {
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
        return new Molecule("h2-nonfinite-deriv-probe", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, -0.7, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, 0.7, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }
}
