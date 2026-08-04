package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.CompositePocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignment;
import totah.lab.athena.pocket.compare.PocketComparator;
import totah.lab.athena.pocket.compare.PocketComparisonOptions;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.web.persistence.PocketSummaryEntity;
import totah.lab.web.persistence.PocketSummaryRepository;
import totah.lab.web.service.PocketSimilarityService.PocketCandidate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PocketSimilarityServiceTest {

    private static final long QUERY_POCKET_ID = 42L;

    private static final PocketGeometryBasis BASIS =
            PocketGeometryBasis.RESIDUE_ATOMS;

    // Irregular 8-point cloud: non-degenerate for principal-axis
    // alignment (>= 6 points, full spatial variance).
    private static final double[][] IRREGULAR_CLOUD = {
            {0.0, 0.0, 0.0},
            {10.0, 0.0, 0.0},
            {0.0, 6.0, 0.0},
            {0.0, 0.0, 3.0},
            {8.0, 5.0, 2.0},
            {2.0, 4.0, 6.0},
            {7.0, 1.0, 5.0},
            {3.0, 8.0, 1.0}
    };

    // The same cloud uniformly scaled by 2: same shape family but no
    // rigid alignment can superimpose it, so comparison similarity
    // drops while point counts stay equal.
    private static final double[][] IRREGULAR_SCALED = {
            {0.0, 0.0, 0.0},
            {20.0, 0.0, 0.0},
            {0.0, 12.0, 0.0},
            {0.0, 0.0, 6.0},
            {16.0, 10.0, 4.0},
            {4.0, 8.0, 12.0},
            {14.0, 2.0, 10.0},
            {6.0, 16.0, 2.0}
    };

    private static final double QUERY_VOLUME = 100.0;
    private static final int QUERY_RESIDUE_COUNT = 20;
    private static final double QUERY_HYDROPHOBIC = 0.5;

    private final PocketSummaryRepository repository =
            mock(PocketSummaryRepository.class);
    private final FakeGeometryLoader geometryLoader =
            new FakeGeometryLoader();
    private final PocketSimilarityService service =
            new PocketSimilarityService(repository, geometryLoader);

    @Test
    void queriesSummaryAndCandidatesExactlyOnce() {
        stubQuerySummary();
        stubCandidates(List.of(candidateSummary(
                7L, 120.0, 10, 0.4
        )));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(7L, cloud(IRREGULAR_CLOUD));

        List<PocketCandidate> result = service.findSimilar(QUERY_POCKET_ID, 10);

        verify(repository, times(1)).findById(QUERY_POCKET_ID);
        verify(repository, times(1)).findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                eq(0.60),
                eq(1.60),
                eq(0.60),
                eq(1.60),
                eq(PageRequest.of(0, 50))
        );

        assertEquals(1, result.size());
        PocketCandidate candidate = result.get(0);
        assertEquals(7L, candidate.pocketId());
        assertEquals(0.2, candidate.volumeDistance(), 1e-9);
        assertEquals(0.5, candidate.residueDistance(), 1e-9);
        assertEquals(0.1, candidate.chemistryDistance(), 1e-9);
        assertEquals(0.8, candidate.descriptorDistance(), 1e-9);
    }

    @Test
    void scalesStageOneLimitWithRequestedLimit() {
        stubQuerySummary();
        stubCandidates(List.of());
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));

        service.findSimilar(QUERY_POCKET_ID, 100);
        verify(repository).findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                eq(0.60),
                eq(1.60),
                eq(0.60),
                eq(1.60),
                eq(PageRequest.of(0, 500))
        );

        service.findSimilar(QUERY_POCKET_ID, 500);
        verify(repository).findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                eq(0.60),
                eq(1.60),
                eq(0.60),
                eq(1.60),
                eq(PageRequest.of(0, 2500))
        );
    }

    @Test
    void loadsAllGeometryWithASingleCall() {
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(1L, 110.0, 20, 0.5),
                candidateSummary(2L, 120.0, 20, 0.5),
                candidateSummary(3L, 130.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(2L, cloud(IRREGULAR_SCALED));
        geometryLoader.register(3L, cloud(IRREGULAR_CLOUD));

        service.findSimilar(QUERY_POCKET_ID, 10);

        assertEquals(1, geometryLoader.loadAllCount());
        assertEquals(
                List.of(QUERY_POCKET_ID, 1L, 2L, 3L),
                geometryLoader.requestedPocketIds()
        );
    }

    @Test
    void skipsCandidatesWhoseGeometryCannotBeLoaded() {
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(1L, 110.0, 20, 0.5),
                candidateSummary(2L, 120.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        geometryLoader.failOn(2L);

        List<PocketCandidate> result = service.findSimilar(QUERY_POCKET_ID, 10);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).pocketId());
    }

    @Test
    void failsRequestWhenQueryGeometryCannotBeLoaded() {
        stubQuerySummary();
        stubCandidates(List.of(candidateSummary(1L, 110.0, 20, 0.5)));
        geometryLoader.failOn(QUERY_POCKET_ID);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.findSimilar(QUERY_POCKET_ID, 10)
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    @Test
    void failsWhenQuerySummaryIsMissing() {
        when(repository.findById(QUERY_POCKET_ID))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.findSimilar(QUERY_POCKET_ID, 10)
        );

        assertEquals(404, exception.getStatusCode().value());
        verify(repository, never()).findDescriptorCandidates(
                any(Long.class),
                any(Double.class),
                any(Double.class),
                any(Double.class),
                any(Double.class),
                any(Pageable.class)
        );
    }

    @Test
    void ranksByDetailedSimilarityBeforeShapeDistance() {
        stubQuerySummary();
        // Candidate 2: identical cloud -> similarity 1.0, shape distance 0.
        // Candidate 3: rotated cloud -> similarity < 1, shape distance 0.
        // Candidate 3 has the better Stage 1 descriptor distance, so any
        // ranking that does not prioritize detailed similarity loses it.
        stubCandidates(List.of(
                candidateSummary(2L, 600.0, 20, 0.5),
                candidateSummary(3L, 200.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(2L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(3L, cloud(IRREGULAR_SCALED));

        List<PocketCandidate> result = service.findSimilar(QUERY_POCKET_ID, 10);

        assertEquals(List.of(2L, 3L), pocketIds(result));
    }

    @Test
    void truncatesToStageTwoBeforeDetailedComparison() {
        stubQuerySummary();

        List<PocketSummaryEntity> summaries = new ArrayList<>();
        for (long pocketId = 1L; pocketId <= 35L; pocketId++) {
            double volume = pocketId <= 30L
                    ? QUERY_VOLUME + pocketId
                    : 10100.0;
            summaries.add(candidateSummary(
                    pocketId, volume, QUERY_RESIDUE_COUNT, QUERY_HYDROPHOBIC
            ));
            geometryLoader.register(pocketId, cloud(IRREGULAR_CLOUD));
        }
        stubCandidates(summaries);
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));

        List<PocketCandidate> result = service.findSimilar(QUERY_POCKET_ID, 10);

        // All 35 clouds compare identically, so Stage 2 shape ordering
        // (descriptor distance tie-break) decides the survivors: with a
        // Stage 2 limit of 20, pockets 21-35 must be cut before Stage 3,
        // and the final limit applies.
        List<Long> expected = new ArrayList<>();
        for (long pocketId = 1L; pocketId <= 10L; pocketId++) {
            expected.add(pocketId);
        }
        assertEquals(expected, pocketIds(result));
        assertTrue(pocketIds(result).stream().allMatch(id -> id <= 20L));
    }

    @Test
    void appliesFinalLimit() {
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(1L, 110.0, 20, 0.5),
                candidateSummary(2L, 120.0, 20, 0.5),
                candidateSummary(3L, 130.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(2L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(3L, cloud(IRREGULAR_CLOUD));

        List<PocketCandidate> result = service.findSimilar(QUERY_POCKET_ID, 2);

        assertEquals(2, result.size());
    }

    @Test
    void diagnosticExposesStageRanksAndComparisonMetrics() {
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(1L, 110.0, 20, 0.5),
                candidateSummary(2L, 120.0, 20, 0.5),
                candidateSummary(3L, 130.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(2L, cloud(IRREGULAR_SCALED));
        geometryLoader.register(3L, cloud(IRREGULAR_CLOUD));

        List<PocketSimilarityDiagnostic> rows =
                service.diagnoseSimilar(QUERY_POCKET_ID, 10);

        // Final ordering is the Stage 3 ordering: the scaled cloud
        // (pocket 2) drops behind the two identical clouds.
        assertEquals(
                List.of(1L, 3L, 2L),
                rows.stream()
                        .map(PocketSimilarityDiagnostic::pocketId)
                        .toList()
        );
        assertEquals(
                List.of(1, 2, 3),
                rows.stream()
                        .map(PocketSimilarityDiagnostic::stageThreeRank)
                        .toList()
        );

        PocketSimilarityDiagnostic first = rows.get(0);
        assertEquals(1, first.stageOneRank());
        assertEquals(1, first.stageTwoRank());
        assertEquals(1, first.stageThreeRank());
        assertEquals(0.1, first.descriptorDistance(), 1e-9);
        assertEquals(0.0, first.shapeDistance(), 1e-9);
        assertEquals(1.0, first.overallSimilarity(), 1e-9);
        assertEquals(1.0, first.queryCoverage(), 1e-9);
        assertEquals(1.0, first.candidateCoverage(), 1e-9);
        assertEquals(0.0, first.queryToCandidateMeanDistance(), 1e-9);
        assertEquals(0.0, first.candidateToQueryMeanDistance(), 1e-9);
        assertEquals(8, first.queryPointCount());
        assertEquals(8, first.candidatePointCount());
        assertEquals("RESIDUE_ATOMS", first.basis());
        assertEquals("UP1", first.uniProtId());
        assertEquals("Protein 1", first.proteinName());
        assertEquals("GENE1", first.geneName());
        assertEquals("Homo sapiens", first.organism());

        PocketSimilarityDiagnostic second = rows.get(1);
        assertEquals(3L, second.pocketId());
        assertEquals(3, second.stageOneRank());
        assertEquals(2, second.stageTwoRank());
        assertEquals(2, second.stageThreeRank());

        PocketSimilarityDiagnostic third = rows.get(2);
        assertEquals(2L, third.pocketId());
        assertEquals(2, third.stageOneRank());
        assertEquals(3, third.stageTwoRank());
        assertEquals(3, third.stageThreeRank());
        assertTrue(third.overallSimilarity() < 1.0);
        assertEquals(0.0, third.queryCoverage(), 1e-9);
        assertEquals(0.0, third.candidateCoverage(), 1e-9);
    }

    @Test
    void diagnosticKeepsRepositoryAndGeometryAccessSingle() {
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(1L, 110.0, 20, 0.5),
                candidateSummary(2L, 120.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(2L, cloud(IRREGULAR_SCALED));

        service.diagnoseSimilar(QUERY_POCKET_ID, 10);

        verify(repository, times(1)).findById(QUERY_POCKET_ID);
        verify(repository, times(1)).findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                eq(0.60),
                eq(1.60),
                eq(0.60),
                eq(1.60),
                any(Pageable.class)
        );
        assertEquals(1, geometryLoader.loadAllCount());
    }

    @Test
    void diagnosticRespectsLimit() {
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(1L, 110.0, 20, 0.5),
                candidateSummary(2L, 120.0, 20, 0.5),
                candidateSummary(3L, 130.0, 20, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(2L, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(3L, cloud(IRREGULAR_CLOUD));

        assertEquals(
                2,
                service.diagnoseSimilar(QUERY_POCKET_ID, 2).size()
        );
    }

    @Test
    void geometryReturnsIdentityAndPointCloud() {
        stubQuerySummary();
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));

        PocketGeometryView view = service.getGeometry(QUERY_POCKET_ID);

        assertEquals(QUERY_POCKET_ID, view.pocketId());
        assertEquals("ACC-" + QUERY_POCKET_ID, view.sourceAccession());
        assertEquals(8, view.pointCount());
        assertEquals(8, view.points().size());
        assertEquals(new Point3D(3.75, 3.0, 2.125), view.centroid());
        assertEquals("RESIDUE_ATOMS", view.basis());
        assertEquals(1, geometryLoader.loadAllCount());
    }

    @Test
    void compareLoadsGeometryOnceAndUsesAthenaAlignment() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));

        PocketComparisonDetails details =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        assertEquals(1, geometryLoader.loadAllCount());
        assertEquals(
                List.of(QUERY_POCKET_ID, 1L),
                geometryLoader.requestedPocketIds()
        );

        assertEquals(
                1.0,
                details.comparison().overallSimilarity(),
                1e-9
        );
        assertEquals(8, details.query().pointCount());
        assertEquals(8, details.candidate().pointCount());
        assertEquals(
                PocketSimilarityService.ACTIVE_ALIGNER,
                details.aligner()
        );

        // The response mapping must mirror the Athena alignment
        // exactly: the fixed query cloud and the transformed candidate.
        PocketAlignment alignment = new PocketComparator(
                new CompositePocketAligner(),
                PocketComparisonOptions.defaults()
        ).align(
                cloud(IRREGULAR_CLOUD),
                cloud(IRREGULAR_CLOUD)
        );

        assertEquals(
                alignment.query().points(),
                details.alignedQueryPoints()
        );
        assertEquals(
                alignment.alignedCandidate().points(),
                details.alignedCandidatePoints()
        );

        // The aligned candidate equals the retained transform applied
        // to the original candidate points.
        List<Point3D> transformed =
                alignment.transform().apply(
                        cloud(IRREGULAR_CLOUD).points()
                );

        assertEquals(transformed.size(),
                details.alignedCandidatePoints().size());

        for (int index = 0; index < transformed.size(); index++) {
            assertEquals(
                    transformed.get(index).x(),
                    details.alignedCandidatePoints().get(index).x(),
                    1e-6
            );
            assertEquals(
                    transformed.get(index).y(),
                    details.alignedCandidatePoints().get(index).y(),
                    1e-6
            );
            assertEquals(
                    transformed.get(index).z(),
                    details.alignedCandidatePoints().get(index).z(),
                    1e-6
            );
        }
    }

    @Test
    void geometryFailsForMissingSummary() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.getGeometry(99L)
        );

        assertEquals(404, exception.getStatusCode().value());
    }

    @Test
    void compareFailsWhenCandidateHasNoGeometry() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.failOn(1L);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.compareGeometries(QUERY_POCKET_ID, 1L)
        );

        assertEquals(422, exception.getStatusCode().value());
    }

    private void stubQuerySummary() {
        when(repository.findById(QUERY_POCKET_ID))
                .thenReturn(Optional.of(new TestPocketSummaryEntity(
                        QUERY_POCKET_ID,
                        QUERY_VOLUME,
                        QUERY_RESIDUE_COUNT,
                        QUERY_HYDROPHOBIC
                )));
    }

    private void stubCandidates(List<PocketSummaryEntity> candidates) {
        when(repository.findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                eq(0.60),
                eq(1.60),
                eq(0.60),
                eq(1.60),
                any(Pageable.class)
        )).thenReturn(candidates);
    }

    private static PocketSummaryEntity candidateSummary(
            long pocketId,
            double volume,
            int residueCount,
            double hydrophobicFraction
    ) {
        return new TestPocketSummaryEntity(
                pocketId,
                volume,
                residueCount,
                hydrophobicFraction
        );
    }

    private static class TestPocketSummaryEntity
            extends PocketSummaryEntity {

        private final long pocketId;
        private final double volume;
        private final int residueCount;
        private final double hydrophobicFraction;

        TestPocketSummaryEntity(
                long pocketId,
                double volume,
                int residueCount,
                double hydrophobicFraction
        ) {
            this.pocketId = pocketId;
            this.volume = volume;
            this.residueCount = residueCount;
            this.hydrophobicFraction = hydrophobicFraction;
        }

        @Override
        public Long getPocketId() {
            return pocketId;
        }

        @Override
        public Long getStructureId() {
            return 1000L + pocketId;
        }

        @Override
        public String getSourceAccession() {
            return "ACC-" + pocketId;
        }

        @Override
        public Integer getPocketNumber() {
            return 1;
        }

        @Override
        public Double getVolume() {
            return volume;
        }

        @Override
        public Integer getResidueCount() {
            return residueCount;
        }

        @Override
        public Double getHydrophobicFraction() {
            return hydrophobicFraction;
        }

        @Override
        public Double getAromaticFraction() {
            return 0.1;
        }

        @Override
        public Double getPolarFraction() {
            return 0.2;
        }

        @Override
        public Double getPositiveFraction() {
            return 0.1;
        }

        @Override
        public Double getNegativeFraction() {
            return 0.1;
        }

        @Override
        public String getUniProtId() {
            return "UP" + pocketId;
        }

        @Override
        public String getProteinName() {
            return "Protein " + pocketId;
        }

        @Override
        public String getGeneName() {
            return "GENE" + pocketId;
        }

        @Override
        public String getOrganism() {
            return "Homo sapiens";
        }
    }

    private static PocketPointCloud cloud(double[][] coordinates) {
        List<Point3D> points = Arrays.stream(coordinates)
                .map(coordinate -> new Point3D(
                        coordinate[0],
                        coordinate[1],
                        coordinate[2]
                ))
                .toList();
        return new PocketPointCloud(points, BASIS);
    }

    private static List<Long> pocketIds(List<PocketCandidate> candidates) {
        return candidates.stream()
                .map(PocketCandidate::pocketId)
                .toList();
    }

    private static final class FakeGeometryLoader
            extends PocketPointCloudLoader {

        private final Map<Long, PocketPointCloud> clouds = new HashMap<>();
        private final Set<Long> failing = new HashSet<>();
        private int loadAllCount;
        private List<Long> requestedPocketIds = List.of();

        private FakeGeometryLoader() {
            super(null);
        }

        void register(long pocketId, PocketPointCloud cloud) {
            clouds.put(pocketId, cloud);
        }

        void failOn(long pocketId) {
            failing.add(pocketId);
        }

        int loadAllCount() {
            return loadAllCount;
        }

        List<Long> requestedPocketIds() {
            return requestedPocketIds;
        }

        @Override
        public Map<Long, PocketPointCloud> loadAll(
                Collection<Long> pocketIds
        ) {
            loadAllCount++;
            requestedPocketIds = List.copyOf(pocketIds);

            Map<Long, PocketPointCloud> result = new LinkedHashMap<>();
            for (Long pocketId : pocketIds) {
                PocketPointCloud cloud = clouds.get(pocketId);
                if (cloud != null && !failing.contains(pocketId)) {
                    result.put(pocketId, cloud);
                }
            }
            return result;
        }
    }
}
