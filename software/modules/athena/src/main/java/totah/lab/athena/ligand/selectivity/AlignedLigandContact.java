package totah.lab.athena.ligand.selectivity;

import totah.lab.athena.ligand.contact.ContactType;
import totah.lab.gaia.classification.ResidueCategory;
import totah.lab.gaia.structure.ResidueId;

import java.util.Objects;
import java.util.Set;

/**
 * One row of the aligned differential-contact table: the residues of
 * receptors A and B at one alignment position together with their
 * ligand-contact status, distances, contact types, pocket membership
 * and chemistry deltas.
 *
 * <p>Correspondence comes from the protein sequence alignment, never
 * from raw residue numbers. On {@link DifferentialContactType#UNMAPPED}
 * rows only one side is present: the absent side has a {@code null}
 * name, id, distance, contact type, pocket membership and substitution
 * class, and an empty chemistry set.
 *
 * <p>{@code pocketMemberA}/{@code pocketMemberB} are {@code null} when
 * no pocket was supplied for that side. {@code structurallyEquivalent}
 * is {@code null} unless both pockets were supplied; it then records
 * whether both residues are members of their respective pocket at this
 * aligned position (pocket-wall co-membership — not a superposition
 * claim).
 */
public record AlignedLigandContact(
        int alignmentPosition,
        String residueA,
        String residueB,
        ResidueId residueAId,
        ResidueId residueBId,
        boolean contactA,
        boolean contactB,
        Double minDistanceA,
        Double minDistanceB,
        ContactType contactTypeA,
        ContactType contactTypeB,
        Boolean pocketMemberA,
        Boolean pocketMemberB,
        Set<ResidueCategory> chemistryA,
        Set<ResidueCategory> chemistryB,
        SubstitutionClass substitutionClass,
        boolean conservative,
        Boolean structurallyEquivalent,
        DifferentialContactType differentialType
) {

    public AlignedLigandContact {
        if (alignmentPosition < 1) {
            throw new IllegalArgumentException(
                    "alignmentPosition must be positive"
            );
        }

        Objects.requireNonNull(differentialType, "differentialType");

        if (residueA == null && residueB == null) {
            throw new IllegalArgumentException(
                    "A row must carry at least one residue"
            );
        }

        if (differentialType == DifferentialContactType.UNMAPPED) {
            if (residueA != null && residueB != null) {
                throw new IllegalArgumentException(
                        "UNMAPPED rows must carry exactly one residue"
                );
            }
        } else {
            Objects.requireNonNull(residueA, "residueA");
            Objects.requireNonNull(residueB, "residueB");
            Objects.requireNonNull(
                    substitutionClass,
                    "substitutionClass"
            );
        }

        chemistryA = chemistryA == null
                ? Set.of()
                : Set.copyOf(chemistryA);
        chemistryB = chemistryB == null
                ? Set.of()
                : Set.copyOf(chemistryB);
    }
}
