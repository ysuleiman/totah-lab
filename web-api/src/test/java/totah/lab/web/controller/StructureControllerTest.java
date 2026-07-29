package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.web.service.StructureService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StructureControllerTest {

    @Test
    void returnsStructureWithCanonicalPocketsUrl() throws Exception {
        RecordingStructureService service = new RecordingStructureService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(service))
                .build();

        mockMvc.perform(get("/api/structures/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.pocketsUrl")
                        .value("/api/structures/2/pockets"));

        assertEquals(2L, service.structureId);
    }

    private static final class RecordingStructureService
            extends StructureService {

        private long structureId;

        private RecordingStructureService() {
            super(null);
        }

        @Override
        public StructureDetails getStructure(long structureId) {
            this.structureId = structureId;
            return new StructureDetails(
                    structureId,
                    "ALPHAFOLD",
                    "AF-Q6UX53-F1-model_v6",
                    "A",
                    1,
                    "RAW",
                    null,
                    new ReceptorSummary(1, "METTL7B"),
                    new ArtifactSummary(
                            1,
                            "structure.pdb",
                            "STRUCTURE",
                            "/structures/structure.pdb"
                    ),
                    "/api/structures/" + structureId + "/pockets"
            );
        }
    }
}
