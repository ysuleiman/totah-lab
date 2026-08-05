package totah.lab.athena.pocket.pocketmatch;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

/**
 * Convenience façade over signature construction and comparison:
 * describes both pockets and compares their signatures in one call.
 *
 * <p>Use this when signatures are needed only transiently. Callers
 * that compare one query against many candidates should instead build
 * the query signature once through {@link PocketMatchSignatureFactory}
 * and reuse it with {@link PocketMatchComparator} directly.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and
 * provenance.</p>
 */
public final class PocketMatch {

    private final PocketMatchSignatureFactory signatureFactory;
    private final PocketMatchComparator comparator;

    public PocketMatch() {
        this(PocketMatchConfiguration.defaults());
    }

    public PocketMatch(PocketMatchConfiguration configuration) {
        this(
                new DefaultPocketMatchSignatureFactory(),
                new DefaultPocketMatchComparator(configuration)
        );
    }

    public PocketMatch(
            PocketMatchSignatureFactory signatureFactory,
            PocketMatchComparator comparator
    ) {
        this.signatureFactory = Objects.requireNonNull(
                signatureFactory,
                "signatureFactory"
        );
        this.comparator = Objects.requireNonNull(
                comparator,
                "comparator"
        );
    }

    public PocketMatchComparison compare(
            Structure firstStructure,
            Pocket firstPocket,
            Structure secondStructure,
            Pocket secondPocket
    ) {
        return comparator.compare(
                signatureFactory.describe(firstStructure, firstPocket),
                signatureFactory.describe(secondStructure, secondPocket)
        );
    }
}
