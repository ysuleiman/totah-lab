package totah.lab.athena.pocket.evidence;

import totah.lab.athena.pocket.compare.residue.ResidueReference;

import java.util.Objects;

/**
 * The canonical record of ONE ligand-contact annotation: which pocket
 * the annotation belongs to, which ligand (as a free-form CCD code —
 * a String, not an enum, so any ligand is representable), which
 * residue, how close it comes to the ligand, the contact strength and
 * where the evidence came from.
 *
 * <p>{@link LigandContactStatus#NOT_AVAILABLE} is the explicit marker
 * for absent evidence, created via {@link #notAvailable}: it keeps
 * the pocket reference and the evidence source but carries no
 * residue, distance or contact type — those fields are {@code null}
 * and must never be read as measured values (absence is reported,
 * never fabricated as a zeroed contact).</p>
 *
 * @param status          whether the contact annotation is available
 * @param pocketReference free-form reference of the pocket (or
 *                        structure) the annotation belongs to — a
 *                        pocket id, accession or report label
 * @param ligandCcd       free-form ligand CCD code (for example
 *                        {@code "SAM"}); {@code null} only when no
 *                        ligand is known for a NOT_AVAILABLE record
 * @param residue         the annotated residue; {@code null} when
 *                        NOT_AVAILABLE
 * @param minimumDistance minimum residue-to-ligand distance in
 *                        angstroms; {@code null} when the source does
 *                        not report one (and always when
 *                        NOT_AVAILABLE)
 * @param contactType     contact strength; {@code null} when
 *                        NOT_AVAILABLE
 * @param evidenceSource  where the annotation came from (for example
 *                        {@code "BIOHUB"})
 */
public record LigandContact(
        LigandContactStatus status,
        String pocketReference,
        String ligandCcd,
        ResidueReference residue,
        Double minimumDistance,
        LigandContactType contactType,
        String evidenceSource
) {

    public LigandContact {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(pocketReference, "pocketReference");
        Objects.requireNonNull(evidenceSource, "evidenceSource");

        if (minimumDistance != null
                && (!Double.isFinite(minimumDistance)
                        || minimumDistance < 0.0)) {
            throw new IllegalArgumentException(
                    "minimumDistance must be finite and non-negative"
            );
        }

        if (status == LigandContactStatus.AVAILABLE) {
            Objects.requireNonNull(ligandCcd, "ligandCcd");
            Objects.requireNonNull(residue, "residue");
            Objects.requireNonNull(contactType, "contactType");
        }
    }

    /**
     * An available contact annotation. {@code minimumDistance} may be
     * {@code null} when the evidence source does not report one.
     */
    public static LigandContact available(
            String pocketReference,
            String ligandCcd,
            ResidueReference residue,
            Double minimumDistance,
            LigandContactType contactType,
            String evidenceSource
    ) {
        return new LigandContact(
                LigandContactStatus.AVAILABLE,
                pocketReference,
                ligandCcd,
                residue,
                minimumDistance,
                contactType,
                evidenceSource
        );
    }

    /**
     * The explicit NOT_AVAILABLE marker: no contact evidence exists
     * for the pocket (and ligand, when known). {@code ligandCcd} may
     * be {@code null} when no ligand is known; residue, distance and
     * contact type are always {@code null}.
     */
    public static LigandContact notAvailable(
            String pocketReference,
            String ligandCcd,
            String evidenceSource
    ) {
        return new LigandContact(
                LigandContactStatus.NOT_AVAILABLE,
                pocketReference,
                ligandCcd,
                null,
                null,
                null,
                evidenceSource
        );
    }
}
