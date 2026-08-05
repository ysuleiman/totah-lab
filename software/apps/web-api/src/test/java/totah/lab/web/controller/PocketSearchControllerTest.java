package totah.lab.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.PocketComparison;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.gaia.geometry.BoundingBox;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.service.ChemistryAssessmentView;
import totah.lab.web.service.PocketComparisonDetails;
import totah.lab.web.service.PocketGeometryView;
import totah.lab.web.service.PocketSimilarityDiagnostic;
import totah.lab.web.service.PocketSimilarityService;
import totah.lab.web.service.PocketSimilarityService.PocketCandidate;
import totah.lab.web.service.ResidueCorrespondenceView;
import totah.lab.web.service.ResidueMatchView;
import totah.lab.web.service.ResiduePointView;
import totah.lab.web.service.ResidueSummaryView;
import totah.lab.web.service.TransformView;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PocketSearchControllerTest {

    @Test
    void returnsOnlyTheExistingResponseFields() throws Exception {
        RecordingPocketSimilarityService service =
                new RecordingPocketSimilarityService();
        service.result = List.of(new PocketCandidate(
                7L,
                11L,
                "AF-P12345",
                3,
                0,
                0.8,
                0.2,
                0.5,
                0.1,
                "P12345",
                "Test protein",
                "GENE1",
                "Homo sapiens"
        ));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketSearchController(service))
                .build();

        mockMvc.perform(get("/api/pockets/42/similar")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].*", hasSize(8)))
                .andExpect(jsonPath("$[0].pocketId").value(7))
                .andExpect(jsonPath("$[0].structureId").value(11))
                .andExpect(jsonPath("$[0].sourceAccession")
                        .value("AF-P12345"))
                .andExpect(jsonPath("$[0].pocketNumber").value(3))
                .andExpect(jsonPath("$[0].descriptorDistance").value(0.8))
                .andExpect(jsonPath("$[0].volumeDistance").value(0.2))
                .andExpect(jsonPath("$[0].residueDistance").value(0.5))
                .andExpect(jsonPath("$[0].chemistryDistance").value(0.1))
                .andExpect(jsonPath("$[0].shapeDistance").doesNotExist())
                .andExpect(jsonPath("$[0].overallSimilarity").doesNotExist());

        assertEquals(42L, service.pocketId);
        assertEquals(5, service.limit);
    }

    @Test
    void usesDefaultLimitWhenNotProvided() throws Exception {
        RecordingPocketSimilarityService service =
                new RecordingPocketSimilarityService();

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketSearchController(service))
                .build();

        mockMvc.perform(get("/api/pockets/42/similar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());

        assertEquals(42L, service.pocketId);
        assertEquals(100, service.limit);
    }

    @Test
    void diagnosticEndpointExposesStageRanksAndMetrics() throws Exception {
        RecordingPocketSimilarityService service =
                new RecordingPocketSimilarityService();
        service.diagnostic = List.of(new PocketSimilarityDiagnostic(
                7L,
                11L,
                "AF-P12345",
                3,
                12,
                5,
                0.8,
                0.2,
                0.5,
                0.1,
                2,
                0.04,
                1,
                0.95,
                0.93,
                0.99,
                0.87,
                0.91,
                0.4,
                0.5,
                0.45,
                1.2,
                20,
                21,
                "RESIDUE_ATOMS",
                0.88,
                0.77,
                0.9,
                0.1,
                15,
                2,
                1,
                2,
                20,
                1.0,
                "STRONG_SIMILARITY",
                0.89,
                "P12345",
                "Test protein",
                "GENE1",
                "Homo sapiens"
        ));

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketSearchController(service))
                .build();

        mockMvc.perform(get("/api/pockets/42/similar/diagnostic")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].pocketId").value(7))
                .andExpect(jsonPath("$[0].stageOneRank").value(5))
                .andExpect(jsonPath("$[0].stageTwoRank").value(2))
                .andExpect(jsonPath("$[0].stageThreeRank").value(1))
                .andExpect(jsonPath("$[0].shapeDistance").value(0.04))
                .andExpect(jsonPath("$[0].geometricOverallSimilarity")
                        .value(0.95))
                .andExpect(jsonPath("$[0].overallSimilarity")
                        .doesNotExist())
                .andExpect(jsonPath("$[0].chemistrySimilarity")
                        .value(0.88))
                .andExpect(jsonPath("$[0].chemistryCoverageAdjustedSimilarity")
                        .value(0.77))
                .andExpect(jsonPath("$[0].matchedResidueCount").value(20))
                .andExpect(jsonPath("$[0].classification")
                        .value("STRONG_SIMILARITY"))
                .andExpect(jsonPath("$[0].finalSimilarity").value(0.89))
                .andExpect(jsonPath("$[0].queryCoverage").value(0.87))
                .andExpect(jsonPath("$[0].candidateCoverage").value(0.91))
                .andExpect(jsonPath("$[0].queryToCandidateMeanDistance")
                        .value(0.4))
                .andExpect(jsonPath("$[0].candidateToQueryMeanDistance")
                        .value(0.5))
                .andExpect(jsonPath("$[0].queryPointCount").value(20))
                .andExpect(jsonPath("$[0].candidatePointCount").value(21))
                .andExpect(jsonPath("$[0].alphaSphereCount").value(12))
                .andExpect(jsonPath("$[0].basis").value("RESIDUE_ATOMS"));

        assertEquals(42L, service.pocketId);
        assertEquals(5, service.limit);
    }

    @Test
    void geometryEndpointReturnsPointCloud() throws Exception {
        RecordingPocketSimilarityService service =
                new RecordingPocketSimilarityService();
        service.geometry = new PocketGeometryView(
                42L,
                1000L,
                "AF-Q6UX53-F1-model_v6",
                2,
                4,
                new Point3D(2.5, 2.5, 2.5),
                new BoundingBox(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 10.0, 10.0)
                ),
                "RESIDUE_ATOMS",
                List.of(
                        new Point3D(0.0, 0.0, 0.0),
                        new Point3D(10.0, 0.0, 0.0),
                        new Point3D(0.0, 10.0, 0.0),
                        new Point3D(0.0, 0.0, 10.0)
                ),
                List.of(),
                691.9,
                0.5,
                0.6,
                16,
                20,
                78
        );

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketSearchController(service))
                .build();

        mockMvc.perform(get("/api/pockets/42/geometry"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pocketId").value(42))
                .andExpect(jsonPath("$.sourceAccession")
                        .value("AF-Q6UX53-F1-model_v6"))
                .andExpect(jsonPath("$.pocketNumber").value(2))
                .andExpect(jsonPath("$.pointCount").value(4))
                .andExpect(jsonPath("$.centroid.x").value(2.5))
                .andExpect(jsonPath("$.points", hasSize(4)))
                .andExpect(jsonPath("$.points[1].x").value(10.0))
                .andExpect(jsonPath("$.alphaSpheres", hasSize(0)))
                .andExpect(jsonPath("$.basis").value("RESIDUE_ATOMS"));

        assertEquals(42L, service.pocketId);
    }

    @Test
    void compareEndpointReturnsAlignmentAndMetrics() throws Exception {
        RecordingPocketSimilarityService service =
                new RecordingPocketSimilarityService();
        service.comparisonDetails = new PocketComparisonDetails(
                new PocketGeometryView(
                        42L, 1000L, "AF-Q6UX53-F1-model_v6", 2, 4,
                        new Point3D(2.5, 2.5, 2.5),
                        new BoundingBox(
                                new Point3D(0.0, 0.0, 0.0),
                                new Point3D(10.0, 10.0, 10.0)
                        ),
                        "RESIDUE_ATOMS",
                        List.of(new Point3D(0.0, 0.0, 0.0)),
                        List.of(),
                        691.9,
                        0.5,
                        0.6,
                        16,
                        20,
                        78
                ),
                new PocketGeometryView(
                        7L, 1001L, "AF-P12345-F1-model_v6", 3, 4,
                        new Point3D(1.0, 1.0, 1.0),
                        new BoundingBox(
                                new Point3D(0.0, 0.0, 0.0),
                                new Point3D(5.0, 5.0, 5.0)
                        ),
                        "RESIDUE_ATOMS",
                        List.of(new Point3D(1.0, 1.0, 1.0)),
                        List.of(),
                        300.0,
                        0.4,
                        0.5,
                        14,
                        18,
                        0
                ),
                List.of(new Point3D(-2.5, -2.5, -2.5)),
                List.of(new Point3D(0.0, 0.0, 0.0)),
                new PocketComparison(
                        0.9, 0.85, 1.0, 0.8, 0.75,
                        0.4, 0.5, 0.45, 1.2,
                        4, 4,
                        PocketGeometryBasis.RESIDUE_ATOMS
                ),
                "PCA_ICP",
                new ResidueCorrespondenceView(
                        List.of(new ResidueMatchView(
                                new ResiduePointView(
                                        "A", 202, "", "CYS",
                                        "A:CYS202", "CYSTEINE",
                                        new Point3D(1.0, 2.0, 3.0)
                                ),
                                new ResiduePointView(
                                        "B", 145, "A", "CYS",
                                        "B:CYS145A", "CYSTEINE",
                                        new Point3D(1.5, 2.0, 3.0)
                                ),
                                0.5,
                                "IDENTICAL",
                                true,
                                true
                        )),
                        List.of(new ResiduePointView(
                                "A", 88, "", "LEU",
                                "A:LEU88", "HYDROPHOBIC",
                                new Point3D(9.0, 9.0, 9.0)
                        )),
                        List.of(),
                        new ResidueSummaryView(
                                2, 1, 1, 1, 0,
                                0.5, 1.0, 1.0, 1.0,
                                0.5, 0.5
                        )
                ),
                new TransformView(
                        new double[][]{
                                {0.0, -1.0, 0.0},
                                {1.0, 0.0, 0.0},
                                {0.0, 0.0, 1.0}
                        },
                        new Point3D(3.0, -2.0, 5.0)
                ),
                List.of("CYS148", "CYS202"),
                new ChemistryAssessmentView(
                        1.0,
                        0.5,
                        1.0,
                        0.0,
                        1,
                        0,
                        0,
                        0,
                        1,
                        2,
                        1,
                        1.0,
                        1,
                        "STRONG_SIMILARITY",
                        0.88
                )
        );

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketSearchController(service))
                .build();

        mockMvc.perform(get("/api/pockets/42/compare/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.query.pocketId").value(42))
                .andExpect(jsonPath("$.candidate.pocketId").value(7))
                .andExpect(jsonPath("$.alignedQueryPoints", hasSize(1)))
                .andExpect(jsonPath("$.alignedQueryPoints[0].x")
                        .value(-2.5))
                .andExpect(jsonPath("$.alignedCandidatePoints", hasSize(1)))
                .andExpect(jsonPath("$.comparison.overallSimilarity")
                        .value(0.9))
                .andExpect(jsonPath("$.comparison.queryCoverage")
                        .value(0.8))
                .andExpect(jsonPath("$.comparison.basis")
                        .value("RESIDUE_ATOMS"))
                .andExpect(jsonPath("$.residueCorrespondence.matches",
                        hasSize(1)))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].query.label")
                        .value("A:CYS202"))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].query.chemistry")
                        .value("CYSTEINE"))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].candidate.label")
                        .value("B:CYS145A"))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].candidate.insertionCode")
                        .value("A"))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].distanceAngstroms")
                        .value(0.5))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].matchType")
                        .value("IDENTICAL"))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.matches[0].identicalResidue")
                        .value(true))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.unmatchedQuery",
                        hasSize(1)))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.unmatchedQuery[0].label")
                        .value("A:LEU88"))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.summary.matchedCount")
                        .value(1))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.summary.matchedFractionQuery")
                        .value(0.5))
                .andExpect(jsonPath(
                        "$.residueCorrespondence.summary.identicalFraction")
                        .value(1.0))
                .andExpect(jsonPath("$.transform.rotation[0][1]")
                        .value(-1.0))
                .andExpect(jsonPath("$.transform.rotation[1][0]")
                        .value(1.0))
                .andExpect(jsonPath("$.transform.translation.x")
                        .value(3.0))
                .andExpect(jsonPath("$.transform.translation.z")
                        .value(5.0))
                .andExpect(jsonPath("$.chemistryAssessment.chemistrySimilarity")
                        .value(1.0))
                .andExpect(jsonPath("$.chemistryAssessment.keyMatchedCount")
                        .value(1))
                .andExpect(jsonPath("$.chemistryAssessment.classification")
                        .value("STRONG_SIMILARITY"))
                .andExpect(jsonPath("$.chemistryAssessment.finalSimilarity")
                        .value(0.88));

        assertEquals(42L, service.pocketId);
        assertEquals(7L, service.candidatePocketId);
    }

    @Test
    void geometryEndpointPropagatesNotFound() throws Exception {
        RecordingPocketSimilarityService service =
                new RecordingPocketSimilarityService();
        service.failure = new ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND,
                "Pocket summary not found: 99"
        );

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new PocketSearchController(service))
                .build();

        mockMvc.perform(get("/api/pockets/99/geometry"))
                .andExpect(status().isNotFound());
    }

    private static final class RecordingPocketSimilarityService
            extends PocketSimilarityService {

        private long pocketId;
        private long candidatePocketId;
        private int limit;
        private List<PocketCandidate> result = List.of();
        private List<PocketSimilarityDiagnostic> diagnostic = List.of();
        private PocketGeometryView geometry;
        private PocketComparisonDetails comparisonDetails;
        private RuntimeException failure;

        private RecordingPocketSimilarityService() {
            super(null, null, null, null);
        }

        @Override
        public List<PocketCandidate> findSimilar(
                long queryPocketId,
                int limit
        ) {
            this.pocketId = queryPocketId;
            this.limit = limit;
            return result;
        }

        @Override
        public List<PocketSimilarityDiagnostic> diagnoseSimilar(
                long queryPocketId,
                int limit
        ) {
            this.pocketId = queryPocketId;
            this.limit = limit;
            return diagnostic;
        }

        @Override
        public PocketGeometryView getGeometry(long pocketId) {
            this.pocketId = pocketId;
            if (failure != null) {
                throw failure;
            }
            return geometry;
        }

        @Override
        public PocketComparisonDetails compareGeometries(
                long queryPocketId,
                long candidatePocketId
        ) {
            this.pocketId = queryPocketId;
            this.candidatePocketId = candidatePocketId;
            return comparisonDetails;
        }
    }
}
