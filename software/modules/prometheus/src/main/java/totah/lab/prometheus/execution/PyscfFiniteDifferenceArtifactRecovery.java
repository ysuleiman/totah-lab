package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.recovery.ArtifactChecksums;

/**
 * Recovers finite-difference auxiliary artifacts from a completed legacy pilot
 * result without rerunning electronic structure. Every recovered value is tied
 * to the immutable input/result/log artifacts and their checksums.
 */
final class PyscfFiniteDifferenceArtifactRecovery {

    private static final Pattern CONVERGED_ENERGY = Pattern.compile(
            "converged SCF energy\\s*=\\s*([-+0-9.Ee]+)");
    private static final double BOHR_TO_ANGSTROM = 0.529177210903;
    private final ObjectMapper mapper = new ObjectMapper();

    void recover(Path directory) throws IOException {
        Path plusJson = directory.resolve("finite_difference_plus.json");
        Path minusJson = directory.resolve("finite_difference_minus.json");
        Path plusXyz = directory.resolve("finite_difference_plus.xyz");
        Path minusXyz = directory.resolve("finite_difference_minus.xyz");
        if (Files.isRegularFile(plusJson) && Files.isRegularFile(minusJson)
                && Files.isRegularFile(plusXyz) && Files.isRegularFile(minusXyz)) {
            return;
        }

        Path resultPath = directory.resolve("result.json");
        Path logPath = directory.resolve("raw_combined.log");
        Path geometryPath = directory.resolve("input_geometry.xyz");
        Path specificationPath = directory.resolve("calculation_specification.json");
        JsonNode result = mapper.readTree(resultPath.toFile());
        JsonNode audit = result.path("finite_difference_audit");
        double stepBohr = requiredDouble(audit, "step_bohr", resultPath);
        double[] energies = energies(audit, logPath);
        XyzGeometry geometry = readXyz(geometryPath);
        double[][] plus = copy(geometry.coordinates());
        double[][] minus = copy(geometry.coordinates());
        double[] axis = unitVector(geometry.coordinates()[25], geometry.coordinates()[55]);
        for (int component = 0; component < 3; component++) {
            double displacement = axis[component] * stepBohr * BOHR_TO_ANGSTROM;
            plus[55][component] += displacement;
            minus[55][component] -= displacement;
        }
        writeXyz(plusXyz, geometry.elements(), plus,
                "VALIDATION_AUXILIARY recovered H56 + displacement along S26-to-H56");
        writeXyz(minusXyz, geometry.elements(), minus,
                "VALIDATION_AUXILIARY recovered H56 - displacement along S26-to-H56");
        writeAuxiliary(plusJson, "plus", energies[0], plusXyz, resultPath, logPath,
                geometryPath, specificationPath, stepBohr);
        writeAuxiliary(minusJson, "minus", energies[1], minusXyz, resultPath, logPath,
                geometryPath, specificationPath, stepBohr);
    }

    private double[] energies(JsonNode audit, Path log) throws IOException {
        if (audit.path("plus_energy_hartree").isNumber()
                && audit.path("minus_energy_hartree").isNumber()) {
            return new double[] {audit.path("plus_energy_hartree").asDouble(),
                    audit.path("minus_energy_hartree").asDouble()};
        }
        List<Double> values = new ArrayList<>();
        for (String line : Files.readAllLines(log, StandardCharsets.UTF_8)) {
            Matcher matcher = CONVERGED_ENERGY.matcher(line);
            if (matcher.find()) values.add(Double.parseDouble(matcher.group(1)));
        }
        if (values.size() < 3) {
            throw new IOException("cannot recover primary/plus/minus SCF energies from " + log);
        }
        return new double[] {values.get(values.size() - 2), values.get(values.size() - 1)};
    }

    private void writeAuxiliary(Path target, String side, double energy, Path xyz,
            Path result, Path log, Path input, Path specification, double stepBohr) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("role", "VALIDATION_AUXILIARY");
        root.put("side", side);
        root.put("energy_hartree", energy);
        root.put("energy_units", "hartree");
        root.put("step_bohr", stepBohr);
        root.put("geometry_sha256", ArtifactChecksums.sha256(xyz));
        root.put("recovery", "DERIVED_FROM_COMPLETED_RAW_ARTIFACTS_NO_QM_RERUN");
        ObjectNode provenance = root.putObject("provenance");
        provenance.put("result_json", result.toString());
        provenance.put("result_json_sha256", ArtifactChecksums.sha256(result));
        provenance.put("raw_log", log.toString());
        provenance.put("raw_log_sha256", ArtifactChecksums.sha256(log));
        provenance.put("input_geometry", input.toString());
        provenance.put("input_geometry_sha256", ArtifactChecksums.sha256(input));
        provenance.put("calculation_specification", specification.toString());
        provenance.put("calculation_specification_sha256", ArtifactChecksums.sha256(specification));
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);
    }

    private static XyzGeometry readXyz(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        int count = Integer.parseInt(lines.getFirst().trim());
        List<String> elements = new ArrayList<>();
        double[][] coordinates = new double[count][3];
        for (int atom = 0; atom < count; atom++) {
            String[] fields = lines.get(atom + 2).trim().split("\\s+");
            elements.add(fields[0]);
            for (int component = 0; component < 3; component++) {
                coordinates[atom][component] = Double.parseDouble(fields[component + 1]);
            }
        }
        return new XyzGeometry(List.copyOf(elements), coordinates);
    }

    private static void writeXyz(Path path, List<String> elements, double[][] coordinates,
            String comment) throws IOException {
        StringBuilder text = new StringBuilder().append(elements.size()).append('\n')
                .append(comment).append('\n');
        for (int atom = 0; atom < elements.size(); atom++) {
            text.append(String.format(Locale.ROOT, "%-2s % .15f % .15f % .15f%n",
                    elements.get(atom), coordinates[atom][0], coordinates[atom][1], coordinates[atom][2]));
        }
        Files.writeString(path, text, StandardCharsets.UTF_8);
    }

    private static double requiredDouble(JsonNode node, String field, Path source) throws IOException {
        if (!node.path(field).isNumber()) throw new IOException("missing " + field + " in " + source);
        return node.path(field).asDouble();
    }

    private static double[] unitVector(double[] start, double[] end) {
        double[] vector = {end[0] - start[0], end[1] - start[1], end[2] - start[2]};
        double norm = Math.sqrt(vector[0] * vector[0] + vector[1] * vector[1] + vector[2] * vector[2]);
        for (int i = 0; i < 3; i++) vector[i] /= norm;
        return vector;
    }

    private static double[][] copy(double[][] source) {
        double[][] copy = new double[source.length][3];
        for (int atom = 0; atom < source.length; atom++) copy[atom] = source[atom].clone();
        return copy;
    }

    private record XyzGeometry(List<String> elements, double[][] coordinates) { }
}
