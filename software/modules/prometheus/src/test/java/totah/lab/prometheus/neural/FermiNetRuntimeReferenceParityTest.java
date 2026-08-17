package totah.lab.prometheus.neural;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.LocalEnergyComponents;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;

/**
 * Runtime parity against the frozen DeepMind/JAX FermiNet reference fixture.
 *
 * <p>Reference commit:
 * c4312c315dda1c5728994ba89629744f71c6eb66
 *
 * <p>This test independently checks the complete runtime boundary:
 *
 * <ul>
 *   <li>wavefunction sign</li>
 *   <li>log|Psi|</li>
 *   <li>all 30 coordinate derivatives of log|Psi|</li>
 *   <li>nabla^2 Psi / Psi</li>
 *   <li>all local-energy components</li>
 *   <li>total local energy</li>
 *   <li>frozen Metropolis proposal log|Psi|</li>
 *   <li>Metropolis log acceptance ratio</li>
 * </ul>
 */
final class FermiNetRuntimeReferenceParityTest {

    private static final String REFERENCE_COMMIT =
            "c4312c315dda1c5728994ba89629744f71c6eb66";

    private static final String FIXTURE_RESOURCE =
            "/totah/lab/prometheus/neural/ferminet-runtime-reference-parity-v1.json";

    /*
     * These are direct JAX reference values, not Java finite differences.
     */
    private static final int EXPECTED_SIGN =
            -1;

    private static final double EXPECTED_LOG_ABS_PSI =
            -13.106706361813577;

    private static final double[] EXPECTED_COORDINATE_GRADIENT = {
            0.4864526801548079,
            -0.35750451450327436,
            -4.250304444982975,
            -3.2847173427628,
            3.986371063007229,
            1.1688029840859167,
            1.9336141134745235,
            -1.6881959469855885,
            4.268044611261239,
            0.5675994288915565,
            -1.0411907297320684,
            -0.7584668757921317,
            1.5884132511210565,
            -2.7131003378717597,
            -1.5445613512767045,
            0.028977041568633177,
            0.3429750335325716,
            -0.8682525725565671,
            -0.9039523119173285,
            0.9123767276459959,
            0.5153360203186791,
            -2.4479543522800418,
            0.3083365590712923,
            0.676365129014411,
            1.0785953958919299,
            0.7515713578915306,
            -0.812740665060735,
            2.1770730709134023,
            -0.19133594330296932,
            -0.41315310046932074
    };

    private static final double EXPECTED_LAPLACIAN_OVER_PSI =
            -47.22749443462057;

    private static final double EXPECTED_KINETIC =
            23.613747217310284;

    private static final double EXPECTED_ELECTRON_NUCLEAR =
            -141.93585354810583;

    private static final double EXPECTED_ELECTRON_ELECTRON =
            51.967053873030004;

    private static final double EXPECTED_NUCLEAR_NUCLEAR =
            9.2635179312345;

    private static final double EXPECTED_TOTAL_LOCAL_ENERGY =
            -57.091534526531035;

    private static final double EXPECTED_PROPOSAL_LOG_ABS_PSI =
            -13.159604918116454;

    private static final double EXPECTED_METROPOLIS_LOG_RATIO =
            -0.10579711260575309;

    /*
     * Forward calculations should normally agree much more tightly than this.
     * Derivative/Laplacian paths are allowed slightly more floating-point
     * accumulation error.
     */
    private static final double FORWARD_TOLERANCE =
            2.0e-11;

    private static final double GRADIENT_TOLERANCE =
            2.0e-9;

    private static final double LAPLACIAN_TOLERANCE =
            2.0e-8;

    private static final double ENERGY_TOLERANCE =
            2.0e-8;

    @Test
    void javaRuntimeMatchesFrozenDeepmindJaxReference() throws Exception {

        assertEquals(
                REFERENCE_COMMIT,
                ReferenceFermiNetPretrainer.REFERENCE_COMMIT,
                "wrong pinned FermiNet reference commit");

        Molecule molecule =
                water();

        FermiNetV1Configuration configuration =
                FermiNetV1Configuration.testFixture();

        FermiNetParameterLayout layout =
                new FermiNetParameterLayout(
                        configuration,
                        molecule);

        JsonNode fixture;
        try (InputStream input = getClass().getResourceAsStream(FIXTURE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException("missing fixture: " + FIXTURE_RESOURCE);
            }
            fixture = new ObjectMapper().readTree(input);
        }

        assertEquals(
                REFERENCE_COMMIT,
                fixture.path("ferminet_commit").asText(),
                "fixture reference commit");

        JsonNode serializedParameters = fixture.path("parameters");
        double[] exportedParameters = new double[serializedParameters.size()];
        for (int i = 0; i < exportedParameters.length; i++) {
            exportedParameters[i] = serializedParameters.get(i).doubleValue();
        }

        int exportedParameterCount = fixture.path("parameter_count").asInt();
        assertEquals(exportedParameterCount, exportedParameters.length,
                "serialized parameter count");
        assertEquals(layout.parameterCount(), exportedParameterCount,
                "JAX/Java parameter count");

        /*
         * IMPORTANT:
         *
         * Load the exact mapped JAX parameter vector. Java and JAX PRNGs are
         * deliberately not compared.
         */
        FermiNetV1State state =
                new FermiNetV1State(
                        molecule,
                        configuration,
                        FermiNetParameters.fromArray(
                                layout,
                                exportedParameters));

        int loadedParameterCount = state.parameterCount();
        boolean parameterVectorsIdentical =
                Arrays.equals(exportedParameters, state.parameterArray());
        assertEquals(exportedParameterCount, loadedParameterCount,
                "loaded Java parameter count");
        assertTrue(parameterVectorsIdentical,
                "loaded Java parameters must be bit-identical to fixture parameters");

        QuantumCoordinates coordinates =
                coordinates();

        /*
         * ------------------------------------------------------------
         * Fast forward path
         * ------------------------------------------------------------
         */
        var sampling =
                state.samplingEvaluation(
                        coordinates);

        assertEquals(
                EXPECTED_SIGN,
                sampling.sign(),
                "wavefunction sign");

        assertEquals(
                EXPECTED_LOG_ABS_PSI,
                sampling.logAbsoluteWavefunction(),
                FORWARD_TOLERANCE,
                "sampling log|Psi|");

        /*
         * ------------------------------------------------------------
         * Derivative-complete forward path
         * ------------------------------------------------------------
         */
        var spatial =
                state.spatialEvaluation(
                        coordinates);

        assertEquals(
                EXPECTED_SIGN,
                spatial.sign(),
                "spatial wavefunction sign");

        assertEquals(
                EXPECTED_LOG_ABS_PSI,
                spatial.logAbsoluteWavefunction(),
                FORWARD_TOLERANCE,
                "spatial log|Psi|");

        /*
         * The fast sampler and derivative-complete evaluator must themselves
         * represent exactly the same Java wavefunction.
         */
        assertEquals(
                spatial.sign(),
                sampling.sign(),
                "fast/full Java sign parity");

        assertEquals(
                spatial.logAbsoluteWavefunction(),
                sampling.logAbsoluteWavefunction(),
                1.0e-12,
                "fast/full Java log|Psi| parity");

        /*
         * ------------------------------------------------------------
         * JAX coordinate-gradient parity
         * ------------------------------------------------------------
         */
        double[] actualGradient =
                spatial.logCoordinateGradient();

        assertEquals(
                EXPECTED_COORDINATE_GRADIENT.length,
                actualGradient.length,
                "coordinate-gradient dimension");

        double maximumGradientError =
                0.0;

        int maximumGradientErrorIndex =
                -1;

        for (int i = 0;
             i < actualGradient.length;
             i++) {

            double error =
                    Math.abs(
                            actualGradient[i]
                                    - EXPECTED_COORDINATE_GRADIENT[i]);

            if (error > maximumGradientError) {

                maximumGradientError =
                        error;

                maximumGradientErrorIndex =
                        i;
            }

            assertEquals(
                    EXPECTED_COORDINATE_GRADIENT[i],
                    actualGradient[i],
                    GRADIENT_TOLERANCE,
                    "coordinate gradient " + i);
        }

        /*
         * ------------------------------------------------------------
         * JAX Laplacian parity
         * ------------------------------------------------------------
         */
        double actualLaplacian =
                spatial.laplacianOverWavefunction();

        assertEquals(
                EXPECTED_LAPLACIAN_OVER_PSI,
                actualLaplacian,
                LAPLACIAN_TOLERANCE,
                "laplacianOverWavefunction");

        /*
         * ------------------------------------------------------------
         * Canonical Hamiltonian parity
         * ------------------------------------------------------------
         */
        LocalEnergyComponents energy =
                FermiNetVmc.localEnergy(
                        state,
                        coordinates);

        assertEquals(
                EXPECTED_KINETIC,
                energy.kineticHartree(),
                ENERGY_TOLERANCE,
                "kinetic energy");

        assertEquals(
                EXPECTED_ELECTRON_NUCLEAR,
                energy.electronNuclearHartree(),
                ENERGY_TOLERANCE,
                "electron-nuclear energy");

        assertEquals(
                EXPECTED_ELECTRON_ELECTRON,
                energy.electronElectronHartree(),
                ENERGY_TOLERANCE,
                "electron-electron energy");

        assertEquals(
                EXPECTED_NUCLEAR_NUCLEAR,
                energy.nuclearNuclearHartree(),
                ENERGY_TOLERANCE,
                "nuclear-nuclear energy");

        assertEquals(
                EXPECTED_TOTAL_LOCAL_ENERGY,
                energy.totalHartree(),
                ENERGY_TOLERANCE,
                "total local energy");

        /*
         * Independent internal Hamiltonian identity.
         */
        assertEquals(
                energy.kineticHartree()
                        + energy.electronNuclearHartree()
                        + energy.electronElectronHartree()
                        + energy.nuclearNuclearHartree(),
                energy.totalHartree(),
                1.0e-12,
                "local-energy component sum");

        /*
         * ------------------------------------------------------------
         * Frozen all-electron proposal
         * ------------------------------------------------------------
         */
        QuantumCoordinates proposal =
                proposalCoordinates();

        var proposalEvaluation =
                state.samplingEvaluation(
                        proposal);

        assertEquals(
                EXPECTED_PROPOSAL_LOG_ABS_PSI,
                proposalEvaluation.logAbsoluteWavefunction(),
                FORWARD_TOLERANCE,
                "proposal log|Psi|");

        double metropolisLogRatio =
                2.0
                        * (proposalEvaluation.logAbsoluteWavefunction()
                        - sampling.logAbsoluteWavefunction());

        assertEquals(
                EXPECTED_METROPOLIS_LOG_RATIO,
                metropolisLogRatio,
                FORWARD_TOLERANCE,
                "Metropolis log ratio");

        double maximumForwardError = Math.max(
                Math.abs(spatial.logAbsoluteWavefunction() - EXPECTED_LOG_ABS_PSI),
                Math.abs(proposalEvaluation.logAbsoluteWavefunction()
                        - EXPECTED_PROPOSAL_LOG_ABS_PSI));
        double laplacianError = Math.abs(
                actualLaplacian - EXPECTED_LAPLACIAN_OVER_PSI);
        double kineticError = Math.abs(energy.kineticHartree() - EXPECTED_KINETIC);
        double electronNuclearError = Math.abs(
                energy.electronNuclearHartree() - EXPECTED_ELECTRON_NUCLEAR);
        double electronElectronError = Math.abs(
                energy.electronElectronHartree() - EXPECTED_ELECTRON_ELECTRON);
        double nuclearNuclearError = Math.abs(
                energy.nuclearNuclearHartree() - EXPECTED_NUCLEAR_NUCLEAR);
        double totalEnergyError = Math.abs(
                energy.totalHartree() - EXPECTED_TOTAL_LOCAL_ENERGY);
        double metropolisRatioError = Math.abs(
                metropolisLogRatio - EXPECTED_METROPOLIS_LOG_RATIO);

        /*
         * ------------------------------------------------------------
         * Diagnostic report
         * ------------------------------------------------------------
         */
        System.out.printf(
                """
                FERMINET_RUNTIME_REFERENCE_PARITY_PASS
                  reference_commit=%s
                  exported_parameter_count=%d
                  java_parameter_count=%d
                  parameter_vectors_identical=%s
                  sign=%d
                  log_abs_psi=%.17g
                  max_forward_error=%.17g
                  max_coordinate_gradient_error=%.17g
                  max_coordinate_gradient_error_index=%d
                  laplacian_over_psi=%.17g
                  laplacian_error=%.17g
                  kinetic=%.17g
                  kinetic_error=%.17g
                  electron_nuclear=%.17g
                  electron_nuclear_error=%.17g
                  electron_electron=%.17g
                  electron_electron_error=%.17g
                  nuclear_nuclear=%.17g
                  nuclear_nuclear_error=%.17g
                  total_local_energy=%.17g
                  total_local_energy_error=%.17g
                  proposal_log_abs_psi=%.17g
                  metropolis_log_ratio=%.17g
                  metropolis_log_ratio_error=%.17g
                %n""",
                REFERENCE_COMMIT,
                exportedParameterCount,
                loadedParameterCount,
                parameterVectorsIdentical,
                spatial.sign(),
                spatial.logAbsoluteWavefunction(),
                maximumForwardError,
                maximumGradientError,
                maximumGradientErrorIndex,
                actualLaplacian,
                laplacianError,
                energy.kineticHartree(),
                kineticError,
                energy.electronNuclearHartree(),
                electronNuclearError,
                energy.electronElectronHartree(),
                electronElectronError,
                energy.nuclearNuclearHartree(),
                nuclearNuclearError,
                energy.totalHartree(),
                totalEnergyError,
                proposalEvaluation.logAbsoluteWavefunction(),
                metropolisLogRatio,
                metropolisRatioError);
    }

    private static QuantumCoordinates coordinates() {

        double[][] xyz = {
                {0.18, 0.11, 0.27},
                {-0.31, 0.42, -0.16},
                {0.57, -0.28, 0.33},
                {-0.63, -0.37, 0.21},
                {0.24, 0.71, -0.45},
                {-0.22, -0.15, -0.38},
                {0.36, -0.54, 0.19},
                {-0.48, 0.26, 0.51},
                {0.69, 0.18, -0.24},
                {-0.12, 0.61, 0.37}
        };

        return coordinates(
                xyz);
    }

    private static QuantumCoordinates proposalCoordinates() {

        /*
         * Frozen JAX fixture proposal:
         *
         * electron 0:
         *   +0.025 bohr x
         *   -0.010 bohr y
         *   +0.015 bohr z
         *
         * all other electrons unchanged.
         */
        double[][] xyz = {
                {0.205, 0.10, 0.28500000000000003},
                {-0.31, 0.42, -0.16},
                {0.57, -0.28, 0.33},
                {-0.63, -0.37, 0.21},
                {0.24, 0.71, -0.45},
                {-0.22, -0.15, -0.38},
                {0.36, -0.54, 0.19},
                {-0.48, 0.26, 0.51},
                {0.69, 0.18, -0.24},
                {-0.12, 0.61, 0.37}
        };

        return coordinates(
                xyz);
    }

    private static QuantumCoordinates coordinates(
            double[][] xyz) {

        List<QuantumCoordinates.ParticleCoordinate> particles =
                new ArrayList<>();

        for (int i = 0;
             i < xyz.length;
             i++) {

            particles.add(
                    new QuantumCoordinates.ParticleCoordinate(
                            i,
                            xyz[i][0],
                            xyz[i][1],
                            xyz[i][2],
                            i < 5
                                    ? SpinProjection.ALPHA
                                    : SpinProjection.BETA));
        }

        return new QuantumCoordinates(
                particles);
    }

    private static Molecule water() {

        return new Molecule(
                "ferminet-runtime-reference-water",
                List.of(
                        new NuclearCenter(
                                0,
                                "O",
                                new NuclearCharge(8),
                                new CartesianPosition(
                                        0.0,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                1,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        1.7952398191849366,
                                        0.0,
                                        0.0,
                                        LengthUnit.BOHR)),
                        new NuclearCenter(
                                2,
                                "H",
                                new NuclearCharge(1),
                                new CartesianPosition(
                                        -0.46464225035067114,
                                        1.7340684963325879,
                                        0.0,
                                        LengthUnit.BOHR))),
                new MolecularCharge(0),
                new ElectronCount(10),
                new SpinSector(
                        5,
                        5,
                        1));
    }
}
