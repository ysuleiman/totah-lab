package totah.lab.daedalus.docking;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Validated prepared-molecule inputs for a future docking executor.
 */
public record DockingInput(
        Path receptorPdbqt,
        Path ligandPdbqt,
        Optional<Path> flexPdbqt) {

    public DockingInput {
        Objects.requireNonNull(receptorPdbqt, "receptorPdbqt is null");
        Objects.requireNonNull(ligandPdbqt, "ligandPdbqt is null");
        flexPdbqt = Objects.requireNonNull(flexPdbqt, "flexPdbqt is null");
    }
}
