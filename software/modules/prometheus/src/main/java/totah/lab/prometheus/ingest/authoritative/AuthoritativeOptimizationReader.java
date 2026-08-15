package totah.lab.prometheus.ingest.authoritative;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Comparator;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.recovery.FieldSourceProvenance;
import totah.lab.prometheus.recovery.RecoveredField;
import totah.lab.prometheus.recovery.RecoveryClassification;

/**
 * Reads Unit 05H/05L constrained optimizations from their calculation inputs,
 * constraint files, trajectories, final geometries and machine result files.
 * Reports and summary CSVs are deliberately not inputs to reconstruction.
 */
public final class AuthoritativeOptimizationReader {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Reads every directly contained point having the complete raw optimization artifact set. */
    public List<AuthoritativeOptimizationRecord> readPointSet(Path pointsDirectory) throws IOException {
        Objects.requireNonNull(pointsDirectory, "pointsDirectory");
        try (Stream<Path> children = Files.list(pointsDirectory)) {
            List<Path> pointDirectories = children.filter(Files::isDirectory)
                    .filter(path -> Files.isRegularFile(path.resolve("input.json")))
                    .filter(path -> Files.isRegularFile(path.resolve("result.json")))
                    .filter(path -> Files.isRegularFile(path.resolve("trajectory.json")))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            List<AuthoritativeOptimizationRecord> records = new ArrayList<>();
            for (Path point : pointDirectories) {
                records.add(read(point));
            }
            return List.copyOf(records);
        }
    }

    public AuthoritativeOptimizationRecord read(Path pointDirectory) throws IOException {
        Objects.requireNonNull(pointDirectory, "pointDirectory");
        Path input = required(pointDirectory.resolve("input.json"));
        Path result = required(pointDirectory.resolve("result.json"));
        Path constraints = required(pointDirectory.resolve("constraints.txt"));
        Path geometry = required(pointDirectory.resolve("final.xyz"));
        Path trajectory = required(pointDirectory.resolve("trajectory.json"));
        JsonNode in = JSON.readTree(input.toFile());
        JsonNode out = JSON.readTree(result.toFile());
        JsonNode steps = JSON.readTree(trajectory.toFile());

        List<String> notes = new ArrayList<>();
        double resultEnergy = requiredDouble(out, "energy_hartree", result);
        if (!steps.isArray() || steps.isEmpty()) {
            throw new IOException("trajectory contains no optimization cycles: " + trajectory);
        }
        JsonNode lastStep = steps.get(steps.size() - 1);
        double trajectoryEnergy = requiredDouble(lastStep, "energy_hartree", trajectory);
        double energyDifference = Math.abs(resultEnergy - trajectoryEnergy);
        notes.add("result-vs-final-trajectory energy difference=" + energyDifference + " hartree");
        if (energyDifference > 1.0e-7) {
            throw new IOException("result energy does not match final trajectory energy: " + pointDirectory);
        }

        String computedGeometryHash = ArtifactChecksums.sha256(geometry);
        String recordedGeometryHash = requiredText(out, "final_xyz_sha256", result);
        if (!computedGeometryHash.equalsIgnoreCase(recordedGeometryHash)) {
            throw new IOException("final geometry checksum mismatch: " + geometry);
        }
        int cycles = requiredInt(out, "cycles", result);
        if (cycles != steps.size()) {
            throw new IOException("cycle count does not match trajectory length: " + pointDirectory);
        }

        Map<String, String> versions = new LinkedHashMap<>();
        JsonNode software = in.path("software");
        if (!software.isObject()) {
            throw new IOException("input software object missing: " + input);
        }
        Iterator<Map.Entry<String, JsonNode>> fields = software.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            versions.put(field.getKey(), field.getValue().asText());
        }

        List<String> constraintLines = Files.readAllLines(constraints).stream()
                .map(String::trim)
                .filter(line -> !line.isEmpty() && !line.equals("$set") && !line.equals("$end"))
                .toList();
        if (constraintLines.isEmpty()) {
            throw new IOException("constraint file contains no constraints: " + constraints);
        }

        return new AuthoritativeOptimizationRecord(
                raw("point_id", requiredText(in, "point", input), input, "/point"),
                raw("formal_charge", requiredInt(in, "charge", input), input, "/charge"),
                raw("multiplicity", requiredInt(in, "multiplicity", input), input, "/multiplicity"),
                raw("electronic_structure_method", requiredText(in, "geometry_method", input),
                        input, "/geometry_method"),
                software("software_versions", Map.copyOf(versions), input, "/software"),
                raw("constraints", constraintLines, constraints, "lines 1-" + Files.readAllLines(constraints).size()),
                raw("final_energy_hartree", resultEnergy, result, "/energy_hartree",
                        source(trajectory, "/" + (steps.size() - 1) + "/energy_hartree", "JSON structured field")),
                raw("scf_converged", requiredBoolean(out, "scf_converged", result), result, "/scf_converged"),
                raw("optimization_status", requiredText(out, "status", result), result, "/status"),
                raw("optimization_cycles", cycles, result, "/cycles",
                        source(trajectory, "array length", "JSON array length")),
                raw("final_geometry_sha256", computedGeometryHash, geometry, "entire file",
                        source(result, "/final_xyz_sha256", "JSON structured field")),
                notes);
    }

    /** Compares recovered absolute energies with an explicitly identified historical CSV column. */
    public List<HistoricalValueComparison> compareHistoricalAbsoluteEnergies(
            List<AuthoritativeOptimizationRecord> records,
            Path historicalCsv,
            String idColumn,
            String energyColumn,
            double tolerance) throws IOException {
        List<String> lines = Files.readAllLines(historicalCsv);
        if (lines.isEmpty()) {
            throw new IOException("empty historical CSV: " + historicalCsv);
        }
        String[] header = lines.getFirst().split(",", -1);
        int idIndex = indexOf(header, idColumn, historicalCsv);
        int energyIndex = indexOf(header, energyColumn, historicalCsv);
        Map<String, HistoricalCell> cells = new LinkedHashMap<>();
        for (int line = 1; line < lines.size(); line++) {
            if (lines.get(line).isBlank()) {
                continue;
            }
            String[] values = lines.get(line).split(",", -1);
            if (values.length != header.length) {
                throw new IOException("malformed historical CSV row at line " + (line + 1));
            }
            cells.put(values[idIndex], new HistoricalCell(Double.parseDouble(values[energyIndex]), line + 1));
        }
        String checksum = ArtifactChecksums.sha256(historicalCsv);
        List<HistoricalValueComparison> comparisons = new ArrayList<>();
        for (AuthoritativeOptimizationRecord record : records) {
            String id = record.pointId().value().orElseThrow();
            HistoricalCell cell = cells.get(id);
            if (cell == null) {
                throw new IOException("historical table missing optimization " + id);
            }
            double recovered = record.finalEnergyHartree().value().orElseThrow();
            double difference = Math.abs(recovered - cell.value());
            comparisons.add(new HistoricalValueComparison(id, "final_energy_hartree", recovered, cell.value(),
                    difference, difference <= tolerance, historicalCsv.toString(), checksum,
                    "line " + cell.line() + ", column " + energyColumn));
        }
        return List.copyOf(comparisons);
    }

    /**
     * Reconstructs the Unit 05L relative energies from raw absolute energies and
     * caller-supplied raw 05H parent energies, then compares them with the legacy table.
     */
    public List<HistoricalValueComparison> compareHistoricalRelativeEnergies(
            List<AuthoritativeOptimizationRecord> records,
            Map<Integer, Double> parentEnergyHartreeByPhi,
            Path historicalCsv,
            double toleranceKcalMol) throws IOException {
        List<String> lines = Files.readAllLines(historicalCsv);
        if (lines.isEmpty()) {
            throw new IOException("empty historical CSV: " + historicalCsv);
        }
        String[] header = lines.getFirst().split(",", -1);
        int idIndex = indexOf(header, "point", historicalCsv);
        int phiIndex = indexOf(header, "parent_phi_deg", historicalCsv);
        int deltaIndex = indexOf(header, "deltaE_vs_parent_kcal_mol", historicalCsv);
        Map<String, RelativeCell> cells = new LinkedHashMap<>();
        for (int line = 1; line < lines.size(); line++) {
            if (lines.get(line).isBlank()) {
                continue;
            }
            String[] values = lines.get(line).split(",", -1);
            if (values.length != header.length) {
                throw new IOException("malformed historical CSV row at line " + (line + 1));
            }
            cells.put(values[idIndex], new RelativeCell(Integer.parseInt(values[phiIndex]),
                    Double.parseDouble(values[deltaIndex]), line + 1));
        }
        String checksum = ArtifactChecksums.sha256(historicalCsv);
        List<HistoricalValueComparison> comparisons = new ArrayList<>();
        for (AuthoritativeOptimizationRecord record : records) {
            String id = record.pointId().value().orElseThrow();
            RelativeCell cell = cells.get(id);
            if (cell == null) {
                throw new IOException("historical table missing optimization " + id);
            }
            Double parent = parentEnergyHartreeByPhi.get(cell.parentPhi());
            if (parent == null) {
                throw new IOException("raw parent energy missing for phi=" + cell.parentPhi());
            }
            double recovered = (record.finalEnergyHartree().value().orElseThrow() - parent) * 627.5094740631;
            double difference = Math.abs(recovered - cell.value());
            comparisons.add(new HistoricalValueComparison(id, "deltaE_vs_parent_kcal_mol", recovered,
                    cell.value(), difference, difference <= toleranceKcalMol, historicalCsv.toString(), checksum,
                    "line " + cell.line() + ", column deltaE_vs_parent_kcal_mol"));
        }
        return List.copyOf(comparisons);
    }

    private static int indexOf(String[] header, String column, Path csv) throws IOException {
        for (int i = 0; i < header.length; i++) {
            if (header[i].equals(column)) {
                return i;
            }
        }
        throw new IOException("missing column " + column + " in " + csv);
    }

    private static Path required(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException("required raw artifact missing: " + path);
        }
        return path;
    }

    private static String requiredText(JsonNode node, String field, Path source) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isValueNode() || value.asText().isBlank()) {
            throw new IOException("missing field " + field + " in " + source);
        }
        return value.asText();
    }

    private static double requiredDouble(JsonNode node, String field, Path source) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IOException("missing numeric field " + field + " in " + source);
        }
        return value.doubleValue();
    }

    private static int requiredInt(JsonNode node, String field, Path source) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isIntegralNumber()) {
            throw new IOException("missing integer field " + field + " in " + source);
        }
        return value.intValue();
    }

    private static boolean requiredBoolean(JsonNode node, String field, Path source) throws IOException {
        JsonNode value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IOException("missing boolean field " + field + " in " + source);
        }
        return value.booleanValue();
    }

    private static <T> RecoveredField<T> raw(String name, T value, Path path, String locator,
            FieldSourceProvenance... corroborating) throws IOException {
        List<FieldSourceProvenance> provenance = new ArrayList<>();
        provenance.add(source(path, locator, locator.startsWith("/") ? "JSON structured field" : "raw file"));
        provenance.addAll(List.of(corroborating));
        return new RecoveredField<>(name, Optional.of(value), RecoveryClassification.RECOVERABLE_FROM_RAW_ARTIFACT,
                provenance, "Recovered from authoritative calculation artifact");
    }

    private static <T> RecoveredField<T> software(String name, T value, Path path, String locator)
            throws IOException {
        return new RecoveredField<>(name, Optional.of(value),
                RecoveryClassification.RECOVERABLE_FROM_SOFTWARE_ENVIRONMENT_ARTIFACT,
                List.of(source(path, locator, "JSON structured field")),
                "Recovered from software metadata serialized with the calculation input");
    }

    private static FieldSourceProvenance source(Path path, String locator, String method) throws IOException {
        return new FieldSourceProvenance(path.toString(), ArtifactChecksums.sha256(path), locator, method);
    }

    private record HistoricalCell(double value, int line) {
    }

    private record RelativeCell(int parentPhi, double value, int line) {
    }
}
