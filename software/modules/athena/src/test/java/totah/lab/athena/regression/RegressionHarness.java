package totah.lab.athena.regression;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.hermes.file.pdbqt.PdbqtAtom;
import totah.lab.hermes.file.pdbqt.PdbqtFile;
import totah.lab.hermes.file.pdbqt.PdbqtModel;
import totah.lab.hermes.file.pdbqt.reader.PdbqtReader;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Shared support for the Athena v2 regression tests against the frozen
 * historical METTL7 analysis outputs (golden source:
 * {@code tmp/athena-v2-regression-reference/REFERENCE.md}).
 *
 * <p>All input files are committed fixtures on the test classpath under
 * {@code /mettl7-v2-regression/} (see the {@code MANIFEST.csv} there for
 * provenance and checksums); {@link #requireInput} materializes them to a
 * temporary directory so the Path-based readers work from any clone.</p>
 *
 * <p>Parsing deliberately mirrors the historical Python conventions:
 * PDB atom records are read by fixed columns (element from columns
 * 77-78, falling back to the atom name's first letter, as
 * {@code analyze_interference.py:31-32} and
 * {@code run_structural_design.py:element} do); PDBQT poses are read
 * through the hermes {@link PdbqtReader} and the 1-based MODEL index is
 * the historical {@code representative_mode}.</p>
 *
 * <p>Every compared number is appended to
 * {@code ATHENA_INTERACTION_V2_REGRESSION_RESULTS.csv} at the repository
 * root via {@link #record}. The file is rewritten on each test JVM
 * start; rows appear in test-execution order.</p>
 */
final class RegressionHarness {

    static final Path REPO_ROOT = locateRepoRoot();
    static final Path RESULTS_CSV =
            REPO_ROOT.resolve("ATHENA_INTERACTION_V2_REGRESSION_RESULTS.csv");

    /** Classpath root of the committed regression fixtures. */
    static final String FIXTURE_ROOT = "/mettl7-v2-regression/";

    private static final Map<String, Path> extractedFixtures =
            new LinkedHashMap<>();
    private static Path fixtureTempDir;
    private static boolean csvInitialized;

    private RegressionHarness() {
    }

    private static Path locateRepoRoot() {
        for (Path current = Path.of("").toAbsolutePath();
                current != null; current = current.getParent()) {
            if (Files.isDirectory(
                    current.resolve("software/modules/athena"))) {
                return current;
            }
        }
        throw new IllegalStateException(
                "Could not locate repository root from "
                        + Path.of("").toAbsolutePath());
    }

    /** One PDB atom record, parsed with the historical Python column convention. */
    record PdbAtom(
            String name,
            String residue,
            String chain,
            int number,
            String element,
            Point3D xyz) {
    }

    /** Parses ATOM/HETATM records exactly like the historical scripts. */
    static List<PdbAtom> pdbAtoms(Path path) {
        List<PdbAtom> atoms = new ArrayList<>();
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        for (String line : lines) {
            if (!line.startsWith("ATOM  ") && !line.startsWith("HETATM")) {
                continue;
            }
            String name = line.substring(12, 16).trim();
            String element =
                    line.length() >= 78 ? line.substring(76, 78).trim() : "";
            if (element.isEmpty()) {
                String upper = name.toUpperCase(Locale.ROOT);
                element = upper.length() >= 2
                        && (upper.startsWith("CL") || upper.startsWith("BR"))
                        ? upper.substring(0, 2)
                        : upper.substring(0, 1);
            }
            atoms.add(new PdbAtom(
                    name,
                    line.substring(17, 20).trim(),
                    line.substring(21, 22).trim(),
                    Integer.parseInt(line.substring(22, 26).trim()),
                    element.toUpperCase(Locale.ROOT),
                    new Point3D(
                            Double.parseDouble(line.substring(30, 38).trim()),
                            Double.parseDouble(line.substring(38, 46).trim()),
                            Double.parseDouble(line.substring(46, 54).trim()))));
        }
        return atoms;
    }

    /** Heavy atoms only (element != H), as the historical scripts filter. */
    static List<PdbAtom> heavy(List<PdbAtom> atoms) {
        return atoms.stream().filter(atom -> !"H".equals(atom.element()))
                .toList();
    }

    static List<Point3D> points(List<PdbAtom> atoms) {
        return atoms.stream().map(PdbAtom::xyz).toList();
    }

    static PdbqtFile readPdbqt(Path path) {
        try {
            return new PdbqtReader().read(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Returns the model with the given 1-based MODEL index. */
    static PdbqtModel model(PdbqtFile file, int mode) {
        for (PdbqtModel model : file.models()) {
            if (model.modelNumber() == mode) {
                return model;
            }
        }
        throw new IllegalArgumentException(
                "PDBQT has no MODEL " + mode + " (" + file.models().size()
                        + " models)");
    }

    /** Heavy-atom points of a PDBQT pose model (AD4-type-derived elements). */
    static List<Point3D> heavyPosePoints(PdbqtModel model) {
        return model.atoms().stream()
                .filter(atom -> !"H".equalsIgnoreCase(atom.element()))
                .map(PdbqtAtom::position)
                .toList();
    }

    /**
     * Heavy-atom filter matching the netarsudil historical scripts:
     * AD4 type not in {H, HD} and atom name not starting with H.
     */
    static List<PdbqtAtom> heavyNetarsudilConvention(List<PdbqtAtom> atoms) {
        return atoms.stream()
                .filter(atom -> !"H".equalsIgnoreCase(atom.element()))
                .filter(atom -> !atom.atomName().startsWith("H"))
                .toList();
    }

    /** Minimum pairwise distance between two point sets. */
    static double minDistance(List<Point3D> first, List<Point3D> second) {
        double best = Double.POSITIVE_INFINITY;
        for (Point3D a : first) {
            for (Point3D b : second) {
                double distance = a.distance(b);
                if (distance < best) {
                    best = distance;
                }
            }
        }
        return best;
    }

    /** Reads a simple comma-separated historical CSV (no quoted fields). */
    static List<Map<String, String>> readCsv(Path path) {
        List<String> lines;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        String[] header = lines.getFirst().split(",", -1);
        List<Map<String, String>> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.split(",", -1);
            Map<String, String> row = new LinkedHashMap<>();
            for (int column = 0; column < header.length; column++) {
                row.put(header[column],
                        column < fields.length ? fields[column] : "");
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Resolves a committed fixture from the test classpath
     * ({@code /mettl7-v2-regression/<fixturePath>}) and materializes it to
     * a temporary file for the Path-based readers. On absence records a
     * NOT_COMPUTABLE row and fails the test with a clear message.
     */
    static synchronized Path requireInput(
            String fixturePath, String category, String metric) {
        Path extracted = extractedFixtures.get(fixturePath);
        if (extracted != null) {
            return extracted;
        }
        String resource = FIXTURE_ROOT + fixturePath;
        try (InputStream stream =
                RegressionHarness.class.getResourceAsStream(resource)) {
            if (stream == null) {
                record(category, metric, "", "",
                        "fixture missing from classpath: " + resource,
                        "NOT_COMPUTABLE_MISSING_INPUT");
                fail("Missing regression fixture on classpath: " + resource);
            }
            if (fixtureTempDir == null) {
                fixtureTempDir = Files.createTempDirectory(
                        "athena-v2-regression-fixtures");
                fixtureTempDir.toFile().deleteOnExit();
            }
            Path target = fixtureTempDir.resolve(fixturePath);
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(stream, target);
            extractedFixtures.put(fixturePath, target);
            return target;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static double parseDouble(String value) {
        return Double.parseDouble(value);
    }

    /** Appends one comparison row to the results CSV. */
    static synchronized void record(
            String category,
            String metric,
            String historicalValue,
            String javaValue,
            String conventionNote,
            String status) {

        String delta = "";
        try {
            delta = Double.toString(Double.parseDouble(javaValue)
                    - Double.parseDouble(historicalValue));
        } catch (NumberFormatException ignored) {
            // Non-numeric side (e.g. booleans, empty): no delta.
        }
        String row = String.join(",",
                escape(category),
                escape(metric),
                escape(historicalValue),
                escape(javaValue),
                escape(delta),
                escape(conventionNote),
                escape(status)) + System.lineSeparator();
        try {
            if (!csvInitialized) {
                Files.writeString(RESULTS_CSV,
                        "category,metric,historical_value,java_value,delta,"
                                + "convention_note,status"
                                + System.lineSeparator());
                csvInitialized = true;
            }
            Files.writeString(RESULTS_CSV, row,
                    java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    static void record(
            String category,
            String metric,
            double historicalValue,
            double javaValue,
            String conventionNote,
            String status) {
        record(category, metric, Double.toString(historicalValue),
                Double.toString(javaValue), conventionNote, status);
    }

    private static String escape(String field) {
        if (field.contains(",") || field.contains("\"")
                || field.contains("\n")) {
            return '"' + field.replace("\"", "\"\"") + '"';
        }
        return field;
    }
}
