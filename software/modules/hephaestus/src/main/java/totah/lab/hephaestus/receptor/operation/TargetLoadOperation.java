package totah.lab.hephaestus.receptor.operation;


import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.load.TargetLoadRequest;
import totah.lab.hermes.file.reader.StructureReader;
import totah.lab.hephaestus.factory.ProteinFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class TargetLoadOperation {

    private final StructureReader structureReader;
    private final ProteinFactory proteinFactory;

    public TargetLoadOperation(
            StructureReader structureReader,
            ProteinFactory proteinFactory) {

        this.structureReader = Objects.requireNonNull(
                structureReader,
                "structureReader");

        this.proteinFactory = Objects.requireNonNull(
                proteinFactory,
                "proteinFactory");
    }

    public PreparedProtein apply(
            TargetLoadRequest request) throws IOException {

        Objects.requireNonNull(request, "request");

        Path input = request.input();
        validateInput(input);

        if (!structureReader.supports(input)) {
            throw new IllegalArgumentException(
                    "Unsupported structure format: " + input);
        }

        Structure structure =
                structureReader.read(input);

        if (structure.getResidueCount() == 0) {
            throw new IOException(
                    "No residues loaded from " + input);
        }

        Protein protein =
                proteinFactory.create(
                        request.targetId(),
                        structure);

        if (protein == null) {
            throw new IllegalStateException(
                    "ProteinFactory returned null.");
        }

        return PreparedProtein.of(protein)
                .withAttribute(
                        "source-path",
                        input.toAbsolutePath().normalize());
    }

    private void validateInput(Path input)
            throws IOException {

        if (!Files.exists(input)) {
            throw new IOException(
                    "Target structure file does not exist: "
                            + input);
        }

        if (!Files.isRegularFile(input)) {
            throw new IOException(
                    "Target structure path is not a regular file: "
                            + input);
        }

        if (!Files.isReadable(input)) {
            throw new IOException(
                    "Target structure file is not readable: "
                            + input);
        }
    }
}
