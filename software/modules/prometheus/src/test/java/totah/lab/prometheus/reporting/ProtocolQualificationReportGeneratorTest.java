package totah.lab.prometheus.reporting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.recovery.ArtifactChecksums;

class ProtocolQualificationReportGeneratorTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path temporary;

    @Test
    void refusesToWriteAnythingBeforeNewResultExists() throws Exception {
        Fixture fixture = fixture(false);
        ProtocolQualificationReportGenerator generator = new ProtocolQualificationReportGenerator();

        assertThrows(IOException.class, () -> generator.generate(fixture.request()));
        assertFalse(Files.exists(fixture.output()));
    }

    @Test
    void writesExactlySixQualifiedDeliverablesAfterResultExists() throws Exception {
        Fixture fixture = fixture(true);
        ProtocolQualificationDecision decision = new ProtocolQualificationReportGenerator()
                .generate(fixture.request());

        assertEquals(ProtocolQualificationDecision.PROTOCOL_QUALIFIED_FOR_SCALEUP, decision);
        try (var files = Files.list(fixture.output())) {
            assertEquals(6, files.count());
        }
        assertTrue(Files.readString(fixture.output().resolve(
                ProtocolQualificationReportGenerator.RESULTS)).contains("C9_gradient_x_hartree_per_bohr"));
        assertTrue(Files.readString(fixture.output().resolve(
                ProtocolQualificationReportGenerator.UNIT_AUDIT)).contains("Finite-difference gate: PASS"));
        assertEquals(decision.name(), JSON.readTree(fixture.output().resolve(
                ProtocolQualificationReportGenerator.DECISION).toFile()).path("classification").asText());
        assertEquals(5, Files.readAllLines(fixture.output().resolve(
                ProtocolQualificationReportGenerator.CHECKSUMS)).size());
    }

    private Fixture fixture(boolean includeResult) throws Exception {
        Path input = temporary.resolve("input");
        Path output = temporary.resolve("output");
        Files.createDirectories(input);
        Path controlGeometry = input.resolve("control.xyz");
        Path problemGeometry = input.resolve("problem.xyz");
        writeGeometry(controlGeometry, 0.0);
        writeGeometry(problemGeometry, 0.01);
        Path controlGradient = input.resolve("control-gradient.txt");
        StringBuilder gradientText = new StringBuilder();
        for (int i = 0; i < 56; i++) {
            gradientText.append("0.001 0.002 0.003\n");
        }
        Files.writeString(controlGradient, gradientText);
        Path controlResult = input.resolve("control-result.json");
        Files.writeString(controlResult, "{\"energy_hartree\":-100.0}\n");
        Path historical = input.resolve("historical.json");
        ObjectNode old = JSON.createObjectNode();
        old.put("energy_hartree", -99.0);
        old.put("final_xyz_sha256", ArtifactChecksums.sha256(problemGeometry));
        JSON.writeValue(historical.toFile(), old);
        Path manifest = input.resolve("manifest.json");
        ObjectNode manifestJson = JSON.createObjectNode();
        ArrayNode calculations = manifestJson.putArray("calculations");
        ObjectNode calculation = calculations.addObject();
        calculation.put("specification_id", "pilot-phi060-psi060-B-m10");
        calculation.put("protocol", "PBE|def2-SVP|D3(BJ)|density-fitted gas phase|false|PySCF|2.14.0");
        calculation.put("formal_charge", 0);
        calculation.put("multiplicity", 1);
        JSON.writeValue(manifest.toFile(), manifestJson);
        Path selection = input.resolve("selection.csv");
        Files.writeString(selection, "point,ordinary_LJ_change_kcal_mol\nproblem,-11.584998\n");
        Path result = input.resolve("result.json");
        if (includeResult) {
            ObjectNode current = JSON.createObjectNode();
            current.put("status", "CONVERGED");
            current.put("scf_converged", true);
            current.put("input_geometry_sha256", ArtifactChecksums.sha256(problemGeometry));
            current.put("energy_hartree", -99.0 + 1.0e-8);
            current.put("gradient_force_identity_max_abs", 0.0);
            current.put("force_definition", "force = -gradient");
            ObjectNode units = current.putObject("units");
            units.put("energy", "hartree");
            units.put("gradient", "hartree/bohr");
            units.put("force", "hartree/bohr");
            ObjectNode protocol = current.putObject("protocol");
            protocol.put("method", "PBE");
            protocol.put("basis", "def2-SVP");
            protocol.put("dispersion", "D3(BJ)");
            ObjectNode fd = current.putObject("finite_difference_audit");
            fd.put("coordinate", "H56 displacement along S26->H56");
            fd.put("step_bohr", 0.001);
            fd.put("central_difference_hartree_per_bohr", 0.003);
            fd.put("analytic_gradient_projection_hartree_per_bohr", 0.003001);
            fd.put("absolute_difference_hartree_per_bohr", 1.0e-6);
            ArrayNode gradients = current.putArray("gradient_hartree_per_bohr");
            ArrayNode forces = current.putArray("force_hartree_per_bohr");
            for (int i = 0; i < 56; i++) {
                gradients.addArray().add(0.001).add(0.002).add(0.003);
                forces.addArray().add(-0.001).add(-0.002).add(-0.003);
            }
            JSON.writeValue(result.toFile(), current);
        }
        ProtocolQualificationRequest request = new ProtocolQualificationRequest(output, manifest,
                controlGeometry, controlResult, controlGradient, problemGeometry, historical, result, selection,
                Map.of("C9", 9, "C10", 10, "S26", 26, "H56", 56));
        return new Fixture(request, output);
    }

    private static void writeGeometry(Path path, double offset) throws IOException {
        String[] elements = new String[56];
        java.util.Arrays.fill(elements, "C");
        elements[25] = "S";
        elements[55] = "H";
        StringBuilder xyz = new StringBuilder("56\nfixture\n");
        for (int i = 0; i < 56; i++) {
            double x = i * 0.21 + offset;
            double y = (i % 5) * 0.37 + ((i * i) % 7) * 0.03;
            double z = (i % 3) * 0.41 + ((i * i) % 11) * 0.02;
            xyz.append(elements[i]).append(' ').append(x).append(' ').append(y).append(' ').append(z).append('\n');
        }
        Files.writeString(path, xyz);
    }

    private record Fixture(ProtocolQualificationRequest request, Path output) {
    }
}
