package totah.lab.prometheus.recovery.authoritative;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AuthoritativeScientificAuditRunnerTest {

    @TempDir
    Path output;

    @Test
    void auditsRemainingCanonicalGapsAndReconstructsTrustedRawEvidence() throws Exception {
        Path root = repositoryRoot();
        AuthoritativeScientificAuditRunner.Result result = new AuthoritativeScientificAuditRunner().run(
                root.resolve("analysis/mettl7-phase2"),
                root.resolve("analysis/prometheus/evidence-store"), output);

        // The canonical importer has already applied the 130 authoritative
        // recoveries; only the deliberately unrecoverable fields remain.
        assertThat(result.audit().entries()).hasSize(67);
        assertThat(result.audit().entries()).allSatisfy(entry ->
                assertThat(entry.recovery().classification()).isNotNull());
        assertThat(result.reconstruction().minima()).isEqualTo(3);
        assertThat(result.reconstruction().hessians()).isEqualTo(3);
        assertThat(result.reconstruction().probes()).isEqualTo(19);
        assertThat(result.reconstruction().historicalComparisons())
                .allMatch(comparison -> comparison.matchesTolerance());
        assertThat(output.resolve("AUTHORITATIVE_FIELD_RECOVERY.csv")).exists();
        assertThat(output.resolve("AUTHORITATIVE_RECONSTRUCTION_SUMMARY.md")).exists();
    }

    private static Path repositoryRoot() {
        Path working = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return working.endsWith(Path.of("software/modules/prometheus"))
                ? working.resolve("../../..").normalize()
                : working;
    }
}
