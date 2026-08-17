package totah.lab.athena.tmt;

/** Fail-closed regression evidence for a configured sulfonium center. */
public record SulfoniumStereochemistryAssessment(
        double initialSignedVolume,
        double finalSignedVolume,
        double minimumAbsoluteVolume,
        boolean preserved,
        String reason) {

    public SulfoniumStereochemistryAssessment {
        if (!Double.isFinite(initialSignedVolume) || !Double.isFinite(finalSignedVolume)) {
            throw new IllegalArgumentException("signed volumes must be finite");
        }
        if (!Double.isFinite(minimumAbsoluteVolume) || minimumAbsoluteVolume <= 0.0) {
            throw new IllegalArgumentException("minimumAbsoluteVolume must be positive and finite");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static SulfoniumStereochemistryAssessment assess(
            double initialSignedVolume, double finalSignedVolume, double minimumAbsoluteVolume) {
        boolean resolved = Math.abs(initialSignedVolume) >= minimumAbsoluteVolume
                && Math.abs(finalSignedVolume) >= minimumAbsoluteVolume;
        boolean sameHandedness = Math.signum(initialSignedVolume) == Math.signum(finalSignedVolume);
        boolean preserved = resolved && sameHandedness;
        String reason = !resolved
                ? "UNRESOLVED_SULFONIUM_GEOMETRY"
                : sameHandedness ? "CONFIGURATION_PRESERVED" : "CONFIGURATION_INVERTED";
        return new SulfoniumStereochemistryAssessment(
                initialSignedVolume, finalSignedVolume, minimumAbsoluteVolume, preserved, reason);
    }
}
