package totah.lab.web.service;

import org.springframework.stereotype.Component;
import totah.lab.athena.pocket.compare.PocketAlignmentResult;
import totah.lab.athena.pocket.compare.residue.ResidueSubstitutionScorer;
import totah.lab.athena.pocket.evidence.KeyResidueEvidence;
import totah.lab.athena.pocket.evidence.PocketAlignmentEvidence;
import totah.lab.athena.pocket.evidence.PocketAlignmentEvidenceFactory;
import totah.lab.athena.pocket.evidence.PocketAssessmentRules;
import totah.lab.athena.pocket.evidence.PocketAssessmentVerdict;
import totah.lab.athena.pocket.evidence.PocketComparisonEvidence;
import totah.lab.athena.pocket.evidence.PocketFunctionalEvidence;
import totah.lab.athena.pocket.evidence.PocketResidueEvidence;
import totah.lab.athena.pocket.evidence.PocketResidueEvidenceFactory;
import totah.lab.athena.pocket.evidence.PocketRetrievalEvidence;
import totah.lab.athena.sequence.SequenceAlignment;
import totah.lab.web.persistence.PocketSummaryEntity;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Production assembly of the athena {@link PocketComparisonEvidence}
 * bundle for one Stage 3 candidate of the similarity pipeline: both
 * alignment hypotheses, the residue-level correspondence under the
 * selected alignment and the rules verdict, built from the same
 * {@link PocketAlignmentResult} the ranking already computed —
 * nothing is realigned or rescored here.
 *
 * <p>The Stage 3 assembly is key-residue- and ligand-free: the
 * ranking layer carries neither the configured key residues nor
 * BioHub ligand-contact annotations, so the functional evidence
 * reports an empty key-residue summary and no ligand contacts
 * (absence is reported, never fabricated). The ligand-aware assembly
 * of the pairwise report lives in
 * {@link PocketComparisonReportService}.</p>
 */
@Component
public class PocketComparisonEvidenceAssembler {

    private final PocketAlignmentEvidenceFactory alignmentFactory =
            new PocketAlignmentEvidenceFactory();
    private final PocketResidueEvidenceFactory residueFactory =
            new PocketResidueEvidenceFactory(
                    new ResidueSubstitutionScorer()
            );
    private final PocketAssessmentRules assessmentRules =
            PocketAssessmentRules.defaults();

    /**
     * Assembles the evidence bundle of one compared candidate and
     * derives the rules verdict from it.
     *
     * @param sequenceAlignment the cached protein sequence alignment
     *                          of the receptor pair, or {@code null}
     *                          when no sequence evidence exists
     */
    public PocketComparisonEvidence assemble(
            PocketSummaryEntity querySummary,
            PocketSummaryEntity candidateSummary,
            PocketAlignmentResult alignmentResult,
            SequenceAlignment sequenceAlignment,
            PocketRetrievalEvidence retrieval
    ) {
        Objects.requireNonNull(querySummary, "querySummary");
        Objects.requireNonNull(candidateSummary, "candidateSummary");
        Objects.requireNonNull(alignmentResult, "alignmentResult");
        Objects.requireNonNull(retrieval, "retrieval");

        PocketAlignmentEvidence alignment =
                alignmentFactory.create(alignmentResult);
        PocketResidueEvidence residues = residueFactory.create(
                alignmentResult.correspondence(),
                sequenceAlignment,
                Set.of()
        );
        PocketFunctionalEvidence functional =
                new PocketFunctionalEvidence(
                        Optional.empty(),
                        new KeyResidueEvidence(0, 0, 0, 0)
                );

        PocketAssessmentVerdict verdict = assessmentRules.assess(
                alignment,
                residues,
                functional
        );

        return new PocketComparisonEvidence(
                retrieval,
                alignment,
                residues,
                functional,
                verdict
        );
    }
}
