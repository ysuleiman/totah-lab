package totah.lab.web.pocketmatch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import totah.lab.athena.pocket.pocketmatch.DefaultPocketMatchComparator;
import totah.lab.athena.pocket.pocketmatch.PocketMatchComparator;
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

    public boolean isEnabled() {
        return properties.isEnabled();
    }

    /**
     * Returns up to {@code pocket.search.pocket-match.limit} pocket ids
     * ordered by descending PocketMatch symmetric score, or an empty
     * list when the channel is disabled or unavailable.
     */
    public List<Long> topCandidates(long queryPocketId) {
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

        // lowest score at the head; evicted when better candidates arrive
        PriorityQueue<ScoredPocket> best = new PriorityQueue<>(
                Comparator.comparingDouble(ScoredPocket::score)
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
                double score = comparator
                        .compare(querySignature, record.signature())
                        .symmetricScore();
                if (best.size() < properties.getLimit()) {
                    best.add(new ScoredPocket(record.pocketId(), score));
                } else if (!best.isEmpty()
                        && score > best.peek().score()) {
                    best.poll();
                    best.add(new ScoredPocket(record.pocketId(), score));
                }
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed reading PocketMatch signature store " + store,
                    exception
            );
        }

        return best.stream()
                .sorted(Comparator.comparingDouble(
                        ScoredPocket::score).reversed())
                .map(ScoredPocket::pocketId)
                .toList();
    }

    private record ScoredPocket(long pocketId, double score) {
    }
}
