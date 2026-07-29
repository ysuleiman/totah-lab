package totah.lab.http.biohub.artifact;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class BiohubPocketEvidenceReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsConfidenceAndClassifiesDirectContacts() throws Exception {
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

        var evidence = new BiohubPocketEvidenceReader().read(pocket);

        assertThat(evidence.ligandCcd()).isEqualTo("SAM");
        assertThat(evidence.residues()).hasSize(2);
        assertThat(evidence.residues().getFirst().directContact()).isTrue();
        assertThat(evidence.residues().getLast().directContact()).isFalse();
    }
}
