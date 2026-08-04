package totah.lab.athena.pocket.compare;

import totah.lab.athena.pocket.align.PrincipalAxisPocketAligner;
import totah.lab.athena.pocket.geometry.PocketPointCloud;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.Objects;

/**
 * Performs coarse principal-axis alignment followed by iterative
 * closest-point refinement.
 *
 * <p>The returned transform maps the original candidate pocket directly into
 * the original query coordinate system.</p>
 */
public final class CompositePocketAligner implements PocketAligner {

    private final PocketAligner initialAligner;
    private final PocketAligner refinementAligner;

    /**
     * Creates the standard pocket-alignment pipeline:
     *
     * <pre>
     * principal-axis alignment -> ICP refinement
     * </pre>
     */
    public CompositePocketAligner() {
        this(
                new PrincipalAxisPocketAligner(),
                new IcpPocketAligner()
        );
    }

    public CompositePocketAligner(
            PocketAligner initialAligner,
            PocketAligner refinementAligner
    ) {
        this.initialAligner = Objects.requireNonNull(
                initialAligner,
                "initialAligner"
        );

        this.refinementAligner = Objects.requireNonNull(
                refinementAligner,
                "refinementAligner"
        );
    }

    @Override
    public PocketAlignment align(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(candidate, "candidate");

        requireSameBasis(query, candidate);

        /*
         * First transform:
         *
         * original candidate -> coarsely aligned candidate
         */
        PocketAlignment initialAlignment =
                initialAligner.align(query, candidate);

        /*
         * Second transform:
         *
         * coarsely aligned candidate -> ICP-refined candidate
         */
        PocketAlignment refinedAlignment =
                refinementAligner.align(
                        query,
                        initialAlignment.alignedCandidate()
                );

        /*
         * Compose:
         *
         * original candidate
         *     -> initial alignment
         *     -> refinement alignment
         */
        RigidTransform combinedTransform =
                initialAlignment.transform().andThen(
                        refinedAlignment.transform()
                );

        /*
         * Reapply the combined transform to the original candidate. This
         * ensures the returned aligned cloud and returned transform remain
         * numerically consistent.
         */
        PocketPointCloud alignedCandidate =
                new PocketPointCloud(
                        combinedTransform.apply(
                                candidate.points()
                        ),
                        candidate.basis()
                );

        return new PocketAlignment(
                query,
                alignedCandidate,
                combinedTransform,
                refinedAlignment.rmsd(),
                initialAlignment.iterations()
                        + refinedAlignment.iterations(),
                initialAlignment.converged()
                        && refinedAlignment.converged()
        );
    }

    private static void requireSameBasis(
            PocketPointCloud query,
            PocketPointCloud candidate
    ) {
        if (query.basis() != candidate.basis()) {
            throw new IllegalArgumentException(
                    "Cannot align pockets represented by different bases: "
                            + query.basis()
                            + " vs "
                            + candidate.basis()
            );
        }
    }
}