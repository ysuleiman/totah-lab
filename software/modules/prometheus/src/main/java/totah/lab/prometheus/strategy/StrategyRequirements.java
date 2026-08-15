package totah.lab.prometheus.strategy;

final class StrategyRequirements {
    private StrategyRequirements() {}

    static ScientificRequirementDescriptor development(
            ScientificEvidenceKind kind, String constraint, boolean exact, boolean derivable, String purpose) {
        return new ScientificRequirementDescriptor(kind, constraint, exact, derivable, false, false, purpose);
    }

    static ScientificRequirementDescriptor optional(
            ScientificEvidenceKind kind, String constraint, boolean exact, boolean derivable, String purpose) {
        return new ScientificRequirementDescriptor(kind, constraint, exact, derivable, false, true, purpose);
    }

    static ScientificRequirementDescriptor holdout(
            ScientificEvidenceKind kind, String constraint, boolean exact, boolean derivable, String purpose) {
        return new ScientificRequirementDescriptor(kind, constraint, exact, derivable, true, false, purpose);
    }
}
