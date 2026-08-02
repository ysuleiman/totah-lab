package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.hermes.biohub.artifact.BiohubPocketEvidenceReader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
class BiohubPocketEvidenceServiceTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mapsDirectAndChosenPocketConsensusResidues() throws Exception {
        Path pocketPath = writeArtifacts();
        var artifactEvidence =
                new BiohubPocketEvidenceReader().read(pocketPath);

        PocketService.PocketEvidence evidence =
                BiohubPocketEvidenceService.map(
                artifactEvidence,
                List.of(
                        residue(780L, 78),
                        residue(820L, 82)
                ),
                Set.of(780L)
            );

        assertThat(evidence.ligandCcd()).isEqualTo("SAM");
        assertThat(evidence.directContactResidueIds())
                .containsExactly(780L);
        assertThat(evidence.chosenPocketOverlapResidueIds())
                .containsExactly(780L);
        assertThat(evidence.directChosenPocketOverlapResidueIds())
                .containsExactly(780L);
        assertThat(evidence.residueEvidence()).first().satisfies(residue -> {
            assertThat(residue.residueId()).isEqualTo(780L);
            assertThat(residue.residueNumber()).isEqualTo(78);
            assertThat(residue.minimumDistance()).isEqualTo(2.7);
            assertThat(residue.chosenPocketMember()).isTrue();
        });
    }

    private PocketService.ResidueDetails residue(long id, int number) {
        return new PocketService.ResidueDetails(
                id,
                "A",
                number,
                "",
                "GLY"
        );
    }

    private Path writeArtifacts() throws Exception {
        Path pocket = temporaryDirectory.resolve(
                "target_sam_esmfold2_complex_pocket_6A.json"
        );
        Files.writeString(pocket, """
                {
                  "ligandCcd": "SAM",
                  "cutoff": 6.0,
                  "residues": [
                    {
                      "chain": "A",
                      "residueNumber": 78,
                      "residueName": "GLY",
                      "minimumDistance": 2.7,
                      "contactingAtomPairCount": 4
                    },
                    {
                      "chain": "A",
                      "residueNumber": 82,
                      "residueName": "GLY",
                      "minimumDistance": 5.9,
                      "contactingAtomPairCount": 1
                    }
                  ]
                }
                """);
        Files.writeString(
                temporaryDirectory.resolve(
                        "target_sam_esmfold2_complex.json"
                ),
                """
                {
                  "prediction": {
                    "ligandCcd": "SAM",
                    "model": "esmfold2-fast",
                    "ptm": 0.95,
                    "interfacePtm": 0.98
                  }
                }
                """
        );
        return pocket;
    }
}
