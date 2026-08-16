package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.recovery.ArtifactChecksums;

/** Historical PySCF campaign adapter; Java-only policy permanently disables new execution. */
@Deprecated(forRemoval = true)
public final class LockedPyscfForceTargetExecutor implements EvidenceExecutor {
    private final Path python;
    private final Path script;
    private final Path outputRoot;
    private final Map<String, Path> geometries;
    private final Set<String> authorizedChecksums;
    private final int threads;
    private final String atomMapHash;
    private final ObjectMapper mapper = new ObjectMapper();

    public LockedPyscfForceTargetExecutor(Path python, Path script, Path outputRoot,
            Map<String, Path> geometries, Set<String> authorizedChecksums, int threads,
            String atomMapHash) {
        this.python = python.toAbsolutePath().normalize();
        this.script = script.toAbsolutePath().normalize();
        this.outputRoot = outputRoot.toAbsolutePath().normalize();
        this.geometries = Map.copyOf(geometries);
        this.authorizedChecksums = Set.copyOf(authorizedChecksums);
        this.threads = threads;
        this.atomMapHash = Objects.requireNonNull(atomMapHash, "atomMapHash");
    }

    @Override public String executorId() { return "pyscf-locked-force-campaign"; }
    @Override public boolean supports(CalculationSpecification spec) {
        Objects.requireNonNull(spec, "spec");
        return false;
    }

    @Override public RawCalculationResult execute(CalculationSpecification spec)
            throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        throw ExternalPythonExecutionPolicy.disabled(executorId());
        /*
        if (!supports(spec) || !authorizedChecksums.contains(spec.checksum())) {
            throw new EvidenceExecutionException("force target specification is not authorized: " + spec.checksum());
        }
        Path source = geometries.get(spec.geometry().sha256());
        if (source == null) throw new EvidenceExecutionException("geometry identity is not in frozen manifest");
        Path directory = outputRoot.resolve(spec.specificationId());
        try {
            Files.createDirectories(directory);
            Path geometry = directory.resolve("input_geometry.xyz");
            Path specification = directory.resolve("calculation_specification.json");
            Path result = directory.resolve("result.json");
            if (!Files.isRegularFile(result)) {
                Files.copy(source, geometry, StandardCopyOption.COPY_ATTRIBUTES,
                        StandardCopyOption.REPLACE_EXISTING);
                writeSpecification(spec, geometry, specification);
                ProcessBuilder process = new ProcessBuilder(python.toString(), script.toString(),
                        "--spec", specification.toString(), "--geometry", geometry.toString(),
                        "--output", result.toString());
                process.directory(directory.toFile());
                process.environment().put("PROMETHEUS_PYSCF_THREADS", Integer.toString(threads));
                process.redirectErrorStream(true);
                process.redirectOutput(directory.resolve("raw_combined.log").toFile());
                int exit = process.start().waitFor();
                if (exit != 0) throw new EvidenceExecutionException("PySCF force target exited " + exit);
            }
            return raw(spec, directory);
        } catch (IOException e) {
            throw new EvidenceExecutionException("force target I/O failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvidenceExecutionException("force target interrupted", e);
        }
        */
    }

    private void writeSpecification(CalculationSpecification spec, Path geometry, Path target) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("specification_id", spec.specificationId());
        node.put("specification_checksum", spec.checksum());
        node.put("scientific_identity", new totah.lab.prometheus.evidence.EvidenceIdentity(
                spec.molecule(), atomMapHash,
                spec.geometry(), spec.formalCharge(), spec.multiplicity(), spec.calculationType(), spec.protocol(),
                spec.constraints(), spec.requiredOutputs()).evidenceHash());
        node.put("geometry_identity", spec.geometry().sha256());
        node.put("geometry_atom_count", spec.geometry().atomCount());
        node.put("input_geometry_sha256", ArtifactChecksums.sha256(geometry));
        node.put("formal_charge", spec.formalCharge()); node.put("multiplicity", spec.multiplicity());
        node.put("method", spec.protocol().method()); node.put("basis", spec.protocol().basis());
        node.put("dispersion", spec.protocol().dispersion()); node.put("environment", spec.protocol().environment());
        node.putPOJO("constraints", spec.constraints()); node.putPOJO("required_outputs", spec.requiredOutputs());
        mapper.writerWithDefaultPrettyPrinter().writeValue(target.toFile(), node);
    }

    private RawCalculationResult raw(CalculationSpecification spec, Path directory) throws IOException {
        JsonNode result = mapper.readTree(directory.resolve("result.json").toFile());
        String checksum = result.path("specification_checksum").asText("");
        if (!checksum.equals(spec.checksum())) {
            throw new IOException("result specification checksum mismatch");
        }
        String expectedIdentity = new EvidenceIdentity(spec.molecule(), atomMapHash, spec.geometry(),
                spec.formalCharge(), spec.multiplicity(), spec.calculationType(), spec.protocol(),
                spec.constraints(), spec.requiredOutputs()).evidenceHash();
        if (!result.path("scientific_identity").asText("").equals(expectedIdentity)) {
            throw new IOException("result scientific identity mismatch");
        }
        ConvergenceStatus convergence = convergence(result);
        List<RawArtifact> artifacts = new ArrayList<>();
        try (var files = Files.list(directory)) {
            for (Path path : files.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList()) {
                artifacts.add(new RawArtifact(path.getFileName().toString(), ArtifactChecksums.sha256(path),
                        "generated_force_target_artifact"));
            }
        }
        return new RawCalculationResult(spec, artifacts, convergence,
                "locked campaign energy+Cartesian-gradient target");
    }

    private static ConvergenceStatus convergence(JsonNode result) {
        JsonNode scf = result.get("scf_converged");
        if (scf != null && scf.isBoolean()) {
            return scf.booleanValue() ? ConvergenceStatus.CONVERGED : ConvergenceStatus.NOT_CONVERGED;
        }
        String status = result.path("status").asText("").strip().toUpperCase();
        return switch (status) {
            case "CONVERGED", "COMPLETE", "COMPLETED" -> ConvergenceStatus.CONVERGED;
            case "NOT_CONVERGED", "UNCONVERGED", "INCOMPLETE" -> ConvergenceStatus.NOT_CONVERGED;
            case "FAILED", "ERROR" -> ConvergenceStatus.FAILED;
            default -> ConvergenceStatus.UNKNOWN;
        };
    }
}
