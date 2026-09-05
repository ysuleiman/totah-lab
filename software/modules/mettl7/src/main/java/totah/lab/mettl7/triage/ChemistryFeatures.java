package totah.lab.mettl7.triage;

import java.util.Objects;

public record ChemistryFeatures(
        String chemistryClass,
        boolean plausibleMethylAcceptor,
        boolean accessibleReactiveSulfur,
        boolean competentSamApproach,
        boolean toleratedTopology,
        boolean arylamine,
        boolean nonproductiveControl) {
    public ChemistryFeatures {
        if (chemistryClass == null || chemistryClass.isBlank()) {
            throw new IllegalArgumentException("chemistryClass must not be blank");
        }
    }
}
