package totah.lab.web.service;

/**
 * Web view of a single matched residue pair, mirroring Athena's
 * {@code ResidueMatch}.
 */
public record ResidueMatchView(
        ResiduePointView query,
        ResiduePointView candidate,
        double distanceAngstroms,
        String matchType,
        boolean identicalResidue,
        boolean chemistryCompatible
) {
}
