package totah.lab.web.service;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import totah.lab.athena.pocket.compare.MultiHypothesisPocketAligner;
import totah.lab.athena.pocket.compare.PocketAlignment;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.PocketComparison;
import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.PocketSimilarityClassification;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryAssessment;
import totah.lab.athena.pocket.compare.residue.ResidueChemistryScorer;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionAssessment;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.pocket.evidence.GlobalShapeRetrievalEvidence;
import totah.lab.athena.pocket.evidence.PocketCandidateSource;
import totah.lab.athena.pocket.evidence.PocketComparisonEvidence;
import totah.lab.athena.pocket.evidence.PocketMatchRetrievalEvidence;
import totah.lab.athena.pocket.evidence.PocketRetrievalEvidence;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.athena.pocket.similar.PocketShapeDescriptor;
import totah.lab.athena.pocket.similar.PocketShapeDescriptorFactory;
import totah.lab.athena.pocket.similar.PocketShapeDistance;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.web.persistence.PocketSummaryEntity;
import totah.lab.web.persistence.PocketSummaryRepository;
import totah.lab.web.persistence.StructureRepository;
import totah.lab.web.pocketmatch.PocketMatchCandidateProvider;
import totah.lab.web.pocketmatch.PocketMatchCandidateProvider
        .PocketMatchCandidate;
import totah.lab.web.pocketmatch.PocketSearchProperties;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToDoubleFunction;

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

    // High-recall Stage 1 bands (temporary regression fix): fpocket
    // may segment homologous binding sites into pockets of very
    // different size (compact vs merged), so volume/residue ratios
    // must stay wide sanity gates, not similarity gates. Descriptor-
    // led retrieval replaces them later.
    private static final double MINIMUM_VOLUME_RATIO = 0.35;
    private static final double MAXIMUM_VOLUME_RATIO = 2.75;
    private static final double MINIMUM_RESIDUE_RATIO = 0.40;
    private static final double MAXIMUM_RESIDUE_RATIO = 2.75;

    private static final Comparator<LoadedCandidate> STAGE_TWO_ORDER =
            Comparator.comparingDouble(LoadedCandidate::shapeDistance)
                    .thenComparingDouble(candidate ->
                            candidate.candidate().descriptorDistance())
                    .thenComparingLong(candidate ->
                            candidate.candidate().pocketId());

    private static final Comparator<ComparedCandidate> STAGE_THREE_ORDER =
            Comparator.comparingDouble((ComparedCandidate compared) ->
                            compared.finalSimilarity())
                    .reversed()
                    .thenComparing(descending(compared ->
                            compared.assessment()
                                    .chemistryCoverageAdjustedSimilarity()))
                    .thenComparing(descending(compared ->
                            compared.assessment()
                                    .compatibleMatchedFraction()))
                    .thenComparing(descending(compared ->
                            compared.assessment()
                                    .keyResidueChemistrySimilarity()))
                    .thenComparing(descending(compared ->
                            compared.comparison().overallSimilarity()))
                    .thenComparingDouble(compared ->
                            compared.loaded().shapeDistance())
                    .thenComparingDouble(compared ->
                            compared.loaded().candidate().descriptorDistance())
                    .thenComparingLong(compared ->
                            compared.loaded().candidate().pocketId());

    private static Comparator<ComparedCandidate> descending(
            ToDoubleFunction<ComparedCandidate> key
    ) {
        return Comparator.comparingDouble(key).reversed();
    }

    /**
     * The aligner used for Stage 3 and pairwise comparison. Reported
     * in the comparison response so the UI can display it.
     */
    public static final String ACTIVE_ALIGNER = "PCA_ICP";

    private final PocketSummaryRepository pocketSummaryRepository;
    private final StructureRepository structureRepository;
    private final PocketPointCloudLoader geometryLoader;
    private final PocketResidueLoader residueLoader;
    private final KeyResidueConfiguration keyResidueConfiguration;
    private final ProteinSequenceAlignmentService sequenceAlignmentService;
    private final PocketMatchCandidateProvider pocketMatchCandidates;
    private final PocketSearchProperties searchProperties;

    /*
     * Null in legacy (test) construction: diagnostics then carry no
     * evidence-pipeline assessment and Stage 3 skips evidence assembly.
     */
    private final PocketComparisonEvidenceAssembler evidenceAssembler;

    private final MultiHypothesisPocketAligner multiHypothesisAligner =
            new MultiHypothesisPocketAligner();
    private final ResidueChemistryScorer chemistryScorer =
            new ResidueChemistryScorer();
    private final ResidueSubstitutionScorer substitutionScorer =
            new ResidueSubstitutionScorer();

    /**
     * Legacy construction without retrieval-channel configuration or
     * evidence assembly: the global-shape channel is enabled and
     * uncapped, chosen references are always included, and no
     * evidence-pipeline assessment is computed. Preserves the exact
     * pre-configuration pipeline behavior.
     */
    public PocketSimilarityService(
            PocketSummaryRepository pocketSummaryRepository,
            StructureRepository structureRepository,
            PocketPointCloudLoader geometryLoader,
            PocketResidueLoader residueLoader,
            KeyResidueConfiguration keyResidueConfiguration,
            ProteinSequenceAlignmentService sequenceAlignmentService,
            PocketMatchCandidateProvider pocketMatchCandidates
    ) {
        this(pocketSummaryRepository,
                structureRepository,
                geometryLoader,
                residueLoader,
                keyResidueConfiguration,
                sequenceAlignmentService,
                pocketMatchCandidates,
                legacySearchProperties(),
                null);
    }

    private static PocketSearchProperties legacySearchProperties() {
        PocketSearchProperties properties = new PocketSearchProperties();
        properties.getGlobalShape().setEnabled(true);
        properties.getGlobalShape().setLimit(Integer.MAX_VALUE);
        properties.setIncludeChosenReferences(true);
        return properties;
    }

    @Autowired
    public PocketSimilarityService(
            PocketSummaryRepository pocketSummaryRepository,
            StructureRepository structureRepository,
            PocketPointCloudLoader geometryLoader,
            PocketResidueLoader residueLoader,
            KeyResidueConfiguration keyResidueConfiguration,
            ProteinSequenceAlignmentService sequenceAlignmentService,
            PocketMatchCandidateProvider pocketMatchCandidates,
            PocketSearchProperties searchProperties,
            PocketComparisonEvidenceAssembler evidenceAssembler
    ) {
        this.pocketSummaryRepository = pocketSummaryRepository;
        this.structureRepository = structureRepository;
        this.geometryLoader = geometryLoader;
        this.residueLoader = residueLoader;
        this.keyResidueConfiguration = keyResidueConfiguration;
        this.sequenceAlignmentService = sequenceAlignmentService;
        this.pocketMatchCandidates = pocketMatchCandidates;
        this.searchProperties = searchProperties;
        this.evidenceAssembler = evidenceAssembler;
    }

    @Transactional(readOnly = true)
    public List<PocketCandidate> findSimilar(
            long queryPocketId,
            int limit
    ) {
        // The default result set is chemistry-gated: only candidates
        // classified STRONG_SIMILARITY or MODERATE_SIMILARITY are
        // returned; shape-only neighbors and rejected candidates are
        // visible through diagnoseSimilar.
        return rankCandidates(queryPocketId, limit).stream()
                .filter(compared -> compared.classification()
                        == PocketSimilarityClassification.STRONG_SIMILARITY
                        || compared.classification()
                        == PocketSimilarityClassification.MODERATE_SIMILARITY)
                .limit(limit)
                .map(compared -> compared.loaded().candidate())
                .toList();
    }

    /**
     * Development diagnostic: same pipeline as {@link #findSimilar},
     * but returns every evaluated candidate (including shape-only
     * neighbors and rejected ones) with the intermediate stage ranks,
     * the full {@link PocketComparison} metrics, and the chemistry
     * assessment instead of the production response shape. Does not
     * alter ranking.
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

        PocketPointCloudLoader.LoadedPointClouds loaded =
                geometryLoader.loadAllWithSpheres(List.of(pocketId));

        return toGeometryView(
                summary,
                requireCloud(loaded.pointClouds(), pocketId),
                loaded.alphaSpheres().getOrDefault(pocketId, List.of())
        );
    }

    /**
     * Pairwise comparison of two pockets, for the inspection UI. The
     * alignment is selected by Athena's {@code
     * MultiHypothesisPocketAligner} (PCA+ICP, or a sequence-seeded
     * Kabsch when the receptors' cached protein sequence alignment
     * provides a usable seed); metrics, the aligned point clouds, and
     * the residue correspondence all come from the selected
     * hypothesis. Loads both clouds with a single bulk call and each
     * pocket's residues exactly once.
     */
    @Transactional(readOnly = true)
    public PocketComparisonDetails compareGeometries(
            long queryPocketId,
            long candidatePocketId
    ) {
        ComparisonRun run = compareRun(queryPocketId, candidatePocketId);

        PocketAlignment alignment = run.alignmentResult().alignment();

        return new PocketComparisonDetails(
                toGeometryView(
                        run.querySummary(),
                        run.queryCloud(),
                        run.queryAlphaSpheres()
                ),
                toGeometryView(
                        run.candidateSummary(),
                        run.candidateCloud(),
                        run.candidateAlphaSpheres()
                ),
                alignment.query().points(),
                alignment.alignedCandidate().points(),
                run.alignmentResult().comparison(),
                ACTIVE_ALIGNER,
                ResidueCorrespondenceViewMapper.toView(
                        run.alignmentResult().correspondence(),
                        run.substitutionAssessment()
                ),
                new TransformView(
                        alignment.transform().rotation(),
                        alignment.transform().translation()
                ),
                run.keyResidues(),
                ChemistryAssessmentView.toView(
                        run.chemistryAssessment(),
                        run.substitutionAssessment(),
                        chemistryScorer.classify(
                                run.chemistryAssessment(),
                                run.finalSimilarity()
                        ),
                        run.finalSimilarity()
                ),
                AlignmentMetadataView.toView(run.alignmentResult())
        );

    }

    /**
     * The shared live comparison path behind
     * {@link #compareGeometries}: loads both pockets exactly once,
     * runs the multi-hypothesis alignment (with the cached protein
     * sequence alignment as the seed source) and derives the
     * chemistry/substitution assessments of the SELECTED hypothesis.
     * Also used by the comparison-report service, which assembles the
     * athena evidence bundle from the same run — no alignment or
     * metric is recomputed per caller.
     */
    ComparisonRun compareRun(
            long queryPocketId,
            long candidatePocketId
    ) {
        long requestStartNanos = System.nanoTime();

        PocketSummaryEntity querySummary = findSummary(queryPocketId);
        PocketSummaryEntity candidateSummary =
                findSummary(candidatePocketId);

        long geometryStartNanos = System.nanoTime();

        PocketPointCloudLoader.LoadedPointClouds loaded =
                geometryLoader.loadAllWithSpheres(
                        List.of(queryPocketId, candidatePocketId)
                );

        Map<Long, PocketPointCloud> pointClouds = loaded.pointClouds();

        PocketPointCloud queryCloud =
                requireCloud(pointClouds, queryPocketId);
        PocketPointCloud candidateCloud =
                requireCloud(pointClouds, candidatePocketId);

        long residueExtractionStartNanos = System.nanoTime();
        List<PocketResiduePoint> queryResidues =
                residueLoader.load(queryPocketId);
        List<PocketResiduePoint> candidateResidues =
                residueLoader.load(candidatePocketId);
        long residueExtractionMs =
                elapsedMillis(residueExtractionStartNanos);

        final SequenceAlignment sequenceAlignment;
        final PocketAlignmentResult alignmentResult;

        try {
            sequenceAlignment = sequenceAlignmentFor(
                    querySummary.getReceptorId(),
                    candidateSummary.getReceptorId(),
                    new HashMap<>()
            );

            alignmentResult = multiHypothesisAligner.align(
                    queryCloud,
                    candidateCloud,
                    queryResidues,
                    candidateResidues,
                    sequenceAlignment
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
        long geometryMs = elapsedMillis(geometryStartNanos);

        PocketComparison comparison = alignmentResult.comparison();
        ResidueCorrespondence correspondence =
                alignmentResult.correspondence();

        List<String> keyResidues =
                keyResidueConfiguration.forUniProtId(
                        querySummary.getUniProtId()
                );
        ResidueChemistryAssessment assessment =
                chemistryScorer.assess(
                        correspondence,
                        new HashSet<>(keyResidues)
                );
        ResidueSubstitutionAssessment substitutionAssessment =
                substitutionScorer.assess(correspondence);
        double finalSimilarity =
                ResidueChemistryScorer.finalSimilarity(
                        comparison.overallSimilarity(),
                        assessment
                );

        logComparisonTiming(
                queryPocketId,
                candidatePocketId,
                geometryMs,
                residueExtractionMs,
                elapsedMillis(requestStartNanos)
        );

        return new ComparisonRun(
                querySummary,
                candidateSummary,
                queryCloud,
                candidateCloud,
                loaded.alphaSpheres().getOrDefault(queryPocketId, List.of()),
                loaded.alphaSpheres()
                        .getOrDefault(candidatePocketId, List.of()),
                sequenceAlignment,
                alignmentResult,
                keyResidues,
                assessment,
                substitutionAssessment,
                finalSimilarity
        );
    }

    private void logComparisonTiming(
            long queryPocketId,
            long candidatePocketId,
            long geometryMs,
            long residueExtractionMs,
            long totalMs
    ) {
        LOGGER.info(
                "pocket comparison timing:"
                        + " queryPocketId={}"
                        + " candidatePocketId={}"
                        + " geometryMs={}"
                        + " residueExtractionMs={}"
                        + " totalMs={}",
                queryPocketId,
                candidatePocketId,
                geometryMs,
                residueExtractionMs,
                totalMs
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
            PocketPointCloud pointCloud,
            List<AlphaSphereView> alphaSpheres
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
                pointCloud.points(),
                alphaSpheres,
                summary.getVolume(),
                summary.getScore(),
                summary.getDruggabilityScore(),
                summary.getResidueCount(),
                summary.getAtomCount(),
                summary.getAlphaSphereCount()
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

        int stageOneLimit = Math.min(
                Math.max(
                        MINIMUM_STAGE_ONE_LIMIT,
                        limit * STAGE_ONE_MULTIPLIER
                ),
                searchProperties.getGlobalShape().getLimit()
        );
        int stageTwoLimit = Math.min(
                Math.max(
                        MINIMUM_STAGE_TWO_LIMIT,
                        limit * STAGE_TWO_MULTIPLIER
                ),
                stageOneLimit
        );

        long stageOneStartNanos = System.nanoTime();
        StageOneSelection stageOne = unionStageOneCandidates(
                querySummary,
                searchProperties.getGlobalShape().isEnabled()
                        ? findStageOneCandidates(
                                querySummary,
                                stageOneLimit
                        )
                        : List.of()
        );
        List<PocketSummaryEntity> stageOneSummaries = stageOne.summaries();

        Map<Long, Long> candidateReceptorIds =
                new HashMap<>(stageOneSummaries.size());
        Map<Long, PocketSummaryEntity> candidateSummaries =
                new HashMap<>(stageOneSummaries.size());
        for (PocketSummaryEntity summary : stageOneSummaries) {
            candidateReceptorIds.put(
                    summary.getPocketId(),
                    summary.getReceptorId()
            );
            candidateSummaries.put(summary.getPocketId(), summary);
        }

        List<PocketCandidate> stageOneCandidates =
                stageOneSummaries.stream()
                        .map(summary -> toCandidate(querySummary, summary)
                                .withCandidateSources(
                                        stageOne.candidateSources()
                                                .getOrDefault(
                                                        summary.getPocketId(),
                                                        Set.of()
                                                )
                                ))
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
                stageTwoLimit,
                stageOne
        );
        long stageTwoMs = elapsedMillis(stageTwoStartNanos);

        LOGGER.info(
                "Multi-hypothesis alignment comparing {} candidates",
                stageTwoSurvivors.size()
        );

        long stageThreeStartNanos = System.nanoTime();

        List<PocketResiduePoint> queryResidues =
                residueLoader.load(queryPocketId);
        Set<String> keyResidues = new HashSet<>(
                keyResidueConfiguration.forUniProtId(
                        querySummary.getUniProtId()
                )
        );

        // One cached protein sequence alignment per receptor pair: at
        // most one computation per pair per request (usually a single
        // persisted-cache lookup).
        Map<Long, SequenceAlignment> sequenceAlignments =
                new HashMap<>();

        List<ComparedCandidate> compared = new ArrayList<>();

        for (LoadedCandidate loaded : stageTwoSurvivors) {
            try {
                List<PocketResiduePoint> candidateResidues =
                        residueLoader.load(
                                loaded.candidate().pocketId()
                        );

                SequenceAlignment sequenceAlignment =
                        sequenceAlignmentFor(
                                querySummary.getReceptorId(),
                                candidateReceptorIds.get(
                                        loaded.candidate().pocketId()
                                ),
                                sequenceAlignments
                        );

                PocketAlignmentResult alignmentResult =
                        multiHypothesisAligner.align(
                                queryCloud,
                                loaded.pointCloud(),
                                queryResidues,
                                candidateResidues,
                                sequenceAlignment
                        );

                PocketComparison comparison =
                        alignmentResult.comparison();
                ResidueCorrespondence correspondence =
                        alignmentResult.correspondence();

                ResidueChemistryAssessment assessment =
                        chemistryScorer.assess(
                                correspondence,
                                keyResidues
                        );
                ResidueSubstitutionAssessment substitutionAssessment =
                        substitutionScorer.assess(correspondence);
                double finalSimilarity =
                        ResidueChemistryScorer.finalSimilarity(
                                comparison.overallSimilarity(),
                                assessment
                        );

                String evidenceAssessment = null;
                if (evidenceAssembler != null) {
                    PocketComparisonEvidence evidence =
                            evidenceAssembler.assemble(
                                    querySummary,
                                    candidateSummaries.get(
                                            loaded.candidate().pocketId()
                                    ),
                                    alignmentResult,
                                    sequenceAlignment,
                                    retrievalEvidence(loaded, stageOne)
                            );
                    evidenceAssessment =
                            evidence.assessment().verdict().name();
                }

                compared.add(new ComparedCandidate(
                        loaded,
                        comparison,
                        assessment,
                        substitutionAssessment,
                        finalSimilarity,
                        chemistryScorer.classify(
                                assessment,
                                finalSimilarity
                        ),
                        alignmentResult.initialization().name(),
                        evidenceAssessment
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

    /**
     * Stage 1 retrieval: ordered by the precomputed shape-descriptor
     * distance evaluated in SQL. Query pockets without a precomputed
     * descriptor (legacy rows) fall back to the legacy volume/residue/
     * chemistry ordering.
     */
    private List<PocketSummaryEntity> findStageOneCandidates(
            PocketSummaryEntity querySummary,
            int stageOneLimit
    ) {
        PageRequest page = PageRequest.of(0, stageOneLimit);

        double[] queryHistogram = querySummary.getRadialHistogram();

        if (querySummary.getRadiusOfGyration() == null
                || querySummary.getExtentMajor() == null
                || querySummary.getElongation() == null
                || querySummary.getFlatness() == null
                || queryHistogram == null) {
            return pocketSummaryRepository
                    .findDescriptorCandidatesLegacyOrder(
                            querySummary.getPocketId(),
                            MINIMUM_VOLUME_RATIO,
                            MAXIMUM_VOLUME_RATIO,
                            MINIMUM_RESIDUE_RATIO,
                            MAXIMUM_RESIDUE_RATIO,
                            page
                    );
        }

        return pocketSummaryRepository.findDescriptorCandidates(
                querySummary.getPocketId(),
                MINIMUM_VOLUME_RATIO,
                MAXIMUM_VOLUME_RATIO,
                MINIMUM_RESIDUE_RATIO,
                MAXIMUM_RESIDUE_RATIO,
                querySummary.getRadiusOfGyration(),
                querySummary.getExtentMajor(),
                querySummary.getElongation(),
                querySummary.getFlatness(),
                queryHistogram[0],
                queryHistogram[1],
                queryHistogram[2],
                queryHistogram[3],
                queryHistogram[4],
                queryHistogram[5],
                queryHistogram[6],
                queryHistogram[7],
                queryHistogram[8],
                queryHistogram[9],
                queryHistogram[10],
                queryHistogram[11],
                page
        );
    }

    /**
     * The Stage 1 candidate set with its per-candidate retrieval
     * metadata: the unioned summaries in channel order (SQL global
     * top N, then PocketMatch top N, then chosen), the 1-based SQL
     * retrieval ranks (SQL candidates only — union-added candidates
     * have no Stage 1 rank, reported as {@code 0}), the provenance of
     * the channel that first surfaced each candidate, the chosen
     * pocket ids (for the Stage 2 truncation guarantee), and the
     * PocketMatch channel evidence (kept separate from the descriptor
     * distances; never blended).
     */
    private record StageOneSelection(
            List<PocketSummaryEntity> summaries,
            Map<Long, Integer> stageOneRanks,
            Map<Long, CandidateProvenance> provenance,
            Map<Long, Set<PocketCandidateSource>> candidateSources,
            Set<Long> chosenPocketIds,
            Map<Long, PocketMatchCandidate> pocketMatchEvidence
    ) {
    }

    /**
     * Builds the Stage 1 candidate set as a union of up to three
     * channels, in order: the SQL global-shape retrieval, the
     * experimental PocketMatch channel
     * ({@code pocket.search.pocket-match.enabled}, disabled by
     * default), and the chosen-reference guarantee
     * ({@code docking.structure.chosen_pocket_id}).
     *
     * <p>The union only ADDS candidates; it never restricts the SQL
     * search space. A candidate present in several channels is
     * deduplicated and keeps the provenance — and, for SQL members,
     * the natural retrieval rank — of the first channel that surfaced
     * it (a chosen pocket that is also in the SQL list keeps {@code
     * GLOBAL_SHAPE}; the dual membership is intentional, no combined
     * flag).</p>
     *
     * <p>Chosen semantics: a chosen pocket is guaranteed downstream
     * evaluation — it is included even when it falls outside the SQL
     * result limit and it survives the Stage 2 truncation (see
     * {@link #selectByShapeDistance}). Chosen is not automatic
     * similarity and carries no ranking bonus: its Stage 3 position
     * comes from its own scores only.</p>
     */
    private StageOneSelection unionStageOneCandidates(
            PocketSummaryEntity querySummary,
            List<PocketSummaryEntity> sqlSummaries
    ) {
        Map<Long, Integer> stageOneRanks = new HashMap<>();
        Map<Long, CandidateProvenance> provenance = new HashMap<>();
        Map<Long, PocketMatchCandidate> pocketMatchEvidence =
                new HashMap<>();

        Set<Long> present = new HashSet<>();
        present.add(querySummary.getPocketId());

        List<PocketSummaryEntity> union =
                new ArrayList<>(sqlSummaries);
        for (int index = 0; index < sqlSummaries.size(); index++) {
            long pocketId = sqlSummaries.get(index).getPocketId();
            stageOneRanks.put(pocketId, index + 1);
            provenance.put(pocketId, CandidateProvenance.GLOBAL_SHAPE);
            present.add(pocketId);
        }

        List<PocketMatchCandidate> pocketMatch =
                pocketMatchCandidates.isEnabled()
                        ? pocketMatchCandidates.topCandidates(
                                querySummary.getPocketId())
                        : List.of();
        for (PocketMatchCandidate candidate : pocketMatch) {
            pocketMatchEvidence.put(candidate.pocketId(), candidate);
        }

        // pocket.search.include-chosen-references gates the
        // chosen-reference channel (default on).
        Set<Long> chosenPocketIds = new LinkedHashSet<>();
        if (searchProperties.isIncludeChosenReferences()) {
            chosenPocketIds.addAll(
                    structureRepository.findAllChosenPocketIds()
            );
        }
        chosenPocketIds.remove(querySummary.getPocketId());

        // Union-added candidates need their summary rows; one bulk
        // fetch for both channels, appended in channel order.
        List<Long> missing = new ArrayList<>();
        for (PocketMatchCandidate candidate : pocketMatch) {
            if (!present.contains(candidate.pocketId())) {
                missing.add(candidate.pocketId());
                present.add(candidate.pocketId());
            }
        }
        List<Long> missingChosen = new ArrayList<>();
        for (Long chosenPocketId : chosenPocketIds) {
            if (!present.contains(chosenPocketId)) {
                missingChosen.add(chosenPocketId);
                present.add(chosenPocketId);
            }
        }
        missing.addAll(missingChosen);

        Map<Long, PocketSummaryEntity> byId = new HashMap<>();
        if (!missing.isEmpty()) {
            for (PocketSummaryEntity summary :
                    pocketSummaryRepository.findAllById(missing)) {
                byId.put(summary.getPocketId(), summary);
            }
        }

        for (PocketMatchCandidate candidate : pocketMatch) {
            PocketSummaryEntity summary = byId.get(candidate.pocketId());
            if (summary != null
                    && !provenance.containsKey(candidate.pocketId())) {
                provenance.put(
                        candidate.pocketId(),
                        CandidateProvenance.POCKET_MATCH
                );
                union.add(summary);
            }
        }
        for (Long chosenPocketId : missingChosen) {
            PocketSummaryEntity summary = byId.get(chosenPocketId);
            if (summary != null
                    && !provenance.containsKey(chosenPocketId)) {
                provenance.put(
                        chosenPocketId,
                        CandidateProvenance.CHOSEN_REFERENCE
                );
                union.add(summary);
            }
        }

        // Channel membership of every unioned candidate (dual
        // membership preserved, independent of first-surfaced
        // provenance).
        Set<Long> pocketMatchIds = new HashSet<>();
        for (PocketMatchCandidate candidate : pocketMatch) {
            pocketMatchIds.add(candidate.pocketId());
        }

        Map<Long, Set<PocketCandidateSource>> candidateSources =
                new HashMap<>();
        for (PocketSummaryEntity summary : union) {
            long pocketId = summary.getPocketId();
            Set<PocketCandidateSource> sources =
                    EnumSet.noneOf(PocketCandidateSource.class);
            if (stageOneRanks.containsKey(pocketId)) {
                sources.add(PocketCandidateSource.GLOBAL_SHAPE);
            }
            if (pocketMatchIds.contains(pocketId)) {
                sources.add(PocketCandidateSource.POCKET_MATCH);
            }
            if (chosenPocketIds.contains(pocketId)) {
                sources.add(PocketCandidateSource.CHOSEN_REFERENCE);
            }
            candidateSources.put(pocketId, sources);
        }

        return new StageOneSelection(
                union,
                stageOneRanks,
                provenance,
                candidateSources,
                chosenPocketIds,
                pocketMatchEvidence
        );
    }

    /**
     * The retrieval provenance of one Stage 3 candidate as athena
     * evidence: the global-shape rank/distance for SQL-retrieved
     * candidates, the full PocketMatch channel evidence when that
     * channel surfaced the candidate, and the chosen-reference flag.
     * Channels that never saw the candidate report
     * {@code evaluated = false} — ranks and scores are never
     * invented.
     */
    private static PocketRetrievalEvidence retrievalEvidence(
            LoadedCandidate loaded,
            StageOneSelection stageOne
    ) {
        long pocketId = loaded.candidate().pocketId();

        GlobalShapeRetrievalEvidence globalShape = loaded.stageOneRank() > 0
                ? new GlobalShapeRetrievalEvidence(
                        true,
                        OptionalInt.of(loaded.stageOneRank()),
                        OptionalDouble.of(
                                loaded.candidate().descriptorDistance()
                        )
                )
                : GlobalShapeRetrievalEvidence.notEvaluated();

        PocketMatchCandidate pocketMatch =
                stageOne.pocketMatchEvidence().get(pocketId);
        PocketMatchRetrievalEvidence pocketMatchEvidence =
                pocketMatch == null
                        ? PocketMatchRetrievalEvidence.notEvaluated()
                        : new PocketMatchRetrievalEvidence(
                                true,
                                optionalRank(pocketMatch.symmetricRank()),
                                optionalRank(
                                        pocketMatch.queryCoverageRank()
                                ),
                                optionalScore(
                                        pocketMatch.symmetricScore()
                                ),
                                OptionalDouble.of(
                                        pocketMatch.queryCoverage()
                                ),
                                optionalScore(
                                        pocketMatch.candidateCoverage()
                                ),
                                pocketMatch.toleranceAngstroms()
                        );

        boolean chosenReference =
                stageOne.chosenPocketIds().contains(pocketId);

        Set<PocketCandidateSource> sources =
                stageOne.candidateSources().get(pocketId);
        if (sources == null) {
            // Not expected (every Stage 3 candidate comes from the
            // union): fall back to the first-surfaced provenance.
            sources = EnumSet.of(switch (loaded.provenance()) {
                case GLOBAL_SHAPE -> PocketCandidateSource.GLOBAL_SHAPE;
                case POCKET_MATCH -> PocketCandidateSource.POCKET_MATCH;
                case CHOSEN_REFERENCE ->
                        PocketCandidateSource.CHOSEN_REFERENCE;
            });
        }

        return new PocketRetrievalEvidence(
                globalShape,
                pocketMatchEvidence,
                chosenReference,
                sources
        );
    }

    private static OptionalInt optionalRank(Integer rank) {
        return rank == null
                ? OptionalInt.empty()
                : OptionalInt.of(rank);
    }

    private static OptionalDouble optionalScore(Double score) {
        return score == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(score);
    }

    private List<LoadedCandidate> selectByShapeDistance(
            List<PocketCandidate> candidates,
            Map<Long, PocketPointCloud> pointClouds,
            PocketShapeDescriptor queryDescriptor,
            int stageTwoLimit,
            StageOneSelection stageOne
    ) {
        List<LoadedCandidate> loaded =
                new ArrayList<>(candidates.size());

        for (PocketCandidate candidate : candidates) {
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
                // SQL-retrieved candidates keep their natural Stage 1
                // rank; union-added candidates (PocketMatch-only,
                // chosen-only) have no Stage 1 rank, reported as 0.
                PocketMatchCandidate pocketMatch = stageOne
                        .pocketMatchEvidence()
                        .get(candidate.pocketId());
                loaded.add(new LoadedCandidate(
                        candidate,
                        pointCloud,
                        shapeDescriptor,
                        shapeDistance,
                        stageOne.stageOneRanks()
                                .getOrDefault(candidate.pocketId(), 0),
                        0,
                        stageOne.provenance()
                                .getOrDefault(
                                        candidate.pocketId(),
                                        CandidateProvenance.GLOBAL_SHAPE
                                ),
                        pocketMatch == null
                                ? null
                                : pocketMatch.queryCoverage(),
                        pocketMatch == null
                                ? null
                                : pocketMatch.rank(),
                        pocketMatch == null
                                ? null
                                : pocketMatch.symmetricRank(),
                        pocketMatch == null
                                ? null
                                : pocketMatch.queryCoverageRank()
                ));
            } catch (RuntimeException exception) {
                LOGGER.warn(
                        "Skipping pocket {} during geometric reranking: {}",
                        candidate.pocketId(),
                        exception.getMessage()
                );
            }
        }

        List<LoadedCandidate> sorted = loaded.stream()
                .sorted(STAGE_TWO_ORDER)
                .toList();

        // Chosen-reference candidates are guaranteed Stage 2
        // evaluation: they survive the truncation regardless of
        // stageTwoLimit and do not consume non-chosen slots. Their
        // Stage 2 rank still comes from their shape distance only.
        List<LoadedCandidate> survivors = new ArrayList<>();
        int retainedNonChosen = 0;
        for (LoadedCandidate candidate : sorted) {
            boolean chosen = stageOne.chosenPocketIds()
                    .contains(candidate.candidate().pocketId());
            if (!chosen) {
                if (retainedNonChosen >= stageTwoLimit) {
                    continue;
                }
                retainedNonChosen++;
            }
            survivors.add(candidate);
        }

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
        ResidueChemistryAssessment assessment = compared.assessment();

        return new PocketSimilarityDiagnostic(
                candidate.pocketId(),
                candidate.structureId(),
                candidate.sourceAccession(),
                candidate.pocketNumber(),
                candidate.alphaSphereCount(),
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
                assessment.chemistrySimilarity(),
                assessment.chemistryCoverageAdjustedSimilarity(),
                assessment.compatibleMatchedFraction(),
                assessment.spatialReplacementFraction(),
                assessment.identicalCount(),
                assessment.conservativeCount(),
                assessment.chemistryCompatibleCount(),
                assessment.spatialReplacementCount(),
                assessment.matchedResidueCount(),
                assessment.keyResidueChemistrySimilarity(),
                compared.substitutionAssessment()
                        .meanSubstitutionSimilarity(),
                compared.classification().name(),
                compared.finalSimilarity(),
                candidate.uniProtId(),
                candidate.proteinName(),
                candidate.geneName(),
                candidate.organism(),
                compared.alignmentInitialization(),
                loaded.provenance().name(),
                loaded.pocketMatchQueryCoverage(),
                loaded.pocketMatchRank(),
                loaded.pocketMatchSymmetricRank(),
                loaded.pocketMatchQueryCoverageRank(),
                candidate.candidateSources().stream()
                        .map(Enum::name)
                        .sorted()
                        .toList(),
                compared.evidenceAssessment()
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

    /**
     * The cached protein sequence alignment for one query/candidate
     * receptor pair, or {@code null} when no sequence seed can exist
     * (missing receptor ids) or the alignment cannot be obtained; in
     * both cases the pocket alignment falls back to PCA+ICP, which is
     * byte-identical to the pre-seed behavior. Lookups are memoized
     * per request so each receptor pair costs at most one cache
     * access.
     */
    private SequenceAlignment sequenceAlignmentFor(
            Long queryReceptorId,
            Long candidateReceptorId,
            Map<Long, SequenceAlignment> requestCache
    ) {
        if (queryReceptorId == null || candidateReceptorId == null) {
            return null;
        }

        if (requestCache.containsKey(candidateReceptorId)) {
            return requestCache.get(candidateReceptorId);
        }

        SequenceAlignment alignment;

        try {
            alignment = sequenceAlignmentService.alignmentFor(
                    queryReceptorId,
                    candidateReceptorId
            );
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "Sequence alignment for receptors {} -> {}"
                            + " unavailable; falling back to PCA+ICP: {}",
                    queryReceptorId,
                    candidateReceptorId,
                    exception.getMessage()
            );
            alignment = null;
        }

        requestCache.put(candidateReceptorId, alignment);

        return alignment;
    }

    private PocketCandidate toCandidate(            PocketSummaryEntity query,
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
                candidate.getAlphaSphereCount() == null
                        ? 0
                        : candidate.getAlphaSphereCount(),
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
            int alphaSphereCount,
            double descriptorDistance,
            double volumeDistance,
            double residueDistance,
            double chemistryDistance,
            String uniProtId,
            String proteinName,
            String geneName,
            String organism,
            Set<PocketCandidateSource> candidateSources
    ) {
        /**
         * Legacy construction without retrieval provenance
         * (equivalent to an empty source set).
         */
        public PocketCandidate(
                long pocketId,
                long structureId,
                String sourceAccession,
                int pocketNumber,
                int alphaSphereCount,
                double descriptorDistance,
                double volumeDistance,
                double residueDistance,
                double chemistryDistance,
                String uniProtId,
                String proteinName,
                String geneName,
                String organism
        ) {
            this(pocketId, structureId, sourceAccession, pocketNumber,
                    alphaSphereCount, descriptorDistance, volumeDistance,
                    residueDistance, chemistryDistance, uniProtId,
                    proteinName, geneName, organism, Set.of());
        }

        public PocketCandidate {
            candidateSources = Set.copyOf(
                    Objects.requireNonNull(
                            candidateSources,
                            "candidateSources"
                    )
            );
        }

        private PocketCandidate withCandidateSources(
                Set<PocketCandidateSource> sources
        ) {
            return new PocketCandidate(
                    pocketId,
                    structureId,
                    sourceAccession,
                    pocketNumber,
                    alphaSphereCount,
                    descriptorDistance,
                    volumeDistance,
                    residueDistance,
                    chemistryDistance,
                    uniProtId,
                    proteinName,
                    geneName,
                    organism,
                    sources
            );
        }
    }

    private record LoadedCandidate(
            PocketCandidate candidate,
            PocketPointCloud pointCloud,
            PocketShapeDescriptor shapeDescriptor,
            double shapeDistance,
            int stageOneRank,
            int stageTwoRank,
            CandidateProvenance provenance,
            Double pocketMatchQueryCoverage,
            Integer pocketMatchRank,
            Integer pocketMatchSymmetricRank,
            Integer pocketMatchQueryCoverageRank
    ) {
        private LoadedCandidate withStageTwoRank(int stageTwoRank) {
            return new LoadedCandidate(
                    candidate,
                    pointCloud,
                    shapeDescriptor,
                    shapeDistance,
                    stageOneRank,
                    stageTwoRank,
                    provenance,
                    pocketMatchQueryCoverage,
                    pocketMatchRank,
                    pocketMatchSymmetricRank,
                    pocketMatchQueryCoverageRank
            );
        }
    }

    /**
     * The raw outcome of the shared comparison path: everything
     * {@link #compareGeometries} and the comparison-report service
     * need, loaded and computed exactly once.
     *
     * @param sequenceAlignment the cached protein sequence alignment
     *                          of the receptor pair, or {@code null}
     *                          when no sequence seed could exist
     */
    record ComparisonRun(
            PocketSummaryEntity querySummary,
            PocketSummaryEntity candidateSummary,
            PocketPointCloud queryCloud,
            PocketPointCloud candidateCloud,
            List<AlphaSphereView> queryAlphaSpheres,
            List<AlphaSphereView> candidateAlphaSpheres,
            SequenceAlignment sequenceAlignment,
            PocketAlignmentResult alignmentResult,
            List<String> keyResidues,
            ResidueChemistryAssessment chemistryAssessment,
            ResidueSubstitutionAssessment substitutionAssessment,
            double finalSimilarity
    ) {
    }

    private record ComparedCandidate(
            LoadedCandidate loaded,
            PocketComparison comparison,
            ResidueChemistryAssessment assessment,
            ResidueSubstitutionAssessment substitutionAssessment,
            double finalSimilarity,
            PocketSimilarityClassification classification,
            String alignmentInitialization,
            String evidenceAssessment
    ) {
    }
}
