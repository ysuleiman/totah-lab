package totah.lab.prometheus.neural.ferminet.force;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
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
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetRuntimeSampling;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetStateAccess;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1Configuration;
import totah.lab.prometheus.neural.ferminet.runtime.FermiNetV1State;

/**
 * Cross-estimator agreement falsification (campaign task A7, rebuilt on a
 * properly sampled fixture): the AC-ZV estimator
 * ({@link AcZvFermiNetForceEstimator}) is checked against the REAL correlated
 * finite-difference reference
 * ({@link FermiNetCorrelatedFiniteDifferenceForceReference}), both evaluated
 * in-JVM on the same small seeded water network and the same seeded
 * Metropolis |&Psi;|&sup2;-sampled dataset. The correlated-fd-reference.json
 * written here is the genuine serialized reference {@code Result}, not a
 * fabricated diagnostic stand-in, so the comparison values are scientifically
 * meaningful.
 *
 * <p>Fixture history: the first version of this test used the deterministic
 * 32-point SWCT-style dataset and failed with z-scores up to 180.6. Root
 * cause: the AC-ZV estimator forms an unweighted sample mean of
 * F_nn + div Q . grad log|&Psi;|, which estimates the Hellmann-Feynman force
 * only under |&Psi;|&sup2; sampling, while the correlated-FD reference
 * reweights (self-normalized |&Psi;&plusmn;|&sup2;/|&Psi;|&sup2; importance
 * weights) and estimates -dE/dR under any carrier distribution. Under an
 * arbitrary point set the two means target different quantities, so the
 * oracle was inapplicable. The fixture here draws 4 walkers x 256 retained
 * configurations (1024 samples) from |&Psi;|&sup2; via the seeded RWM
 * sampler ({@link FermiNetRuntimeSampling#sampleSerial}), with production
 * VMC parameters (move width 0.02 bohr, spacing 10 sweeps, 500 warmup
 * sweeps, seed 20260823L) on the public-constructor equivalent of the
 * package-private testFixture network (dims 3,2,8,4,2; parameter seed
 * 44017L), so both estimators now target the same frozen-parameter
 * expectation values.
 *
 * <p>Per the {@link AcZvFermiNetForceEstimator} class javadoc, an
 * indistinguishable difference of means is NOT evidence of unbiasedness;
 * these tests assert statistical consistency at a diagnostic k=5 level, never
 * equality. Observed on the locked sampled fixture (1024 samples, acceptance
 * 0.931, local-energy mean -22.11 Ha / sd 7.42 Ha, consistent with the
 * unconverged random network): XEST-01 max |z| = 0.416 across all 9
 * components; XEST-02 net-force z = 1.07 / 4.04 / 0.27 on axes x/y/z;
 * XEST-03 classification AC_ZV_VARIANCE_REDUCED. The estimators agree
 * statistically under proper |&Psi;|&sup2; sampling; the earlier red result
 * on the deterministic fixture was a fixture artifact, not an estimator
 * defect. Runtime ~67 s, dominated by the 9-component correlated-FD
 * reference evaluation.
 */
final class CrossEstimatorFdAgreementTest {

    private static final int COMPONENTS = 9;
    private static final int WALKERS = 4;
    private static final int RETAINED_PER_WALKER = 256;
    private static final int SAMPLES = WALKERS * RETAINED_PER_WALKER;
    private static final int WARMUP_SWEEPS = 500;
    private static final int SWEEPS_BETWEEN_RETAINED = 10;
    private static final double STEP_SIZE_BOHR = 0.02;
    private static final long SAMPLING_SEED = 20260823L;
    /** Diagnostic generosity: consistency threshold in combined sigmas. */
    private static final double K = 5.0;

    @TempDir static Path temporary;

    private static Comparison comparison;

    /**
     * Builds the seeded water fixture once: draws the |&Psi;|&sup2; sample
     * set in-JVM, verifies every retained local energy is finite (plausible
     * |&Psi;|&sup2; draw sanity), writes the dataset, evaluates the REAL
     * correlated-FD reference, serializes it verbatim as
     * correlated-fd-reference.json next to the configurations file (its
     * parameter checksum and dataset identity genuinely match the context, so
     * the estimator provenance load gates pass on real evidence), and runs
     * the AC-ZV estimator.
     */
    @BeforeAll
    static void buildSampledFixture() throws Exception {
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

        // Seeded Metropolis draw from |Psi|^2; retained samples are
        // walker-interleaved (retained step major, walker minor), exactly the
        // chain = sample % walkers topology the dataset writer encodes.
        var sampling = FermiNetRuntimeSampling.sampleSerial(state,
                new FermiNetRuntimeSampling.Request(WALKERS, WARMUP_SWEEPS,
                        RETAINED_PER_WALKER, SWEEPS_BETWEEN_RETAINED,
                        STEP_SIZE_BOHR, SAMPLING_SEED));
        assertThat(sampling.samples()).hasSize(SAMPLES);
        assertThat(sampling.acceptance()).isBetween(0.0, 1.0);
        double energySum = 0.0;
        double energySquareSum = 0.0;
        for (var energy : sampling.localEnergies()) {
            assertThat(energy.totalHartree())
                    .as("retained local energy finiteness").isFinite();
            energySum += energy.totalHartree();
            energySquareSum += energy.totalHartree() * energy.totalHartree();
        }
        double energyMean = energySum / SAMPLES;
        double energyVariance = Math.max(0.0,
                (energySquareSum - energySum * energySum / SAMPLES)
                        / (SAMPLES - 1));
        System.out.printf(java.util.Locale.ROOT,
                "XEST-FIXTURE SAMPLES=%d WALKERS=%d RETAINED=%d ACCEPTANCE=%.6f"
                        + " ENERGY_MEAN=%.10f ENERGY_SD=%.6e%n",
                SAMPLES, WALKERS, RETAINED_PER_WALKER, sampling.acceptance(),
                energyMean, Math.sqrt(energyVariance));

        Path file = temporary.resolve("configurations.csv");
        var identity = FermiNetCorrelatedFdConfigurationFile.write(
                file, sampling.samples(), WALKERS);
        var context = new FermiNetForceEvaluationContext(
                state, parameters, geometry, file, identity,
                "0".repeat(64), parameters);

        // The REAL correlated-FD reference, evaluated in-JVM on the same
        // frozen state and dataset; serialized verbatim via Jackson.
        var reference = new FermiNetCorrelatedFiniteDifferenceForceReference()
                .evaluate(state, file, WALKERS);
        assertThat(reference.parameterChecksum()).isEqualTo(parameters);
        assertThat(reference.dataset()).isEqualTo(identity);
        new ObjectMapper().writeValue(
                file.getParent().resolve("correlated-fd-reference.json").toFile(),
                reference);

        NuclearForceResult result = new AcZvFermiNetForceEstimator().estimate(
                context, NuclearForceConfiguration.acZv(),
                FermiNetDerivativeEngines.create(
                        FermiNetDerivativeConfiguration.batchedForward()));
        comparison = new Comparison(result, reference);
    }

    /**
     * XEST-01: per-component statistical consistency,
     * |mean_ACZV - force_FD| &le; K * sqrt(se_ACZV^2 + se_FD^2), for all 9
     * nucleus/axis components, with both standard errors taken from the two
     * independent in-JVM evaluations on the |&Psi;|&sup2;-sampled dataset.
     */
    @Test
    void acZvMeansAreStatisticallyConsistentWithGenuineCorrelatedFdReference()
            throws Exception {
        double[] zScores = new double[COMPONENTS];
        for (int component = 0; component < COMPONENTS; component++) {
            var aczv = comparison.result().components().get(component);
            var fd = comparison.reference().components().get(component);
            assertThat(aczv.nucleus()).isEqualTo(fd.nucleus());
            assertThat(aczv.axis()).isEqualTo(fd.axis());
            assertThat(aczv.finiteCount()).isEqualTo(SAMPLES);
            assertThat(aczv.nonfiniteCount()).isEqualTo(0);
            assertThat(aczv.chainStandardError())
                    .as("AC-ZV chain SE component %d", component)
                    .isFinite().isPositive();
            assertThat(fd.forceStandardError())
                    .as("FD chain SE component %d", component)
                    .isFinite().isPositive();
            double difference = Math.abs(
                    aczv.meanHartreePerBohr() - fd.forceHartreePerBohr());
            double combined = Math.sqrt(
                    aczv.chainStandardError() * aczv.chainStandardError()
                            + fd.forceStandardError() * fd.forceStandardError());
            zScores[component] = difference / combined;
            System.out.printf(java.util.Locale.ROOT,
                    "XEST-01 COMPONENT=%d ACZV_MEAN=%.16e FD_FORCE=%.16e"
                            + " COMBINED_SE=%.6e Z=%.6f%n",
                    component, aczv.meanHartreePerBohr(),
                    fd.forceHartreePerBohr(), combined, zScores[component]);
        }
        for (int component = 0; component < COMPONENTS; component++) {
            assertThat(zScores[component])
                    .as("consistency z-score component %d", component)
                    .isLessThanOrEqualTo(K);
        }
    }

    /**
     * XEST-02: translational invariance. The nucleus-nucleus part is
     * pairwise antisymmetric, so its sum over nuclei vanishes exactly
     * (asserted to 1e-12); the contraction part is zero-mean only in
     * expectation, so the net AC-ZV force per axis must vanish only within
     * its own statistical uncertainty: |sum over nuclei of mean_ACZV|
     * &le; K * sqrt(sum of per-nucleus chain SE^2). No tight tolerance is
     * applied to the stochastic part.
     */
    @Test
    void acZvNetForceOnNucleiVanishesWithinStatisticalUncertainty()
            throws Exception {
        var diagnostics = (NuclearForceResult.AcZvDiagnostics)
                comparison.result().estimatorDiagnostics();

        double[] netMeans = new double[3];
        double[] netCombined = new double[3];
        for (int axis = 0; axis < 3; axis++) {
            double netNn = 0.0;
            double netVarianceOfMean = 0.0;
            for (int nucleus = 0; nucleus < 3; nucleus++) {
                int component = 3 * nucleus + axis;
                var aczv = comparison.result().components().get(component);
                netMeans[axis] += aczv.meanHartreePerBohr();
                netNn += diagnostics.components().get(component)
                        .nuclearRepulsionTermHartreePerBohr();
                netVarianceOfMean += aczv.chainStandardError()
                        * aczv.chainStandardError();
            }
            // Exact: Newton's third law on the pairwise nn term.
            assertThat(netNn)
                    .as("nn net force axis %d must vanish exactly", axis)
                    .isEqualTo(0.0,
                            org.assertj.core.data.Offset.offset(1.0e-12));
            netCombined[axis] = Math.sqrt(netVarianceOfMean);
            System.out.printf(java.util.Locale.ROOT,
                    "XEST-02 AXIS=%d NET_MEAN=%.16e COMBINED_SE=%.6e Z=%.6f%n",
                    axis, netMeans[axis], netCombined[axis],
                    Math.abs(netMeans[axis]) / netCombined[axis]);
        }
        for (int axis = 0; axis < 3; axis++) {
            // Stochastic: contraction sum is zero-mean only in expectation.
            assertThat(Math.abs(netMeans[axis]) / netCombined[axis])
                    .as("net-force z-score axis %d: |%.6e| <= %.1f * %.6e",
                            axis, netMeans[axis], K, netCombined[axis])
                    .isLessThanOrEqualTo(K);
        }
    }

    /**
     * XEST-03: with the genuine reference in place, the classification must
     * be one of the documented non-failure evidence levels; every sample of
     * every component must be finite (the IMPLEMENTATION_FAILURE trigger).
     */
    @Test
    void genuineReferenceYieldsNonFailureClassification() throws Exception {
        assertThat(comparison.result().classification()).isIn(
                AcZvFermiNetForceEstimator.NUMERICALLY_OPERATIONAL,
                AcZvFermiNetForceEstimator.VARIANCE_REDUCED,
                AcZvFermiNetForceEstimator.VARIANCE_FAILURE);
        assertThat(comparison.result().classification())
                .isNotEqualTo(AcZvFermiNetForceEstimator.IMPLEMENTATION_FAILURE);
        assertThat(comparison.result().components()).hasSize(COMPONENTS);
        for (int component = 0; component < COMPONENTS; component++) {
            var aczv = comparison.result().components().get(component);
            assertThat(aczv.finiteCount())
                    .as("finite count component %d", component)
                    .isEqualTo(SAMPLES);
            assertThat(aczv.nonfiniteCount())
                    .as("nonfinite count component %d", component)
                    .isEqualTo(0);
            assertThat(aczv.meanHartreePerBohr()).isFinite();
            assertThat(comparison.reference().components().get(component)
                    .forceHartreePerBohr()).isFinite();
        }
        System.out.printf(java.util.Locale.ROOT,
                "XEST-03 CLASSIFICATION=%s%n",
                comparison.result().classification());
    }

    // ------------------------------------------------------------------
    // Self-contained fixture helpers.
    // ------------------------------------------------------------------

    /** AC-ZV result plus the genuine reference it was checked against. */
    private record Comparison(
            NuclearForceResult result,
            FermiNetCorrelatedFiniteDifferenceForceReference.Result reference) {}

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
