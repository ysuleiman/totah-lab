package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import totah.lab.web.service.ResidueEvidenceService;
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
                .standaloneSetup(new StructureController(
                        service,
                        new RecordingResidueEvidenceService()
                ))
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
    void returnsStructureArtifactForVisualization() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(
                        new RecordingStructureService(),
                        new RecordingResidueEvidenceService()
                ))
                .build();

        mockMvc.perform(get("/api/structures/2/file"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("text/plain"))
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.content()
                        .string("ATOM structure 2"));
    }

    @Test
    void returnsValidatedSamForVisualization() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(
                        new RecordingStructureService(),
                        new RecordingResidueEvidenceService()
                ))
                .build();

        mockMvc.perform(get("/api/structures/2/sam-file"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result
                        .MockMvcResultMatchers.content()
                        .string("HETATM SAM structure 2"));
    }

    @Test
    void bindsResidueNeighborCutoff() throws Exception {
        RecordingStructureService service = new RecordingStructureService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(
                        service,
                        new RecordingResidueEvidenceService()
                ))
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

    @Test
    void bindsNamedAtomDistanceRequest() throws Exception {
        RecordingStructureService service = new RecordingStructureService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(
                        service,
                        new RecordingResidueEvidenceService()
                ))
                .build();

        mockMvc.perform(get(
                        "/api/structures/2/residues/202/distance"
                                + "?toResidueId=203"
                                + "&fromAtom=SG"
                                + "&toAtom=SG"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstAtom").value("SG"))
                .andExpect(jsonPath("$.secondAtom").value("SG"))
                .andExpect(jsonPath("$.distance").value(4.8));
    }

    @Test
    void returnsStructureResidueEvidence() throws Exception {
        RecordingResidueEvidenceService evidenceService =
                new RecordingResidueEvidenceService();
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new StructureController(
                        new RecordingStructureService(),
                        evidenceService
                ))
                .build();

        mockMvc.perform(get(
                        "/api/structures/2/residue-evidence"
                                + "?analysisType=ESMC_CONSTRAINT"
                ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].residueId").value(78))
                .andExpect(jsonPath("$[0].score").value(15.140625))
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].provider")
                        .value("BIOHUB_ESMC"))
                .andExpect(jsonPath("$[0].artifactId").value(43));

        assertEquals(2L, evidenceService.structureId);
        assertEquals("ESMC_CONSTRAINT", evidenceService.analysisType);
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
        public String getStructureFileContent(long structureId) {
            return "ATOM structure " + structureId;
        }

        @Override
        public String getValidatedSamFileContent(long structureId) {
            return "HETATM SAM structure " + structureId;
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
                    java.util.List.of("CA", "SG"),
                    cutoff,
                    java.util.List.of(new NeighborDetails(
                            203L,
                            "A",
                            203,
                            " ",
                            "ASN",
                            java.util.List.of("CA", "CB"),
                            3.2
                    ))
            );
        }

        @Override
        public AtomDistance getAtomDistance(
                long structureId,
                long firstResidueId,
                long secondResidueId,
                String firstAtomName,
                String secondAtomName
        ) {
            return new AtomDistance(
                    new ResidueDetails(202, "A", 202, " ", "CYS"),
                    firstAtomName,
                    new ResidueDetails(203, "A", 203, " ", "CYS"),
                    secondAtomName,
                    4.8
            );
        }
    }

    private static final class RecordingResidueEvidenceService
            extends ResidueEvidenceService {

        private long structureId;
        private String analysisType;

        private RecordingResidueEvidenceService() {
            super(null, null);
        }

        @Override
        public java.util.List<ResidueEvidence> getEvidence(
                long structureId,
                String analysisType
        ) {
            this.structureId = structureId;
            this.analysisType = analysisType;
            return java.util.List.of(new ResidueEvidence(
                    78,
                    "ESMC_CONSTRAINT",
                    15.140625,
                    1,
                    "BIOHUB_ESMC",
                    "esmc-300m-2024-12",
                    "A",
                    12.0,
                    0.1,
                    43
            ));
        }
    }
}
