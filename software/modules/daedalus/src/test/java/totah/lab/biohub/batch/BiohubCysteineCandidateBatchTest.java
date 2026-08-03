package totah.lab.biohub.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiohubCysteineCandidateBatchTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsMculeIdentifiersAndSmilesFromWorkbook() throws Exception {
        Path workbookPath = temporaryDirectory.resolve("candidates.xlsx");
        try (var workbook = new XSSFWorkbook()) {
            var sheet = workbook.createSheet("candidates");
            var row = sheet.createRow(0);
            row.createCell(0).setCellValue("MCULE-1234567890");
            row.createCell(1).setCellValue("CC(=O)NC1=CC=CC=C1");
            try (OutputStream output = Files.newOutputStream(workbookPath)) {
                workbook.write(output);
            }
        }

        Map<String, String> result = new BiohubCysteineCandidateBatch()
                .readMculeSmiles(workbookPath);

        assertEquals(
                Map.of("MCULE-1234567890", "CC(=O)NC1=CC=CC=C1"),
                result
        );
    }

    @Test
    void distinguishesSmilesFromWorkbookMetadata() {
        BiohubCysteineCandidateBatch batch =
                new BiohubCysteineCandidateBatch();

        assertTrue(batch.isSmiles("CC(=O)NC1=CC=CC=C1", "MCULE-123"));
        assertFalse(batch.isSmiles("MCULE-123", "MCULE-123"));
        assertFalse(batch.isSmiles("out_001", "MCULE-123"));
        assertFalse(batch.isSmiles("-11.2", "MCULE-123"));
    }

    @Test
    void contactFlagUsesConfiguredResidue() {
        List<BiohubCysteineCandidateBatch.Contact> contacts = List.of(
                new BiohubCysteineCandidateBatch.Contact("A", 150, "SER", 3.1, 2),
                new BiohubCysteineCandidateBatch.Contact("A", 202, "CYS", 2.4, 5)
        );

        assertTrue(BiohubCysteineCandidateBatch.contactsResidue(
                contacts, 202, "CYS"));
        assertTrue(BiohubCysteineCandidateBatch.contactsResidue(
                contacts, 150, "SER"));
        assertFalse(BiohubCysteineCandidateBatch.contactsResidue(
                contacts, 202, "SER"));
        assertFalse(BiohubCysteineCandidateBatch.contactsResidue(
                contacts, 150, "CYS"));
        assertFalse(BiohubCysteineCandidateBatch.contactsResidue(
                contacts, 203, "CYS"));
    }

    @Test
    void manifestEntrySerializesResidueNeutralContactFlag() throws Exception {
        BiohubCysteineCandidateBatch.ManifestEntry entry =
                new BiohubCysteineCandidateBatch.ManifestEntry(
                        new BiohubCysteineCandidateBatch.Candidate(
                                "MCULE-1", -8.0, -7.0, 1.0, 1L, 2L),
                        "CC(=O)O",
                        "biohub",
                        "esmc-300m-2024-12",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        0.9,
                        0.8,
                        5,
                        true,
                        "a.json",
                        "a.pdb",
                        "p.json"
                );

        String json = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .writeValueAsString(entry);

        assertTrue(json.contains("\"contactsTargetResidue\":true"));
        assertFalse(json.contains("Cys202"));
    }
}
