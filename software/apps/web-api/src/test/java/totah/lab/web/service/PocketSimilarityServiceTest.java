package totah.lab.web.service;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.CompositePocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignment;
import totah.lab.athena.pocket.compare.PocketComparator;
import totah.lab.athena.pocket.compare.PocketComparisonOptions;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.PocketResiduePointTransformer;
import totah.lab.athena.pocket.compare.residue.ResidueChemistry;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondenceCalculator;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;
import totah.lab.athena.pocket.geometry.PocketGeometryBasis;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;
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

    // A larger "merged" pocket: the query cloud (shifted) embedded in
    // a conserved region, plus a small adjacent subpocket extending
    // just past the main cloud's +x edge that the query does not cover.
    private static final double[][] MERGED_POCKET_CLOUD = {
            {1.0, 0.5, 0.0},
            {11.0, 0.5, 0.0},
            {1.0, 6.5, 0.0},
            {1.0, 0.5, 3.0},
            {9.0, 5.5, 2.0},
            {3.0, 4.5, 6.0},
            {8.0, 1.5, 5.0},
            {4.0, 8.5, 1.0},
            {12.5, 1.0, 1.0},
            {13.5, 3.0, 2.0},
            {12.0, 5.0, 1.5}
    };

    private static final double QUERY_VOLUME = 100.0;
    private static final int QUERY_RESIDUE_COUNT = 20;
    private static final double QUERY_HYDROPHOBIC = 0.5;

    // 90 degrees about the z-axis plus a translation.
    private static final RigidTransform KNOWN_TRANSFORM =
            new RigidTransform(
                    new double[][]{
                            {0.0, -1.0, 0.0},
                            {1.0, 0.0, 0.0},
                            {0.0, 0.0, 1.0}
                    },
                    new Point3D(3.0, -2.0, 5.0)
            );

    private static final String[] RESIDUE_NAMES = {
            "ALA", "PHE", "SER", "LYS", "ASP", "CYS", "GLY", "LEU"
    };

    private static final ResidueChemistry[] RESIDUE_CHEMISTRIES = {
            ResidueChemistry.HYDROPHOBIC,
            ResidueChemistry.AROMATIC,
            ResidueChemistry.POLAR,
            ResidueChemistry.POSITIVE,
            ResidueChemistry.NEGATIVE,
            ResidueChemistry.CYSTEINE,
            ResidueChemistry.GLYCINE,
            ResidueChemistry.HYDROPHOBIC
    };

    private final PocketSummaryRepository repository =
            mock(PocketSummaryRepository.class);
    private final FakeGeometryLoader geometryLoader =
            new FakeGeometryLoader();
    private final FakeResidueLoader residueLoader =
            new FakeResidueLoader();
    private final KeyResidueConfiguration keyResidues =
            new KeyResidueConfiguration();
    private final PocketSimilarityService service =
            new PocketSimilarityService(
                    repository,
                    geometryLoader,
                    residueLoader,
                    keyResidues
            );

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
                eq(0.35),
                eq(2.75),
                eq(0.40),
                eq(2.75),
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
                eq(0.35),
                eq(2.75),
                eq(0.40),
                eq(2.75),
                eq(PageRequest.of(0, 500))
        );

        service.findSimilar(QUERY_POCKET_ID, 500);
        verify(repository).findDescriptorCandidates(
                eq(QUERY_POCKET_ID),
                eq(0.35),
                eq(2.75),
                eq(0.40),
                eq(2.75),
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
    void partialPocketCandidateReachesStageThreeWithAsymmetricCoverage() {
        // Regression fixture for the METTL7A pocket-32 case: a large
        // merged candidate pocket whose conserved region contains the
        // entire query cloud, plus an adjacent subpocket the query
        // does not cover. Stage 2/3 must run and report directional
        // coverage, not reject the size mismatch.
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(7L, 1520.0, 38, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(7L, cloud(MERGED_POCKET_CLOUD));

        List<PocketSimilarityDiagnostic> rows =
                service.diagnoseSimilar(QUERY_POCKET_ID, 10);

        assertEquals(1, rows.size());

        PocketSimilarityDiagnostic row = rows.get(0);
        assertEquals(11, row.candidatePointCount());
        assertTrue(
                row.queryCoverage() >= 0.6,
                "forward (query) coverage was " + row.queryCoverage()
        );
        assertTrue(
                row.candidateCoverage() < row.queryCoverage(),
                "reverse coverage "
                        + row.candidateCoverage()
                        + " should be below forward coverage "
                        + row.queryCoverage()
        );
    }

    @Test
    void unrelatedLargeCandidateDoesNotRankHighly() {
        // Removing the size gates must not let a mismatched cloud win:
        // a uniformly inflated pocket fails on alignment quality.
        stubQuerySummary();
        stubCandidates(List.of(
                candidateSummary(7L, 1520.0, 38, 0.5)
        ));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(7L, cloud(IRREGULAR_SCALED));

        List<PocketSimilarityDiagnostic> rows =
                service.diagnoseSimilar(QUERY_POCKET_ID, 10);

        assertEquals(1, rows.size());
        assertTrue(
                rows.get(0).overallSimilarity() < 0.3,
                "overall similarity was "
                        + rows.get(0).overallSimilarity()
        );
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
                eq(0.35),
                eq(2.75),
                eq(0.40),
                eq(2.75),
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
        assertTrue(view.alphaSpheres().isEmpty());
        assertEquals(1, geometryLoader.loadAllCount());
    }

    @Test
    void geometryIncludesAlphaSpheresForSphereBasis() {
        stubQuerySummary();
        List<AlphaSphereView> views = List.of(
                new AlphaSphereView(0, new Point3D(0.0, 0.0, 0.0), 4.5),
                new AlphaSphereView(1, new Point3D(10.0, 0.0, 0.0), 4.0),
                new AlphaSphereView(2, new Point3D(0.0, 6.0, 0.0), 3.5),
                new AlphaSphereView(3, new Point3D(0.0, 0.0, 3.0), 3.0),
                new AlphaSphereView(4, new Point3D(8.0, 5.0, 2.0), 2.5),
                new AlphaSphereView(5, new Point3D(2.0, 4.0, 6.0), 2.0),
                new AlphaSphereView(6, new Point3D(7.0, 1.0, 5.0), 1.5),
                new AlphaSphereView(7, new Point3D(3.0, 8.0, 1.0), 1.0)
        );
        geometryLoader.registerSpheres(
                QUERY_POCKET_ID,
                new PocketPointCloud(
                        views.stream()
                                .map(AlphaSphereView::center)
                                .toList(),
                        PocketGeometryBasis.ALPHA_SPHERES
                ),
                views
        );

        PocketGeometryView view = service.getGeometry(QUERY_POCKET_ID);

        assertEquals("ALPHA_SPHERES", view.basis());
        assertEquals(8, view.pointCount());
        assertEquals(views, view.alphaSpheres());
        assertEquals(1, geometryLoader.loadAllCount());
    }

    @Test
    void compareFailsWith422ForMixedBases() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.registerSpheres(
                1L,
                new PocketPointCloud(
                        cloud(IRREGULAR_CLOUD).points(),
                        PocketGeometryBasis.ALPHA_SPHERES
                ),
                List.of()
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.compareGeometries(QUERY_POCKET_ID, 1L)
        );

        assertEquals(422, exception.getStatusCode().value());
        assertTrue(exception.getMessage().contains("RESIDUE_ATOMS"));
        assertTrue(exception.getMessage().contains("ALPHA_SPHERES"));
    }

    @Test
    void findSimilarSkipsMixedBasisCandidatesInStageThree() {
        stubQuerySummary();
        stubCandidates(List.of(candidateSummary(1L, 110.0, 20, 0.5)));
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.registerSpheres(
                1L,
                new PocketPointCloud(
                        cloud(IRREGULAR_CLOUD).points(),
                        PocketGeometryBasis.ALPHA_SPHERES
                ),
                List.of()
        );

        List<PocketCandidate> result =
                service.findSimilar(QUERY_POCKET_ID, 10);

        // The mixed-basis candidate is skipped with a WARN in Stage 3
        // instead of failing the whole request.
        assertTrue(result.isEmpty());
    }

    @Test
    void compareKeepsSphereRadiiUnchangedByAlignment() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );

        List<Point3D> points = cloud(IRREGULAR_CLOUD).points();
        List<AlphaSphereView> queryViews = sphereViews(points, 4.0);
        List<AlphaSphereView> candidateViews = sphereViews(points, 2.5);

        geometryLoader.registerSpheres(
                QUERY_POCKET_ID,
                new PocketPointCloud(
                        points,
                        PocketGeometryBasis.ALPHA_SPHERES
                ),
                queryViews
        );
        geometryLoader.registerSpheres(
                1L,
                new PocketPointCloud(
                        points,
                        PocketGeometryBasis.ALPHA_SPHERES
                ),
                candidateViews
        );

        PocketComparisonDetails details =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        assertEquals("ALPHA_SPHERES", details.query().basis());
        assertEquals("ALPHA_SPHERES", details.candidate().basis());
        assertEquals(queryViews, details.query().alphaSpheres());
        assertEquals(candidateViews, details.candidate().alphaSpheres());
        assertEquals(1, geometryLoader.loadAllCount());

        // The aligned centers still satisfy the transform invariant.
        List<Point3D> transformed =
                new PocketComparator(
                        new CompositePocketAligner(),
                        PocketComparisonOptions.defaults()
                )
                        .align(
                                new PocketPointCloud(
                                        points,
                                        PocketGeometryBasis.ALPHA_SPHERES
                                ),
                                new PocketPointCloud(
                                        points,
                                        PocketGeometryBasis.ALPHA_SPHERES
                                )
                        )
                        .transform()
                        .apply(points);

        assertEquals(
                transformed.size(),
                details.alignedCandidatePoints().size()
        );
        for (int index = 0; index < transformed.size(); index++) {
            assertEquals(
                    transformed.get(index).x(),
                    details.alignedCandidatePoints().get(index).x(),
                    1e-6
            );
        }
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

    @Test
    void compareReturnsResidueCorrespondenceMatchingAthenaCalculator() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );

        // The candidate cloud and its residue points are moved by a
        // known rigid transform; the retained alignment must map the
        // candidate residues back onto the query residues.
        List<Point3D> movedPoints =
                KNOWN_TRANSFORM.apply(cloud(IRREGULAR_CLOUD).points());

        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, new PocketPointCloud(movedPoints, BASIS));

        List<PocketResiduePoint> queryResidues =
                residuePoints("A", 1, cloud(IRREGULAR_CLOUD).points());
        List<PocketResiduePoint> candidateResidues =
                residuePoints("B", 101, movedPoints);

        residueLoader.register(QUERY_POCKET_ID, queryResidues);
        residueLoader.register(1L, candidateResidues);

        PocketComparisonDetails details =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        // Expected values come from the Athena transformer and
        // calculator on the same inputs, using the same retained
        // alignment the comparator produces.
        PocketAlignment alignment = new PocketComparator(
                new CompositePocketAligner(),
                PocketComparisonOptions.defaults()
        ).align(
                cloud(IRREGULAR_CLOUD),
                new PocketPointCloud(movedPoints, BASIS)
        );

        ResidueCorrespondence expected =
                new ResidueCorrespondenceCalculator().calculate(
                        queryResidues,
                        new PocketResiduePointTransformer().transform(
                                candidateResidues,
                                alignment.transform()
                        )
                );

        ResidueCorrespondenceView view =
                details.residueCorrespondence();

        assertEquals(expected.matches().size(), view.matches().size());
        assertEquals(
                expected.unmatchedQuery().size(),
                view.unmatchedQuery().size()
        );
        assertEquals(
                expected.unmatchedCandidate().size(),
                view.unmatchedCandidate().size()
        );

        for (int index = 0; index < expected.matches().size(); index++) {
            ResidueMatch expectedMatch = expected.matches().get(index);
            ResidueMatchView actualMatch = view.matches().get(index);

            assertEquals(
                    expectedMatch.matchType().name(),
                    actualMatch.matchType()
            );
            assertEquals(
                    expectedMatch.identicalResidue(),
                    actualMatch.identicalResidue()
            );
            assertEquals(
                    expectedMatch.chemistryCompatible(),
                    actualMatch.chemistryCompatible()
            );
            assertEquals(
                    expectedMatch.distanceAngstroms(),
                    actualMatch.distanceAngstroms(),
                    1e-9
            );
            assertEquals(
                    expectedMatch.query().reference().residueName(),
                    actualMatch.query().residueName()
            );
            assertEquals(
                    expectedMatch.candidate().reference().residueName(),
                    actualMatch.candidate().residueName()
            );
        }

        ResidueSummaryView summary = view.summary();
        assertEquals(queryResidues.size(), summary.queryResidueCount());
        assertEquals(
                candidateResidues.size(),
                summary.candidateResidueCount()
        );
        assertEquals(expected.matches().size(), summary.matchedCount());
        assertEquals(
                expected.matchedFractionQuery(),
                summary.matchedFractionQuery(),
                1e-9
        );
        assertEquals(
                expected.matchedFractionCandidate(),
                summary.matchedFractionCandidate(),
                1e-9
        );
        assertEquals(
                expected.identicalFraction(),
                summary.identicalFraction(),
                1e-9
        );
        assertEquals(
                expected.chemistryCompatibleFraction(),
                summary.chemistryCompatibleFraction(),
                1e-9
        );
        assertEquals(
                expected.meanMatchedDistance(),
                summary.meanMatchedDistance(),
                1e-9
        );
        assertEquals(
                expected.maximumMatchedDistance(),
                summary.maximumMatchedDistance(),
                1e-9
        );

        // Every residue matches its identical counterpart near zero
        // distance once the retained transform is applied.
        assertEquals(queryResidues.size(), summary.matchedCount());
        assertTrue(summary.maximumMatchedDistance() < 1.0e-2);
        assertEquals(1.0, summary.identicalFraction(), 1e-9);
    }

    @Test
    void residueMatchCoordinatesShareTheAlignedFrame() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );

        List<Point3D> movedPoints =
                KNOWN_TRANSFORM.apply(cloud(IRREGULAR_CLOUD).points());

        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, new PocketPointCloud(movedPoints, BASIS));

        residueLoader.register(
                QUERY_POCKET_ID,
                residuePoints("A", 1, cloud(IRREGULAR_CLOUD).points())
        );
        residueLoader.register(
                1L,
                residuePoints("B", 101, movedPoints)
        );

        PocketComparisonDetails details =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        // Frame invariant: the candidate residue coordinates in the
        // response are the transform-applied (aligned) coordinates, so
        // recomputing the Euclidean distance between the serialized
        // query and candidate positions must reproduce the reported
        // match distance exactly. The aligned candidate cloud lives in
        // the same frame.
        for (ResidueMatchView match
                : details.residueCorrespondence().matches()) {

            double recomputed = match.query().position().distance(
                    match.candidate().position()
            );

            assertEquals(
                    match.distanceAngstroms(),
                    recomputed,
                    1e-9,
                    "coordinate frame mismatch for "
                            + match.query().label()
                            + " -> "
                            + match.candidate().label()
            );
        }

        // The aligned residue points and the aligned candidate cloud
        // share a frame: every matched candidate residue sits within
        // the correspondence cutoff of an aligned-cloud neighbor.
        for (ResidueMatchView match
                : details.residueCorrespondence().matches()) {

            double nearest = details.alignedCandidatePoints()
                    .stream()
                    .mapToDouble(point ->
                            match.candidate().position().distance(point))
                    .min()
                    .orElseThrow();

            assertTrue(
                    nearest < 4.0,
                    "aligned residue point "
                            + match.candidate().label()
                            + " is "
                            + nearest
                            + " Å from the aligned candidate cloud"
            );
        }
    }

    @Test
    void compareReturnsRetainedTransformAndLoadsResiduesOnce() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));

        PocketComparisonDetails details =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        assertEquals(1, residueLoader.loadCount(QUERY_POCKET_ID));
        assertEquals(1, residueLoader.loadCount(1L));

        RigidTransform expected = new PocketComparator(
                new CompositePocketAligner(),
                PocketComparisonOptions.defaults()
        ).align(
                cloud(IRREGULAR_CLOUD),
                cloud(IRREGULAR_CLOUD)
        ).transform();

        double[][] expectedRotation = expected.rotation();
        double[][] actualRotation = details.transform().rotation();

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                assertEquals(
                        expectedRotation[row][column],
                        actualRotation[row][column],
                        0.0
                );
            }
        }

        assertEquals(
                expected.translation(),
                details.transform().translation()
        );
    }

    @Test
    void compareReturnsConfiguredKeyResiduesForTheQueryTargetOnly() {
        keyResidues.getKeyResidues().put(
                "UP" + QUERY_POCKET_ID,
                List.of("LEU145", "CYS148", "CYS202")
        );

        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));

        PocketComparisonDetails configured =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        assertEquals(
                List.of("LEU145", "CYS148", "CYS202"),
                configured.keyResidues()
        );

        // A query without configuration gets no key residues; nothing
        // else about the comparison changes.
        keyResidues.getKeyResidues().clear();

        PocketComparisonDetails unconfigured =
                service.compareGeometries(QUERY_POCKET_ID, 1L);

        assertEquals(List.of(), unconfigured.keyResidues());
        assertEquals(
                configured.comparison().overallSimilarity(),
                unconfigured.comparison().overallSimilarity(),
                0.0
        );
    }

    @Test
    void comparePropagatesResidueLoadingFailure() {
        stubQuerySummary();
        when(repository.findById(1L)).thenReturn(
                Optional.of(new TestPocketSummaryEntity(1L, 110.0, 20, 0.5))
        );
        geometryLoader.register(QUERY_POCKET_ID, cloud(IRREGULAR_CLOUD));
        geometryLoader.register(1L, cloud(IRREGULAR_CLOUD));
        residueLoader.failOn(1L);

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
                eq(0.35),
                eq(2.75),
                eq(0.40),
                eq(2.75),
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
        public Integer getAlphaSphereCount() {
            return 0;
        }

        @Override
        public String getGeometryBasis() {
            return PocketGeometryBasis.RESIDUE_ATOMS.name();
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

    private static List<AlphaSphereView> sphereViews(
            List<Point3D> centers,
            double radius
    ) {
        List<AlphaSphereView> views = new ArrayList<>();
        for (int index = 0; index < centers.size(); index++) {
            views.add(new AlphaSphereView(
                    index,
                    centers.get(index),
                    radius
            ));
        }
        return views;
    }

    private static List<PocketResiduePoint> residuePoints(
            String chainId,
            int firstResidueNumber,
            List<Point3D> positions
    ) {
        List<PocketResiduePoint> residues = new ArrayList<>();

        for (int index = 0; index < positions.size(); index++) {
            residues.add(new PocketResiduePoint(
                    new ResidueReference(
                            chainId,
                            firstResidueNumber + index,
                            ' ',
                            RESIDUE_NAMES[index]
                    ),
                    positions.get(index),
                    RESIDUE_CHEMISTRIES[index]
            ));
        }

        return residues;
    }

    private static List<Long> pocketIds(List<PocketCandidate> candidates) {
        return candidates.stream()
                .map(PocketCandidate::pocketId)
                .toList();
    }

    private static final class FakeGeometryLoader
            extends PocketPointCloudLoader {
        private final Map<Long, PocketPointCloud> clouds = new HashMap<>();
        private final Map<Long, List<AlphaSphereView>> spheres =
                new HashMap<>();
        private final Set<Long> failing = new HashSet<>();
        private int loadAllCount;
        private List<Long> requestedPocketIds = List.of();

        private FakeGeometryLoader() {
            super(null, null);
        }

        void register(long pocketId, PocketPointCloud cloud) {
            clouds.put(pocketId, cloud);
        }

        /**
         * Registers an ALPHA_SPHERES cloud together with the persisted
         * sphere views, modelling the sphere preference of the real
         * loader: a pocket with spheres is loaded with basis
         * ALPHA_SPHERES and exposes its radii.
         */
        void registerSpheres(
                long pocketId,
                PocketPointCloud cloud,
                List<AlphaSphereView> views
        ) {
            clouds.put(pocketId, cloud);
            spheres.put(pocketId, views);
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
        public LoadedPointClouds loadAllWithSpheres(
                Collection<Long> pocketIds
        ) {
            loadAllCount++;
            requestedPocketIds = List.copyOf(pocketIds);

            Map<Long, PocketPointCloud> resultClouds =
                    new LinkedHashMap<>();
            Map<Long, List<AlphaSphereView>> resultSpheres =
                    new LinkedHashMap<>();
            for (Long pocketId : pocketIds) {
                PocketPointCloud cloud = clouds.get(pocketId);
                if (cloud != null && !failing.contains(pocketId)) {
                    resultClouds.put(pocketId, cloud);
                    List<AlphaSphereView> views = spheres.get(pocketId);
                    if (views != null) {
                        resultSpheres.put(pocketId, views);
                    }
                }
            }
            return new LoadedPointClouds(resultClouds, resultSpheres);
        }
    }

    private static final class FakeResidueLoader
            extends PocketResidueLoader {

        private final Map<Long, List<PocketResiduePoint>> residues =
                new HashMap<>();
        private final Set<Long> failing = new HashSet<>();
        private final Map<Long, Integer> loadCounts = new HashMap<>();

        private FakeResidueLoader() {
            super(null, null, null);
        }

        void register(long pocketId, List<PocketResiduePoint> points) {
            residues.put(pocketId, points);
        }

        void failOn(long pocketId) {
            failing.add(pocketId);
        }

        int loadCount(long pocketId) {
            return loadCounts.getOrDefault(pocketId, 0);
        }

        @Override
        public List<PocketResiduePoint> load(long pocketId) {
            loadCounts.merge(pocketId, 1, Integer::sum);

            if (failing.contains(pocketId)) {
                throw new ResponseStatusException(
                        org.springframework.http.HttpStatus
                                .UNPROCESSABLE_ENTITY,
                        "Pocket " + pocketId
                                + " structure artifact cannot be loaded"
                );
            }

            return residues.getOrDefault(pocketId, List.of());
        }
    }
}
