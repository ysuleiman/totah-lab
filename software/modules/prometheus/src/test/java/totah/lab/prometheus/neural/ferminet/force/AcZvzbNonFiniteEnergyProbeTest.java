package totah.lab.prometheus.neural.ferminet.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

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
import totah.lab.prometheus.neural.ferminet.force.AcZvzbFermiNetForceEstimator;
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
 * Defect probe: non-finite local energy inside
 * {@link AcZvzbFermiNetForceEstimator#estimate}.
 *
 * <p>The estimator javadoc (AcZvzbFermiNetForceEstimator.java lines 54-58)
 * documents classification IMPLEMENTATION_FAILURE "when any sample or
 * component is non-finite", implying a non-finite sample is recorded (NaN)
 * like the singularity path. But the sampling callback (lines 123-136)
 * catches only {@link FermiNetPhysicalSingularityException}. The guards that
 * actually fire throw other unchecked types, all uncaught: the Coulomb
 * distance guard (FermiNetVmc.java lines 782-787, IllegalArgumentException
 * "Coulomb singularity", firing already at r <= 1e-12 bohr), the local-energy
 * component guard (FermiNetVmc.java lines 472-479, IllegalStateException),
 * and the estimator's own non-finite guard (line 129, IllegalStateException,
 * effectively unreachable for Coulomb non-finiteness because distance()
 * throws first). Each propagates out of the forEach callback and aborts the
 * whole estimate().
 *
 * <p>The class declares the runtime package to reach the package-private
 * {@link FermiNetV1Configuration#testFixture} (precedent:
 * FermiNetCorrelatedFiniteDifferenceForceReferenceTest).
 */
final class AcZvzbNonFiniteEnergyProbeTest {

    @TempDir Path temporary;

    /**
     * NONFINITE-ZVZB-01 (reachability control / exception-channel audit): an
     * electron exactly on a nucleus passes electron-configuration validation
     * and the FermiNet spatial pass (no determinant singularity), then the
     * electron-nuclear Coulomb distance guard throws
     * IllegalArgumentException("Coulomb singularity") at FermiNetVmc.java
     * lines 782-787 - NOT the FermiNetPhysicalSingularityException that the
     * estimate() catch block handles. A generic configuration on the same
     * seeded fixture must evaluate finitely, so the trigger is the
     * coalescence, not a broken fixture.
     */
    @Test
    void coulombSingularityEscapesAsIllegalArgumentNotPhysicalSingularityChannel() {
        FermiNetV1State state = fixtureState();

        double generic = FermiNetRuntimeSampling.localEnergyWithLog(
                state, genericCoordinates()).localEnergy().totalHartree();
        assertThat(generic)
                .as("generic configuration on the seeded fixture must be finite")
                .isFinite();

        Throwable thrown = catchThrowable(() -> FermiNetRuntimeSampling
                .localEnergyWithLog(state, coalescedCoordinates()));
        assertThat(thrown)
                .as("electron-nucleus coalescence escapes through the"
                        + " IllegalArgumentException Coulomb-singularity guard,"
                        + " FermiNetVmc.java:782-787, not the channel"
                        + " estimate() catches")
                .isInstanceOf(IllegalArgumentException.class)
                .isNotInstanceOf(FermiNetPhysicalSingularityException.class)
                .hasMessageContaining("Coulomb singularity");
    }

    /**
     * NONFINITE-ZVZB-02 (documented-intent oracle): a dataset containing one
     * coalesced sample (non-finite local energy, no determinant singularity)
     * must, per the estimator javadoc, be recorded as a NaN sample and
     * classify IMPLEMENTATION_FAILURE - not abort the entire estimate().
     *
     * <p>DEFECT: the IllegalArgumentException("Coulomb singularity") from
     * FermiNetVmc.java:785 (and likewise the IllegalStateException channels
     * at FermiNetVmc.java:477 and AcZvzbFermiNetForceEstimator.java:129) is
     * not caught by the FermiNetPhysicalSingularityException-only catch at
     * lines 133-136, so estimate() throws instead of returning the documented
     * IMPLEMENTATION_FAILURE classification. This test is EXPECTED TO FAIL
     * until the production catch is widened; do not weaken it.
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

        var context = new FermiNetForceEvaluationContext(
                state, parameterChecksum, geometryIdentity, configurationFile,
                identity, "0".repeat(64), parameterChecksum);

        NuclearForceResult result = new AcZvzbFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.acZvzb(),
                FermiNetDerivativeEngines.create(
                        FermiNetDerivativeConfiguration.referenceJet()));

        assertThat(result.classification())
                .as("javadoc lines 54-58: any non-finite sample must classify"
                        + " IMPLEMENTATION_FAILURE, not abort estimate()")
                .isEqualTo(AcZvzbFermiNetForceEstimator.IMPLEMENTATION_FAILURE);
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
        return new Molecule("h2-nonfinite-probe", List.of(
                new NuclearCenter(0, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, -0.7, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(0, 0, 0.7, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2),
                new SpinSector(1, 1, 1));
    }
}
