package totah.lab.web.pocketmatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import totah.lab.athena.pocket.pocketmatch.DefaultPocketMatchComparator;
import totah.lab.athena.pocket.pocketmatch.PocketMatchComparator;
import totah.lab.athena.pocket.pocketmatch.PocketMatchComparison;
import totah.lab.athena.pocket.pocketmatch.PocketMatchConfiguration;
import totah.lab.athena.pocket.pocketmatch.PocketMatchSignature;
import totah.lab.web.pocketmatch.PocketMatchSignatureCodec
        .StoredPocketMatchSignature;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;

/**
 * Experimental PocketMatch candidate channel: scores the query pocket
 * against a precomputed binary signature store (produced by
 * {@link PocketMatchBenchmarkRunner}) and returns the highest-scoring
 * pocket ids for union into Stage 1.
 *
 * <p>Disabled by default
 * ({@code pocket.search.pocket-match.enabled=false}); when disabled,
 * or when the store is absent, every call returns an empty list and
 * the production retrieval path is unchanged.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the {@code totah.lab.athena.pocket.pocketmatch} package
 * documentation for the full citation and provenance.</p>
 */
@Component
public class PocketMatchCandidateProvider {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(PocketMatchCandidateProvider.class);

    private final PocketMatchProperties properties;
    private final PocketMatchSignatureLoader signatureLoader;

    public PocketMatchCandidateProvider(
            PocketMatchProperties properties,
            PocketMatchSignatureLoader signatureLoader
    ) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.signatureLoader = Objects.requireNonNull(
                signatureLoader,
                "signatureLoader"
        );
    }

    /**
     * One PocketMatch candidate with its query-coverage score and its
     * 1-based rank in the channel's top-N ordering (ordered by the
     * configured {@code pocket.search.pocket-match.ranking} metric).
     */
    public record PocketMatchCandidate(
            long pocketId,
            double queryCoverage,
            int rank
    ) {
    }

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Returns up to {@code pocket.search.pocket-match.limit} candidates
     * ordered by the configured ranking metric (descending), or an
     * empty list when the channel is disabled or unavailable.
     */
    public List<PocketMatchCandidate> topCandidates(long queryPocketId) {
        if (!properties.isEnabled()) {
            return List.of();
        }

        Path store = Path.of(properties.getSignatureStore());
        if (!Files.exists(store)) {
            LOGGER.warn(
                    "PocketMatch channel enabled but signature store "
                            + "is missing: {}",
                    store.toAbsolutePath()
            );
            return List.of();
        }

        final PocketMatchSignature querySignature;
        try {
            querySignature = signatureLoader.load(queryPocketId);
        } catch (Exception exception) {
            LOGGER.warn(
                    "PocketMatch query signature unavailable for pocket "
                            + "{}: {}",
                    queryPocketId,
                    exception.getMessage()
            );
            return List.of();
        }

        PocketMatchComparator comparator = new DefaultPocketMatchComparator(
                new PocketMatchConfiguration(
                        properties.getDistanceTolerance()
                )
        );

        // lowest ranking score at the head; evicted when better
        // candidates arrive
        PriorityQueue<ScoredPocket> best = new PriorityQueue<>(
                Comparator.comparingDouble(ScoredPocket::rankingScore)
        );

        try (DataInputStream input = new DataInputStream(
                new BufferedInputStream(
                        Files.newInputStream(store),
                        1 << 20
                )
        )) {
            StoredPocketMatchSignature record;
            while ((record = PocketMatchSignatureCodec
                    .readRecord(input)) != null) {
                if (record.pocketId() == queryPocketId) {
                    continue;
                }
                PocketMatchComparison comparison = comparator
                        .compare(querySignature, record.signature());
                double rankingScore = rankingScore(comparison);
                if (best.size() < properties.getLimit()) {
                    best.add(new ScoredPocket(
                            record.pocketId(),
                            comparison.firstCoverage(),
                            rankingScore
                    ));
                } else if (!best.isEmpty()
                        && rankingScore > best.peek().rankingScore()) {
                    best.poll();
                    best.add(new ScoredPocket(
                            record.pocketId(),
                            comparison.firstCoverage(),
                            rankingScore
                    ));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed reading PocketMatch signature store " + store,
                    exception
            );
        }

        List<ScoredPocket> ordered = best.stream()
                .sorted(Comparator.comparingDouble(
                        ScoredPocket::rankingScore).reversed())
                .toList();

        List<PocketMatchCandidate> candidates =
                new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            ScoredPocket scored = ordered.get(index);
            candidates.add(new PocketMatchCandidate(
                    scored.pocketId(),
                    scored.queryCoverage(),
                    index + 1
            ));
        }
        return candidates;
    }

    private double rankingScore(PocketMatchComparison comparison) {
        return switch (properties.getRanking()) {
            case QUERY_COVERAGE -> comparison.firstCoverage();
            case SYMMETRIC_SCORE -> comparison.symmetricScore();
        };
    }

    private record ScoredPocket(
            long pocketId,
            double queryCoverage,
            double rankingScore
    ) {
    }
}
