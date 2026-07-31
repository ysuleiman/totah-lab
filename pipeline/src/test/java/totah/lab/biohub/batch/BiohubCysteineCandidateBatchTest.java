package totah.lab.biohub.batch;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
}
