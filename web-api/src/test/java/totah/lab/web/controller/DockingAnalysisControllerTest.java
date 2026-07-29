package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.web.service.DockingAnalysisService;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DockingAnalysisControllerTest {

    @Test
    void exposesRunScopedResidueAnalysis() throws Exception {
        RecordingService service = new RecordingService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new DockingAnalysisController(service))
                .build();

        mockMvc.perform(get("/api/structures/3/docking-runs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].totalLigandCount").value(9999));

        mockMvc.perform(get("/api/docking-runs/7/residue-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residueId").value(200))
                .andExpect(jsonPath("$[0].contactingLigandFraction")
                        .value(0.3));

        mockMvc.perform(get(
                        "/api/docking-runs/7/residue-score-bands"
                                + "?residueId=200"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].scoreLower").value(-8))
                .andExpect(jsonPath("$[0].contactingLigandCount")
                        .value(30));

        assertEquals(3L, service.structureId);
        assertEquals(7L, service.runId);
        assertEquals(200L, service.residueId);
    }

    private static final class RecordingService
            extends DockingAnalysisService {

        private long structureId;
        private long runId;
        private Long residueId;

        private RecordingService() {
            super(null);
        }

        @Override
        public List<DockingRunSummary> getRunsForStructure(
                long structureId
        ) {
            this.structureId = structureId;
            return List.of(new DockingRunSummary(
                    7,
                    structureId,
                    2,
                    LocalDateTime.of(2026, 7, 29, 12, 0),
                    9999,
                    9999
            ));
        }

        @Override
        public List<ResidueAnalysis> getResidueSummary(long runId) {
            this.runId = runId;
            return List.of(new ResidueAnalysis(
                    runId,
                    3,
                    2,
                    200,
                    "A",
                    200,
                    "ASP",
                    100,
                    30,
                    0.3,
                    100,
                    30,
                    0.3,
                    20,
                    10,
                    0.5,
                    10,
                    1,
                    0.1,
                    0.4,
                    5.0,
                    2.3,
                    -8.4,
                    -8.2,
                    -11.0,
                    -6.2,
                    2.1,
                    3.0,
                    3.0
            ));
        }

        @Override
        public List<ResidueScoreBand> getResidueScoreBands(
                long runId,
                Long residueId
        ) {
            this.runId = runId;
            this.residueId = residueId;
            return List.of(new ResidueScoreBand(
                    runId,
                    3,
                    2,
                    -8,
                    -6,
                    residueId,
                    "A",
                    200,
                    "ASP",
                    100,
                    30,
                    0.3,
                    100,
                    30,
                    0.3,
                    -7.1,
                    -7.0,
                    -7.9,
                    -6.1,
                    2.1,
                    3.0,
                    3.0
            ));
        }
    }
}
