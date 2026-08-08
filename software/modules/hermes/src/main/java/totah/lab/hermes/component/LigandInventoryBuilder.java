package totah.lab.hermes.component;

import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.mmcif.reader.MmcifNonPolymerReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Scans a directory of mmCIF structure files and assembles a
 * {@link LigandInventory} of every non-polymer component with its bound
 * occurrences and classification.
 */
public final class LigandInventoryBuilder {

    private final MmcifNonPolymerReader reader = new MmcifNonPolymerReader();
    private final LigandClassifier classifier = new LigandClassifier();

    /**
     * Builds the inventory from every {@code *.cif} file directly inside
     * {@code structuresDir}. Files named {@code <PDBID>-assembly1.cif} are
     * treated as biological assembly 1, all others as entries.
     */
    public LigandInventory build(Path structuresDir) throws IOException {
        Objects.requireNonNull(structuresDir, "structuresDir");
        if (!Files.isDirectory(structuresDir)) {
            throw new IOException(
                    "Structures directory does not exist: " + structuresDir);
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(structuresDir)) {
            files = stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".cif"))
                    .sorted()
                    .toList();
        }

        Map<String, List<BoundComponentOccurrence>> occurrencesByComponent =
                new TreeMap<>();

        for (Path file : files) {
            SourceFile sourceFile = describe(file);
            for (BoundComponentOccurrence occurrence : reader.read(
                    file, sourceFile.pdbId(), sourceFile.sourceKind(),
                    sourceFile.assemblyId())) {
                occurrencesByComponent
                        .computeIfAbsent(
                                occurrence.componentId(),
                                ignored -> new ArrayList<>())
                        .add(occurrence);
            }
        }

        Map<String, ComponentInventory> components = new TreeMap<>();
        Map<LigandClassification, Integer> counts =
                new EnumMap<>(LigandClassification.class);
        int totalOccurrences = 0;

        for (Map.Entry<String, List<BoundComponentOccurrence>> entry
                : occurrencesByComponent.entrySet()) {

            List<BoundComponentOccurrence> occurrences = entry.getValue();
            LigandClassification classification = classifier.classify(
                    entry.getKey(),
                    occurrences.isEmpty()
                            ? List.of()
                            : occurrences.getFirst().atoms());

            components.put(entry.getKey(), new ComponentInventory(
                    entry.getKey(), classification, occurrences, null, null));
            counts.merge(classification, 1, Integer::sum);
            totalOccurrences += occurrences.size();
        }

        return new LigandInventory(components, totalOccurrences, counts);
    }

    private static SourceFile describe(Path file) {
        String name = file.getFileName().toString();
        String base = name.substring(0, name.length() - ".cif".length());
        int assemblyMarker = base.toLowerCase(Locale.ROOT).lastIndexOf("-assembly");
        if (assemblyMarker > 0) {
            String assemblyId = base.substring(assemblyMarker + "-assembly".length());
            if (assemblyId.isBlank()) {
                return new SourceFile(base.toUpperCase(Locale.ROOT),
                        BoundComponentOccurrence.SourceKind.ENTRY, null);
            }
            return new SourceFile(
                    base.substring(0, assemblyMarker).toUpperCase(Locale.ROOT),
                    BoundComponentOccurrence.SourceKind.ASSEMBLY,
                    assemblyId);
        }
        return new SourceFile(
                base.toUpperCase(Locale.ROOT),
                BoundComponentOccurrence.SourceKind.ENTRY,
                null);
    }

    private record SourceFile(
            String pdbId,
            BoundComponentOccurrence.SourceKind sourceKind,
            String assemblyId) {
    }
}
