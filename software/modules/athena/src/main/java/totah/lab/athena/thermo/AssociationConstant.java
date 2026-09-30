package totah.lab.athena.thermo;

/** Association constant represented explicitly in L/mol. */
public record AssociationConstant(double litresPerMole) {
    public AssociationConstant {
        if (!Double.isFinite(litresPerMole) || litresPerMole <= 0.0) {
            throw new IllegalArgumentException("Ka must be finite and positive");
        }
    }

    public DissociationConstant dissociationConstant() {
        return new DissociationConstant(1.0 / litresPerMole);
    }
}
