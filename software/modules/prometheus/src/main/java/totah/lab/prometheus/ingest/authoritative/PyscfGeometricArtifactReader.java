package totah.lab.prometheus.ingest.authoritative;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;

import totah.lab.prometheus.ingest.JsonArtifacts;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryClassification;

/**
 * Reads the actual machine artifacts emitted by the Unit-05O PySCF/geomeTRIC
 * drivers. Reports and directory names are deliberately not consulted.
 */
public final class PyscfGeometricArtifactReader {

    private static final Pattern GEOMETRIC_VERSION = Pattern.compile("geomeTRIC started\\. Version: ([^ ]+)");
    private static final Pattern STEP = Pattern.compile(
            "Step\\s+(\\d+).*?E \\(change\\) = (-?\\d+\\.\\d+)");
    private static final Pattern ANSI = Pattern.compile("\\u001B\\[[;\\d]*m");
    private static final Pattern METHOD = Pattern.compile(
            "^([A-Za-z0-9]+)-([^/]+)/([^ ]+)(?: density-fitted)? ([a-z-]+) phase(?: analytic Hessian)?$");

    public PyscfGeometricOptimization readOptimization(Path directory) throws IOException {
        Path inputPath = require(directory, "input.json");
        Path resultPath = require(directory, "result.json");
        Path geometryPath = require(directory, "final.xyz");
        Path gradientPath = require(directory, "final_gradient_hartree_per_bohr.txt");
        Path logPath = require(directory, "raw_combined.log");
        JsonNode input = JsonArtifacts.readTree(inputPath);
        JsonNode result = JsonArtifacts.readTree(resultPath);
        String methodText = requiredText(input, "method", inputPath);

        List<String> logLines = Files.readAllLines(logPath);
        int convergedLine = findLine(logLines, "Converged! =D");
        TerminalStep terminal = terminalStep(logLines);

        Map<String, RecoveredField<String>> software = software(input, inputPath);
        Matcher versionMatcher = GEOMETRIC_VERSION.matcher(stripAnsi(String.join("\n", logLines)));
        if (versionMatcher.find()) {
            software.put("geometric_log", raw("software.geometric_log", versionMatcher.group(1),
                    logPath, "line matching geomeTRIC started/version"));
        }

        List<RawValueDiscrepancy> comparisons = new ArrayList<>();
        double finalEnergy = requiredDouble(result, "energy_hartree", resultPath);
        if (terminal != null) {
            comparisons.add(new RawValueDiscrepancy(
                    "final_single_point_energy_vs_geometric_terminal_energy_hartree",
                    finalEnergy,
                    terminal.energy(),
                    Math.abs(finalEnergy - terminal.energy()),
                    source(resultPath, "/energy_hartree", "Jackson JSON field"),
                    source(logPath, "line " + terminal.line(), "geomeTRIC terminal-step regex")));
        }

        return new PyscfGeometricOptimization(
                raw("calculation_id", requiredText(input, "minimum_id", inputPath), inputPath, "/minimum_id"),
                raw("method", methodText, inputPath, "/method"),
                protocol(methodText, inputPath),
                raw("charge", requiredInt(input, "charge", inputPath), inputPath, "/charge"),
                raw("multiplicity", requiredInt(input, "multiplicity", inputPath), inputPath, "/multiplicity"),
                raw("constraints", requiredText(input, "constraints", inputPath), inputPath, "/constraints"),
                software,
                raw("final_geometry", readXyz(geometryPath), geometryPath, "XYZ records 3..EOF"),
                raw("final_energy_hartree", finalEnergy, resultPath, "/energy_hartree"),
                raw("final_gradient_hartree_per_bohr", readDoubles(gradientPath), gradientPath, "numeric matrix records 1..EOF"),
                raw("scf_converged", requiredBoolean(result, "scf_converged", resultPath), resultPath, "/scf_converged"),
                raw("geometry_converged", convergedLine > 0, logPath,
                        convergedLine > 0 ? "line " + convergedLine : "complete log searched; marker absent"),
                raw("optimization_cycles", requiredInt(result, "cycles", resultPath), resultPath, "/cycles"),
                comparisons);
    }

    public PyscfHessianResult readHessian(Path directory) throws IOException {
        Path inputPath = require(directory, "input.json");
        Path resultPath = require(directory, "result.json");
        Path hessianPath = require(directory, "cartesian_hessian_flat_hartree_per_bohr2.txt");
        Path frequencyPath = require(directory, "frequencies_cm-1.txt");
        JsonNode input = JsonArtifacts.readTree(inputPath);
        JsonNode result = JsonArtifacts.readTree(resultPath);
        String methodText = requiredText(input, "method", inputPath);
        NumericMatrix matrix = readMatrix(hessianPath);
        if (matrix.rows() != matrix.columns()) {
            throw new IOException("Cartesian Hessian is not square: " + matrix.rows() + "x" + matrix.columns());
        }

        boolean checksumsVerified = verifyArtifactChecksums(result, resultPath.getParent());
        List<Double> frequencies = readDoubles(frequencyPath);
        Integer declaredCount = requiredInt(result, "frequency_count", resultPath);
        if (frequencies.size() != declaredCount) {
            throw new IOException("frequency_count=" + declaredCount + " but artifact contains " + frequencies.size());
        }

        return new PyscfHessianResult(
                raw("calculation_id", requiredText(input, "minimum_id", inputPath), inputPath, "/minimum_id"),
                raw("method", methodText, inputPath, "/method"),
                protocol(methodText, inputPath),
                raw("charge", requiredInt(input, "charge", inputPath), inputPath, "/charge"),
                raw("multiplicity", requiredInt(input, "multiplicity", inputPath), inputPath, "/multiplicity"),
                software(input, inputPath),
                raw("energy_hartree", requiredDouble(result, "energy_hartree", resultPath), resultPath, "/energy_hartree"),
                raw("cartesian_hessian", matrix.values(), hessianPath, "numeric matrix records 1..EOF"),
                matrix.rows(),
                "hartree/bohr^2 (unmass-weighted Cartesian second derivatives)",
                raw("frequencies", frequencies, frequencyPath, "numeric records 1..EOF"),
                "cm^-1",
                raw("frequency_projection", requiredText(input, "frequency_projection", inputPath), inputPath,
                        "/frequency_projection"),
                raw("scf_converged", requiredBoolean(result, "scf_converged", resultPath), resultPath, "/scf_converged"),
                raw("status", requiredText(result, "status", resultPath), resultPath, "/status"),
                checksumsVerified,
                "normal_modes_mass_weighted.npy is PySCF harmonic_analysis norm_mode; the Cartesian Hessian itself is not mass weighted",
                List.of());
    }

    /**
     * Compares raw-reconstructed minimum energies with a named historical CSV
     * field. The CSV is used only after reconstruction and never as evidence for
     * the recovered value.
     */
    public List<HistoricalValueComparison> compareHistoricalEnergies(
            List<PyscfGeometricOptimization> optimizations,
            Path historicalCsv,
            String idColumn,
            String energyColumn,
            double toleranceHartree) throws IOException {
        List<String> lines = Files.readAllLines(historicalCsv);
        if (lines.isEmpty()) {
            throw new IOException("empty historical CSV: " + historicalCsv);
        }
        String[] header = lines.getFirst().split(",", -1);
        int idIndex = column(header, idColumn, historicalCsv);
        int energyIndex = column(header, energyColumn, historicalCsv);
        Map<String, HistoricalCell> cells = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] values = lines.get(i).split(",", -1);
            if (values.length != header.length) {
                throw new IOException("malformed historical CSV row at line " + (i + 1));
            }
            cells.put(values[idIndex], new HistoricalCell(Double.parseDouble(values[energyIndex]), i + 1));
        }
        String checksum = ArtifactChecksums.sha256(historicalCsv);
        List<HistoricalValueComparison> comparisons = new ArrayList<>();
        for (PyscfGeometricOptimization optimization : optimizations) {
            String id = optimization.calculationId().value().orElseThrow();
            HistoricalCell cell = cells.get(id);
            if (cell == null) {
                throw new IOException("historical CSV lacks " + id);
            }
            double recovered = optimization.finalEnergyHartree().value().orElseThrow();
            double difference = Math.abs(recovered - cell.value());
            comparisons.add(new HistoricalValueComparison(id, "energy_hartree", recovered, cell.value(),
                    difference, difference <= toleranceHartree, historicalCsv.toString(), checksum,
                    "line " + cell.line() + ", column " + energyColumn));
        }
        return List.copyOf(comparisons);
    }

    private static int column(String[] header, String name, Path path) throws IOException {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(name)) {
                return i;
            }
        }
        throw new IOException("missing column " + name + " in " + path);
    }

    private static ElectronicStructureProtocol protocol(String method, Path inputPath) throws IOException {
        Matcher matcher = METHOD.matcher(method);
        if (!matcher.matches()) {
            throw new IOException("unsupported structured method syntax in " + inputPath + ": " + method);
        }
        return new ElectronicStructureProtocol(
                derived("functional", matcher.group(1), inputPath, "/method"),
                derived("basis_set", matcher.group(3), inputPath, "/method"),
                derived("dispersion", matcher.group(2), inputPath, "/method"),
                derived("density_fitted", method.contains(" density-fitted "), inputPath, "/method"),
                derived("phase", matcher.group(4), inputPath, "/method"));
    }

    private static Map<String, RecoveredField<String>> software(JsonNode input, Path path) throws IOException {
        JsonNode node = input.path("software");
        if (!node.isObject()) {
            throw new IOException("missing object /software in " + path);
        }
        Map<String, RecoveredField<String>> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> result.put(entry.getKey(), environment(
                "software." + entry.getKey(), entry.getValue().asText(), path, "/software/" + entry.getKey())));
        return result;
    }

    private static boolean verifyArtifactChecksums(JsonNode result, Path directory) throws IOException {
        JsonNode hashes = result.path("artifact_sha256");
        if (!hashes.isObject()) {
            throw new IOException("missing /artifact_sha256 in Hessian result");
        }
        var iterator = hashes.fields();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            Path artifact = require(directory, entry.getKey());
            if (!ArtifactChecksums.sha256(artifact).equalsIgnoreCase(entry.getValue().asText())) {
                return false;
            }
        }
        return true;
    }

    private static CartesianGeometry readXyz(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        int count;
        try {
            count = Integer.parseInt(lines.getFirst().trim());
        } catch (RuntimeException exception) {
            throw new IOException("invalid XYZ atom count in " + path, exception);
        }
        if (lines.size() < count + 2) {
            throw new IOException("truncated XYZ " + path);
        }
        List<CartesianGeometry.Atom> atoms = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String[] fields = lines.get(i + 2).trim().split("\\s+");
            if (fields.length < 4) {
                throw new IOException("invalid XYZ record " + (i + 3) + " in " + path);
            }
            atoms.add(new CartesianGeometry.Atom(fields[0], Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]), Double.parseDouble(fields[3])));
        }
        return new CartesianGeometry(atoms, "angstrom");
    }

    private static List<Double> readDoubles(Path path) throws IOException {
        List<Double> values = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            for (String token : line.trim().split("\\s+")) {
                if (!token.isBlank()) {
                    values.add(Double.parseDouble(token));
                }
            }
        }
        return List.copyOf(values);
    }

    private static NumericMatrix readMatrix(Path path) throws IOException {
        List<Double> values = new ArrayList<>();
        int columns = -1;
        int rows = 0;
        for (String line : Files.readAllLines(path)) {
            if (line.isBlank()) {
                continue;
            }
            String[] fields = line.trim().split("\\s+");
            if (columns < 0) {
                columns = fields.length;
            } else if (columns != fields.length) {
                throw new IOException("ragged numeric matrix in " + path + " at row " + (rows + 1));
            }
            for (String field : fields) {
                values.add(Double.parseDouble(field));
            }
            rows++;
        }
        return new NumericMatrix(List.copyOf(values), rows, columns);
    }

    private static TerminalStep terminalStep(List<String> lines) {
        TerminalStep last = null;
        for (int i = 0; i < lines.size(); i++) {
            Matcher matcher = STEP.matcher(stripAnsi(lines.get(i)));
            if (matcher.find()) {
                last = new TerminalStep(Integer.parseInt(matcher.group(1)),
                        Double.parseDouble(matcher.group(2)), i + 1);
            }
        }
        return last;
    }

    private static int findLine(List<String> lines, String text) {
        for (int i = 0; i < lines.size(); i++) {
            if (stripAnsi(lines.get(i)).contains(text)) {
                return i + 1;
            }
        }
        return -1;
    }

    private static String stripAnsi(String value) {
        return ANSI.matcher(value).replaceAll("");
    }

    private static Path require(Path directory, String name) throws IOException {
        Path path = directory.resolve(name);
        if (!Files.isRegularFile(path)) {
            throw new IOException("required authoritative artifact missing: " + path);
        }
        return path;
    }

    private static String requiredText(JsonNode root, String field, Path path) throws IOException {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.asText().isBlank()) {
            throw new IOException("missing textual /" + field + " in " + path);
        }
        return node.asText();
    }

    private static int requiredInt(JsonNode root, String field, Path path) throws IOException {
        JsonNode node = root.get(field);
        if (node == null || !node.isIntegralNumber()) {
            throw new IOException("missing integer /" + field + " in " + path);
        }
        return node.intValue();
    }

    private static double requiredDouble(JsonNode root, String field, Path path) throws IOException {
        JsonNode node = root.get(field);
        if (node == null || !node.isNumber()) {
            throw new IOException("missing number /" + field + " in " + path);
        }
        return node.doubleValue();
    }

    private static boolean requiredBoolean(JsonNode root, String field, Path path) throws IOException {
        JsonNode node = root.get(field);
        if (node == null || !node.isBoolean()) {
            throw new IOException("missing boolean /" + field + " in " + path);
        }
        return node.booleanValue();
    }

    private static <T> RecoveredField<T> raw(String name, T value, Path path, String locator) throws IOException {
        return field(name, value, RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                path, locator, "authoritative format reader");
    }

    private static <T> RecoveredField<T> environment(String name, T value, Path path, String locator) {
        try {
            return field(name, value, RecoveryClassification.RECOVERABLE_FROM_SOFTWARE_ENVIRONMENT_ARTIFACT,
                    path, locator, "structured environment metadata reader");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static <T> RecoveredField<T> derived(String name, T value, Path path, String locator) throws IOException {
        return field(name, value, RecoveryClassification.DERIVABLE, path, locator,
                "deterministic structured-method parser");
    }

    private static <T> RecoveredField<T> field(
            String name, T value, RecoveryClassification classification, Path path,
            String locator, String method) throws IOException {
        return new RecoveredField<>(name, Optional.of(value), classification,
                List.of(source(path, locator, method)), "value parsed without scientific inference");
    }

    private static FieldSourceProvenance source(Path path, String locator, String method) throws IOException {
        return new FieldSourceProvenance(path.toString(), ArtifactChecksums.sha256(path), locator, method);
    }

    private record NumericMatrix(List<Double> values, int rows, int columns) { }
    private record TerminalStep(int cycle, double energy, int line) { }
    private record HistoricalCell(double value, int line) { }
}
