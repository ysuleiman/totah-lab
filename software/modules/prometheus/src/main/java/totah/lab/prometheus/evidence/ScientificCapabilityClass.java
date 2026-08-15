package totah.lab.prometheus.evidence;

/** Scientific boundary for generated numerical evidence. */
public enum ScientificCapabilityClass {
    AB_INITIO(true),
    REFERENCE_ASSISTED_DIAGNOSTIC(false),
    SURROGATE(false);

    private final boolean productionAbInitioEvidenceEligible;

    ScientificCapabilityClass(boolean productionAbInitioEvidenceEligible) {
        this.productionAbInitioEvidenceEligible = productionAbInitioEvidenceEligible;
    }

    public boolean productionAbInitioEvidenceEligible() {
        return productionAbInitioEvidenceEligible;
    }
}
