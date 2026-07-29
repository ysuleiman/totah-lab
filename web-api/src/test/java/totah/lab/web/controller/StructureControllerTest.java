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
                .andExpect(jsonPath("$.receptor.uniProtId").value("Q6UX53"))
                .andExpect(jsonPath("$.receptor.proteinName")
                        .value("Thiol S-methyltransferase TMT1B"))
                .andExpect(jsonPath("$.chosenPocket.id").value(1))
                .andExpect(jsonPath("$.chosenPocket.source")
                        .value("FPOCKET"))
                .andExpect(jsonPath("$.residues[0].residueName")
                        .value("MET"))
                .andExpect(jsonPath("$.pocketsUrl")
                        .value("/api/structures/2/pockets"));

        assertEquals(2L, service.structureId);
    }

    @Test
    void bindsResidueNeighborCutoff() throws Exception {
        RecordingStructureService service = new RecordingStructureService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(service))
                .build();

        mockMvc.perform(get(
                        "/api/structures/2/residues/202/neighbors"
                                + "?cutoff=5.5"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedResidue.id").value(202))
                .andExpect(jsonPath("$.cutoff").value(5.5))
                .andExpect(jsonPath("$.neighbors[0].distance").value(3.2));

        assertEquals(2L, service.neighborStructureId);
        assertEquals(202L, service.residueId);
        assertEquals(5.5, service.cutoff);
    }

    private static final class RecordingStructureService
            extends StructureService {

        private long structureId;
        private long neighborStructureId;
        private long residueId;
        private double cutoff;

        private RecordingStructureService() {
            super(null, null);
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
                    new ReceptorSummary(
                            1,
                            "METTL7B",
                            "Q6UX53",
                            "Thiol S-methyltransferase TMT1B",
                            "METTL7B",
                            "Homo sapiens"
                    ),
                    new ArtifactSummary(
                            1,
                            "structure.pdb",
                            "STRUCTURE",
                            "/structures/structure.pdb"
                    ),
                    new ChosenPocketSummary(1, 2, "FPOCKET"),
                    java.util.List.of(new ResidueDetails(
                            1,
                            "A",
                            1,
                            " ",
                            "MET"
                    )),
                    "/api/structures/" + structureId + "/pockets"
            );
        }

        @Override
        public ResidueNeighborhood getResidueNeighbors(
                long structureId,
                long residueId,
                double cutoff
        ) {
            this.neighborStructureId = structureId;
            this.residueId = residueId;
            this.cutoff = cutoff;
            return new ResidueNeighborhood(
                    new ResidueDetails(202, "A", 202, " ", "CYS"),
                    cutoff,
                    java.util.List.of(new NeighborDetails(
                            203L,
                            "A",
                            203,
                            " ",
                            "ASN",
                            3.2
                    ))
            );
        }
    }
}
