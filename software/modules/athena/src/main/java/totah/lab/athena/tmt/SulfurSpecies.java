package totah.lab.athena.tmt;

/** Chemically distinct sulfur states; these are not interchangeable metadata labels. */
public enum SulfurSpecies {
    RSH(0, 1),
    RS_MINUS(-1, 0),
    H2S(0, 2),
    HS_MINUS(-1, 1);

    private final int formalCharge;
    private final int sulfurHydrogenCount;

    SulfurSpecies(int formalCharge, int sulfurHydrogenCount) {
        this.formalCharge = formalCharge;
        this.sulfurHydrogenCount = sulfurHydrogenCount;
    }

    public int formalCharge() {
        return formalCharge;
    }

    public int sulfurHydrogenCount() {
        return sulfurHydrogenCount;
    }
}
