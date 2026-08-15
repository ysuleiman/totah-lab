package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import totah.lab.prometheus.recovery.ArtifactChecksums;

/** Reads and validates the machine-readable output of the locked PySCF runner. */
public final class PyscfEnergyGradientResultReader {

    private static final double SIGN_TOLERANCE = 1.0e-12;
    private final ObjectMapper mapper = new ObjectMapper();

    public PyscfEnergyGradientResult read(Path resultJson) throws IOException {
        JsonNode root = mapper.readTree(resultJson.toFile());
        List<List<Double>> gradient = matrix(root.path("gradient_hartree_per_bohr"));
        List<List<Double>> force = matrix(root.path("force_hartree_per_bohr"));
        if (gradient.size() != force.size() || gradient.isEmpty()) {
            throw new IOException("gradient/force dimensions disagree or are empty: " + resultJson);
        }
        double normSquared = 0.0;
        for (int atom = 0; atom < gradient.size(); atom++) {
            if (gradient.get(atom).size() != 3 || force.get(atom).size() != 3) {
                throw new IOException("gradient and force must be N x 3: " + resultJson);
            }
            for (int xyz = 0; xyz < 3; xyz++) {
                double g = gradient.get(atom).get(xyz);
                double f = force.get(atom).get(xyz);
                if (Math.abs(g + f) > SIGN_TOLERANCE) {
                    throw new IOException("force != -gradient at atom " + (atom + 1)
                            + " component " + xyz + ": " + resultJson);
                }
                normSquared += g * g;
            }
        }
        double declaredNorm = requiredDouble(root, "gradient_norm_hartree_per_bohr", resultJson);
        if (Math.abs(Math.sqrt(normSquared) - declaredNorm) > 1.0e-10) {
            throw new IOException("declared gradient norm disagrees with vector: " + resultJson);
        }
        Path geometry = resultJson.resolveSibling("input_geometry.xyz");
        String expectedGeometrySha = requiredText(root, "input_geometry_sha256", resultJson);
        if (!Files.isRegularFile(geometry)
                || !ArtifactChecksums.sha256(geometry).equals(expectedGeometrySha)) {
            throw new IOException("input geometry checksum mismatch: " + geometry);
        }
        Path specification = resultJson.resolveSibling("calculation_specification.json");
        String specificationSha = requiredText(root, "calculation_specification_sha256", resultJson);
        if (!Files.isRegularFile(specification)
                || !ArtifactChecksums.sha256(specification).equals(specificationSha)) {
            throw new IOException("calculation specification checksum mismatch: " + specification);
        }
        JsonNode finiteDifference = root.path("finite_difference_audit");
        double plusEnergy = auxiliaryEnergy(resultJson.resolveSibling("finite_difference_plus.json"),
                finiteDifference, "plus_energy_hartree", resultJson);
        double minusEnergy = auxiliaryEnergy(resultJson.resolveSibling("finite_difference_minus.json"),
                finiteDifference, "minus_energy_hartree", resultJson);
        return new PyscfEnergyGradientResult(
                requiredText(root, "specification_checksum", resultJson),
                requiredText(root, "geometry_identity", resultJson),
                expectedGeometrySha,
                requiredDouble(root, "energy_hartree", resultJson),
                gradient,
                force,
                declaredNorm,
                requiredDouble(finiteDifference, "central_difference_hartree_per_bohr", resultJson),
                requiredDouble(finiteDifference, "analytic_gradient_projection_hartree_per_bohr", resultJson),
                requiredDouble(finiteDifference, "absolute_difference_hartree_per_bohr", resultJson),
                plusEnergy,
                minusEnergy,
                root.path("scf_converged").asBoolean(false),
                root.path("software").path("pyscf").asText("unknown"),
                root.path("software").path("dftd3").asText("unknown"),
                resultJson.toAbsolutePath().normalize());
    }

    private double auxiliaryEnergy(Path auxiliary, JsonNode audit, String field, Path result) throws IOException {
        if (audit.path(field).isNumber()) return audit.path(field).asDouble();
        JsonNode recovered = mapper.readTree(auxiliary.toFile());
        return requiredDouble(recovered, "energy_hartree", result);
    }

    private static List<List<Double>> matrix(JsonNode node) throws IOException {
        if (!node.isArray()) {
            throw new IOException("expected matrix array");
        }
        java.util.ArrayList<List<Double>> rows = new java.util.ArrayList<>();
        for (JsonNode row : node) {
            if (!row.isArray()) {
                throw new IOException("expected matrix row");
            }
            java.util.ArrayList<Double> values = new java.util.ArrayList<>();
            row.forEach(value -> values.add(value.asDouble()));
            rows.add(List.copyOf(values));
        }
        return List.copyOf(rows);
    }

    private static String requiredText(JsonNode root, String field, Path source) throws IOException {
        String value = root.path(field).asText("");
        if (value.isBlank()) {
            throw new IOException("missing " + field + " in " + source);
        }
        return value;
    }

    private static double requiredDouble(JsonNode root, String field, Path source) throws IOException {
        JsonNode value = root.path(field);
        if (!value.isNumber()) {
            throw new IOException("missing numeric " + field + " in " + source);
        }
        return value.asDouble();
    }
}
