package totah.lab.athena.thermo;

/** Dissociation constant represented explicitly in mol/L. */
public record DissociationConstant(double molesPerLitre) {
    public DissociationConstant {
        if (!Double.isFinite(molesPerLitre) || molesPerLitre <= 0.0) {
            throw new IllegalArgumentException("Kd must be finite and positive");
        }
    }

    public AssociationConstant associationConstant() {
        return new AssociationConstant(1.0 / molesPerLitre);
    }
}
