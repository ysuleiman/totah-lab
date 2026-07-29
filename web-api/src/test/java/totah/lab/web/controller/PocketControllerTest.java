package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.web.service.PocketService;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PocketControllerTest {

    @Test
    void bindsPocketAndStructureIdentifiers() throws Exception {
        RecordingPocketService service = new RecordingPocketService();

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketController(service))
                .build();

        mockMvc.perform(get("/api/pockets/7/residues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
        mockMvc.perform(get("/api/structures/3/pockets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        org.junit.jupiter.api.Assertions.assertEquals(7L, service.pocketId);
        org.junit.jupiter.api.Assertions.assertEquals(3L, service.structureId);
    }

    private static final class RecordingPocketService extends PocketService {

        private long pocketId;
        private long structureId;

        private RecordingPocketService() {
            super(null, null);
        }

        @Override
        public List<ResidueDetails> getResiduesForPocket(long pocketId) {
            this.pocketId = pocketId;
            return List.of();
        }

        @Override
        public List<PocketSummary> getPocketsForStructure(long structureId) {
            this.structureId = structureId;
            return List.of();
        }
    }
}
