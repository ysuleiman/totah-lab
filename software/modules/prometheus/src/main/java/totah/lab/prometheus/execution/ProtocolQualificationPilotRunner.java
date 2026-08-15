package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.ingest.LegacyCanonicalAtomLoader;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;
import totah.lab.prometheus.store.GeneratedFailureClassification;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.reporting.ProtocolQualificationReportGenerator;
import totah.lab.prometheus.reporting.ProtocolQualificationRequest;

/** CLI for exactly the preregistered MIN02 + Unit-05L protocol pilot. */
public final class ProtocolQualificationPilotRunner {

    private ProtocolQualificationPilotRunner() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException(
                    "usage: ProtocolQualificationPilotRunner <repository-root> <python> <output-directory>");
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path python = Path.of(args[1]).toAbsolutePath().normalize();
        Path output = Path.of(args[2]).toAbsolutePath().normalize();
        Path archive = root.resolve("analysis/mettl7-phase2");
        CanonicalAtomMap atomMap = LegacyCanonicalAtomLoader.load(archive.resolve("execution-unit-02"));
        Path min02 = archive.resolve("execution-unit-05O/qm-native-minima/MIN02/final.xyz");
        Path problem = archive.resolve(
                "execution-unit-05L/points/phi060_psi060_B_m10/final.xyz");
        GeometryIdentity min02Identity = identity(atomMap, min02);
        GeometryIdentity problemIdentity = identity(atomMap, problem);
        QmProtocol protocol = new QmProtocol(
                "PBE", "def2-SVP", "D3(BJ)", "density-fitted gas phase",
                false, "PySCF", "2.14.0");
        CalculationSpecification min02Spec = specification(
                "forcebalance-pilot-min02", "verified QM-native development minimum protocol control",
                atomMap, min02Identity, protocol);
        CalculationSpecification problemSpec = specification(
                "forcebalance-pilot-phi060-psi060-B-m10",
                "representative ANGLE_LJ_COUPLED_DEFECT_SUPPORTED development geometry",
                atomMap, problemIdentity, protocol);

        Files.createDirectories(output);
        writeManifest(output.resolve("PILOT_CALCULATION_SPECIFICATIONS.json"), atomMap,
                List.of(min02Spec, problemSpec), List.of(min02, problem));

        // MIN02 already has an authoritative same-protocol energy+gradient and
        // is intentionally not passed to an executor. Only the missing 05L
        // fixed-geometry force evaluation is allowlisted.
        Path script = root.resolve(
                "software/modules/prometheus/scripts/run_locked_pyscf_energy_gradient.py");
        LockedPyscfEnergyGradientExecutor executor = new LockedPyscfEnergyGradientExecutor(
                python, script, output.resolve("raw"),
                Map.of(problemIdentity.sha256(), problem), Set.of(problemSpec.checksum()));
        EvidenceIdentity intendedIdentity = new EvidenceIdentity(atomMap.molecule(), atomMap.canonicalHash(),
                problemIdentity, 0, 1, CalculationType.FORCE_EVALUATION, protocol, List.of(),
                problemSpec.requiredOutputs());
        GeneratedEvidenceRegistry generatedRegistry = new GeneratedEvidenceRegistry(
                output.resolve("generated-evidence-registry"));
        GeneratedEvidenceLifecycle lifecycle = new GeneratedEvidenceLifecycle(generatedRegistry);
        Path artifactBase = output.resolve("raw").resolve(problemSpec.specificationId());
        Path interruptedAttempt = output.resolve("failed-attempts/2026-08-14-duplicate-contention");
        boolean explicitRetry = rememberInterruptedAttempt(
                generatedRegistry, problemSpec, intendedIdentity, interruptedAttempt);
        PyscfPilotEvidenceMapper evidenceMapper = new PyscfPilotEvidenceMapper(intendedIdentity, atomMap);
        GeneratedEvidenceLifecycle.LifecycleResult lifecycleResult = explicitRetry
                ? lifecycle.executeExplicitRetry(problemSpec, intendedIdentity, executor, artifactBase,
                        evidenceMapper)
                : lifecycle.executeOrReuse(problemSpec, intendedIdentity, executor, artifactBase,
                        evidenceMapper);
        ObjectNode execution = new ObjectMapper().createObjectNode();
        execution.put("reused_min02_specification_checksum", min02Spec.checksum());
        execution.put("executed_problem_specification_checksum", problemSpec.checksum());
        execution.put("atom_map_hash", atomMap.canonicalHash());
        execution.put("execution_convergence", lifecycleResult.primaryEvidence().convergence().name());
        execution.put("reused_generated_evidence", lifecycleResult.reused());
        execution.put("generated_registry", generatedRegistry.registryFile().toString());
        execution.put("min02_action", "REUSE_AUTHORITATIVE_RAW_RESULT");
        execution.put("problem_action", lifecycleResult.reused()
                ? "REUSE_EXISTING"
                : "EXECUTE_MISSING_FINAL_PROTOCOL_ENERGY_GRADIENT");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("PILOT_EXECUTION_RECORD.json").toFile(), execution);

        // Reporting is deliberately downstream of the atomic result write. The
        // generic generator refuses to emit a partial package if result.json is absent.
        Path min02Directory = archive.resolve("execution-unit-05O/qm-native-minima/MIN02");
        Path problemDirectory = archive.resolve("execution-unit-05L/points/phi060_psi060_B_m10");
        new ProtocolQualificationReportGenerator().generate(new ProtocolQualificationRequest(
                output,
                output.resolve("PILOT_CALCULATION_SPECIFICATIONS.json"),
                min02Directory.resolve("final.xyz"),
                min02Directory.resolve("result.json"),
                min02Directory.resolve("final_gradient_hartree_per_bohr.txt"),
                problemDirectory.resolve("final.xyz"),
                problemDirectory.resolve("result.json"),
                output.resolve("raw/forcebalance-pilot-phi060-psi060-B-m10/result.json"),
                archive.resolve("execution-unit-05L/SPARSE_TWO_ANGLE_CONTACT_RESPONSE.csv"),
                Map.of("C9", 9, "C10", 10, "S26", 26, "H56", 56)));
    }

    private static boolean rememberInterruptedAttempt(
            GeneratedEvidenceRegistry registry,
            CalculationSpecification specification,
            EvidenceIdentity identity,
            Path failedAttempt) throws IOException {
        if (registry.failure(specification.checksum()).isPresent()) {
            return true;
        }
        if (!Files.isDirectory(failedAttempt)) {
            return false;
        }
        List<RawArtifact> artifacts = new ArrayList<>();
        try (var paths = Files.walk(failedAttempt)) {
            for (Path path : paths.filter(Files::isRegularFile).sorted().toList()) {
                artifacts.add(new RawArtifact(failedAttempt.relativize(path).toString(),
                        ArtifactChecksums.sha256(path), "interrupted_attempt_artifact"));
            }
        }
        registry.recordFailure(specification.checksum(), identity.evidenceHash(),
                GeneratedFailureClassification.EXECUTION_FAILED, Optional.of(failedAttempt), artifacts,
                "EXECUTION_INTERRUPTED_DUPLICATE_PROCESS_CONTENTION: a timed-out launcher left a duplicate "
                        + "process; the newer duplicate was terminated and the remaining pre-lifecycle process "
                        + "ended before producing an atomic result.json");
        return true;
    }

    private static CalculationSpecification specification(
            String id, String purpose, CanonicalAtomMap atomMap,
            GeometryIdentity geometry, QmProtocol protocol) {
        return new CalculationSpecification(
                id, purpose, atomMap.molecule(), geometry, 0, 1, protocol, List.of(),
                CalculationType.FORCE_EVALUATION,
                List.of("absolute energy in hartree", "gradient in hartree/bohr",
                        "forces in hartree/bohr", "finite-difference gradient audit"),
                List.of("SCF converged", "force equals negative gradient",
                        "finite-difference gradient agrees", "atom order preserved"),
                DatasetRole.DEVELOPMENT, new CostEstimate(1, 4.0, 1.5, 1.5, 0.0));
    }

    private static GeometryIdentity identity(CanonicalAtomMap atomMap, Path xyz) throws IOException {
        List<String> lines = Files.readAllLines(xyz);
        int count = Integer.parseInt(lines.getFirst().trim());
        if (count != atomMap.size()) {
            throw new IOException("XYZ/canonical atom count mismatch: " + xyz);
        }
        List<Point3D> coordinates = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String[] fields = lines.get(i + 2).trim().split("\\s+");
            String expected = atomMap.atoms().get(i).elementSymbol();
            if (!fields[0].equalsIgnoreCase(expected)) {
                throw new IOException("atom-order mismatch at canonical atom " + (i + 1) + " in " + xyz);
            }
            coordinates.add(new Point3D(Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2]), Double.parseDouble(fields[3])));
        }
        return GeometryIdentity.of(atomMap, coordinates);
    }

    private static void writeManifest(
            Path target, CanonicalAtomMap map, List<CalculationSpecification> specs,
            List<Path> geometries) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("canonical_atom_map_hash", map.canonicalHash());
        root.put("pilot_size", 2);
        root.put("scaleup_authorized", false);
        ArrayNode entries = root.putArray("calculations");
        for (int i = 0; i < specs.size(); i++) {
            CalculationSpecification spec = specs.get(i);
            ObjectNode node = entries.addObject();
            node.put("specification_id", spec.specificationId());
            node.put("specification_checksum", spec.checksum());
            node.put("geometry_identity", spec.geometry().sha256());
            node.put("source_geometry", geometries.get(i).toString());
            node.put("protocol", spec.protocol().protocolKey());
            node.put("formal_charge", spec.formalCharge());
            node.put("multiplicity", spec.multiplicity());
            node.put("role", spec.role().name());
            node.putPOJO("required_outputs", spec.requiredOutputs());
            node.putPOJO("acceptance_gates", spec.acceptanceGates());
        }
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), root);
    }
}
