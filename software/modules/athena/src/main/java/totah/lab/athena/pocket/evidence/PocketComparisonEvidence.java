package totah.lab.athena.pocket.evidence;

import java.util.Objects;

/**
 * The complete, method-neutral evidence bundle of a pairwise pocket
 * comparison: retrieval provenance, both alignment hypotheses,
 * residue-level correspondence, functional conservation, and the
 * assessment derived from them. The bundle deliberately carries no
 * combined score — each dimension stays inspectable on its own.
 *
 * @param retrieval  retrieval provenance of the candidate
 * @param alignment  alignment evidence (both hypotheses preserved)
 * @param residues   residue-level correspondence evidence under the
 *                   selected alignment
 * @param functional functional (key-residue and ligand-contact)
 *                   evidence
 * @param assessment the verdict (classification and reason) derived
 *                   from the evidence by {@link PocketAssessmentRules}
 */
public record PocketComparisonEvidence(
        PocketRetrievalEvidence retrieval,
        PocketAlignmentEvidence alignment,
        PocketResidueEvidence residues,
        PocketFunctionalEvidence functional,
        PocketAssessmentVerdict assessment
) {

    public PocketComparisonEvidence {
        Objects.requireNonNull(retrieval, "retrieval");
        Objects.requireNonNull(alignment, "alignment");
        Objects.requireNonNull(residues, "residues");
        Objects.requireNonNull(functional, "functional");
        Objects.requireNonNull(assessment, "assessment");
    }
}
