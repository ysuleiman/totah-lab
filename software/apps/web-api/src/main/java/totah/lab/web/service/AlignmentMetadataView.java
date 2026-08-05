package totah.lab.web.service;

import totah.lab.athena.pocket.compare.PocketAlignmentResult;

/**
 * Alignment metadata of one pairwise pocket comparison: how the
 * selected alignment was initialized and how much of its residue
 * correspondence agrees with the protein sequence alignment. Mirrors
 * the Athena {@code PocketAlignmentResult} accessors one-to-one; no
 * metric is recomputed in web-api.
 */
public record AlignmentMetadataView(
        String initialization,
        int sequenceSeedPairCount,
        int sequenceConsistentCorrespondenceCount,
        double sequenceConsistentCorrespondenceFraction,
        boolean sequenceSeedAvailable,
        boolean sequenceSeedDegenerate
) {

    public static AlignmentMetadataView toView(
            PocketAlignmentResult result
    ) {
        return new AlignmentMetadataView(
                result.initialization().name(),
                result.seedPairCount(),
                result.sequenceConsistentCorrespondenceCount(),
                result.sequenceConsistentCorrespondenceFraction(),
                result.sequenceSeedAvailable(),
                result.sequenceSeedDegenerate()
        );
    }
}
