package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mettl7V2DockingCampaignRunnerTest {
    @TempDir Path temporary;

    @Test
    void executesAllSeedsAndWritesResumableReceipts() throws Exception {
        Path receptor = write("receptor.pdbqt", "RECEPTOR\n");
        Path ligand = write("ligand.pdbqt", "LIGAND\n");
        Path receptors = write("receptors.json", """
                [{"receptor_id":"A0","paralog":"METTL7A","substitutions":[],
                "prepared_path":"%s","prepared_sha256":"%s","status":"VALID"}]
                """.formatted(receptor, sha(receptor)));
        Path ligands = write("ligands.json", """
                {"species":[{"species_id":"TEST","compound_branch":"TEST",
                "stereoisomer":"NA","protonation_or_speciation":"neutral",
                "tautomer":"NA","acceptor_atom":"NA","prepared_path":"%s",
                "prepared_sha256":"%s","preparation_status":"PASS"}]}
                """.formatted(ligand, sha(ligand)));
        Path vina = write("fake-vina", """
                #!/bin/bash
                while [ "$#" -gt 0 ]; do
                  if [ "$1" = "--out" ]; then shift; out="$1"; fi
                  shift
                done
                echo 'POSE' > "$out"
                echo '   mode |   affinity | dist from best mode'
                echo '      1        -5.0      0.000      0.000'
                """);
        Files.setPosixFilePermissions(vina,
                PosixFilePermissions.fromString("rwxr-xr-x"));

        var summary = new Mettl7V2DockingCampaignRunner().run(
                temporary, vina, receptors, ligands, temporary.resolve("out"), 1, 1);

        assertEquals(3, summary.authoritativeExpectedRows());
        assertEquals(3, summary.completedValid());
        assertEquals(0, summary.failed());
        assertTrue(Files.isRegularFile(temporary.resolve(
                "out/runs/A0__TEST__s42/receipt.json")));
    }

    @Test
    void rejectsCpuOversubscriptionBeforeExecution() {
        assertThrows(IllegalArgumentException.class, () ->
                new Mettl7V2DockingCampaignRunner().run(
                        temporary, temporary, temporary, temporary, temporary,
                        Runtime.getRuntime().availableProcessors() + 1, 1));
    }

    private Path write(String name, String content) throws Exception {
        Path path = temporary.resolve(name);
        Files.writeString(path, content);
        return path;
    }

    private static String sha(Path path) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
