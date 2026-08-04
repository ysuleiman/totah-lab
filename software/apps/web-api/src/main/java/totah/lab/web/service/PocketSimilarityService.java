package totah.lab.web.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.CompositePocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignment;
import totah.lab.athena.pocket.compare.PocketComparator;
import totah.lab.athena.pocket.compare.PocketComparison;
import totah.lab.athena.pocket.compare.PocketComparisonOptions;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.pocket.similar.PocketShapeDescriptor;
import totah.lab.athena.pocket.similar.PocketShapeDescriptorFactory;
import totah.lab.athena.pocket.similar.PocketShapeDistance;
import totah.lab.web.persistence.PocketSummaryEntity;
import totah.lab.web.persistence.PocketSummaryRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY;

@Service
public class PocketSimilarityService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PocketSimilarityService.class);

    private static final int MINIMUM_STAGE_ONE_LIMIT = 50;
    private static final int STAGE_ONE_MULTIPLIER = 5;
    private static final int MINIMUM_STAGE_TWO_LIMIT = 15;
    private static final int STAGE_TWO_MULTIPLIER = 2;

    private static final double MINIMUM_VOLUME_RATIO = 0.60;
    private static final double MAXIMUM_VOLUME_RATIO = 1.60;
    private static final double MINIMUM_RESIDUE_RATIO = 0.60;
    private static final double MAXIMUM_RESIDUE_RATIO = 1.60;

    private static final Comparator<LoadedCandidate> STAGE_TWO_ORDER =
            Comparator.comparingDouble(LoadedCandidate::shapeDistance)
                    .thenComparingDouble(candidate ->
                            candidate.candidate().descriptorDistance())
                    .thenComparingLong(candidate ->
                            candidate.candidate().pocketId());

    private static final Comparator<ComparedCandidate> STAGE_THREE_ORDER =
            Comparator.comparingDouble((ComparedCandidate compared) ->
                            compared.comparison().overallSimilarity())
                    .reversed()
                    .thenComparingDouble(compared ->
                            compared.loaded().shapeDistance())
                    .thenComparingDouble(compared ->
                            compared.loaded().candidate().descriptorDistance())
                    .thenComparingLong(compared ->
                            compared.loaded().candidate().pocketId());

    /**
     * The aligner used for Stage 3 and pairwise comparison. Reported
     * in the comparison response so the UI can display it.
     */
    public static final String ACTIVE_ALIGNER = "PCA_ICP";

    private final PocketSummaryRepository pocketSummaryRepository;
    private final PocketPointCloudLoader geometryLoader;
    private final PocketComparator comparator;

    public PocketSimilarityService(
            PocketSummaryRepository pocketSummaryRepository,
            PocketPointCloudLoader geometryLoader
    ) {
        this.pocketSummaryRepository = pocketSummaryRepository;
        this.geometryLoader = geometryLoader;
        this.comparator = new PocketComparator(
                new CompositePocketAligner(),
                PocketComparisonOptions.defaults()
        );
    }

    @Transactional(readOnly = true)
    public List<PocketCandidate> findSimilar(
            long queryPocketId,
            int limit
    ) {
        return rankCandidates(queryPocketId, limit).stream()
                .limit(limit)
                .map(compared -> compared.loaded().candidate())
                .toList();
    }

    /**
     * Development diagnostic: same pipeline as {@link #findSimilar},
     * but returns the intermediate stage ranks and the full
     * {@link PocketComparison} metrics instead of the production
     * response shape. Does not alter ranking.
     */
    @Transactional(readOnly = true)
    public List<PocketSimilarityDiagnostic> diagnoseSimilar(
            long queryPocketId,
            int limit
    ) {
        List<ComparedCandidate> ranked =
                rankCandidates(queryPocketId, limit);

        List<PocketSimilarityDiagnostic> diagnostics =
                new ArrayList<>();

        int count = Math.min(limit, ranked.size());

        for (int index = 0; index < count; index++) {
            diagnostics.add(toDiagnostic(ranked.get(index), index + 1));
        }

        return diagnostics;
    }

    /**
     * Identity and full point cloud of one pocket, for the inspection
     * UI. Loads geometry with a single bulk call.
     */
    @Transactional(readOnly = true)
    public PocketGeometryView getGeometry(long pocketId) {
        PocketSummaryEntity summary = findSummary(pocketId);

        Map<Long, PocketPointCloud> pointClouds =
                geometryLoader.loadAll(List.of(pocketId));

        return toGeometryView(
                summary,
                requireCloud(pointClouds, pocketId)
        );
    }

    /**
     * Pairwise comparison of two pockets, for the inspection UI.
     * Metrics come from {@code PocketComparator}; the aligned point
     * clouds come from Athena's {@code PocketAlignment} (the same
     * centroid alignment the comparator uses). Loads both clouds with
     * a single bulk call.
     */
    @Transactional(readOnly = true)
    public PocketComparisonDetails compareGeometries(
            long queryPocketId,
            long candidatePocketId
    ) {
        PocketSummaryEntity querySummary = findSummary(queryPocketId);
        PocketSummaryEntity candidateSummary =
                findSummary(candidatePocketId);

        Map<Long, PocketPointCloud> pointClouds =
                geometryLoader.loadAll(
                        List.of(queryPocketId, candidatePocketId)
                );

        PocketPointCloud queryCloud =
                requireCloud(pointClouds, queryPocketId);
        PocketPointCloud candidateCloud =
                requireCloud(pointClouds, candidatePocketId);

        final PocketAlignment alignment;
        final PocketComparison comparison;

        try {
            alignment = comparator.align(
                    queryCloud,
                    candidateCloud
            );

            comparison = comparator.compareAligned(
                    alignment
            );
        } catch (RuntimeException exception) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Pockets " + queryPocketId + " and "
                            + candidatePocketId
                            + " cannot be aligned: "
                            + exception.getMessage(),
                    exception
            );
        }

        return new PocketComparisonDetails(
                toGeometryView(querySummary, queryCloud),
                toGeometryView(candidateSummary, candidateCloud),
                alignment.query().points(),
                alignment.alignedCandidate().points(),
                comparison,
                ACTIVE_ALIGNER
        );

    }

    private PocketSummaryEntity findSummary(long pocketId) {
        return pocketSummaryRepository
                .findById(pocketId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Pocket summary not found: " + pocketId
                ));
    }

    private static PocketPointCloud requireCloud(
            Map<Long, PocketPointCloud> pointClouds,
            long pocketId
    ) {
        PocketPointCloud pointCloud = pointClouds.get(pocketId);

        if (pointCloud == null) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Pocket " + pocketId + " has no usable geometry"
            );
        }

        return pointCloud;
    }

    private static PocketGeometryView toGeometryView(
            PocketSummaryEntity summary,
            PocketPointCloud pointCloud
    ) {
        return new PocketGeometryView(
                summary.getPocketId(),
                summary.getStructureId(),
                summary.getSourceAccession(),
                summary.getPocketNumber(),
                pointCloud.size(),
                pointCloud.centroid(),
                pointCloud.bounds(),
                pointCloud.basis().name(),
                pointCloud.points()
        );
    }

    private List<ComparedCandidate> rankCandidates(
            long queryPocketId,
            int limit
    ) {
        long requestStartNanos = System.nanoTime();

        long summaryStartNanos = System.nanoTime();
        PocketSummaryEntity querySummary = pocketSummaryRepository
                .findById(queryPocketId)
                .orElseThrow(() -> new ResponseStatusException(
                        NOT_FOUND,
                        "Pocket summary not found: " + queryPocketId
                ));
        long summaryLookupMs = elapsedMillis(summaryStartNanos);

        int stageOneLimit = Math.max(
                MINIMUM_STAGE_ONE_LIMIT,
                limit * STAGE_ONE_MULTIPLIER
        );
        int stageTwoLimit = Math.min(
                Math.max(
                        MINIMUM_STAGE_TWO_LIMIT,
                        limit * STAGE_TWO_MULTIPLIER
                ),
                stageOneLimit
        );

        long stageOneStartNanos = System.nanoTime();
        List<PocketCandidate> stageOneCandidates = pocketSummaryRepository
                .findDescriptorCandidates(
                        queryPocketId,
                        MINIMUM_VOLUME_RATIO,
                        MAXIMUM_VOLUME_RATIO,
                        MINIMUM_RESIDUE_RATIO,
                        MAXIMUM_RESIDUE_RATIO,
                        PageRequest.of(0, stageOneLimit)
                )
                .stream()
                .map(summary -> toCandidate(querySummary, summary))
                .toList();
        long stageOneQueryMs = elapsedMillis(stageOneStartNanos);

        if (stageOneCandidates.isEmpty()) {
            logTiming(
                    queryPocketId, limit, 0, 0,
                    summaryLookupMs, stageOneQueryMs, 0L, 0L, 0L,
                    elapsedMillis(requestStartNanos)
            );
            return List.of();
        }

        List<Long> pocketIds =
                new ArrayList<>(stageOneCandidates.size() + 1);
        pocketIds.add(queryPocketId);
        for (PocketCandidate candidate : stageOneCandidates) {
            pocketIds.add(candidate.pocketId());
        }

        long geometryStartNanos = System.nanoTime();
        Map<Long, PocketPointCloud> pointClouds =
                geometryLoader.loadAll(pocketIds);
        long geometryLoadMs = elapsedMillis(geometryStartNanos);

        PocketPointCloud queryCloud = pointClouds.get(queryPocketId);
        if (queryCloud == null) {
            throw new ResponseStatusException(
                    UNPROCESSABLE_ENTITY,
                    "Pocket " + queryPocketId + " has no usable geometry"
            );
        }

        long stageTwoStartNanos = System.nanoTime();
        PocketShapeDescriptor queryDescriptor =
                PocketShapeDescriptorFactory.describe(
                        queryCloud,
                        PocketShapeDescriptorFactory.DEFAULT_RADIAL_BIN_COUNT
                );

        List<LoadedCandidate> stageTwoSurvivors = selectByShapeDistance(
                stageOneCandidates,
                pointClouds,
                queryDescriptor,
                stageTwoLimit
        );
        long stageTwoMs = elapsedMillis(stageTwoStartNanos);

        LOGGER.info(
                "PocketComparator comparing {} candidates",
                stageTwoSurvivors.size()
        );

        long stageThreeStartNanos = System.nanoTime();
        List<ComparedCandidate> compared = new ArrayList<>();

        for (LoadedCandidate loaded : stageTwoSurvivors) {
            try {
                compared.add(new ComparedCandidate(
                        loaded,
                        comparator.compare(queryCloud, loaded.pointCloud())
                ));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Skipping pocket {} during detailed comparison: {}",
                        loaded.candidate().pocketId(),
                        exception.getMessage()
                );
            }
        }

        compared = compared.stream()
                .sorted(STAGE_THREE_ORDER)
                .toList();
        long stageThreeMs = elapsedMillis(stageThreeStartNanos);

        logTiming(
                queryPocketId, limit,
                stageOneCandidates.size(), stageTwoSurvivors.size(),
                summaryLookupMs, stageOneQueryMs, geometryLoadMs,
                stageTwoMs, stageThreeMs,
                elapsedMillis(requestStartNanos)
        );

        return compared;
    }

    private List<LoadedCandidate> selectByShapeDistance(
            List<PocketCandidate> candidates,
            Map<Long, PocketPointCloud> pointClouds,
            PocketShapeDescriptor queryDescriptor,
            int stageTwoLimit
    ) {
        List<LoadedCandidate> loaded =
                new ArrayList<>(candidates.size());

        for (int index = 0; index < candidates.size(); index++) {
            PocketCandidate candidate = candidates.get(index);
            PocketPointCloud pointCloud =
                    pointClouds.get(candidate.pocketId());
            if (pointCloud == null) {
                LOGGER.warn(
                        "Skipping pocket {} during geometric reranking:"
                                + " no usable geometry",
                        candidate.pocketId()
                );
                continue;
            }

            try {
                PocketShapeDescriptor shapeDescriptor =
                        PocketShapeDescriptorFactory.describe(
                                pointCloud,
                                PocketShapeDescriptorFactory
                                        .DEFAULT_RADIAL_BIN_COUNT
                        );
                double shapeDistance = PocketShapeDistance.calculate(
                        queryDescriptor,
                        shapeDescriptor
                );
                loaded.add(new LoadedCandidate(
                        candidate,
                        pointCloud,
                        shapeDescriptor,
                        shapeDistance,
                        index + 1,
                        0
                ));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Skipping pocket {} during geometric reranking: {}",
                        candidate.pocketId(),
                        exception.getMessage()
                );
            }
        }

        List<LoadedCandidate> survivors = loaded.stream()
                .sorted(STAGE_TWO_ORDER)
                .limit(stageTwoLimit)
                .toList();

        List<LoadedCandidate> ranked =
                new ArrayList<>(survivors.size());

        for (int index = 0; index < survivors.size(); index++) {
            ranked.add(survivors.get(index)
                    .withStageTwoRank(index + 1));
        }

        return ranked;
    }

    private PocketSimilarityDiagnostic toDiagnostic(
            ComparedCandidate compared,
            int stageThreeRank
    ) {
        LoadedCandidate loaded = compared.loaded();
        PocketCandidate candidate = loaded.candidate();
        PocketComparison comparison = compared.comparison();

        return new PocketSimilarityDiagnostic(
                candidate.pocketId(),
                candidate.structureId(),
                candidate.sourceAccession(),
                candidate.pocketNumber(),
                loaded.stageOneRank(),
                candidate.descriptorDistance(),
                candidate.volumeDistance(),
                candidate.residueDistance(),
                candidate.chemistryDistance(),
                loaded.stageTwoRank(),
                loaded.shapeDistance(),
                stageThreeRank,
                comparison.overallSimilarity(),
                comparison.geometrySimilarity(),
                comparison.sizeSimilarity(),
                comparison.queryCoverage(),
                comparison.candidateCoverage(),
                comparison.queryToCandidateMeanDistance(),
                comparison.candidateToQueryMeanDistance(),
                comparison.meanBidirectionalDistance(),
                comparison.maximumNearestNeighborDistance(),
                comparison.queryPointCount(),
                comparison.candidatePointCount(),
                comparison.basis().name(),
                candidate.uniProtId(),
                candidate.proteinName(),
                candidate.geneName(),
                candidate.organism()
        );
    }

    private void logTiming(
            long queryPocketId,
            int requestedLimit,
            int stageOneCandidates,
            int stageTwoCandidates,
            long summaryLookupMs,
            long stageOneQueryMs,
            long geometryLoadMs,
            long stageTwoMs,
            long stageThreeMs,
            long totalMs
    ) {
        LOGGER.info(
                "pocket similarity timing:"
                        + " queryPocketId={}"
                        + " requestedLimit={}"
                        + " stageOneCandidates={}"
                        + " stageTwoCandidates={}"
                        + " summaryLookupMs={}"
                        + " stageOneQueryMs={}"
                        + " geometryLoadMs={}"
                        + " stageTwoMs={}"
                        + " stageThreeMs={}"
                        + " totalMs={}",
                queryPocketId,
                requestedLimit,
                stageOneCandidates,
                stageTwoCandidates,
                summaryLookupMs,
                stageOneQueryMs,
                geometryLoadMs,
                stageTwoMs,
                stageThreeMs,
                totalMs
        );
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    private PocketCandidate toCandidate(
            PocketSummaryEntity query,
            PocketSummaryEntity candidate
    ) {
        double volumeDistance =
                relativeDifference(
                        query.getVolume(),
                        candidate.getVolume()
                );

        double residueDistance =
                relativeDifference(
                        query.getResidueCount(),
                        candidate.getResidueCount()
                );

        double chemistryDistance =
                difference(
                        query.getHydrophobicFraction(),
                        candidate.getHydrophobicFraction()
                )
                        + difference(
                        query.getAromaticFraction(),
                        candidate.getAromaticFraction()
                )
                        + difference(
                        query.getPolarFraction(),
                        candidate.getPolarFraction()
                )
                        + difference(
                        query.getPositiveFraction(),
                        candidate.getPositiveFraction()
                )
                        + difference(
                        query.getNegativeFraction(),
                        candidate.getNegativeFraction()
                );

        double descriptorDistance =
                volumeDistance
                        + residueDistance
                        + chemistryDistance;

        return new PocketCandidate(
                candidate.getPocketId(),
                candidate.getStructureId(),
                candidate.getSourceAccession(),
                candidate.getPocketNumber(),
                descriptorDistance,
                volumeDistance,
                residueDistance,
                chemistryDistance,
                candidate.getUniProtId(),
                candidate.getProteinName(),
                candidate.getGeneName(),
                candidate.getOrganism()
        );
    }

    private double relativeDifference(
            Number first,
            Number second
    ) {
        if (first == null || second == null) {
            return 1.0;
        }

        double denominator = Math.abs(first.doubleValue());

        if (denominator == 0.0) {
            return Math.abs(second.doubleValue());
        }

        return Math.abs(
                first.doubleValue() - second.doubleValue()
        ) / denominator;
    }

    private double difference(Double first, Double second) {
        if (first == null || second == null) {
            return 1.0;
        }

        return Math.abs(first - second);
    }

    public record PocketCandidate(
            long pocketId,
            long structureId,
            String sourceAccession,
            int pocketNumber,
            double descriptorDistance,
            double volumeDistance,
            double residueDistance,
            double chemistryDistance,
            String uniProtId,
            String proteinName,
            String geneName,
            String organism
    ) {
    }

    private record LoadedCandidate(
            PocketCandidate candidate,
            PocketPointCloud pointCloud,
            PocketShapeDescriptor shapeDescriptor,
            double shapeDistance,
            int stageOneRank,
            int stageTwoRank
    ) {
        private LoadedCandidate withStageTwoRank(int stageTwoRank) {
            return new LoadedCandidate(
                    candidate,
                    pointCloud,
                    shapeDescriptor,
                    shapeDistance,
                    stageOneRank,
                    stageTwoRank
            );
        }
    }

    private record ComparedCandidate(
            LoadedCandidate loaded,
            PocketComparison comparison
    ) {
    }
}
