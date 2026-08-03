package totah.lab.gaia.structure;

/** Provenance for a representative atom selected from source alternate locations. */
public record AlternateLocationProvenance(
        char selectedAlternateLocation,
        boolean alternativesPresent) {

    public static final AlternateLocationProvenance NONE =
            new AlternateLocationProvenance(' ', false);

    public AlternateLocationProvenance {
        selectedAlternateLocation =
                AtomReference.normalizeInsertionCode(selectedAlternateLocation);
    }
}
