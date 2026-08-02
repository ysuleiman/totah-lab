package totah.lab.gaia.classification;

import java.util.Objects;

public record ResidueClassificationEvidence(
        ResidueClassification classification,
        ClassificationSource source,
        String reason) {

    public ResidueClassificationEvidence {
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(source, "source");

        reason = reason == null
                ? ""
                : reason.trim();
    }

    public static ResidueClassificationEvidence of(
            ResidueClassification classification,
            ClassificationSource source) {

        return new ResidueClassificationEvidence(
                classification,
                source,
                "");
    }

    public static ResidueClassificationEvidence of(
            ResidueClassification classification,
            ClassificationSource source,
            String reason) {

        return new ResidueClassificationEvidence(
                classification,
                source,
                reason);
    }
}