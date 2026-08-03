package totah.lab;


import org.biojava.nbio.structure.Structure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Stream;

public final class AlphaFoldReader {

    public static void main(String[] args) throws IOException {
        Path directory = Path.of("/Users/yazan/artifacts/UP000005640_9606_HUMAN_v6");

        try (Stream<Path> files = Files.walk(directory)) {
            files.filter(Files::isRegularFile)
                    .filter(AlphaFoldReader::isStructureFile)
                    .forEach(AlphaFoldReader::processFile);
        }
    }

    private static boolean isStructureFile(Path path) {
        String name = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        return name.endsWith(".pdb.gz")
                || name.endsWith(".cif.gz")
                || name.endsWith(".mmcif.gz")
                || name.endsWith(".pdb")
                || name.endsWith(".cif")
                || name.endsWith(".mmcif");
    }

    private static void processFile(Path path) {
        try {
            Structure structure = AlphaFoldStructureReader.read(path);

            System.out.printf(
                    "%s: %d chains%n",
                    path.getFileName(),
                    structure.getChains().size()
            );

        } catch (Exception e) {
            System.err.printf(
                    "Failed to read %s: %s%n",
                    path,
                    e.getMessage()
            );
        }
    }
}