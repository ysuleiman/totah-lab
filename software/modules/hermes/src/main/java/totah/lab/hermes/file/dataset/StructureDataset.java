package totah.lab.hermes.file.dataset;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/** Structure and pocket artifacts resolved from one filesystem directory. */
public record StructureDataset(
        Path directory,
        Path structurePath,
        Structure structure,
        List<Pocket> pockets) {

    public StructureDataset {
        directory = Objects.requireNonNull(
                directory,
                "directory").toAbsolutePath().normalize();
        structurePath = Objects.requireNonNull(
                structurePath,
                "structurePath").toAbsolutePath().normalize();
        Objects.requireNonNull(structure, "structure");
        pockets = List.copyOf(
                Objects.requireNonNull(pockets, "pockets"));
        if (!structurePath.getParent().equals(directory)) {
            throw new IllegalArgumentException(
                    "structurePath must be directly inside directory");
        }
    }
}
