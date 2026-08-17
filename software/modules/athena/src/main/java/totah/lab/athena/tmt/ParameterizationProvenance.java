package totah.lab.athena.tmt;

import java.util.Objects;

/** Provenance for one explicit nonstandard chemical-state parameter set. */
public record ParameterizationProvenance(
        String chemicalIdentity,
        String protonationState,
        String sourceGeometry,
        int formalCharge,
        String chargeMethod,
        String atomTypeMethod,
        String bondedParameterSource,
        String torsionSource,
        ParameterValidationStatus validationStatus,
        String sha256) {

    public ParameterizationProvenance {
        chemicalIdentity = requireText(chemicalIdentity, "chemicalIdentity");
        protonationState = requireText(protonationState, "protonationState");
        sourceGeometry = requireText(sourceGeometry, "sourceGeometry");
        chargeMethod = requireText(chargeMethod, "chargeMethod");
        atomTypeMethod = requireText(atomTypeMethod, "atomTypeMethod");
        bondedParameterSource = requireText(bondedParameterSource, "bondedParameterSource");
        torsionSource = requireText(torsionSource, "torsionSource");
        validationStatus = Objects.requireNonNull(validationStatus, "validationStatus");
        sha256 = requireText(sha256, "sha256");
        if (!sha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("sha256 must contain 64 lowercase hexadecimal characters");
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
