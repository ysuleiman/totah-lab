package totah.lab.athena.pocket.pocketmatch;

import java.util.List;

/**
 * Representative points extracted from one pocket together with the
 * per-residue accounting used for signature diagnostics.
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the package documentation for the full citation and provenance.</p>
 */
public record PocketMatchPointExtraction(
        List<PocketMatchPoint> points,
        int inputResidueCount,
        int representedResidueCount,
        int skippedResidueCount
) {

    public PocketMatchPointExtraction {
        points = List.copyOf(points);
        if (inputResidueCount < 0
                || representedResidueCount < 0
                || skippedResidueCount < 0) {
            throw new IllegalArgumentException(
                    "residue counts must be non-negative"
            );
        }
    }
}
