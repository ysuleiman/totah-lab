package totah.lab.web.pocketmatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import totah.lab.athena.pocket.pocketmatch.DefaultPocketMatchComparator;
import totah.lab.athena.pocket.pocketmatch.PocketMatchComparison;
import totah.lab.athena.pocket.pocketmatch.PocketMatchConfiguration;
import totah.lab.athena.pocket.pocketmatch.PocketMatchSignature;
import totah.lab.gaia.structure.Structure;
import totah.lab.web.pocketmatch.PocketMatchSignatureCodec
        .StoredPocketMatchSignature;
import totah.lab.web.pocketmatch.PocketMatchSignatureLoader
        .ResidueIdentity;

import javax.sql.DataSource;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Offline benchmark of the PocketMatch-style Stage 1 representation
 * against the current global shape descriptor.
 *
 * <p>Enabled with {@code pocket.search.pocket-match.benchmark-enabled=true}
 * (intended with {@code --spring.main.web-application-type=none}).
 * Parses every pocket's parent structure artifact once (full-fidelity
 * residue atoms; {@code docking.pocket_atom} is only pocket-lining
 * contact atoms and cannot supply representative points), builds
 * PocketMatch signatures, persists them as a binary store (the same
 * artifact the experimental retrieval channel consumes), scores them
 * against the benchmark query pocket, and writes a markdown report
 * with rank comparisons, storage statistics, and latency
 * measurements.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the {@code totah.lab.athena.pocket.pocketmatch} package
 * documentation for the full citation and provenance.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "pocket.search.pocket-match",
        name = "benchmark-enabled",
        havingValue = "true"
)
public class PocketMatchBenchmarkRunner implements CommandLineRunner {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PocketMatchBenchmarkRunner.class);

    /** Fixed benchmark cases: METTL7B pocket 3, METTL7B pocket 1,
     *  and the AF-Q14112 geometry-only false positive. */
    private static final List<Long> FIXED_CASE_POCKET_IDS =
            List.of(3L, 1L, 313826L);

    private static final Map<Long, String> CASE_LABELS = Map.of(
            3L, "METTL7B pocket 3 (homologous)",
            1L, "METTL7B pocket 1 (secondary)",
            313826L, "AF-Q14112 pocket 313826 (geometry-only FP)"
    );

    private static final double[] SWEEP_TOLERANCES = {0.25, 0.50, 1.00};

    private static final int[] RECALL_DEPTHS = {100, 500, 1000};

    private static final long PROJECTED_POCKET_COUNT = 510_462L;

    private static final int MAX_WORKERS = 8;

    private static final String STRUCTURES_SQL = """
            SELECT DISTINCT s.id, a.storage_location
            FROM docking.pocket p
            JOIN docking.structure s ON s.id = p.structure_id
            JOIN docking.artifacts a ON a.id = s.artifact_id
            ORDER BY s.id
            """;

    private static final String STRUCTURE_POCKETS_SQL = """
            SELECT p.id AS pocket_id, pr.chain, pr.residue_number,
                   r.insertion_code, pr.residue_name
            FROM docking.pocket p
            JOIN docking.pocket_residue pr ON pr.pocket_id = p.id
            LEFT JOIN docking.residue r ON r.id = pr.residue_id
            WHERE p.structure_id = ?
            ORDER BY p.id, pr.id
            """;

    /*
     * Full Stage 1 ordering: the exact PocketRetrievalDistance formula
     * and the wide volume/residue sanity bands used by
     * PocketSummaryRepository.findDescriptorCandidates, evaluated over
     * the whole corpus (no page limit).
     */
    private static final String STAGE_ONE_ORDER_SQL = """
            SELECT candidate.pocket_id
            FROM docking.pocket_summary_mv candidate,
                 docking.pocket_summary_mv query_pocket
            WHERE query_pocket.pocket_id = ?
              AND candidate.pocket_id <> query_pocket.pocket_id
              AND candidate.volume BETWEEN
                  query_pocket.volume * 0.35 AND query_pocket.volume * 2.75
              AND candidate.residue_count >=
                  query_pocket.residue_count * 0.40
              AND candidate.residue_count <=
                  query_pocket.residue_count * 2.75
            ORDER BY
                least(
                    0.20 * (case
                        when candidate.radius_of_gyration <= 0.0
                             or query_pocket.radius_of_gyration <= 0.0
                        then 1.0
                        else least(1.0, abs(ln(
                            candidate.radius_of_gyration
                                / query_pocket.radius_of_gyration))
                                / ln(4.0)) end)
                    + 0.20 * (case
                        when candidate.extent_major <= 0.0
                             or query_pocket.extent_major <= 0.0
                        then 1.0
                        else least(1.0, abs(ln(
                            candidate.extent_major
                                / query_pocket.extent_major))
                                / ln(4.0)) end)
                    + 0.15 * abs(candidate.elongation
                                 - query_pocket.elongation)
                    + 0.15 * abs(candidate.flatness
                                 - query_pocket.flatness)
                    + 0.30 * (0.5 * (
                          abs(candidate.h0 - query_pocket.h0)
                        + abs(candidate.h1 - query_pocket.h1)
                        + abs(candidate.h2 - query_pocket.h2)
                        + abs(candidate.h3 - query_pocket.h3)
                        + abs(candidate.h4 - query_pocket.h4)
                        + abs(candidate.h5 - query_pocket.h5)
                        + abs(candidate.h6 - query_pocket.h6)
                        + abs(candidate.h7 - query_pocket.h7)
                        + abs(candidate.h8 - query_pocket.h8)
                        + abs(candidate.h9 - query_pocket.h9)
                        + abs(candidate.h10 - query_pocket.h10)
                        + abs(candidate.h11 - query_pocket.h11))),
                    (0.15 * abs(candidate.elongation
                                - query_pocket.elongation)
                    + 0.15 * abs(candidate.flatness
                                 - query_pocket.flatness)
                    + 0.30 * (0.5 * (
                          abs(candidate.h0 - query_pocket.h0)
                        + abs(candidate.h1 - query_pocket.h1)
                        + abs(candidate.h2 - query_pocket.h2)
                        + abs(candidate.h3 - query_pocket.h3)
                        + abs(candidate.h4 - query_pocket.h4)
                        + abs(candidate.h5 - query_pocket.h5)
                        + abs(candidate.h6 - query_pocket.h6)
                        + abs(candidate.h7 - query_pocket.h7)
                        + abs(candidate.h8 - query_pocket.h8)
                        + abs(candidate.h9 - query_pocket.h9)
                        + abs(candidate.h10 - query_pocket.h10)
                        + abs(candidate.h11 - query_pocket.h11)))) / 0.60
                        + 0.05),
                candidate.pocket_id
            """;

    private final DataSource dataSource;
    private final PocketMatchProperties properties;
    private final PocketMatchSignatureLoader loader;

    public PocketMatchBenchmarkRunner(
            DataSource dataSource,
            PocketMatchProperties properties,
            PocketMatchSignatureLoader loader
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    @Override
    public void run(String... arguments) throws Exception {
        long queryPocketId = properties.getBenchmarkQueryPocketId();
        double tolerance = properties.getDistanceTolerance();

        LOGGER.info(
                "PocketMatch benchmark starting: queryPocketId={}"
                        + " tolerance={}",
                queryPocketId,
                tolerance
        );

        PocketMatchSignature querySignature =
                loader.load(queryPocketId);
        LOGGER.info(
                "Query signature: {} distances, diagnostics={}",
                querySignature.totalDistanceCount(),
                querySignature.diagnostics()
        );

        Map<Long, PocketMatchSignature> caseSignatures = new HashMap<>();
        for (long casePocketId : FIXED_CASE_POCKET_IDS) {
            caseSignatures.put(casePocketId, loader.load(casePocketId));
        }

        DefaultPocketMatchComparator comparator =
                new DefaultPocketMatchComparator(
                        new PocketMatchConfiguration(tolerance)
                );

        StringBuilder report = new StringBuilder();
        report.append("# PocketMatch Stage 1 benchmark\n\n");
        report.append("Query pocket: ").append(queryPocketId)
                .append(" (METTL7A pocket 32)\n");
        report.append("Distance tolerance: ").append(tolerance)
                .append(" angstroms\n");
        report.append("Signatures: full-fidelity residue atoms parsed")
                .append(" from structure artifacts\n\n");

        appendPairwiseSection(
                report, queryPocketId, querySignature,
                caseSignatures, comparator
        );

        Path storePath = Path.of(properties.getSignatureStore());
        CorpusScan scan = scanCorpus(
                queryPocketId, querySignature, comparator, storePath
        );

        List<Long> stageOneOrder = stageOneOrder(queryPocketId);

        appendRankingSection(report, scan, stageOneOrder);

        appendToleranceSweep(
                report, queryPocketId, querySignature, storePath
        );

        appendStorageSection(report, scan);
        appendLatencySection(
                report, querySignature, caseSignatures, comparator, scan
        );

        Path reportPath = Path.of(properties.getBenchmarkReport());
        if (reportPath.getParent() != null) {
            Files.createDirectories(reportPath.getParent());
        }
        Files.writeString(reportPath, report.toString());
        LOGGER.info("PocketMatch benchmark report written to {}",
                reportPath.toAbsolutePath());
    }

    private void appendPairwiseSection(
            StringBuilder report,
            long queryPocketId,
            PocketMatchSignature querySignature,
            Map<Long, PocketMatchSignature> caseSignatures,
            DefaultPocketMatchComparator comparator
    ) {
        report.append("## Fixed-case pairwise scores\n\n");
        report.append("| direction | matched | query coverage")
                .append(" | candidate coverage | symmetric score |\n");
        report.append("|---|---|---|---|---|\n");

        for (long casePocketId : FIXED_CASE_POCKET_IDS) {
            PocketMatchSignature candidate =
                    caseSignatures.get(casePocketId);

            PocketMatchComparison forward =
                    comparator.compare(querySignature, candidate);
            PocketMatchComparison reverse =
                    comparator.compare(candidate, querySignature);

            appendComparisonRow(
                    report,
                    queryPocketId + " -> " + label(casePocketId),
                    forward
            );
            appendComparisonRow(
                    report,
                    label(casePocketId) + " -> " + queryPocketId,
                    reverse
            );
        }
        report.append('\n');
    }

    private static void appendComparisonRow(
            StringBuilder report,
            String direction,
            PocketMatchComparison comparison
    ) {
        report.append("| ").append(direction)
                .append(" | ").append(comparison.matchedDistanceCount())
                .append(" | ")
                .append(formatScore(comparison.firstCoverage()))
                .append(" | ")
                .append(formatScore(comparison.secondCoverage()))
                .append(" | ")
                .append(formatScore(comparison.symmetricScore()))
                .append(" |\n");
    }

    private CorpusScan scanCorpus(
            long queryPocketId,
            PocketMatchSignature querySignature,
            DefaultPocketMatchComparator comparator,
            Path storePath
    ) throws IOException, SQLException, InterruptedException {
        if (storePath.getParent() != null) {
            Files.createDirectories(storePath.getParent());
        }

        List<StructureRef> structures = loadStructureRefs();
        LOGGER.info("Corpus scan: {} structures", structures.size());

        Map<Long, Double> symmetricScores = new HashMap<>();
        Map<Long, Double> queryCoverages = new HashMap<>();
        Map<Long, Double> candidateCoverages = new HashMap<>();
        List<Integer> signatureSizes = new ArrayList<>();

        long pockets = 0;
        long compareNanos = 0;
        int failedStructures = 0;
        long scanStart = System.nanoTime();
        int maximum = properties.getBenchmarkMaximumPockets();

        ExecutorService pool = Executors.newFixedThreadPool(
                Math.min(MAX_WORKERS,
                        Runtime.getRuntime().availableProcessors())
        );
        CompletionService<StructureOutcome> completion =
                new ExecutorCompletionService<>(pool);

        int submitted = 0;
        for (StructureRef structure : structures) {
            completion.submit(() -> processStructure(
                    structure,
                    queryPocketId,
                    querySignature,
                    comparator
            ));
            submitted++;
        }

        try (DataOutputStream storeOutput = new DataOutputStream(
                new BufferedOutputStream(
                        Files.newOutputStream(storePath),
                        1 << 20
                )
        )) {
            for (int done = 0; done < submitted; done++) {
                StructureOutcome outcome = take(completion);
                if (outcome.failure() != null) {
                    failedStructures++;
                    LOGGER.warn(
                            "Skipping structure {}: {}",
                            outcome.structureId(),
                            outcome.failure()
                    );
                    continue;
                }
                for (PocketOutcome pocket : outcome.pockets()) {
                    PocketMatchSignatureCodec.writeRecord(
                            storeOutput,
                            pocket.pocketId(),
                            pocket.signature()
                    );
                    signatureSizes.add(
                            estimateRecordBytes(pocket.signature()));
                    compareNanos += pocket.compareNanos();
                    if (pocket.comparison() != null) {
                        symmetricScores.put(
                                pocket.pocketId(),
                                pocket.comparison().symmetricScore());
                        queryCoverages.put(
                                pocket.pocketId(),
                                pocket.comparison().firstCoverage());
                        candidateCoverages.put(
                                pocket.pocketId(),
                                pocket.comparison().secondCoverage());
                    }
                    pockets++;
                }
                if (done % 1000 == 0) {
                    LOGGER.info(
                            "Corpus scan progress: {}/{} structures,"
                                    + " {} pockets",
                            done,
                            submitted,
                            pockets
                    );
                }
                if (maximum > 0 && pockets >= maximum) {
                    break;
                }
            }
        } finally {
            pool.shutdownNow();
        }

        long scanMillis = (System.nanoTime() - scanStart) / 1_000_000L;
        LOGGER.info(
                "Corpus scan complete: {} pockets from {} structures"
                        + " ({} failed) in {} ms",
                pockets,
                submitted,
                failedStructures,
                scanMillis
        );

        return new CorpusScan(
                pockets,
                failedStructures,
                scanMillis,
                compareNanos,
                symmetricScores,
                queryCoverages,
                candidateCoverages,
                signatureSizes
        );
    }

    private StructureOutcome processStructure(
            StructureRef structureRef,
            long queryPocketId,
            PocketMatchSignature querySignature,
            DefaultPocketMatchComparator comparator
    ) {
        try {
            Structure structure =
                    loader.readStructure(structureRef.storageLocation());

            Map<Long, List<ResidueIdentity>> pocketResidues =
                    structurePocketResidues(structureRef.structureId());

            List<PocketOutcome> outcomes = new ArrayList<>();
            for (Map.Entry<Long, List<ResidueIdentity>> entry :
                    pocketResidues.entrySet()) {
                long pocketId = entry.getKey();
                PocketMatchSignature signature = loader.describe(
                        structure,
                        pocketId,
                        entry.getValue()
                );

                PocketMatchComparison comparison = null;
                long compareNanos = 0L;
                if (pocketId != queryPocketId) {
                    long start = System.nanoTime();
                    comparison = comparator.compare(
                            querySignature, signature);
                    compareNanos = System.nanoTime() - start;
                }
                outcomes.add(new PocketOutcome(
                        pocketId,
                        signature,
                        comparison,
                        compareNanos
                ));
            }
            return new StructureOutcome(
                    structureRef.structureId(), outcomes, null);
        } catch (Exception exception) {
            return new StructureOutcome(
                    structureRef.structureId(),
                    List.of(),
                    exception.getClass().getSimpleName() + ": "
                            + exception.getMessage()
            );
        }
    }

    private Map<Long, List<ResidueIdentity>> structurePocketResidues(
            long structureId
    ) throws SQLException {
        Map<Long, List<ResidueIdentity>> residues = new HashMap<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STRUCTURE_POCKETS_SQL)
        ) {
            statement.setLong(1, structureId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    long pocketId = rows.getLong("pocket_id");
                    residues.computeIfAbsent(
                            pocketId,
                            ignored -> new ArrayList<>()
                    ).add(new ResidueIdentity(
                            rows.getString("chain").trim(),
                            rows.getInt("residue_number"),
                            insertionCode(
                                    rows.getString("insertion_code")),
                            rows.getString("residue_name")
                    ));
                }
            }
        }
        return residues;
    }

    private List<StructureRef> loadStructureRefs() throws SQLException {
        List<StructureRef> structures = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STRUCTURES_SQL)
        ) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    structures.add(new StructureRef(
                            rows.getLong(1),
                            rows.getString(2)
                    ));
                }
            }
        }
        return structures;
    }

    private static StructureOutcome take(
            CompletionService<StructureOutcome> completion
    ) throws InterruptedException {
        try {
            Future<StructureOutcome> future = completion.take();
            return future.get();
        } catch (ExecutionException exception) {
            throw new IllegalStateException(
                    "structure task failed unexpectedly",
                    exception
            );
        }
    }

    private void appendRankingSection(
            StringBuilder report,
            CorpusScan scan,
            List<Long> stageOneOrder
    ) {
        List<Long> symmetricOrder = orderBy(scan.symmetricScores());
        List<Long> queryCoverageOrder = orderBy(scan.queryCoverages());
        List<Long> candidateCoverageOrder =
                orderBy(scan.candidateCoverages());

        Map<Long, Integer> stageOneRanks = ranks(stageOneOrder);
        Map<Long, Integer> symmetricRanks = ranks(symmetricOrder);
        Map<Long, Integer> queryCoverageRanks =
                ranks(queryCoverageOrder);
        Map<Long, Integer> candidateCoverageRanks =
                ranks(candidateCoverageOrder);

        report.append("## Stage 1 rank comparison\n\n");
        report.append("| candidate | current descriptor rank")
                .append(" | PM symmetric rank | PM query-coverage rank")
                .append(" | PM candidate-coverage rank |\n");
        report.append("|---|---|---|---|---|\n");

        Set<Long> highlighted = new HashSet<>(FIXED_CASE_POCKET_IDS);
        highlighted.addAll(first(stageOneOrder, 10));
        highlighted.addAll(first(symmetricOrder, 10));

        for (long pocketId : highlighted) {
            report.append("| ").append(label(pocketId))
                    .append(" | ").append(rank(stageOneRanks, pocketId))
                    .append(" | ")
                    .append(rank(symmetricRanks, pocketId))
                    .append(" | ")
                    .append(rank(queryCoverageRanks, pocketId))
                    .append(" | ")
                    .append(rank(candidateCoverageRanks, pocketId))
                    .append(" |\n");
        }

        report.append("\nCurrent descriptor top 10: ")
                .append(describe(first(stageOneOrder, 10))).append('\n');
        report.append("PocketMatch symmetric top 10: ")
                .append(describe(first(symmetricOrder, 10))).append('\n');

        report.append("\n## Ranking overlap\n\n");
        report.append("| K | current top-K in PM top-K")
                .append(" | PM top-K in current top-K")
                .append(" | METTL7B pocket 3 in current top-K")
                .append(" | METTL7B pocket 3 in PM top-K |\n");
        report.append("|---|---|---|---|---|\n");
        for (int depth : RECALL_DEPTHS) {
            Set<Long> currentTop =
                    new HashSet<>(first(stageOneOrder, depth));
            Set<Long> pocketMatchTop =
                    new HashSet<>(first(symmetricOrder, depth));
            long currentInPm = currentTop.stream()
                    .filter(pocketMatchTop::contains).count();
            long pmInCurrent = pocketMatchTop.stream()
                    .filter(currentTop::contains).count();
            report.append("| ").append(depth)
                    .append(" | ").append(currentInPm)
                    .append(" | ").append(pmInCurrent)
                    .append(" | ")
                    .append(currentTop.contains(3L) ? "yes" : "no")
                    .append(" | ")
                    .append(pocketMatchTop.contains(3L) ? "yes" : "no")
                    .append(" |\n");
        }
        report.append('\n');
    }

    private void appendToleranceSweep(
            StringBuilder report,
            long queryPocketId,
            PocketMatchSignature querySignature,
            Path storePath
    ) throws IOException {
        report.append("## Distance-tolerance sweep\n\n");
        report.append("| tolerance | pocket 3 rank | pocket 1 rank")
                .append(" | pocket 313826 rank |\n");
        report.append("|---|---|---|---|\n");

        for (double sweepTolerance : SWEEP_TOLERANCES) {
            DefaultPocketMatchComparator sweepComparator =
                    new DefaultPocketMatchComparator(
                            new PocketMatchConfiguration(sweepTolerance)
                    );
            Map<Long, Double> scores = new HashMap<>();
            try (DataInputStream input = new DataInputStream(
                    new BufferedInputStream(
                            Files.newInputStream(storePath),
                            1 << 20
                    )
            )) {
                StoredPocketMatchSignature record;
                while ((record = PocketMatchSignatureCodec
                        .readRecord(input)) != null) {
                    if (record.pocketId() == queryPocketId) {
                        continue;
                    }
                    scores.put(
                            record.pocketId(),
                            sweepComparator.compare(
                                    querySignature,
                                    record.signature()
                            ).symmetricScore()
                    );
                }
            }
            Map<Long, Integer> sweepRanks = ranks(orderBy(scores));
            report.append("| ").append(sweepTolerance)
                    .append(" | ").append(rank(sweepRanks, 3L))
                    .append(" | ").append(rank(sweepRanks, 1L))
                    .append(" | ").append(rank(sweepRanks, 313826L))
                    .append(" |\n");
        }
        report.append('\n');
    }

    private void appendStorageSection(
            StringBuilder report,
            CorpusScan scan
    ) {
        List<Integer> sizes = scan.signatureSizes().stream()
                .sorted()
                .toList();
        if (sizes.isEmpty()) {
            return;
        }

        double mean = sizes.stream()
                .mapToInt(Integer::intValue).average().orElse(0.0);
        long median = sizes.get(sizes.size() / 2);
        long p95 = sizes.get(
                Math.min(sizes.size() - 1,
                        (int) (sizes.size() * 0.95)));
        double totalProjected = mean * PROJECTED_POCKET_COUNT;

        report.append("## Signature storage\n\n");
        report.append("Binary float records over ")
                .append(sizes.size()).append(" pockets:\n\n");
        report.append("- mean: ").append(formatBytes(mean)).append('\n');
        report.append("- median: ")
                .append(formatBytes(median)).append('\n');
        report.append("- p95: ").append(formatBytes(p95)).append('\n');
        report.append("- projected total for ")
                .append(PROJECTED_POCKET_COUNT)
                .append(" pockets: ")
                .append(formatBytes(totalProjected)).append('\n');
        report.append("- raw double-precision payload would double")
                .append(" the per-record distance bytes;")
                .append(" a PostgreSQL float8 array adds row and")
                .append(" array overhead on top of that\n\n");
    }

    private void appendLatencySection(
            StringBuilder report,
            PocketMatchSignature querySignature,
            Map<Long, PocketMatchSignature> caseSignatures,
            DefaultPocketMatchComparator comparator,
            CorpusScan scan
    ) {
        PocketMatchSignature sample =
                caseSignatures.get(FIXED_CASE_POCKET_IDS.get(0));

        // warmup, then measured single-pair latency
        for (int index = 0; index < 200; index++) {
            comparator.compare(querySignature, sample);
        }
        int repetitions = 1_000;
        long start = System.nanoTime();
        for (int index = 0; index < repetitions; index++) {
            comparator.compare(querySignature, sample);
        }
        double singlePairMicros =
                (System.nanoTime() - start) / 1_000.0 / repetitions;

        report.append("## Comparison latency\n\n");
        report.append("- single-pair comparison: ")
                .append(String.format("%.1f", singlePairMicros))
                .append(" microseconds\n");
        report.append("- measured comparison time during the scan: ")
                .append(String.format("%.1f",
                        scan.compareNanos() / 1_000_000_000.0))
                .append(" s total, ")
                .append(String.format("%.1f",
                        scan.compareNanos() / 1_000.0
                                / Math.max(1, scan.pocketCount())))
                .append(" microseconds per pocket\n");
        report.append("- full corpus scan measured: ")
                .append(scan.scanMillis() / 1_000.0)
                .append(" s for ")
                .append(scan.pocketCount())
                .append(" pockets (signature build from parsed")
                .append(" structure artifacts, serialization, and")
                .append(" comparison; ")
                .append(scan.failedStructureCount())
                .append(" structures failed)\n");
        report.append("- projected query-versus-all at ")
                .append(PROJECTED_POCKET_COUNT)
                .append(" pockets with precomputed signatures: ")
                .append(String.format("%.1f",
                        singlePairMicros * PROJECTED_POCKET_COUNT
                                / 1_000_000.0))
                .append(" s single-threaded\n\n");
    }

    private List<Long> stageOneOrder(long queryPocketId)
            throws SQLException {
        List<Long> order = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STAGE_ONE_ORDER_SQL)
        ) {
            statement.setLong(1, queryPocketId);
            statement.setFetchSize(10_000);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    order.add(rows.getLong(1));
                }
            }
        }
        return order;
    }

    private static List<Long> orderBy(Map<Long, Double> scores) {
        return scores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue(
                                Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey)
                .toList();
    }

    private static Map<Long, Integer> ranks(List<Long> order) {
        Map<Long, Integer> ranks = new HashMap<>(order.size());
        for (int index = 0; index < order.size(); index++) {
            ranks.put(order.get(index), index + 1);
        }
        return ranks;
    }

    private static String rank(Map<Long, Integer> ranks, long pocketId) {
        Integer rank = ranks.get(pocketId);
        return rank == null ? "not ranked" : String.valueOf(rank);
    }

    private static List<Long> first(List<Long> order, int count) {
        return order.subList(0, Math.min(count, order.size()));
    }

    private static String describe(List<Long> pocketIds) {
        StringBuilder description = new StringBuilder();
        for (long pocketId : pocketIds) {
            if (description.length() > 0) {
                description.append(", ");
            }
            description.append(pocketId);
            String caseLabel = CASE_LABELS.get(pocketId);
            if (caseLabel != null) {
                description.append(" (").append(caseLabel).append(')');
            }
        }
        return description.toString();
    }

    private static String label(long pocketId) {
        String caseLabel = CASE_LABELS.get(pocketId);
        return caseLabel == null
                ? "pocket " + pocketId
                : caseLabel;
    }

    private static String formatScore(double score) {
        return String.format("%.4f", score);
    }

    private static String formatBytes(double bytes) {
        if (bytes >= 1L << 30) {
            return String.format("%.2f GiB", bytes / (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format("%.2f MiB", bytes / (1L << 20));
        }
        if (bytes >= 1L << 10) {
            return String.format("%.2f KiB", bytes / (1L << 10));
        }
        return String.format("%.0f B", bytes);
    }

    private static Character insertionCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().charAt(0);
    }

    private static int estimateRecordBytes(
            PocketMatchSignature signature
    ) {
        int bytes = Long.BYTES + Integer.BYTES;
        for (double[] distances : signature.sortedDistances()) {
            bytes += Integer.BYTES
                    + distances.length * Float.BYTES;
        }
        return bytes;
    }

    private record StructureRef(
            long structureId,
            String storageLocation
    ) {
    }

    private record PocketOutcome(
            long pocketId,
            PocketMatchSignature signature,
            PocketMatchComparison comparison,
            long compareNanos
    ) {
    }

    private record StructureOutcome(
            long structureId,
            List<PocketOutcome> pockets,
            String failure
    ) {
    }

    private record CorpusScan(
            long pocketCount,
            int failedStructureCount,
            long scanMillis,
            long compareNanos,
            Map<Long, Double> symmetricScores,
            Map<Long, Double> queryCoverages,
            Map<Long, Double> candidateCoverages,
            List<Integer> signatureSizes
    ) {
    }
}
