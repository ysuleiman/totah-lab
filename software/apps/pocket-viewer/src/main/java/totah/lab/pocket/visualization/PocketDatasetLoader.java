package totah.lab.pocket.visualization;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.reader.BioJavaStructureReader;
import totah.lab.hermes.file.reader.StructureReader;
import totah.lab.io.PocketIO;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

final class PocketDatasetLoader {
    private static final List<String> STRUCTURE_EXTENSIONS =
            List.of(".pdb", ".cif", ".mmcif");

    private final StructureReader structureReader;

    PocketDatasetLoader() {
        this(new BioJavaStructureReader());
    }

    PocketDatasetLoader(StructureReader structureReader) {
        this.structureReader = Objects.requireNonNull(
                structureReader,
                "structureReader");
    }

    PocketDataset load(Path directory) throws IOException {
        Objects.requireNonNull(directory, "directory");
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) {
            throw new IOException(
                    "Pocket dataset is not a directory: " + normalized);
        }

        Path structurePath = findStructure(normalized);
        Structure structure = structureReader.read(structurePath);
        List<Pocket> pockets = PocketIO.load(normalized);
        String id = datasetId(normalized);
        Protein protein = new Protein(
                id,
                null,
                id,
                null,
                null,
                null,
                structure);
        return new PocketDataset(protein, pockets);
    }

    private static Path findStructure(Path directory) throws IOException {
        try (Stream<Path> paths = Files.walk(directory, 2)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(PocketDatasetLoader::isStructureFile)
                    .filter(path -> !isGeneratedPocketStructure(
                            directory, path))
                    .sorted(Comparator.<Path>comparingInt(
                                    path -> preference(directory, path))
                            .thenComparing(Path::toString))
                    .findFirst()
                    .orElseThrow(() -> new IOException(
                            "No PDB, CIF, or mmCIF structure found in "
                                    + directory));
        }
    }

    private static boolean isStructureFile(Path path) {
        String name = path.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        return STRUCTURE_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean isGeneratedPocketStructure(
            Path directory,
            Path path) {
        Path relative = directory.relativize(path);
        if (relative.getNameCount() < 2) {
            return false;
        }
        String firstDirectory = relative.getName(0).toString()
                .toLowerCase(Locale.ROOT);
        return firstDirectory.equals("fpocket")
                || firstDirectory.equals("prank")
                || firstDirectory.equals("p2rank");
    }

    private static int preference(Path directory, Path path) {
        String name = path.getFileName().toString()
                .toLowerCase(Locale.ROOT);
        int depthPenalty = directory.relativize(path).getNameCount() * 10;
        if (name.endsWith(".cif") || name.endsWith(".mmcif")) {
            return depthPenalty;
        }
        return depthPenalty + 1;
    }

    private static String datasetId(Path directory) {
        Path fileName = directory.getFileName();
        return fileName == null ? directory.toString() : fileName.toString();
    }
}
