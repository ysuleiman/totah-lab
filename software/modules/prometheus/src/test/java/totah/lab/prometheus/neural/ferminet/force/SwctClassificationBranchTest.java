package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import totah.lab.prometheus.neural.ferminet.pretraining.FermiNetPretrainingQualification;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFdConfigurationFile;
import totah.lab.prometheus.neural.ferminet.reference.FermiNetCorrelatedFiniteDifferenceForceReference;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeConfiguration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetDerivativeEngines;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetOptimizationCheckpoint;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameterLayout;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetParameters;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Branch falsification for the SWCT classification logic in
 * {@link SwctFermiNetForceEstimator#estimate} (lines ~247-256):
 * SWCT_VARIANCE_REDUCED when every component beats the correlated-FD
 * reference variance, SWCT_VARIANCE_FAILURE when none does, and
 * SWCT_NUMERICALLY_OPERATIONAL for the mixed case. The correlated-FD
 * reference is fabricated with steered per-component forceVariance values;
 * every other provenance field is kept consistent so the load gates
 * (parameterChecksum + dataset identity, lines ~640-644) pass. This tests
 * branch logic only, never scientific agreement.
 *
 * <p>The network is the small public-constructor equivalent of the
 * package-private testFixture configuration (2 layers, widths 8/4, 2
 * determinants), seeded identically to the reference tests (44017L).
 */
final class SwctClassificationBranchTest {

    private static final int COMPONENTS = 9;
    private static final int SAMPLES = 4;
    private static final int WALKERS = 2;

    @TempDir Path temporary;

    /** BRANCH-SWCT-01 (control): all FD variances larger -> VARIANCE_REDUCED. */
    @Test
    void allFdVariancesLargerThanEstimatorYieldVarianceReduced()
            throws Exception {
        FermiNetForceEvaluationContext context = newContext();
        NuclearForceResult result = estimate(context, uniform(1.0e12));

        assertThat(result.classification())
                .isEqualTo(SwctFermiNetForceEstimator.VARIANCE_REDUCED);
        assertThat(result.components()).hasSize(COMPONENTS);
        for (var component : result.components()) {
            assertThat(component.finiteCount()).isEqualTo(SAMPLES);
            assertThat(component.nonfiniteCount()).isEqualTo(0);
            // Sanity: steering baseline requires finite positive variances.
            assertThat(component.variance()).isFinite().isPositive();
        }
    }

    /** BRANCH-SWCT-02: all FD variances smaller -> VARIANCE_FAILURE. */
    @Test
    void allFdVariancesSmallerThanEstimatorYieldVarianceFailure()
            throws Exception {
        FermiNetForceEvaluationContext context = newContext();
        double[] observed = observedEstimatorVariances(context);
        double[] smaller = scaled(observed, 1.0e-3);

        NuclearForceResult result = estimate(context, smaller);

        assertThat(result.classification())
                .isEqualTo(SwctFermiNetForceEstimator.VARIANCE_FAILURE);
        for (int component = 0; component < COMPONENTS; component++) {
            // Explicitly verify the branch precondition: no component reduced.
            assertThat(result.components().get(component).variance())
                    .as("estimator variance component %d", component)
                    .isGreaterThan(smaller[component]);
        }
    }

    /** BRANCH-SWCT-03: mixed FD variances -> NUMERICALLY_OPERATIONAL. */
    @Test
    void mixedFdVariancesYieldNumericallyOperational() throws Exception {
        FermiNetForceEvaluationContext context = newContext();
        double[] observed = observedEstimatorVariances(context);
        double[] mixed = new double[COMPONENTS];
        for (int component = 0; component < COMPONENTS; component++) {
            mixed[component] = observed[component]
                    * (component % 2 == 0 ? 1.0e3 : 1.0e-3);
        }

        NuclearForceResult result = estimate(context, mixed);

        assertThat(result.classification())
                .isEqualTo(SwctFermiNetForceEstimator.NUMERICALLY_OPERATIONAL);
        int reduced = 0;
        for (int component = 0; component < COMPONENTS; component++) {
            boolean isReduced = result.components().get(component).variance()
                    < mixed[component];
            assertThat(isReduced)
                    .as("reduction steering component %d", component)
                    .isEqualTo(component % 2 == 0);
            if (isReduced) {
                reduced++;
            }
        }
        assertThat(reduced).isEqualTo(5);
    }

    // ------------------------------------------------------------------
    // Self-contained fixture helpers (copiable for reuse).
    // ------------------------------------------------------------------

    /**
     * Runs the estimator once against an all-huge fabricated reference
     * (control branch) and reads back the per-component estimator variances
     * used for steering. Asserts the control classification and finite
     * positive variances so steering is well-defined.
     */
    private double[] observedEstimatorVariances(
            FermiNetForceEvaluationContext context) throws Exception {
        NuclearForceResult probe = estimate(context, uniform(1.0e12));
        assertThat(probe.classification())
                .isEqualTo(SwctFermiNetForceEstimator.VARIANCE_REDUCED);
        double[] variances = new double[COMPONENTS];
        for (int component = 0; component < COMPONENTS; component++) {
            variances[component] = probe.components().get(component).variance();
            assertThat(variances[component])
                    .as("estimator variance component %d", component)
                    .isFinite().isPositive();
        }
        return variances;
    }

    private NuclearForceResult estimate(
            FermiNetForceEvaluationContext context, double[] fdForceVariances)
            throws Exception {
        writeFdReference(context, fdForceVariances);
        return new SwctFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.swct(),
                FermiNetDerivativeEngines.create(
                        FermiNetDerivativeConfiguration.batchedForward()));
    }

    private FermiNetForceEvaluationContext newContext() throws Exception {
        Molecule molecule = water();
        // Same dimensions as the package-private testFixture configuration,
        // via the public canonical constructor.
        FermiNetV1Configuration network = new FermiNetV1Configuration(
                3, 2, 8, 4, 2, true, true, false, false, false, false);
        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(network, molecule);
        FermiNetV1State state = new FermiNetV1State(molecule, network,
                FermiNetParameters.initialize(layout, 44017L));
        String parameters = FermiNetOptimizationCheckpoint.parameterChecksum(
                FermiNetStateAccess.parameterSnapshot(state));
        String geometry =
                FermiNetPretrainingQualification.geometryIdentity(molecule);
        Path file = temporary.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                file, samples(), WALKERS);
        return new FermiNetForceEvaluationContext(
                state, parameters, geometry, file, identity,
                "0".repeat(64), parameters);
    }

    /**
     * Fabricated diagnostic-only FD stand-in with steered per-component
     * forceVariance; never a scientific reference. All other provenance
     * fields match the context so the estimator load gates pass.
     */
    private static void writeFdReference(
            FermiNetForceEvaluationContext context, double[] fdForceVariances)
            throws Exception {
        var tails = new FermiNetCorrelatedFiniteDifferenceForceReference
                .TailDiagnostics(0, 0, 0, 0, 0, 0, 0, 0, 0);
        List<FermiNetCorrelatedFiniteDifferenceForceReference.ComponentResult>
                components = new ArrayList<>();
        int index = 0;
        for (int nucleus = 0; nucleus < 3; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                components.add(
                        new FermiNetCorrelatedFiniteDifferenceForceReference
                                .ComponentResult(nucleus, axis,
                                        switch (axis) {
                                            case 0 -> "x";
                                            case 1 -> "y";
                                            default -> "z";
                                        },
                                        0.0, 0.0, 0.0, 0.0, 1.0, 1.0,
                                        fdForceVariances[index++],
                                        1.0, 1.0, 1.0,
                                        context.parameterChecksum(),
                                        "0".repeat(64), "0".repeat(64), tails,
                                        new double[] {0.0}));
            }
        }
        new ObjectMapper().writeValue(
                context.configurationFile().getParent()
                        .resolve("correlated-fd-reference.json").toFile(),
                new FermiNetCorrelatedFiniteDifferenceForceReference.Result(
                        1.0e-3, context.parameterChecksum(), "0".repeat(64),
                        context.dataset(), components));
    }

    private static double[] uniform(double value) {
        double[] values = new double[COMPONENTS];
        java.util.Arrays.fill(values, value);
        return values;
    }

    private static double[] scaled(double[] values, double factor) {
        double[] scaled = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            scaled[i] = values[i] * factor;
        }
        return scaled;
    }

    private static List<QuantumCoordinates> samples() {
        return List.of(
                coordinates(0.00), coordinates(0.02),
                coordinates(-0.01), coordinates(0.03));
    }

    private static QuantumCoordinates coordinates(double shift) {
        double[][] xyz = {
                {0.18, 0.11, 0.27}, {-0.31, 0.42, -0.16},
                {0.57, -0.28, 0.33}, {-0.63, -0.37, 0.21},
                {0.24, 0.71, -0.45}, {-0.22, -0.15, -0.38},
                {0.36, -0.54, 0.19}, {-0.48, 0.26, 0.51},
                {0.69, 0.18, -0.24}, {-0.12, 0.61, 0.37}
        };
        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();
        for (int i = 0; i < xyz.length; i++) {
            particles.add(new QuantumCoordinates.ParticleCoordinate(
                    i, xyz[i][0] + shift, xyz[i][1], xyz[i][2],
                    i < 5 ? SpinProjection.ALPHA : SpinProjection.BETA));
        }
        return new QuantumCoordinates(particles);
    }

    private static Molecule water() {
        return new Molecule("ferminet-v1-water", List.of(
                new NuclearCenter(0, "O", new NuclearCharge(8),
                        new CartesianPosition(0.0, 0.0, 0.0, LengthUnit.BOHR)),
                new NuclearCenter(1, "H", new NuclearCharge(1),
                        new CartesianPosition(1.7952398191849366, 0.0, 0.0,
                                LengthUnit.BOHR)),
                new NuclearCenter(2, "H", new NuclearCharge(1),
                        new CartesianPosition(-0.46464225035067114,
                                1.7340684963325879, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(10),
                new SpinSector(5, 5, 1));
    }
}
