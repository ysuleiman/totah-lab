package totah.lab.daedalus.docking.sequential;

import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.model.PreparedProtein;

import java.nio.file.Path;
import java.util.Objects;

/** Inputs for docking a cofactor and then retaining one pose as receptor. */
public record FixedCofactorDockingRequest(
        String runId,
        PreparedProtein preparedProtein,
        PreparedLigand preparedCofactor,
        String cofactorId,
        String componentCode,
        int cofactorModelNumber,
        Path proteinPdbqt,
        Path cofactorPdbqt,
        Path ligandPdbqt,
        VinaDockingOptions cofactorDockingOptions,
        VinaDockingOptions ligandDockingOptions,
        FixedCofactorDockingArtifacts artifacts) {

    public FixedCofactorDockingRequest {
        runId = requireNonBlank(runId, "runId");
        Objects.requireNonNull(preparedProtein, "preparedProtein");
        Objects.requireNonNull(preparedCofactor, "preparedCofactor");
        cofactorId = requireNonBlank(cofactorId, "cofactorId");
        componentCode = requireNonBlank(componentCode, "componentCode");
        if (cofactorModelNumber < 1) {
            throw new IllegalArgumentException(
                    "cofactorModelNumber must be positive");
        }
        proteinPdbqt = normalize(proteinPdbqt, "proteinPdbqt");
        cofactorPdbqt = normalize(cofactorPdbqt, "cofactorPdbqt");
        ligandPdbqt = normalize(ligandPdbqt, "ligandPdbqt");
        Objects.requireNonNull(cofactorDockingOptions, "cofactorDockingOptions");
        Objects.requireNonNull(ligandDockingOptions, "ligandDockingOptions");
        Objects.requireNonNull(artifacts, "artifacts");
    }

    private static String requireNonBlank(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private static Path normalize(Path path, String fieldName) {
        return Objects.requireNonNull(path, fieldName)
                .toAbsolutePath().normalize();
    }
}
