package totah.lab.web.service;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectivityWorkbookServiceTest {

    @Test
    void createsFormattedExcelWorkbookWithEvidence() throws Exception {
        byte[] workbook = new SelectivityWorkbookService().create(List.of(
                new DockingAnalysisService.SelectivityScore(
                        "compact-id",
                        "MCULE-1",
                        "CCO",
                        -9.2,
                        -7.0,
                        2.2,
                        11,
                        12,
                        101,
                        102
                )
        ));

        boolean foundWorksheet = false;
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(workbook)
        )) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("xl/worksheets/sheet1.xml".equals(entry.getName())) {
                    String xml = new String(
                            zip.readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                    assertTrue(xml.contains("MCULE-1"));
                    assertTrue(xml.contains("Delta = METTL7A − METTL7B"));
                    assertTrue(xml.contains("<autoFilter ref=\"A4:J5\"/>"));
                    assertTrue(xml.contains(">SMILES<"));
                    assertTrue(xml.contains(">CCO<"));
                    foundWorksheet = true;
                }
            }
        }
        assertTrue(foundWorksheet);
    }
}
