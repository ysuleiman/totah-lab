package totah.lab.web.service;

import totah.lab.athena.pocket.compare.residue.PocketResiduePoint;
import totah.lab.athena.pocket.compare.residue.ResidueCorrespondence;
import totah.lab.athena.pocket.compare.residue.ResidueMatch;
import totah.lab.athena.pocket.compare.residue.ResidueReference;

import java.util.List;

/**
 * Maps Athena residue-correspondence results to the web view records,
 * keeping Athena types out of the JSON payload.
 */
final class ResidueCorrespondenceViewMapper {

    private static final char BLANK_INSERTION_CODE = ' ';

    private ResidueCorrespondenceViewMapper() {
    }

    static ResidueCorrespondenceView toView(
            ResidueCorrespondence correspondence
    ) {
        return new ResidueCorrespondenceView(
                correspondence.matches()
                        .stream()
                        .map(ResidueCorrespondenceViewMapper::toView)
                        .toList(),
                toPointViews(correspondence.unmatchedQuery()),
                toPointViews(correspondence.unmatchedCandidate()),
                new ResidueSummaryView(
                        correspondence.matches().size()
                                + correspondence.unmatchedQuery().size(),
                        correspondence.matches().size()
                                + correspondence.unmatchedCandidate()
                                        .size(),
                        correspondence.matches().size(),
                        correspondence.unmatchedQuery().size(),
                        correspondence.unmatchedCandidate().size(),
                        correspondence.matchedFractionQuery(),
                        correspondence.matchedFractionCandidate(),
                        correspondence.identicalFraction(),
                        correspondence.chemistryCompatibleFraction(),
                        correspondence.meanMatchedDistance(),
                        correspondence.maximumMatchedDistance()
                )
        );
    }

    private static ResidueMatchView toView(ResidueMatch match) {
        return new ResidueMatchView(
                toView(match.query()),
                toView(match.candidate()),
                match.distanceAngstroms(),
                match.matchType().name(),
                match.identicalResidue(),
                match.chemistryCompatible()
        );
    }

    private static List<ResiduePointView> toPointViews(
            List<PocketResiduePoint> points
    ) {
        return points
                .stream()
                .map(ResidueCorrespondenceViewMapper::toView)
                .toList();
    }

    private static ResiduePointView toView(PocketResiduePoint point) {
        ResidueReference reference = point.reference();

        String insertionCode =
                reference.insertionCode() == BLANK_INSERTION_CODE
                        ? ""
                        : String.valueOf(reference.insertionCode());

        String label = reference.chainId()
                + ":"
                + reference.residueName()
                + reference.residueNumber()
                + insertionCode;

        return new ResiduePointView(
                reference.chainId(),
                reference.residueNumber(),
                insertionCode,
                reference.residueName(),
                label,
                point.chemistry().name(),
                point.position()
        );
    }
}
