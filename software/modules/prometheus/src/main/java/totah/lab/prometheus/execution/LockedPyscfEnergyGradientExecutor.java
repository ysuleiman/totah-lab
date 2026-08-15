package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.recovery.ArtifactChecksums;

/**
 * Narrow execution boundary for the authorized fixed-geometry PySCF pilot.
 * It accepts only pre-authorized specification checksums and never optimizes a
 * geometry. Scientific content is serialized before invoking the configured,
 * checksummed Python runner.
 */
public final class LockedPyscfEnergyGradientExecutor implements EvidenceExecutor {

    private final Path python;
    private final Path runnerScript;
    private final Path outputRoot;
    private final Map<String, Path> geometryByIdentity;
    private final Set<String> authorizedSpecificationChecksums;
    private final ObjectMapper mapper = new ObjectMapper();

    public LockedPyscfEnergyGradientExecutor(
            Path python,
            Path runnerScript,
            Path outputRoot,
            Map<String, Path> geometryByIdentity,
            Set<String> authorizedSpecificationChecksums) {
        this.python = Objects.requireNonNull(python, "python").toAbsolutePath().normalize();
        this.runnerScript = Objects.requireNonNull(runnerScript, "runnerScript").toAbsolutePath().normalize();
        this.outputRoot = Objects.requireNonNull(outputRoot, "outputRoot").toAbsolutePath().normalize();
        this.geometryByIdentity = Map.copyOf(Objects.requireNonNull(geometryByIdentity, "geometryByIdentity"));
        this.authorizedSpecificationChecksums = Set.copyOf(Objects.requireNonNull(
                authorizedSpecificationChecksums, "authorizedSpecificationChecksums"));
    }

    @Override
    public String executorId() {
        return "pyscf-locked-energy-gradient-pilot";
    }

    @Override
    public boolean supports(CalculationSpecification spec) {
        Objects.requireNonNull(spec, "spec");
        String software = spec.protocol().software().toLowerCase(Locale.ROOT);
        return software.startsWith("pyscf")
                && (spec.calculationType() == CalculationType.FORCE_EVALUATION
                    || spec.calculationType() == CalculationType.SINGLE_POINT)
                && spec.constraints().isEmpty()
                && requests(spec, "energy")
                && (requests(spec, "gradient") || requests(spec, "forces"));
    }

    @Override
    public RawCalculationResult execute(CalculationSpecification spec) throws EvidenceExecutionException {
        Objects.requireNonNull(spec, "spec");
        if (!supports(spec)) {
            throw new EvidenceExecutionException("unsupported PySCF pilot specification: " + spec.specificationId());
        }
        if (!authorizedSpecificationChecksums.contains(spec.checksum())) {
            throw new EvidenceExecutionException("specification was not authorized for the two-point pilot: "
                    + spec.checksum());
        }
        enforceProtocol(spec);
        Path sourceGeometry = geometryByIdentity.get(spec.geometry().sha256());
        if (sourceGeometry == null) {
            throw new EvidenceExecutionException("no geometry artifact registered for identity "
                    + spec.geometry().sha256());
        }
        Path directory = outputRoot.resolve(spec.specificationId());
        try {
            Files.createDirectories(directory);
            Path geometry = directory.resolve("input_geometry.xyz");
            Path specification = directory.resolve("calculation_specification.json");
            Path result = directory.resolve("result.json");
            if (Files.exists(result)) {
                new PyscfFiniteDifferenceArtifactRecovery().recover(directory);
                PyscfEnergyGradientResult parsed = new PyscfEnergyGradientResultReader().read(result);
                if (!parsed.specificationChecksum().equals(spec.checksum())) {
                    throw new EvidenceExecutionException("existing result belongs to a different specification");
                }
                return rawResult(spec, directory, parsed.scfConverged(), "reused exact pilot result");
            }
            // A technically interrupted attempt may have copied the immutable
            // input before producing result.json. Re-entry must be resumable
            // while still replacing it only with the same registered source.
            Files.copy(sourceGeometry, geometry, StandardCopyOption.COPY_ATTRIBUTES,
                    StandardCopyOption.REPLACE_EXISTING);
            writeSpecification(spec, geometry, specification);
            ProcessBuilder builder = new ProcessBuilder(
                    python.toString(), runnerScript.toString(),
                    "--spec", specification.toString(),
                    "--geometry", geometry.toString(),
                    "--output", result.toString());
            builder.directory(directory.toFile());
            builder.redirectErrorStream(true);
            builder.redirectOutput(directory.resolve("raw_combined.log").toFile());
            int exit = builder.start().waitFor();
            if (exit != 0) {
                throw new EvidenceExecutionException("PySCF runner exited " + exit + "; see "
                        + directory.resolve("raw_combined.log"));
            }
            PyscfEnergyGradientResult parsed = new PyscfEnergyGradientResultReader().read(result);
            if (!parsed.specificationChecksum().equals(spec.checksum())
                    || !parsed.geometryIdentity().equals(spec.geometry().sha256())) {
                throw new EvidenceExecutionException("runner result scientific identity mismatch");
            }
            return rawResult(spec, directory, parsed.scfConverged(), "executed locked PySCF energy+gradient pilot");
        } catch (IOException e) {
            throw new EvidenceExecutionException("PySCF pilot I/O failure", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EvidenceExecutionException("PySCF pilot interrupted", e);
        }
    }

    private void writeSpecification(CalculationSpecification spec, Path geometry, Path destination)
            throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("specification_id", spec.specificationId());
        root.put("specification_checksum", spec.checksum());
        root.put("scientific_purpose", spec.scientificPurpose());
        root.put("molecule_id", spec.molecule().moleculeId());
        root.put("geometry_identity", spec.geometry().sha256());
        root.put("geometry_atom_count", spec.geometry().atomCount());
        root.put("input_geometry_sha256", ArtifactChecksums.sha256(geometry));
        root.put("formal_charge", spec.formalCharge());
        root.put("multiplicity", spec.multiplicity());
        root.put("method", spec.protocol().method());
        root.put("basis", spec.protocol().basis());
        root.put("dispersion", spec.protocol().dispersion());
        root.put("environment", spec.protocol().environment());
        root.put("software", spec.protocol().software());
        root.put("software_version", spec.protocol().softwareVersion());
        root.put("calculation_type", spec.calculationType().name());
        root.putPOJO("constraints", spec.constraints());
        root.putPOJO("required_outputs", spec.requiredOutputs());
        root.putPOJO("acceptance_gates", spec.acceptanceGates());
        root.put("dataset_role", spec.role().name());
        mapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), root);
    }

    private RawCalculationResult rawResult(
            CalculationSpecification spec, Path directory, boolean converged, String note) throws IOException {
        List<RawArtifact> artifacts = new ArrayList<>();
        try (var files = Files.list(directory)) {
            files.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).forEach(path -> {
                try {
                    artifacts.add(new RawArtifact(path.getFileName().toString(),
                            ArtifactChecksums.sha256(path), artifactClass(path)));
                } catch (IOException e) {
                    throw new java.io.UncheckedIOException(e);
                }
            });
        } catch (java.io.UncheckedIOException e) {
            throw e.getCause();
        }
        return new RawCalculationResult(spec, artifacts,
                converged ? ConvergenceStatus.CONVERGED : ConvergenceStatus.NOT_CONVERGED, note);
    }

    private static void enforceProtocol(CalculationSpecification spec) throws EvidenceExecutionException {
        String method = spec.protocol().method().replace(" ", "").toUpperCase(Locale.ROOT);
        String basis = spec.protocol().basis().toLowerCase(Locale.ROOT);
        String dispersion = spec.protocol().dispersion().replace(" ", "").toUpperCase(Locale.ROOT);
        if (!method.equals("PBE") || !basis.equals("def2-svp") || !dispersion.equals("D3(BJ)")
                || !spec.protocol().environment().toLowerCase(Locale.ROOT).contains("gas")) {
            throw new EvidenceExecutionException(
                    "pilot is locked to density-fitted PBE-D3(BJ)/def2-SVP gas phase");
        }
    }

    private static boolean requests(CalculationSpecification spec, String token) {
        return spec.requiredOutputs().stream()
                .map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(value -> value.contains(token));
    }

    private static String artifactClass(Path path) {
        String name = path.getFileName().toString();
        if (name.endsWith(".json")) return "result_json";
        if (name.endsWith(".xyz")) return "geometry";
        if (name.contains("gradient")) return "gradient";
        if (name.contains("force")) return "force";
        return "log";
    }
}
