package totah.lab.analysis.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.protein.analysis.ResidueConstraintAnalysis;
import totah.lab.protein.analysis.ResidueConstraintEvidence;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResidueConstraintArtifactTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void roundTripsVersionedResidueConstraintArtifact() throws Exception {
        ResidueConstraintAnalysis analysis = new ResidueConstraintAnalysis(
                "BIOHUB_ESMC",
                "esmc-300m-2024-12",
                "A",
                Instant.parse("2026-07-29T20:00:00Z"),
                List.of(new ResidueConstraintEvidence(
                        1,
                        'A',
                        -0.1,
                        -3.2,
                        'G',
                        -2.1,
                        3.1,
                        2.0,
                        1,
                        0.4
                ))
        );
        Path artifactPath = temporaryDirectory.resolve(
                "Q6UX53_esmc_residue_constraints.json"
        );

        new ResidueConstraintArtifactWriter().write(
                artifactPath,
                analysis
        );
        ResidueConstraintAnalysis restored =
                new ResidueConstraintArtifactReader().read(artifactPath);

        assertThat(restored).isEqualTo(analysis);
    }
}
