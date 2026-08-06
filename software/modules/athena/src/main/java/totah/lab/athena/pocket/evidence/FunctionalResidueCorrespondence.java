package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.residue.ResidueReference;

import java.util.Objects;
import java.util.Optional;

/**
 * One query pocket residue in the context of ligand-contact
 * annotation: its spatial correspondence under the selected
 * alignment (empty when the query residue has no match) plus the
 * annotation flags of both sides. All annotation combinations are
 * representable: both annotated, query-only annotated,
 * candidate-only annotated (a matched pair whose candidate side is
 * annotated while the query side is not), and annotated-but-unmatched
 * query residues.
 *
 * @param queryResidue       the query pocket residue
 * @param correspondence     per-pair evidence of the spatial match,
 *                           empty when the query residue is unmatched
 * @param queryAnnotated     whether the query residue is annotated
 *                           as contacting the ligand
 * @param candidateAnnotated whether the candidate residue is
 *                           annotated as contacting the ligand
 *                           (always {@code false} when there is no
 *                           correspondence)
 */
public record FunctionalResidueCorrespondence(
        ResidueReference queryResidue,
        Optional<ResidueCorrespondenceEvidence> correspondence,
        boolean queryAnnotated,
        boolean candidateAnnotated
) {

    public FunctionalResidueCorrespondence {
        Objects.requireNonNull(queryResidue, "queryResidue");
        Objects.requireNonNull(correspondence, "correspondence");

        if (correspondence.isEmpty() && candidateAnnotated) {
            throw new IllegalArgumentException(
                    "An unmatched query residue cannot have an"
                            + " annotated candidate partner"
            );
        }
    }
}
