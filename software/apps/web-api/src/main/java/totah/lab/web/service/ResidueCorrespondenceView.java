package totah.lab.web.service;

import java.util.List;

/**
 * Web view of an aligned pocket-residue correspondence, mirroring
 * Athena's {@code ResidueCorrespondence} without exposing Athena
 * types in the JSON payload.
 */
public record ResidueCorrespondenceView(
        List<ResidueMatchView> matches,
        List<ResiduePointView> unmatchedQuery,
        List<ResiduePointView> unmatchedCandidate,
        ResidueSummaryView summary
) {
}
