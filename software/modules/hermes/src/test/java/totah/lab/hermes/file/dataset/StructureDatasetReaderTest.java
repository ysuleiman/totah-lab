package totah.lab.hermes.file.dataset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.api.StructureReader;
import totah.lab.hermes.file.pocket.reader.PocketReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureDatasetReaderTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readsExactDirectoryNamedStructureAndDetectedPockets()
            throws IOException {

        Path directory = Files.createDirectory(
                temporaryDirectory.resolve("target"));
        Path exact = Files.writeString(
                directory.resolve("target.cif"),
                "structure");
        Files.writeString(directory.resolve("other.pdb"), "other");
        Structure structure = new Structure(List.of());
        Pocket pocket = pocket();
        StubStructureReader structures =
                new StubStructureReader(structure);
        StubPocketReader pockets =
                new StubPocketReader(true, List.of(pocket));

        StructureDataset dataset = new StructureDatasetReader(
                structures,
                pockets).readDirectory(directory);

        assertEquals(directory.toAbsolutePath(), dataset.directory());
        assertEquals(exact.toAbsolutePath(), dataset.structurePath());
        assertSame(structure, dataset.structure());
        assertEquals(List.of(pocket), dataset.pockets());
        assertEquals(exact.toAbsolutePath(), structures.readPath);
        assertEquals(directory.toAbsolutePath(), pockets.readPath);
        assertThrows(
                UnsupportedOperationException.class,
                () -> dataset.pockets().clear());
    }

    @Test
    void permitsAValidDatasetWithoutPocketResults() throws IOException {
        Path directory = Files.createDirectory(
                temporaryDirectory.resolve("single"));
        Files.writeString(directory.resolve("model.mmcif"), "structure");
        StubPocketReader pockets =
                new StubPocketReader(false, List.of());

        StructureDataset dataset = new StructureDatasetReader(
                new StubStructureReader(new Structure(List.of())),
                pockets).read(directory);

        assertTrue(dataset.pockets().isEmpty());
        assertFalse(pockets.readCalled);
    }

    @Test
    void rejectsAmbiguousStructureCandidates() throws IOException {
        Path directory = Files.createDirectory(
                temporaryDirectory.resolve("ambiguous"));
        Files.writeString(directory.resolve("first.pdb"), "first");
        Files.writeString(directory.resolve("second.cif"), "second");
        StructureDatasetReader reader = new StructureDatasetReader(
                new StubStructureReader(new Structure(List.of())),
                new StubPocketReader(false, List.of()));

        IOException exception = assertThrows(
                IOException.class,
                () -> reader.read(directory));

        assertTrue(exception.getMessage().contains("Ambiguous"));
        assertTrue(exception.getMessage().contains("first.pdb"));
        assertTrue(exception.getMessage().contains("second.cif"));
    }

    @Test
    void rejectsMissingOrEmptyDatasetDirectory() throws IOException {
        StructureDatasetReader reader = new StructureDatasetReader(
                new StubStructureReader(new Structure(List.of())),
                new StubPocketReader(false, List.of()));

        assertThrows(
                IOException.class,
                () -> reader.read(temporaryDirectory.resolve("missing")));
        IOException empty = assertThrows(
                IOException.class,
                () -> reader.read(temporaryDirectory));
        assertTrue(empty.getMessage().contains(
                "No supported structure file"));
    }

    @Test
    void reportsDirectorySupportFromStructureCandidates()
            throws IOException {

        Path directory = Files.createDirectory(
                temporaryDirectory.resolve("supported"));
        StructureDatasetReader reader = new StructureDatasetReader(
                new StubStructureReader(new Structure(List.of())),
                new StubPocketReader(false, List.of()));

        assertFalse(reader.supports(directory));
        Files.writeString(directory.resolve("model.pdb"), "structure");
        assertTrue(reader.supports(directory));
        assertFalse(reader.supports(null));
    }

    private static Pocket pocket() {
        return new Pocket(
                new PocketId("pocket-1"),
                "Pocket 1",
                PocketSource.FPOCKET,
                new Point3D(0.0, 0.0, 0.0),
                List.of(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of());
    }

    private static final class StubStructureReader
            implements StructureReader {

        private final Structure structure;
        private Path readPath;

        private StubStructureReader(Structure structure) {
            this.structure = structure;
        }

        @Override
        public Structure read(Path path) {
            readPath = path;
            return structure;
        }

        @Override
        public boolean supports(Path path) {
            String name = path.getFileName().toString().toLowerCase();
            return name.endsWith(".pdb")
                    || name.endsWith(".cif")
                    || name.endsWith(".mmcif");
        }
    }

    private static final class StubPocketReader implements PocketReader {

        private final boolean supported;
        private final List<Pocket> pockets;
        private boolean readCalled;
        private Path readPath;

        private StubPocketReader(
                boolean supported,
                List<Pocket> pockets) {

            this.supported = supported;
            this.pockets = pockets;
        }

        @Override
        public List<Pocket> read(Path path) {
            readCalled = true;
            readPath = path;
            return pockets;
        }

        @Override
        public boolean supports(Path path) {
            return supported;
        }
    }
}
