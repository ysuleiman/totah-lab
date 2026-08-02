package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.report.narrative.PocketNarrative;
import totah.lab.web.service.PocketReportApplicationService;
import totah.lab.web.service.PocketReportDocxService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PocketReportControllerTest {

    @Test
    void bindsPocketAndDockingRunIdentifiers() throws Exception {
        RecordingReportService service = new RecordingReportService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketReportController(service, null))
                .build();

        mockMvc.perform(get("/api/pockets/11/report?runId=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pocketId").value(11))
                .andExpect(jsonPath("$.data.source").value("FPOCKET"));

        assertEquals(11L, service.pocketId);
        assertEquals(7L, service.runId);
    }

    @Test
    void downloadsEditableDocxReport() throws Exception {
        RecordingReportService service = new RecordingReportService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketReportController(
                        service,
                        null,
                        new PocketReportDocxService()
                ))
                .build();

        mockMvc.perform(get("/api/pockets/11/report.docx?runId=7"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument"
                                + ".wordprocessingml.document"
                ))
                .andExpect(header().string(
                        "Content-Disposition",
                        "attachment; filename=\""
                                + "pocket-11-run-7-report.docx\""
                ))
                .andExpect(result -> assertEquals(
                        "PK",
                        new String(
                                result.getResponse().getContentAsByteArray(),
                                0,
                                2,
                                java.nio.charset.StandardCharsets.US_ASCII
                        )
                ));
    }

    private static final class RecordingReportService
            extends PocketReportApplicationService {

        private long pocketId;
        private long runId;

        private RecordingReportService() {
            super(null, null, null, null, null);
        }

        @Override
        public PocketReport generate(long pocketId, long runId) {
            this.pocketId = pocketId;
            this.runId = runId;
            return new PocketReport(
                    new PocketReportData(
                            pocketId,
                            "FPOCKET pocket 1",
                            PocketSource.FPOCKET,
                            Map.of(),
                            Map.of(),
                            Map.of(),
                            Map.of()
                    ),
                    List.of()
            );
        }

        @Override
        public PocketReportDocument generateDocument(
                long pocketId,
                long runId
        ) {
            PocketReport report = generate(pocketId, runId);
            return new PocketReportDocument(
                    report,
                    new PocketNarrative(
                            "Pocket summary.",
                            List.of(),
                            "Limitations.",
                            "Conclusion."
                    )
            );
        }
    }
}
