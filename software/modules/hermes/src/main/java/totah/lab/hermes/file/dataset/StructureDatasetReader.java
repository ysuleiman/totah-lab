package totah.lab.hermes.file.dataset;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.api.FileReader;
import totah.lab.hermes.file.api.StructureReader;
import totah.lab.hermes.file.pdb.reader.PdbReader;
import totah.lab.hermes.file.pocket.reader.AutoDetectingPocketReader;
import totah.lab.hermes.file.pocket.reader.PocketReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/** Reads one structure and every supported pocket result from a directory. */
public final class StructureDatasetReader
        implements FileReader<StructureDataset> {

    private final StructureReader structureReader;
    private final PocketReader pocketReader;

    public StructureDatasetReader() {
        this(new PdbReader(), new AutoDetectingPocketReader());
    }

    public StructureDatasetReader(
            StructureReader structureReader,
            PocketReader pocketReader) {

        this.structureReader = Objects.requireNonNull(
                structureReader,
                "structureReader");
        this.pocketReader = Objects.requireNonNull(
                pocketReader,
                "pocketReader");
    }

    @Override
    public StructureDataset read(Path directory) throws IOException {
        Path normalizedDirectory = validateDirectory(directory);
        Path structurePath = selectStructure(normalizedDirectory);
        Structure structure = structureReader.read(structurePath);
        List<Pocket> pockets = pocketReader.supports(normalizedDirectory)
                ? pocketReader.read(normalizedDirectory)
                : List.of();
        return new StructureDataset(
                normalizedDirectory,
                structurePath,
                structure,
                pockets);
    }

    public StructureDataset readDirectory(Path directory)
            throws IOException {

        return read(directory);
    }

    @Override
    public boolean supports(Path path) {
        if (path == null || !Files.isDirectory(path)) {
            return false;
        }
        try (Stream<Path> files = Files.list(path)) {
            return files.anyMatch(candidate ->
                    Files.isRegularFile(candidate)
                            && structureReader.supports(candidate));
        } catch (IOException exception) {
            return false;
        }
    }

    private Path selectStructure(Path directory) throws IOException {
        List<Path> candidates;
        try (Stream<Path> files = Files.list(directory)) {
            candidates = files
                    .filter(Files::isRegularFile)
                    .filter(structureReader::supports)
                    .sorted(Comparator.comparing(
                            path -> path.getFileName().toString(),
                            String.CASE_INSENSITIVE_ORDER))
                    .toList();
        }

        if (candidates.isEmpty()) {
            throw new IOException(
                    "No supported structure file found in " + directory);
        }

        String directoryName = directory.getFileName().toString();
        List<Path> exactMatches = candidates.stream()
                .filter(path -> baseName(path).equalsIgnoreCase(
                        directoryName))
                .toList();
        if (exactMatches.size() == 1) {
            return exactMatches.getFirst();
        }
        if (exactMatches.size() > 1) {
            throw ambiguous(directory, exactMatches);
        }
        if (candidates.size() == 1) {
            return candidates.getFirst();
        }
        throw ambiguous(directory, candidates);
    }

    private static Path validateDirectory(Path directory)
            throws IOException {

        Objects.requireNonNull(directory, "directory");
        Path normalized = directory.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IOException(
                    "Structure dataset directory does not exist: "
                            + normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw new IOException(
                    "Structure dataset path is not a directory: "
                            + normalized);
        }
        if (!Files.isReadable(normalized)) {
            throw new IOException(
                    "Structure dataset directory is not readable: "
                            + normalized);
        }
        return normalized;
    }

    private static IOException ambiguous(
            Path directory,
            List<Path> candidates) {

        String names = candidates.stream()
                .map(path -> path.getFileName().toString())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .reduce((first, second) -> first + ", " + second)
                .orElse("");
        return new IOException(
                "Ambiguous structure files in " + directory + ": "
                        + names);
    }

    private static String baseName(Path path) {
        String name = path.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        for (String extension : List.of(".mmcif", ".cif", ".pdb")) {
            if (lower.endsWith(extension)) {
                return name.substring(0, name.length() - extension.length());
            }
        }
        return name;
    }
}
