package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.pocket.PocketSource;
import totah.lab.report.model.PocketReport;
import totah.lab.report.model.PocketReportData;
import totah.lab.web.service.PocketReportApplicationService;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PocketReportControllerTest {

    @Test
    void bindsPocketAndDockingRunIdentifiers() throws Exception {
        RecordingReportService service = new RecordingReportService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketReportController(service))
                .build();

        mockMvc.perform(get("/api/pockets/11/report?runId=7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pocketId").value(11))
                .andExpect(jsonPath("$.data.source").value("FPOCKET"));

        assertEquals(11L, service.pocketId);
        assertEquals(7L, service.runId);
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
    }
}
