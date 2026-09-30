package totah.lab.prometheus.completeness;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adversarial Hessian-layout audit (ADV-LAYOUT-*) of defect F4: the remediation script
 * {@code compute_missing_d3_hessians.py:106-114} produced corrupt "total" Hessians — it
 * symmetrized the (i,j,c,d)-flattened electronic matrix with a plain 2-D transpose (wrong
 * index partner in that layout) and added the D3 matrix in a mismatched (i,c)-flat layout,
 * with corruption up to 0.487 Ha/bohr^2.
 *
 * <p>Invariant under audit: a persisted total Hessian must equal the elementwise sum of its
 * persisted components IN ONE DECLARED LAYOUT. Layout confusion must be caught by the
 * composition gate ({@link ScientificResultCompletenessValidator}), not by downstream
 * consumers.
 *
 * <p>Fixtures are synthetic 2-atom H/Cl bundles with hand-picked, non-accidental symmetric
 * 6x6 components; every mutated total is computed BY THIS TEST and all SHA-256 manifest
 * digests are honestly recomputed after each mutation, so no checksum diagnostic can be the
 * thing that fires. All three tests are EXPECTED GREEN on the current tree (the composite
 * sum-identity gate exists since 747e3a84); if T5/T6 run red, that is a finding about the
 * in-flight validator edit and must be reported, not weakened.
 */
class AdversarialHessianLayoutAcceptanceTest {

    @TempDir
    Path bundleRoot;

    private final ScientificResultCompletenessValidator validator =
            new ScientificResultCompletenessValidator();

    // ------------------------------------------------------------------ tests

    /**
     * ADV-LAYOUT-T4 regression guard (control). Invariant: a valid composite bundle whose
     * total_hessian equals electronic_hessian + dispersion_hessian elementwise in the
     * declared 6x6 layout must validate as REPRODUCIBLE_COMPLETE. Oracle: gate status.
     */
    @Test
    void validCompositeBundleIsReproducibleComplete() throws Exception {
        LayoutBundle bundle = new LayoutBundle();
        bundle.writeValidCompositeBundle();
        assertThat(validator.validate(bundleRoot, bundle.manifest()).status())
                .isEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
    }

    /**
     * ADV-LAYOUT-T5 F4 guard. Invariant: total == electronic + dispersion in ONE DECLARED
     * LAYOUT. The electronic and dispersion components are persisted CORRECTLY; the
     * persisted total faithfully emulates the remediation bug at small scale: the correct
     * symmetric electronic tensor E[(i,c),(j,d)] is rewritten as a C-order flat vector in
     * the (i,j,c,d) layout, reshaped to 6x6, "symmetrized" with the plain flat 2-D
     * transpose — (M + M^T)/2 pairs (i,j) with (c,d) instead of (i,c) with (j,d) — and the
     * dispersion matrix is then added elementwise, exactly the layout mismatch of
     * compute_missing_d3_hessians.py:106-114. The result is finite, 6x6, and symmetric by
     * construction, and every digest is honestly recomputed, so ONLY the composition
     * sum-identity check can catch it. Oracle: gate status must not be COMPLETE and an
     * issue must name total_hessian.
     */
    @Test
    void wrongPartnerSymmetrizedTotalIsRejectedByCompositionGate() throws Exception {
        LayoutBundle bundle = new LayoutBundle();
        bundle.writeValidCompositeBundle();
        double[][] corruptedSymmetrization = wrongPartnerSymmetrization(bundle.electronic);
        double maxCorruption = 0.0;
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 6; column++) {
                maxCorruption = Math.max(maxCorruption,
                        Math.abs(corruptedSymmetrization[row][column] - bundle.electronic[row][column]));
            }
        }
        assertThat(maxCorruption)
                .as("the emulation must actually corrupt the matrix, otherwise the test is vacuous")
                .isGreaterThan(1.0e-3);
        bundle.writeArtifact("total_hessian",
                matrixText(add(corruptedSymmetrization, bundle.dispersion)));
        ScientificResultCompletenessValidator.ValidationResult result =
                validator.validate(bundleRoot, bundle.manifest());
        assertThat(result.status())
                .as("a layout-corrupted total must not pass the composition gate")
                .isNotEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
        assertThat(result.issues())
                .as("the composition diagnostic must name total_hessian")
                .anyMatch(issue -> issue.contains("total_hessian"));
    }

    /**
     * ADV-LAYOUT-T6 layout-slip variant. Invariant: as T5. The persisted total equals
     * electronic + dispersion but with rows 0 and 3 swapped — an axis-interleave layout
     * slip ((i,c) row index read as (c,i)-ish). Digests honestly recomputed. Oracle: gate
     * status must not be COMPLETE and an issue must name total_hessian.
     */
    @Test
    void totalWithSwappedRowsIsRejectedByCompositionGate() throws Exception {
        LayoutBundle bundle = new LayoutBundle();
        bundle.writeValidCompositeBundle();
        double[][] total = add(bundle.electronic, bundle.dispersion);
        double[] rowZero = total[0];
        total[0] = total[3];
        total[3] = rowZero;
        bundle.writeArtifact("total_hessian", matrixText(total));
        ScientificResultCompletenessValidator.ValidationResult result =
                validator.validate(bundleRoot, bundle.manifest());
        assertThat(result.status())
                .as("a row-permuted total must not pass the composition gate")
                .isNotEqualTo(ScientificResultCompleteness.REPRODUCIBLE_COMPLETE);
        assertThat(result.issues())
                .as("the composition diagnostic must name total_hessian")
                .anyMatch(issue -> issue.contains("total_hessian"));
    }

    // ------------------------------------------------------------------ defect emulation

    /**
     * Faithful small-scale emulation of the F4 wrong-partner symmetrization. The input is
     * the correct symmetric electronic Hessian in the declared layout (row = i*3+c,
     * column = j*3+d for atoms i,j and Cartesian components c,d). The bug reinterpreted the
     * same tensor values as a C-order flat vector in the (i,j,c,d) layout —
     * index ((i*2+j)*3+c)*3+d — reshaped that vector to 6x6, and applied a plain 2-D
     * transpose symmetrization, which in that layout pairs the wrong indices. The returned
     * matrix is symmetric by construction, so no symmetry or shape diagnostic can fire.
     */
    private static double[][] wrongPartnerSymmetrization(double[][] electronic) {
        double[] wrongLayoutFlat = new double[36];
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 2; j++) {
                for (int c = 0; c < 3; c++) {
                    for (int d = 0; d < 3; d++) {
                        wrongLayoutFlat[((i * 2 + j) * 3 + c) * 3 + d] =
                                electronic[i * 3 + c][j * 3 + d];
                    }
                }
            }
        }
        double[][] misread = new double[6][6];
        for (int flat = 0; flat < 36; flat++) {
            misread[flat / 6][flat % 6] = wrongLayoutFlat[flat];
        }
        double[][] symmetrized = new double[6][6];
        for (int row = 0; row < 6; row++) {
            for (int column = 0; column < 6; column++) {
                symmetrized[row][column] = (misread[row][column] + misread[column][row]) / 2.0;
            }
        }
        return symmetrized;
    }

    // ------------------------------------------------------------------ bundle harness

    /** Synthetic 2-atom H/Cl QM bundle living in {@link #bundleRoot}. */
    private final class LayoutBundle {
        final Map<String, ScientificArtifactReference> artifacts = new LinkedHashMap<>();
        final double[][] electronic = electronicComponent();
        final double[][] dispersion = dispersionComponent();
        String geometrySha;

        /** Writes every artifact of a valid composite PBE-D3(BJ) bundle with honest digests. */
        void writeValidCompositeBundle() throws Exception {
            writeArtifact("software_versions", "pyscf 2.14.0; simple-dftd3 1.5.0; geomeTRIC 1.1.1\n");
            writeArtifact("code_commit", "0123456789abcdef0123456789abcdef01234567\n");
            writeArtifact("input_checksums", "input.json=" + sha256("synthetic layout input\n") + "\n");
            writeArtifact("output_checksums", "result.json=" + sha256("synthetic layout output\n") + "\n");
            writeArtifact("geometry.xyz",
                    "2\nHCl layout audit geometry (bohr)\nH 0.0 0.0 0.0\nCl 1.3 0.2 0.4\n");
            writeArtifact("atom_order", "H\nCl\n");
            writeArtifact("charge", "0\n");
            writeArtifact("multiplicity", "1\n");
            writeArtifact("method", "PBE-D3(BJ)/def2-SVP\n");
            writeArtifact("basis", "def2-SVP\n");
            writeArtifact("grid", "SG-1 (99 radial, 590 angular)\n");
            writeArtifact("dispersion_configuration",
                    "D3(BJ) a1=0.4289 a2=4.4407 alp=14.0 s8=0.7875 s9=0.0\n");
            writeArtifact("scf_configuration", "conv_tol=1e-10 max_cycle=128 diis=adiis\n");
            writeArtifact("electronic_energy", "-76.0\n");
            writeArtifact("dispersion_energy", "-0.02\n");
            writeArtifact("total_energy", "-76.02\n");
            double[][] electronicGradient = {
                    {1.0e-3, -2.0e-3, 3.0e-3},
                    {-1.0e-3, 2.0e-3, -3.0e-3}};
            double[][] dispersionGradient = {
                    {1.0e-5, -2.0e-5, 3.0e-5},
                    {-1.0e-5, 2.0e-5, -3.0e-5}};
            double[][] totalGradient = add(electronicGradient, dispersionGradient);
            writeArtifact("electronic_gradient", matrixText(electronicGradient));
            writeArtifact("dispersion_gradient", matrixText(dispersionGradient));
            writeArtifact("total_gradient", matrixText(totalGradient));
            writeArtifact("force", matrixText(negate(totalGradient)));
            writeArtifact("convergence_diagnostics",
                    "scf_converged=true rms_density=3.1e-11 max_gradient=4.4e-06\n");
            writeArtifact("hardware_runtime_identity", "layout-audit-cpu; 8 threads; no GPU\n");
            writeArtifact("hessian_requested", "true\n");
            geometrySha = sha256(bundleRoot.resolve("geometry.xyz"));
            writeArtifact("hessian_units", "hartree/bohr^2\n");
            writeArtifact("hessian_dimensions", "6x6\n");
            writeArtifact("hessian_geometry_identity", geometrySha + "\n");
            writeArtifact("hessian_component_identity", componentIdentity(geometrySha));
            writeArtifact("electronic_hessian", matrixText(electronic));
            writeArtifact("dispersion_hessian", matrixText(dispersion));
            writeArtifact("total_hessian", matrixText(add(electronic, dispersion)));
        }

        void writeArtifact(String name, String content) throws Exception {
            Files.writeString(bundleRoot.resolve(name), content);
            artifacts.put(name, new ScientificArtifactReference(
                    Path.of(name), sha256(bundleRoot.resolve(name))));
        }

        ScientificResultManifest manifest() {
            return new ScientificResultManifest(
                    "adv-layout-audit", ScientificResultType.QM_CALCULATION, artifacts);
        }
    }

    private static String componentIdentity(String geometrySha) {
        return "electronic_identity=TRUSTED_PBE_ONLY_HESSIAN\n"
                + "electronic_geometry_sha256=" + geometrySha + "\n"
                + "dispersion_identity=D3_BJ_ONLY_HESSIAN\n"
                + "dispersion_geometry_sha256=" + geometrySha + "\n"
                + "total_identity=PBE_D3_BJ_TOTAL_HESSIAN\n"
                + "total_geometry_sha256=" + geometrySha + "\n";
    }

    /** Non-accidental symmetric electronic Hessian component (hartree/bohr^2 scale). */
    private static double[][] electronicComponent() {
        return new double[][]{
                {0.512, 0.045, -0.032, 0.021, 0.017, -0.028},
                {0.045, 0.487, 0.039, -0.024, 0.031, 0.014},
                {-0.032, 0.039, 0.623, 0.027, -0.019, 0.035},
                {0.021, -0.024, 0.027, 0.398, 0.022, -0.026},
                {0.017, 0.031, -0.019, 0.022, 0.441, 0.029},
                {-0.028, 0.014, 0.035, -0.026, 0.029, 0.556}};
    }

    /** Non-accidental symmetric dispersion Hessian component, distinctly smaller scale. */
    private static double[][] dispersionComponent() {
        return new double[][]{
                {-1.2e-4, -3.1e-4, 2.2e-4, -1.8e-4, 1.4e-4, -2.6e-4},
                {-3.1e-4, -9.5e-5, -2.4e-4, 1.9e-4, -1.6e-4, 2.1e-4},
                {2.2e-4, -2.4e-4, -1.5e-4, -2.8e-4, 1.7e-4, -2.3e-4},
                {-1.8e-4, 1.9e-4, -2.8e-4, -8.7e-5, -1.5e-4, 2.5e-4},
                {1.4e-4, -1.6e-4, 1.7e-4, -1.5e-4, -1.1e-4, -2.0e-4},
                {-2.6e-4, 2.1e-4, -2.3e-4, 2.5e-4, -2.0e-4, -1.3e-4}};
    }

    private static double[][] add(double[][] first, double[][] second) {
        double[][] sum = new double[first.length][first[0].length];
        for (int row = 0; row < first.length; row++) {
            for (int column = 0; column < first[row].length; column++) {
                sum[row][column] = first[row][column] + second[row][column];
            }
        }
        return sum;
    }

    private static double[][] negate(double[][] matrix) {
        double[][] negated = new double[matrix.length][matrix[0].length];
        for (int row = 0; row < matrix.length; row++) {
            for (int column = 0; column < matrix[row].length; column++) {
                negated[row][column] = -matrix[row][column];
            }
        }
        return negated;
    }

    private static String matrixText(double[][] matrix) {
        StringBuilder text = new StringBuilder();
        for (double[] row : matrix) {
            for (int column = 0; column < row.length; column++) {
                if (column > 0) {
                    text.append(' ');
                }
                text.append(Double.toString(row[column]));
            }
            text.append('\n');
        }
        return text.toString();
    }

    /** Independent SHA-256 of a file; deliberately shares no code with the validator. */
    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = Files.newInputStream(path)) {
                byte[] buffer = new byte[4096];
                for (int count; (count = input.read(buffer)) >= 0;) {
                    digest.update(buffer, 0, count);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String sha256(String content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
