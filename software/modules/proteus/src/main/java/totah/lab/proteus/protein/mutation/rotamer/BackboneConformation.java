package totah.lab.proteus.protein.mutation.rotamer;

public record BackboneConformation(double phiRadians, double psiRadians) {
    public BackboneConformation {
        if (!Double.isFinite(phiRadians) || !Double.isFinite(psiRadians)) {
            throw new IllegalArgumentException("Backbone angles must be finite");
        }
    }
}
