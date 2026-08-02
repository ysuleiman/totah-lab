package totah.lab.analysis.disulfide;

import totah.lab.analysis.disulfide.PdbAdjacentCysteineParser.AdjacentCysteinePair;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Command-line scan of a directory containing AlphaFold PDB/PDB.GZ files.
 */
public final class AdjacentCysteineScan {

    private static final int DEFAULT_FLANK_SIZE = 8;

    private AdjacentCysteineScan() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 2 || arguments.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: AdjacentCysteineScan <pdb-directory> <output.csv> [flank-size]");
        }
        int flankSize = arguments.length == 3
                ? Integer.parseInt(arguments[2])
                : DEFAULT_FLANK_SIZE;
        ScanSummary summary = scan(
                Path.of(arguments[0]), Path.of(arguments[1]), flankSize);
        System.out.printf(
                Locale.ROOT,
                "Scanned %d structures; wrote %d CC pairs (%d over 6 A) to %s%n",
                summary.structureCount(),
                summary.pairCount(),
                summary.openPairCount(),
                summary.outputCsv());
    }

    public static ScanSummary scan(
            Path pdbDirectory, Path outputCsv, int flankSize) throws IOException {
        Objects.requireNonNull(pdbDirectory, "pdbDirectory is null");
        Objects.requireNonNull(outputCsv, "outputCsv is null");
        if (!Files.isDirectory(pdbDirectory)) {
            throw new IOException("PDB directory does not exist: " + pdbDirectory);
        }

        List<Path> pdbFiles;
        try (Stream<Path> paths = Files.list(pdbDirectory)) {
            pdbFiles = paths.filter(Files::isRegularFile)
                    .filter(AdjacentCysteineScan::isPdb)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        Path parent = outputCsv.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        PdbAdjacentCysteineParser parser = new PdbAdjacentCysteineParser();
        long pairCount = 0;
        long openPairCount = 0;
        try (BufferedWriter writer = Files.newBufferedWriter(
                outputCsv,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write("uniprot_accession,chain,cys1,cys2,sequence_context,motif_offset,"
                    + "sg_distance_angstrom,distance_class,chi3_degrees,cys1_plddt,"
                    + "cys2_plddt,mean_plddt,filename");
            writer.newLine();
            for (Path pdbFile : pdbFiles) {
                for (AdjacentCysteinePair pair : parser.parse(pdbFile, flankSize)) {
                    writeRow(writer, accession(pdbFile), pdbFile, pair);
                    pairCount++;
                    if (pair.sgDistanceAngstrom().orElse(0.0) > 6.0) {
                        openPairCount++;
                    }
                }
            }
        }
        return new ScanSummary(pdbFiles.size(), pairCount, openPairCount, outputCsv);
    }

    private static void writeRow(
            BufferedWriter writer,
            String accession,
            Path file,
            AdjacentCysteinePair pair) throws IOException {
        String distance = pair.sgDistanceAngstrom()
                .map(value -> format("%.3f", value)).orElse("");
        String chi3 = pair.chi3Degrees()
                .map(value -> format("%.2f", value)).orElse("");
        writer.write(String.join(",",
                csv(accession),
                csv(pair.chain()),
                Integer.toString(pair.firstResidue()),
                Integer.toString(pair.secondResidue()),
                csv(pair.sequenceContext()),
                Integer.toString(pair.motifOffset()),
                distance,
                distanceClass(pair.sgDistanceAngstrom().orElse(null)),
                chi3,
                format("%.2f", pair.firstPlddt()),
                format("%.2f", pair.secondPlddt()),
                format("%.2f", pair.meanPlddt()),
                csv(file.getFileName().toString())));
        writer.newLine();
    }

    private static String distanceClass(Double distance) {
        if (distance == null) {
            return "MISSING_SG";
        }
        if (distance <= 2.3) {
            return "BONDED_GEOMETRY";
        }
        if (distance <= 6.0) {
            return "INTERMEDIATE";
        }
        return "OPEN_GT_6A";
    }

    private static String accession(Path file) {
        String name = file.getFileName().toString();
        if (name.startsWith("AF-")) {
            int model = name.indexOf("-F1-model_");
            if (model > 3) {
                return name.substring(3, model);
            }
        }
        return name;
    }

    private static boolean isPdb(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".pdb") || name.endsWith(".pdb.gz");
    }

    private static String csv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static String format(String pattern, double value) {
        return String.format(Locale.ROOT, pattern, value);
    }

    public record ScanSummary(
            long structureCount,
            long pairCount,
            long openPairCount,
            Path outputCsv) {
    }
}
