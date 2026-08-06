package totah.lab.web.service;

import totah.lab.athena.pocket.evidence.AlignmentHypothesisEvidence;
import totah.lab.athena.pocket.evidence.GlobalShapeRetrievalEvidence;
import totah.lab.athena.pocket.evidence.KeyResidueEvidence;
import totah.lab.athena.pocket.evidence.LigandContact;
import totah.lab.athena.pocket.evidence.LigandContactEvidence;
import totah.lab.athena.pocket.evidence.LigandContactStatus;
import totah.lab.athena.pocket.evidence.PocketCandidateSource;
import totah.lab.athena.pocket.evidence.PocketComparisonEvidence;
import totah.lab.athena.pocket.evidence.PocketMatchRetrievalEvidence;
import totah.lab.athena.pocket.evidence.PocketResidueEvidence;
import totah.lab.athena.pocket.evidence.PocketRetrievalEvidence;
import totah.lab.athena.pocket.evidence.ResidueCorrespondenceEvidence;
import totah.lab.athena.pocket.compare.residue.ResidueReference;

import java.util.List;

/**
 * Serializable structured report of one pairwise pocket comparison:
 * the seven sections (retrieval, alignment, residue comparison,
 * chemistry comparison, key-residue comparison, ligand-contact
 * conservation, interpretation) mapped one-to-one from the athena
 * {@link PocketComparisonEvidence} bundle assembled on the live
 * comparison path. No metric is recomputed in web-api.
 *
 * <p>Every section preserves the evidence dimensions separately; the
 * interpretation section is the only derived content (the rules
 * verdict plus its reason). Sections whose evidence does not exist
 * report it explicitly — the ligand-contact section renders
 * {@code NOT_AVAILABLE} with {@code null} metrics, never zeroed
 * counts.</p>
 */
public record PocketComparisonReportView(
        long queryPocketId,
        long candidatePocketId,
        RetrievalSection retrieval,
        AlignmentSection alignment,
        ResidueComparisonSection residueComparison,
        ChemistryAssessmentView chemistryComparison,
        KeyResidueSection keyResidueComparison,
        LigandContactSection ligandContactConservation,
        InterpretationSection interpretation
) {

    /**
     * Retrieval provenance of the candidate. A direct pairwise report
     * does not pass through the retrieval stages, so both retrieval
     * methods report {@code evaluated = false} and carry no ranks or
     * distances; only the chosen-reference flag is known.
     */
    public record RetrievalSection(
            boolean chosenReference,
            List<String> candidateSources,
            boolean globalShapeEvaluated,
            Integer globalShapeRank,
            Double globalShapeDistance,
            boolean pocketMatchEvaluated,
            Integer pocketMatchSymmetricRank,
            Integer pocketMatchQueryCoverageRank,
            Double pocketMatchSymmetricScore,
            Double pocketMatchQueryCoverage,
            Double pocketMatchCandidateCoverage
    ) {
    }

    /**
     * Alignment evidence: which initialization was selected and why,
     * the sequence-seed diagnostics, and BOTH hypotheses with their
     * real metrics (the losing hypothesis stays inspectable).
     */
    public record AlignmentSection(
            String selectedInitialization,
            String selectionReason,
            int sequenceSeedPairCount,
            int sequenceConsistentCorrespondenceCount,
            double sequenceConsistentCorrespondenceFraction,
            boolean sequenceSeedAvailable,
            boolean sequenceSeedDegenerate,
            HypothesisView pcaIcp,
            HypothesisView sequenceSeeded
    ) {
    }

    /**
     * The preserved metrics of one alignment hypothesis. A hypothesis
     * that was never computed reports {@code available = false} and
     * zeroed metrics, which must not be read as measured values.
     */
    public record HypothesisView(
            boolean available,
            boolean accepted,
            double geometrySimilarity,
            double forwardCoverage,
            double reverseCoverage,
            double forwardMeanDistance,
            double reverseMeanDistance,
            double bidirectionalDistance,
            double maximumNearestNeighborDistance,
            int sequenceConsistentPairCount,
            int residueCorrespondenceCount
    ) {

        static HypothesisView toView(AlignmentHypothesisEvidence evidence) {
            return new HypothesisView(
                    evidence.available(),
                    evidence.accepted(),
                    evidence.geometrySimilarity(),
                    evidence.forwardCoverage(),
                    evidence.reverseCoverage(),
                    evidence.forwardMeanDistance(),
                    evidence.reverseMeanDistance(),
                    evidence.bidirectionalDistance(),
                    evidence.maximumNearestNeighborDistance(),
                    evidence.sequenceConsistentPairCount(),
                    evidence.residueCorrespondenceCount()
            );
        }
    }

    /**
     * Residue-level comparison under the SELECTED alignment: the
     * distinct identity/substitution/chemistry/coverage/sequence
     * aggregates plus one entry per matched pair.
     */
    public record ResidueComparisonSection(
            int queryResidueCount,
            int candidateResidueCount,
            int matchedResidueCount,
            int unmatchedQueryResidueCount,
            int unmatchedCandidateResidueCount,
            int identicalCount,
            int conservativeSubstitutionCount,
            int chemistryCompatibleCount,
            int incompatibleReplacementCount,
            double identityFraction,
            double substitutionSimilarity,
            double chemistrySimilarity,
            double compatibleMatchedFraction,
            double replacementFraction,
            double queryResidueCoverage,
            double candidateResidueCoverage,
            int sequenceConsistentPairCount,
            double sequenceConsistentFraction,
            List<ResiduePairView> correspondences
    ) {
    }

    /**
     * One matched residue pair of the residue comparison.
     */
    public record ResiduePairView(
            String queryChainId,
            int queryResidueNumber,
            String queryInsertionCode,
            String queryResidueName,
            String candidateChainId,
            int candidateResidueNumber,
            String candidateInsertionCode,
            String candidateResidueName,
            double distanceAngstroms,
            boolean sequenceAlignedPair,
            boolean identical,
            boolean conservativeSubstitution,
            String matchType,
            double chemistryScore,
            double substitutionScore,
            boolean queryKeyResidue,
            boolean queryLigandContact,
            boolean candidateLigandContact
    ) {

        static ResiduePairView toView(ResidueCorrespondenceEvidence pair) {
            return new ResiduePairView(
                    pair.queryResidue().chainId(),
                    pair.queryResidue().residueNumber(),
                    insertionCode(pair.queryResidue()),
                    pair.queryAminoAcid(),
                    pair.candidateResidue().chainId(),
                    pair.candidateResidue().residueNumber(),
                    insertionCode(pair.candidateResidue()),
                    pair.candidateAminoAcid(),
                    pair.distanceAngstroms(),
                    pair.sequenceAlignedPair(),
                    pair.identical(),
                    pair.conservativeSubstitution(),
                    pair.matchType().name(),
                    pair.chemistryScore(),
                    pair.substitutionScore(),
                    pair.queryKeyResidue(),
                    pair.querySamContact(),
                    pair.candidateSamContact()
            );
        }

        private static String insertionCode(ResidueReference reference) {
            return String.valueOf(reference.insertionCode()).trim();
        }
    }

    /**
     * Key-residue comparison: the configured key residues of the
     * query and how they fared under the selected alignment.
     */
    public record KeyResidueSection(
            List<String> configuredKeyResidues,
            int totalKeyResidueCount,
            int matchedKeyResidueCount,
            int identicalKeyResidueCount,
            int chemistryCompatibleKeyResidueCount
    ) {
    }

    /**
     * Ligand-contact conservation. When no ligand-contact evidence
     * exists for the structure pair, {@code status} is
     * {@code NOT_AVAILABLE} and every metric is {@code null} — the
     * absence is reported, never rendered as zeroed counts. When
     * available, the aggregates come from the athena
     * {@link LigandContactEvidence} and {@code contacts} carries the
     * canonical per-residue contact records of both pockets.
     */
    public record LigandContactSection(
            String status,
            String ligandCcd,
            String evidenceSource,
            Integer queryContactResidueCount,
            Integer matchedQueryContactResidueCount,
            Integer identicalContactCount,
            Integer conservativeContactCount,
            Integer chemistryCompatibleContactCount,
            Integer incompatibleContactCount,
            Integer unmatchedContactCount,
            Integer sharedContactAnnotationCount,
            Double contactCoverage,
            Double contactIdentityFraction,
            Double contactSubstitutionSimilarity,
            Double contactChemistrySimilarity,
            List<LigandContact> contacts
    ) {

        /**
         * The section of a comparison without ligand-contact
         * evidence: NOT_AVAILABLE, all metrics {@code null}.
         */
        static LigandContactSection notAvailable() {
            return new LigandContactSection(
                    LigandContactStatus.NOT_AVAILABLE.name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    List.of()
            );
        }

        static LigandContactSection toView(
                LigandContactEvidence evidence,
                String evidenceSource,
                List<LigandContact> contacts
        ) {
            return new LigandContactSection(
                    LigandContactStatus.AVAILABLE.name(),
                    evidence.ligandName(),
                    evidenceSource,
                    evidence.queryContactResidueCount(),
                    evidence.matchedQueryContactResidueCount(),
                    evidence.identicalContactCount(),
                    evidence.conservativeContactCount(),
                    evidence.chemistryCompatibleContactCount(),
                    evidence.incompatibleContactCount(),
                    evidence.unmatchedContactCount(),
                    evidence.sharedContactAnnotationCount(),
                    evidence.contactCoverage(),
                    evidence.contactIdentityFraction(),
                    evidence.contactSubstitutionSimilarity(),
                    evidence.contactChemistrySimilarity(),
                    contacts
            );
        }
    }

    /**
     * The interpretation of the evidence: the rules verdict and the
     * reason the matched rule fired.
     */
    public record InterpretationSection(
            String verdict,
            String reason
    ) {
    }

    static PocketComparisonReportView toView(
            long queryPocketId,
            long candidatePocketId,
            PocketComparisonEvidence evidence,
            AlignmentMetadataView alignmentMetadata,
            ChemistryAssessmentView chemistryComparison,
            List<String> configuredKeyResidues,
            String ligandEvidenceSource,
            List<LigandContact> ligandContacts
    ) {
        PocketRetrievalEvidence retrieval = evidence.retrieval();
        PocketResidueEvidence residues = evidence.residues();
        KeyResidueEvidence keyResidues =
                evidence.functional().keyResidues();

        return new PocketComparisonReportView(
                queryPocketId,
                candidatePocketId,
                retrievalSection(retrieval),
                new AlignmentSection(
                        evidence.alignment().selectedInitialization()
                                .name(),
                        evidence.alignment().selectionReason(),
                        alignmentMetadata.sequenceSeedPairCount(),
                        alignmentMetadata
                                .sequenceConsistentCorrespondenceCount(),
                        alignmentMetadata
                                .sequenceConsistentCorrespondenceFraction(),
                        alignmentMetadata.sequenceSeedAvailable(),
                        alignmentMetadata.sequenceSeedDegenerate(),
                        HypothesisView.toView(
                                evidence.alignment().pcaIcp()
                        ),
                        HypothesisView.toView(
                                evidence.alignment().sequenceSeeded()
                        )
                ),
                new ResidueComparisonSection(
                        residues.queryResidueCount(),
                        residues.candidateResidueCount(),
                        residues.matchedResidueCount(),
                        residues.unmatchedQueryResidueCount(),
                        residues.unmatchedCandidateResidueCount(),
                        residues.identicalCount(),
                        residues.conservativeSubstitutionCount(),
                        residues.chemistryCompatibleCount(),
                        residues.incompatibleReplacementCount(),
                        residues.identityFraction(),
                        residues.substitutionSimilarity(),
                        residues.chemistrySimilarity(),
                        residues.compatibleMatchedFraction(),
                        residues.replacementFraction(),
                        residues.queryResidueCoverage(),
                        residues.candidateResidueCoverage(),
                        residues.sequenceConsistentPairCount(),
                        residues.sequenceConsistentFraction(),
                        residues.correspondences().stream()
                                .map(ResiduePairView::toView)
                                .toList()
                ),
                chemistryComparison,
                new KeyResidueSection(
                        List.copyOf(configuredKeyResidues),
                        keyResidues.totalKeyResidueCount(),
                        keyResidues.matchedKeyResidueCount(),
                        keyResidues.identicalKeyResidueCount(),
                        keyResidues.chemistryCompatibleKeyResidueCount()
                ),
                evidence.functional().ligandContacts()
                        .map(contacts -> LigandContactSection.toView(
                                contacts,
                                ligandEvidenceSource,
                                ligandContacts
                        ))
                        .orElseGet(LigandContactSection::notAvailable),
                new InterpretationSection(
                        evidence.assessment().verdict().name(),
                        evidence.assessment().reason()
                )
        );
    }

    private static RetrievalSection retrievalSection(
            PocketRetrievalEvidence retrieval
    ) {
        GlobalShapeRetrievalEvidence globalShape = retrieval.globalShape();
        PocketMatchRetrievalEvidence pocketMatch = retrieval.pocketMatch();

        return new RetrievalSection(
                retrieval.chosenReference(),
                retrieval.candidateSources().stream()
                        .map(PocketCandidateSource::name)
                        .sorted()
                        .toList(),
                globalShape.evaluated(),
                globalShape.rank().isPresent()
                        ? globalShape.rank().orElseThrow()
                        : null,
                globalShape.distance().isPresent()
                        ? globalShape.distance().orElseThrow()
                        : null,
                pocketMatch.evaluated(),
                pocketMatch.symmetricRank().isPresent()
                        ? pocketMatch.symmetricRank().orElseThrow()
                        : null,
                pocketMatch.queryCoverageRank().isPresent()
                        ? pocketMatch.queryCoverageRank().orElseThrow()
                        : null,
                pocketMatch.symmetricScore().isPresent()
                        ? pocketMatch.symmetricScore().orElseThrow()
                        : null,
                pocketMatch.queryCoverage().isPresent()
                        ? pocketMatch.queryCoverage().orElseThrow()
                        : null,
                pocketMatch.candidateCoverage().isPresent()
                        ? pocketMatch.candidateCoverage().orElseThrow()
                        : null
        );
    }
}
