package totah.lab;

import org.biojava.nbio.structure.Structure;
import org.biojava.nbio.structure.io.PDBFileParser;
import org.biojava.nbio.structure.io.cif.CifStructureConverter;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.zip.GZIPInputStream;

public class AlphaFoldStructureReader {

    private AlphaFoldStructureReader() {
    }

    public static Structure read(Path path) throws IOException {
        String name = path.getFileName()
                .toString()
                .toLowerCase(Locale.ROOT);

        try (InputStream raw = Files.newInputStream(path);
             InputStream decompressed = name.endsWith(".gz")
                     ? new GZIPInputStream(raw)
                     : raw;
             BufferedInputStream in = new BufferedInputStream(decompressed)) {

            if (name.endsWith(".pdb") || name.endsWith(".pdb.gz")) {
                PDBFileParser parser = new PDBFileParser();
                return parser.parsePDBFile(in);
            }

            if (name.endsWith(".cif")
                    || name.endsWith(".cif.gz")
                    || name.endsWith(".mmcif")
                    || name.endsWith(".mmcif.gz")) {
                return CifStructureConverter.fromInputStream(in);
            }

            throw new IllegalArgumentException(
                    "Unsupported structure format: " + path
            );
        }
    }
}