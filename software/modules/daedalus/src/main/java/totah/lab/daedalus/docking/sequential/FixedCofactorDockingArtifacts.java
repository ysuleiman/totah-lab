package totah.lab.daedalus.docking.sequential;

import java.nio.file.Path;
import java.util.Objects;

/** Explicit artifacts produced and consumed by sequential docking. */
public record FixedCofactorDockingArtifacts(
        Path cofactorPosesPdbqt,
        Path receptorAssemblyPdb,
        Path receptorAssemblyPdbqt,
        Path ligandPosesPdbqt) {

    public FixedCofactorDockingArtifacts {
        cofactorPosesPdbqt = normalize(cofactorPosesPdbqt, "cofactorPosesPdbqt");
        receptorAssemblyPdb = normalize(receptorAssemblyPdb, "receptorAssemblyPdb");
        receptorAssemblyPdbqt = normalize(receptorAssemblyPdbqt, "receptorAssemblyPdbqt");
        ligandPosesPdbqt = normalize(ligandPosesPdbqt, "ligandPosesPdbqt");
    }

    private static Path normalize(Path path, String fieldName) {
        return Objects.requireNonNull(path, fieldName)
                .toAbsolutePath().normalize();
    }
}
