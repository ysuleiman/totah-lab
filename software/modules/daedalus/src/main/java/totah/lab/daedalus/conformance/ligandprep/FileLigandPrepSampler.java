package totah.lab.daedalus.conformance.ligandprep;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Reads a reference directory of locally prepared ligands: a
 * {@code manifest.tsv} (header {@code id<TAB>name<TAB>h_added}) plus,
 * per row, {@code <id>.sdf} and {@code <id>.meeko.pdbqt}. The sample
 * is the first {@code count} manifest rows sorted by id, so repeated
 * runs are deterministic.
 */
public final class FileLigandPrepSampler implements LigandPrepSampler {

    public static final String MANIFEST = "manifest.tsv";
    public static final String MEEKO_SUFFIX = ".meeko.pdbqt";

    private final Path referenceDirectory;

    public FileLigandPrepSampler(Path referenceDirectory) {
        this.referenceDirectory =
                Objects.requireNonNull(referenceDirectory,
                        "referenceDirectory");
    }

    @Override
    public List<LigandPrepSample> sample(int count) throws IOException {
        Path manifestPath = referenceDirectory.resolve(MANIFEST);
        if (!Files.isRegularFile(manifestPath)) {
            throw new IOException(
                    "reference manifest not found: " + manifestPath);
        }

        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(manifestPath)) {
            if (line.isBlank() || line.startsWith("id\t")) {
                continue;
            }
            String[] fields = line.split("\t", -1);
            if (fields.length < 2) {
                throw new IOException("malformed manifest line: " + line);
            }
            rows.add(fields);
        }
        rows.sort(Comparator.comparing(fields -> fields[0]));

        List<LigandPrepSample> samples = new ArrayList<>();
        for (String[] fields : rows.stream().limit(count).toList()) {
            String id = fields[0];
            Path sdf = referenceDirectory.resolve(id + ".sdf");
            Path meeko = referenceDirectory.resolve(id + MEEKO_SUFFIX);
            if (!Files.isRegularFile(sdf) || !Files.isRegularFile(meeko)) {
                throw new IOException("incomplete reference pair for "
                        + id + ": expected " + sdf + " and " + meeko);
            }
            samples.add(new LigandPrepSample(id, fields[1], sdf, meeko));
        }
        return List.copyOf(samples);
    }
}
