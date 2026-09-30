package totah.lab.prometheus.ingest.authoritative;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.recovery.ArtifactChecksums;

/**
 * Adversarial frequency-integrity audit (ADV-FREQ-*) of defect F1: the TSL MIN01/02/04
 * Hessian archives contain {@code frequencies_cm-1.txt} with 162 = 3N-6 entries of which
 * exactly 3 are EXACT ZEROS — imaginary modes zeroed by the producer
 * ({@code run_minimum_hessians.py}: {@code np.real_if_close(...).astype(float)} on PySCF
 * harmonic_analysis output), turning three 3rd-order saddle points into archived
 * "VERIFIED_LOCAL_MINIMUM" records.
 *
 * <p>Physical invariant: a projected vibrational spectrum (translations/rotations excluded,
 * per the {@code frequency_projection} metadata) of a nonlinear molecule CANNOT contain
 * exact-zero frequencies. An exact zero in a 3N-6 spectrum is a zeroed imaginary mode or a
 * projection failure and must never flow through ingestion silently. Genuine NEGATIVE
 * frequencies, by contrast, are honest data (a true saddle, honestly reported) and must
 * remain ingestible — the defect is the zeroing, not the negativity.
 *
 * <p>Fixtures are synthetic Hessian artifact directories written by this test; checksums in
 * result.json are computed honestly so no integrity check can be the thing that fires.
 * ADV-FREQ-T1 is EXPECTED RED on the current tree: readHessian has no zero-frequency guard.
 * Do not weaken the assertion to make it pass.
 */
class AdversarialFrequencyIntegrityAcceptanceTest {

    @TempDir
    Path directory;

    private final PyscfGeometricArtifactReader reader = new PyscfGeometricArtifactReader();

    /**
     * ADV-FREQ-T1 EXPECTED RED. Invariant: exact zeros in a projected 3N-6 spectrum are
     * non-physical — they are zeroed imaginary modes or a projection failure. A Hessian
     * artifact directory whose frequencies_cm-1.txt contains exact zeros (3 zeros plus
     * positive modes, count matching frequency_count) while result.json claims
     * VERIFIED_LOCAL_MINIMUM must be REJECTED or VISIBLY FLAGGED by the reader: either an
     * IOException (fail-closed refusal) or an explicit comparison/discrepancy record on the
     * result naming the frequency spectrum. The current readHessian performs only a
     * count check and ingests the zeros silently, so this test fails until a zero-guard
     * exists. Do not weaken.
     */
    @Test
    void exactZeroFrequenciesInProjectedSpectrumAreRejectedOrFlagged() throws IOException {
        List<Double> spectrum = new java.util.ArrayList<>();
        spectrum.add(0.0);
        spectrum.add(0.0);
        spectrum.add(0.0);
        for (int mode = 0; mode < 9; mode++) {
            spectrum.add(31.5 + 17.25 * mode);
        }
        writeHessianDirectory(spectrum, "VERIFIED_LOCAL_MINIMUM");

        PyscfHessianResult result;
        try {
            result = reader.readHessian(directory);
        } catch (IOException rejected) {
            // Fail-closed refusal to ingest a non-physical spectrum satisfies the invariant.
            return;
        }
        assertThat(result.comparisons())
                .as("a silently ingested exact-zero projected spectrum must be visibly flagged: "
                        + "an exact zero in a 3N-6 projected spectrum is a zeroed imaginary mode "
                        + "or a projection failure and cannot belong to a VERIFIED_LOCAL_MINIMUM")
                .anyMatch(comparison -> comparison.field().toLowerCase().contains("frequenc"));
    }

    /**
     * ADV-FREQ-T2 green control: an honest all-positive projected spectrum passes through
     * ingestion unchanged.
     */
    @Test
    void allPositiveSpectrumIsIngestedUnchanged() throws IOException {
        List<Double> spectrum = List.of(47.06, 812.34, 1050.7, 1288.0, 1450.2, 2980.5);
        writeHessianDirectory(spectrum, "VERIFIED_LOCAL_MINIMUM");

        PyscfHessianResult result = reader.readHessian(directory);

        assertThat(result.frequencies().value().orElseThrow())
                .containsExactlyElementsOf(spectrum);
        assertThat(result.status().value()).contains("VERIFIED_LOCAL_MINIMUM");
        assertThat(result.artifactChecksumsVerified()).isTrue();
        assertThat(result.frequencyProjection().value().orElseThrow())
                .contains("exclude_trans=True").contains("exclude_rot=True");
    }

    /**
     * ADV-FREQ-T3 green control: an honest spectrum containing genuine NEGATIVE frequencies
     * (a true saddle point, honestly reported by PySCF harmonic_analysis) must remain
     * ingestible as data. The F1 defect is the ZEROING of imaginary modes, not their sign;
     * a guard that also destroys honest negative values would itself be data loss. Asserts
     * the negatives survive parsing exactly.
     */
    @Test
    void genuineNegativeFrequenciesArePreservedAsData() throws IOException {
        List<Double> spectrum = List.of(-812.34, -105.7, -23.1, 47.06, 2980.5, 3120.0);
        writeHessianDirectory(spectrum, "SADDLE_POINT_ORDER_3");

        PyscfHessianResult result = reader.readHessian(directory);

        List<Double> parsed = result.frequencies().value().orElseThrow();
        assertThat(parsed).containsExactlyElementsOf(spectrum);
        assertThat(parsed.get(0)).isCloseTo(-812.34, within(1.0e-12));
        assertThat(parsed.stream().filter(value -> value < 0.0).count()).isEqualTo(3);
        assertThat(result.status().value()).contains("SADDLE_POINT_ORDER_3");
    }

    // ------------------------------------------------------------------ fixture

    /**
     * Writes a self-consistent synthetic Hessian artifact directory: input.json, result.json
     * with honest artifact_sha256 checksums, a symmetric 6x6 Cartesian Hessian (2 atoms,
     * 3N = 6), and the given projected frequency spectrum. Nothing in the fixture is
     * corrupt except the spectrum content itself, so only a frequency-semantics guard can
     * react to it.
     */
    private void writeHessianDirectory(List<Double> frequencies, String status) throws IOException {
        Files.writeString(directory.resolve("input.json"), """
                {"minimum_id":"MIN01","method":"PBE/def2-SVP gas phase analytic Hessian",
                 "charge":0,"multiplicity":1,
                 "frequency_projection":"PySCF harmonic_analysis exclude_trans=True exclude_rot=True",
                 "software":{"pyscf":"2.14.0","numpy":"2.5.2"}}
                """);
        Path hessian = directory.resolve("cartesian_hessian_flat_hartree_per_bohr2.txt");
        Path spectrumPath = directory.resolve("frequencies_cm-1.txt");
        Files.writeString(hessian, """
                0.512 0.045 -0.032 0.021 0.017 -0.028
                0.045 0.487 0.039 -0.024 0.031 0.014
                -0.032 0.039 0.623 0.027 -0.019 0.035
                0.021 -0.024 0.027 0.398 0.022 -0.026
                0.017 0.031 -0.019 0.022 0.441 0.029
                -0.028 0.014 0.035 -0.026 0.029 0.556
                """);
        StringBuilder spectrumText = new StringBuilder();
        for (double frequency : frequencies) {
            spectrumText.append(Double.toString(frequency)).append('\n');
        }
        Files.writeString(spectrumPath, spectrumText.toString());
        Files.writeString(directory.resolve("result.json"), """
                {"status":"%s","energy_hartree":-1477.9438395697284,
                 "scf_converged":true,"frequency_count":%d,
                 "artifact_sha256":{
                   "cartesian_hessian_flat_hartree_per_bohr2.txt":"%s",
                   "frequencies_cm-1.txt":"%s"}}
                """.formatted(status, frequencies.size(),
                ArtifactChecksums.sha256(hessian), ArtifactChecksums.sha256(spectrumPath)));
    }
}
