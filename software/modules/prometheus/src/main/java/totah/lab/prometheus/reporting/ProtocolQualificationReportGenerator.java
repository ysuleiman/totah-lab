package totah.lab.prometheus.reporting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.recovery.ArtifactChecksums;

/**
 * Generates a protocol qualification package from two completed, immutable
 * energy/gradient calculations. This class never invokes a calculation engine.
 */
public final class ProtocolQualificationReportGenerator {

    public static final String PLAN = "PROMETHEUS_FORCEBALANCE_PROTOCOL_PILOT_PLAN.md";
    public static final String RESULTS = "PROMETHEUS_FORCEBALANCE_PROTOCOL_PILOT_RESULTS.csv";
    public static final String UNIT_AUDIT = "PROMETHEUS_FORCEBALANCE_FORCE_UNIT_AUDIT.md";
    public static final String COMPARISON = "PROMETHEUS_FORCEBALANCE_PROTOCOL_COMPARISON.md";
    public static final String DECISION = "PROMETHEUS_FORCEBALANCE_PROTOCOL_DECISION.json";
    public static final String CHECKSUMS = "SHA256SUMS";

    private static final double ENERGY_TOLERANCE_HARTREE = 1.0e-6;
    private static final double FORCE_SIGN_TOLERANCE = 1.0e-12;
    private static final double FINITE_DIFFERENCE_TOLERANCE = 5.0e-5;
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Produces exactly six deliverables, but only after the new result exists. */
    public ProtocolQualificationDecision generate(ProtocolQualificationRequest request) throws IOException {
        requireFile(request.newProblemResult(),
                "new protocol result is not complete; qualification reports were not generated");
        List<Path> required = List.of(request.specificationManifest(), request.controlGeometry(),
                request.controlResult(), request.controlGradient(), request.problemGeometry(),
                request.historicalProblemResult(), request.selectionEvidence());
        for (Path path : required) {
            requireFile(path, "required qualification input missing");
        }

        JsonNode manifest = JSON.readTree(request.specificationManifest().toFile());
        JsonNode control = JSON.readTree(request.controlResult().toFile());
        JsonNode historical = JSON.readTree(request.historicalProblemResult().toFile());
        JsonNode problem = JSON.readTree(request.newProblemResult().toFile());
        Geometry controlGeometry = readGeometry(request.controlGeometry());
        Geometry problemGeometry = readGeometry(request.problemGeometry());
        double[][] controlGradient = readMatrix(request.controlGradient(), controlGeometry.elements().size());
        double[][] problemGradient = readVectorArray(problem, "gradient_hartree_per_bohr",
                problemGeometry.elements().size(), request.newProblemResult());
        double[][] problemForce = readVectorArray(problem, "force_hartree_per_bohr",
                problemGeometry.elements().size(), request.newProblemResult());

        String problemGeometrySha = ArtifactChecksums.sha256(request.problemGeometry());
        boolean readerIntegrity = problemGeometrySha.equals(text(problem, "input_geometry_sha256"))
                && problemGeometrySha.equals(text(historical, "final_xyz_sha256"))
                && problem.path("gradient_hartree_per_bohr").size() == problemGeometry.elements().size()
                && problem.path("force_hartree_per_bohr").size() == problemGeometry.elements().size();
        boolean converged = problem.path("scf_converged").asBoolean(false)
                && "CONVERGED".equals(problem.path("status").asText());
        boolean unitsCorrect = "hartree".equals(problem.path("units").path("energy").asText())
                && "hartree/bohr".equals(problem.path("units").path("gradient").asText())
                && "hartree/bohr".equals(problem.path("units").path("force").asText());
        double forceSignError = maxForceIdentity(problemGradient, problemForce);
        JsonNode finiteDifference = problem.path("finite_difference_audit");
        double finiteDifferenceError = finiteDifference.path("absolute_difference_hartree_per_bohr").asDouble(
                Double.POSITIVE_INFINITY);
        double historicalEnergy = number(historical, "energy_hartree", request.historicalProblemResult());
        double newEnergy = number(problem, "energy_hartree", request.newProblemResult());
        double energyDifference = Math.abs(newEnergy - historicalEnergy);
        boolean protocolMatches = protocolMatches(manifest, problem);

        ProtocolQualificationDecision decision;
        if (!readerIntegrity) {
            decision = ProtocolQualificationDecision.PROTOCOL_RESULT_READER_MISMATCH;
        } else if (!converged || !unitsCorrect || forceSignError > FORCE_SIGN_TOLERANCE
                || finiteDifferenceError > FINITE_DIFFERENCE_TOLERANCE) {
            decision = ProtocolQualificationDecision.PROTOCOL_EXECUTION_MISMATCH;
        } else if (!protocolMatches) {
            decision = ProtocolQualificationDecision.PROTOCOL_NOT_COMPARABLE_TO_EXISTING_EVIDENCE;
        } else if (energyDifference <= ENERGY_TOLERANCE_HARTREE) {
            decision = ProtocolQualificationDecision.PROTOCOL_QUALIFIED_FOR_SCALEUP;
        } else {
            decision = ProtocolQualificationDecision.PROTOCOL_QUALIFIED_WITH_DOCUMENTED_METHOD_SHIFT;
        }

        Files.createDirectories(request.outputDirectory());
        writePlan(request);
        writeResults(request, control, historical, problem, controlGeometry, problemGeometry,
                controlGradient, problemGradient);
        writeUnitAudit(request, problem, forceSignError, finiteDifferenceError, unitsCorrect);
        writeComparison(request, historicalEnergy, newEnergy, energyDifference,
                controlGeometry, problemGeometry, decision);
        writeDecision(request, decision, readerIntegrity, converged, protocolMatches, unitsCorrect,
                forceSignError, finiteDifferenceError, energyDifference);
        writeChecksums(request.outputDirectory());
        return decision;
    }

    private static void writePlan(ProtocolQualificationRequest request) throws IOException {
        String content = """
                # ForceBalance protocol qualification pilot

                This is a two-geometry protocol qualification, not a parameter fit and not a QM campaign.

                Acceptance gates were fixed in software before result inspection:

                - exact input-geometry SHA-256 and vector lengths;
                - converged SCF result;
                - energy units `hartree`, gradient/force units `hartree/bohr`;
                - `force = -gradient` maximum absolute error <= 1e-12;
                - finite-difference/analytic gradient error <= 5e-5 hartree/bohr;
                - historical/new fixed-geometry energy agreement <= 1e-6 hartree for direct qualification.

                The control is an existing verified QM-native minimum. The problem geometry is the preregistered
                Unit 05L state selected by the largest reported C2-H11 ordinary-LJ response (-11.584998 kcal/mol).
                No calculation is launched by this report generator.

                ## Authoritative inputs

                """ + sources(request);
        write(request.outputDirectory().resolve(PLAN), content);
    }

    private static void writeResults(ProtocolQualificationRequest request, JsonNode control,
            JsonNode historical, JsonNode problem, Geometry controlGeometry, Geometry problemGeometry,
            double[][] controlGradient, double[][] problemGradient) throws IOException {
        List<String> atoms = request.localAtomIndicesOneBased().entrySet().stream()
                .sorted(Map.Entry.comparingByValue()).map(Map.Entry::getKey).toList();
        StringBuilder header = new StringBuilder("geometry_id,role,geometry_sha256,energy_hartree,"
                + "historical_energy_hartree,energy_difference_hartree,phi_deg,psi_deg,local_angle_deg");
        for (String atom : atoms) {
            header.append(',').append(atom).append("_gradient_x_hartree_per_bohr,")
                    .append(atom).append("_gradient_y_hartree_per_bohr,")
                    .append(atom).append("_gradient_z_hartree_per_bohr");
        }
        StringBuilder csv = new StringBuilder(header).append('\n');
        appendResultRow(csv, "MIN02", "REUSED_PROTOCOL_CONTROL", request.controlGeometry(),
                number(control, "energy_hartree", request.controlResult()), Double.NaN, controlGeometry,
                controlGradient, atoms, request.localAtomIndicesOneBased());
        appendResultRow(csv, "phi060_psi060_B_m10", "EXECUTED_PROBLEM_GEOMETRY", request.problemGeometry(),
                number(problem, "energy_hartree", request.newProblemResult()),
                number(historical, "energy_hartree", request.historicalProblemResult()), problemGeometry,
                problemGradient, atoms, request.localAtomIndicesOneBased());
        write(request.outputDirectory().resolve(RESULTS), csv.toString());
    }

    private static void appendResultRow(StringBuilder csv, String id, String role, Path geometryPath,
            double energy, double historical, Geometry geometry, double[][] gradient, List<String> atoms,
            Map<String, Integer> indices) throws IOException {
        csv.append(id).append(',').append(role).append(',').append(ArtifactChecksums.sha256(geometryPath))
                .append(',').append(format(energy)).append(',')
                .append(Double.isNaN(historical) ? "" : format(historical)).append(',')
                .append(Double.isNaN(historical) ? "" : format(Math.abs(energy - historical))).append(',')
                .append(format(dihedral(geometry.xyz(), 56, 26, 10, 9))).append(',')
                .append(format(dihedral(geometry.xyz(), 26, 10, 9, 8))).append(',')
                .append(format(angle(geometry.xyz(), 11, 10, 26)));
        for (String atom : atoms) {
            double[] vector = gradient[indices.get(atom) - 1];
            csv.append(',').append(format(vector[0])).append(',').append(format(vector[1]))
                    .append(',').append(format(vector[2]));
        }
        csv.append('\n');
    }

    private static void writeUnitAudit(ProtocolQualificationRequest request, JsonNode problem,
            double signError, double finiteDifferenceError, boolean unitsCorrect) throws IOException {
        JsonNode fd = problem.path("finite_difference_audit");
        String content = """
                # Force and unit audit

                - Energy unit: `%s`
                - Gradient unit: `%s`
                - Force unit: `%s`
                - Declared force definition: `%s`
                - Maximum `abs(gradient + force)`: %.12g
                - Finite-difference coordinate: `%s`
                - Finite-difference step: %.12g bohr
                - Central difference: %.12g hartree/bohr
                - Analytic gradient projection: %.12g hartree/bohr
                - Absolute finite-difference discrepancy: %.12g hartree/bohr
                - Unit gate: %s
                - Sign gate: %s
                - Finite-difference gate: %s

                Source: `%s` (SHA-256 `%s`), structured fields `/units`, `/force_definition`,
                `/gradient_force_identity_max_abs`, and `/finite_difference_audit`.
                """.formatted(problem.path("units").path("energy").asText(),
                problem.path("units").path("gradient").asText(),
                problem.path("units").path("force").asText(), problem.path("force_definition").asText(),
                signError, fd.path("coordinate").asText(), fd.path("step_bohr").asDouble(),
                fd.path("central_difference_hartree_per_bohr").asDouble(),
                fd.path("analytic_gradient_projection_hartree_per_bohr").asDouble(), finiteDifferenceError,
                pass(unitsCorrect), pass(signError <= FORCE_SIGN_TOLERANCE),
                pass(finiteDifferenceError <= FINITE_DIFFERENCE_TOLERANCE), request.newProblemResult(),
                ArtifactChecksums.sha256(request.newProblemResult()));
        write(request.outputDirectory().resolve(UNIT_AUDIT), content);
    }

    private static void writeComparison(ProtocolQualificationRequest request, double historicalEnergy,
            double newEnergy, double difference, Geometry controlGeometry, Geometry problemGeometry,
            ProtocolQualificationDecision decision) throws IOException {
        String content = """
                # Protocol comparison

                ## Problem geometry

                - Historical Unit 05L energy: %.14f hartree
                - New fixed-geometry energy: %.14f hartree
                - Absolute difference: %.12g hartree
                - Geometry SHA-256: `%s`
                - phi / psi / local angle: %.8f / %.8f / %.8f degrees

                ## MIN02 control

                - Geometry SHA-256: `%s`
                - phi / psi / local angle: %.8f / %.8f / %.8f degrees

                ## Classification

                `%s`

                Historical and new values remain separately sourced; agreement does not erase their provenance.

                %s
                """.formatted(historicalEnergy, newEnergy, difference,
                ArtifactChecksums.sha256(request.problemGeometry()),
                dihedral(problemGeometry.xyz(), 56, 26, 10, 9),
                dihedral(problemGeometry.xyz(), 26, 10, 9, 8), angle(problemGeometry.xyz(), 11, 10, 26),
                ArtifactChecksums.sha256(request.controlGeometry()),
                dihedral(controlGeometry.xyz(), 56, 26, 10, 9),
                dihedral(controlGeometry.xyz(), 26, 10, 9, 8), angle(controlGeometry.xyz(), 11, 10, 26),
                decision.name(), sources(request));
        write(request.outputDirectory().resolve(COMPARISON), content);
    }

    private static void writeDecision(ProtocolQualificationRequest request,
            ProtocolQualificationDecision decision, boolean readerIntegrity, boolean converged,
            boolean protocolMatches, boolean unitsCorrect, double signError, double finiteDifferenceError,
            double energyDifference) throws IOException {
        ObjectNode root = JSON.createObjectNode();
        root.put("classification", decision.name());
        root.put("scaleup_authorized", decision == ProtocolQualificationDecision.PROTOCOL_QUALIFIED_FOR_SCALEUP
                || decision == ProtocolQualificationDecision.PROTOCOL_QUALIFIED_WITH_DOCUMENTED_METHOD_SHIFT);
        root.put("reader_integrity_pass", readerIntegrity);
        root.put("scf_convergence_pass", converged);
        root.put("protocol_identity_pass", protocolMatches);
        root.put("units_pass", unitsCorrect);
        root.put("force_sign_max_abs", signError);
        root.put("force_sign_pass", signError <= FORCE_SIGN_TOLERANCE);
        root.put("finite_difference_error_hartree_per_bohr", finiteDifferenceError);
        root.put("finite_difference_pass", finiteDifferenceError <= FINITE_DIFFERENCE_TOLERANCE);
        root.put("historical_energy_difference_hartree", energyDifference);
        root.put("historical_energy_direct_agreement_pass", energyDifference <= ENERGY_TOLERANCE_HARTREE);
        root.put("new_result_path", request.newProblemResult().toString());
        root.put("new_result_sha256", ArtifactChecksums.sha256(request.newProblemResult()));
        JSON.writerWithDefaultPrettyPrinter().writeValue(request.outputDirectory().resolve(DECISION).toFile(), root);
    }

    private static boolean protocolMatches(JsonNode manifest, JsonNode result) {
        JsonNode protocol = result.path("protocol");
        if (!"PBE".equalsIgnoreCase(protocol.path("method").asText())
                || !"def2-SVP".equalsIgnoreCase(protocol.path("basis").asText())
                || !"D3(BJ)".equalsIgnoreCase(protocol.path("dispersion").asText())) {
            return false;
        }
        for (JsonNode calculation : manifest.path("calculations")) {
            if (calculation.path("specification_id").asText().contains("phi060-psi060-B-m10")) {
                return calculation.path("protocol").asText().startsWith("PBE|def2-SVP|D3(BJ)|")
                        && calculation.path("formal_charge").asInt(Integer.MIN_VALUE) == 0
                        && calculation.path("multiplicity").asInt(Integer.MIN_VALUE) == 1;
            }
        }
        return false;
    }

    private static double maxForceIdentity(double[][] gradient, double[][] force) {
        double maximum = 0.0;
        for (int atom = 0; atom < gradient.length; atom++) {
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                maximum = Math.max(maximum, Math.abs(gradient[atom][coordinate] + force[atom][coordinate]));
            }
        }
        return maximum;
    }

    private static Geometry readGeometry(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        int count = Integer.parseInt(lines.getFirst().trim());
        List<String> elements = new ArrayList<>();
        double[][] xyz = new double[count][3];
        for (int atom = 0; atom < count; atom++) {
            String[] fields = lines.get(atom + 2).trim().split("\\s+");
            elements.add(fields[0]);
            for (int coordinate = 0; coordinate < 3; coordinate++) {
                xyz[atom][coordinate] = Double.parseDouble(fields[coordinate + 1]);
            }
        }
        return new Geometry(List.copyOf(elements), xyz);
    }

    private static double[][] readMatrix(Path path, int expectedRows) throws IOException {
        List<String> lines = Files.readAllLines(path).stream().filter(line -> !line.isBlank()).toList();
        if (lines.size() != expectedRows) {
            throw new IOException("gradient row count mismatch: " + path);
        }
        double[][] result = new double[expectedRows][3];
        for (int row = 0; row < expectedRows; row++) {
            String[] fields = lines.get(row).trim().split("\\s+");
            if (fields.length != 3) {
                throw new IOException("gradient column count mismatch: " + path);
            }
            for (int column = 0; column < 3; column++) {
                result[row][column] = Double.parseDouble(fields[column]);
            }
        }
        return result;
    }

    private static double[][] readVectorArray(JsonNode root, String field, int expectedRows, Path path)
            throws IOException {
        JsonNode array = root.path(field);
        if (!array.isArray() || array.size() != expectedRows) {
            throw new IOException(field + " vector count mismatch: " + path);
        }
        double[][] result = new double[expectedRows][3];
        for (int row = 0; row < expectedRows; row++) {
            if (!array.get(row).isArray() || array.get(row).size() != 3) {
                throw new IOException(field + " vector width mismatch: " + path);
            }
            for (int column = 0; column < 3; column++) {
                result[row][column] = array.get(row).get(column).asDouble();
            }
        }
        return result;
    }

    private static double dihedral(double[][] xyz, int a, int b, int c, int d) {
        double[] b0 = subtract(xyz[a - 1], xyz[b - 1]);
        double[] b1 = subtract(xyz[c - 1], xyz[b - 1]);
        double[] b2 = subtract(xyz[d - 1], xyz[c - 1]);
        double[] unit = scale(b1, 1.0 / norm(b1));
        double[] v = subtract(b0, scale(unit, dot(b0, unit)));
        double[] w = subtract(b2, scale(unit, dot(b2, unit)));
        return Math.toDegrees(Math.atan2(dot(cross(unit, v), w), dot(v, w)));
    }

    private static double angle(double[][] xyz, int a, int b, int c) {
        double[] x = subtract(xyz[a - 1], xyz[b - 1]);
        double[] y = subtract(xyz[c - 1], xyz[b - 1]);
        double cosine = dot(x, y) / (norm(x) * norm(y));
        return Math.toDegrees(Math.acos(Math.max(-1.0, Math.min(1.0, cosine))));
    }

    private static double[] subtract(double[] a, double[] b) {
        return new double[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
    }

    private static double[] scale(double[] vector, double factor) {
        return new double[]{vector[0] * factor, vector[1] * factor, vector[2] * factor};
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2],
                a[0] * b[1] - a[1] * b[0]};
    }

    private static double norm(double[] vector) {
        return Math.sqrt(dot(vector, vector));
    }

    private static String sources(ProtocolQualificationRequest request) throws IOException {
        StringBuilder text = new StringBuilder();
        for (Path source : List.of(request.specificationManifest(), request.controlGeometry(),
                request.controlResult(), request.controlGradient(), request.problemGeometry(),
                request.historicalProblemResult(), request.newProblemResult(), request.selectionEvidence())) {
            text.append("- `").append(source).append("` — SHA-256 `")
                    .append(ArtifactChecksums.sha256(source)).append("`\n");
        }
        return text.toString();
    }

    private static void writeChecksums(Path output) throws IOException {
        List<Path> artifacts = List.of(PLAN, RESULTS, UNIT_AUDIT, COMPARISON, DECISION).stream()
                .map(output::resolve).sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
        StringBuilder sums = new StringBuilder();
        for (Path artifact : artifacts) {
            sums.append(ArtifactChecksums.sha256(artifact)).append("  ")
                    .append(artifact.getFileName()).append('\n');
        }
        write(output.resolve(CHECKSUMS), sums.toString());
    }

    private static void write(Path path, String content) throws IOException {
        Files.writeString(path, content.endsWith("\n") ? content : content + "\n", StandardCharsets.UTF_8);
    }

    private static void requireFile(Path path, String message) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(message + ": " + path);
        }
    }

    private static String text(JsonNode root, String field) throws IOException {
        JsonNode value = root.get(field);
        if (value == null || !value.isValueNode() || value.asText().isBlank()) {
            throw new IOException("missing string field " + field);
        }
        return value.asText();
    }

    private static double number(JsonNode root, String field, Path source) throws IOException {
        JsonNode value = root.get(field);
        if (value == null || !value.isNumber()) {
            throw new IOException("missing numeric field " + field + " in " + source);
        }
        return value.asDouble();
    }

    private static String pass(boolean value) {
        return value ? "PASS" : "FAIL";
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.14g", value);
    }

    private record Geometry(List<String> elements, double[][] xyz) {
    }
}
