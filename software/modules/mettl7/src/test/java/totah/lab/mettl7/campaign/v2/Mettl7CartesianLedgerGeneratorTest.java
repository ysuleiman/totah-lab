package totah.lab.mettl7.campaign.v2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Mettl7CartesianLedgerGeneratorTest {
    private static final String SHA = "a".repeat(64);
    @TempDir Path temporary;

    @Test
    void generatesDeterministicCartesianRowsOnlyFromValidFinalManifests() throws Exception {
        Path receptors = write("receptors.json", """
                {"receptors":[{"receptor_id":"A0","paralog":"METTL7A","mutations":[],
                "prepared_path":"A0.pdbqt","prepared_sha256":"%s","window_id":"A_NATIVE",
                "integrity_status":"VALID"}]}""".formatted(SHA));
        Path ligands = write("ligands.json", """
                {"species":[{"species_id":"TSL_RSH","compound_branch":"TSL",
                "stereoisomer":"DEFINED","protonation_or_speciation":"RSH","tautomer":"NA",
                "acceptor_atom":"S","prepared_path":"TSL.pdbqt","prepared_sha256":"%s",
                "preparation_status":"VALID"}]}""".formatted(SHA));
        Path output = temporary.resolve("ledger.csv");

        int count = new Mettl7CartesianLedgerGenerator().write(receptors, ligands, output);

        assertEquals(3, count);
        assertEquals(4, Files.readAllLines(output).size());
        assertTrue(Files.readString(output).contains("A0__TSL_RSH__s42"));
    }

    @Test
    void failsClosedAndDoesNotInventCountForIncompleteManifest() throws Exception {
        Path receptors = write("receptors.json", """
                {"receptors":[{"receptor_id":"B2","paralog":"METTL7B","mutations":["S47Y"],
                "prepared_path":"B2.pdbqt","prepared_sha256":"%s","window_id":"B_NATIVE",
                "integrity_status":"BLOCKED"}]}""".formatted(SHA));
        Path ligands = write("ligands.json", """
                {"species":[{"species_id":"DTT","compound_branch":"DTT","stereoisomer":"",
                "protonation_or_speciation":"","tautomer":"","acceptor_atom":"S",
                "prepared_path":"DTT.pdbqt","prepared_sha256":"%s",
                "preparation_status":"BLOCKED"}]}""".formatted(SHA));
        Path output = temporary.resolve("ledger.csv");

        IOException failure = assertThrows(IOException.class, () ->
                new Mettl7CartesianLedgerGenerator().write(receptors, ligands, output));

        assertTrue(failure.getMessage().contains("B2"));
        assertTrue(failure.getMessage().contains("DTT"));
        assertTrue(Files.notExists(output));
    }

    @Test
    void preservesTechnicalReceptorFailuresAsExplicitRows() throws Exception {
        Path receptors = write("technical-receptors.json", """
                {"receptors":[{"receptor_id":"B2","paralog":"METTL7B","mutations":["S47Y"],
                "prepared_path":"B2.pdbqt","prepared_sha256":"%s","window_id":"B_NATIVE",
                "integrity_status":"TECHNICAL_FAILURE"}]}""".formatted(SHA));
        Path ligands = write("valid-ligands.json", """
                {"species":[{"species_id":"DTT","compound_branch":"DTT","stereoisomer":"2R,3R",
                "protonation_or_speciation":"RSH2","tautomer":"NA","acceptor_atom":"S",
                "prepared_path":"DTT.pdbqt","prepared_sha256":"%s",
                "preparation_status":"VALID"}]}""".formatted(SHA));
        Path output = temporary.resolve("technical-ledger.csv");

        int count = new Mettl7CartesianLedgerGenerator().write(receptors, ligands, output);

        assertEquals(3, count);
        assertEquals(3, Files.readAllLines(output).stream()
                .filter(line -> line.contains("TECHNICAL_FAILURE")).count());
    }

    private Path write(String name, String content) throws IOException {
        Path path = temporary.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
