package totah.lab.prometheus.execution;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.identity.CanonicalAtomMap;
import totah.lab.prometheus.identity.GeometryIdentity;
import totah.lab.prometheus.ingest.LegacyCanonicalAtomLoader;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;
import totah.lab.prometheus.recovery.ArtifactChecksums;
import totah.lab.prometheus.store.GeneratedEvidenceRegistry;

/** Executes only GENERATE_NEW entries from the frozen, checksummed 36-target manifest. */
public final class ForceCampaignRunner {
    private static final List<String> OUTPUTS = List.of(
            "absolute energy in hartree", "gradient in hartree/bohr", "forces in hartree/bohr");
    private ForceCampaignRunner() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("usage: <repository-root> <python> <campaign-root>");
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path python = Path.of(args[1]).toAbsolutePath().normalize();
        Path campaign = Path.of(args[2]).toAbsolutePath().normalize();
        ObjectMapper mapper = new ObjectMapper();
        Path manifestPath = campaign.resolve("FORCE_CAMPAIGN_36_TARGET_MANIFEST.json");
        JsonNode manifest = mapper.readTree(manifestPath.toFile());
        if (manifest.path("target_count").asInt() != 36 || !manifest.path("holdout_sealed").asBoolean()
                || !manifest.path("execution_authorized").asBoolean()
                || manifest.path("forcebalance_authorized").asBoolean()) {
            throw new IOException("campaign manifest authorization/sealing invariant failed");
        }
        CanonicalAtomMap atomMap = LegacyCanonicalAtomLoader.load(
                root.resolve("analysis/mettl7-phase2/execution-unit-02"));
        if (!manifest.path("canonical_atom_map_hash").asText().equals(atomMap.canonicalHash())) {
            throw new IOException("campaign atom-map hash mismatch");
        }
        QmProtocol protocol = new QmProtocol("PBE", "def2-SVP", "D3(BJ)",
                "density-fitted gas phase", false, "PySCF", "2.14.0");
        List<Task> tasks = new ArrayList<>();
        Map<String, Path> geometries = new LinkedHashMap<>();
        Set<String> authorized = new java.util.LinkedHashSet<>();
        int index = 0;
        for (JsonNode target : manifest.path("targets")) {
            index++;
            GeometryIdentity geometry = new GeometryIdentity(target.path("geometry_identity").asText(), 56);
            CalculationSpecification spec = specification(index, atomMap, geometry, protocol);
            EvidenceIdentity identity = new EvidenceIdentity(atomMap.molecule(), atomMap.canonicalHash(), geometry,
                    0, 1, CalculationType.FORCE_EVALUATION, protocol, List.of(), OUTPUTS);
            if (!spec.specificationId().equals(target.path("target_id").asText())
                    || !spec.checksum().equals(target.path("specification_checksum").asText())
                    || !identity.evidenceHash().equals(target.path("scientific_identity").asText())) {
                throw new IOException("manifest/specification identity mismatch for target " + index);
            }
            Path geometryPath = Path.of(target.path("geometry_path").asText());
            if (!ArtifactChecksums.sha256(geometryPath).equals(target.path("geometry_file_sha256").asText())) {
                throw new IOException("geometry artifact checksum mismatch for target " + index);
            }
            tasks.add(new Task(spec, identity, target.path("resolution").asText()));
            geometries.put(geometry.sha256(), geometryPath); authorized.add(spec.checksum());
        }
        GeneratedEvidenceRegistry registry = new GeneratedEvidenceRegistry(
                root.resolve("analysis/prometheus/generated-evidence-registry"));
        LockedPyscfForceTargetExecutor executor = new LockedPyscfForceTargetExecutor(python,
                root.resolve("software/modules/prometheus/scripts/run_locked_pyscf_force_target.py"),
                campaign.resolve("targets"), geometries, authorized, 4, atomMap.canonicalHash());
        GeneratedEvidenceLifecycle lifecycle = new GeneratedEvidenceLifecycle(registry);
        Progress progress = new Progress(campaign.resolve("CAMPAIGN_PROGRESS.json"), tasks.size(), mapper);
        try (var pool = Executors.newFixedThreadPool(3)) {
            List<Callable<Void>> jobs = new ArrayList<>();
            for (Task task : tasks) {
                if (registry.reusable(task.identity().evidenceHash()).isPresent()) {
                    progress.complete(task.spec().specificationId(), "REUSE_EXISTING", null);
                    continue;
                }
                if (!task.preflightResolution().equals("GENERATE_NEW")) {
                    throw new IOException("preflight reuse vanished for " + task.spec().specificationId());
                }
                jobs.add(() -> {
                    try {
                        progress.running(task.spec().specificationId());
                        Path base = campaign.resolve("targets").resolve(task.spec().specificationId());
                        lifecycle.executeOrReuse(task.spec(), task.identity(), executor, base,
                                new PyscfForceTargetEvidenceMapper(task.identity()));
                        progress.complete(task.spec().specificationId(), "REGISTERED_REUSABLE", null);
                    } catch (Exception failure) {
                        progress.complete(task.spec().specificationId(), "FAILED", failure.getMessage());
                    }
                    return null;
                });
            }
            pool.invokeAll(jobs);
        }
        if (tasks.stream().anyMatch(task -> registry.reusable(task.identity().evidenceHash()).isEmpty())) {
            throw new IOException("campaign ended with missing targets; frozen dataset not created");
        }
        freeze(campaign, tasks, registry, mapper, manifestPath);
    }

    private static void freeze(Path campaign, List<Task> tasks, GeneratedEvidenceRegistry registry,
            ObjectMapper mapper, Path manifestPath) throws IOException {
        ObjectNode root = mapper.createObjectNode();
        root.put("status", "FROZEN_READ_ONLY_QM_TARGET_DATASET"); root.put("target_count", tasks.size());
        root.put("source_manifest_sha256", ArtifactChecksums.sha256(manifestPath));
        root.put("forcebalance_may_execute_qm", false);
        ArrayNode values = root.putArray("targets");
        for (Task task : tasks) {
            var evidence = registry.reusable(task.identity().evidenceHash()).orElseThrow();
            ObjectNode node = values.addObject(); node.put("target_id", task.spec().specificationId());
            node.put("scientific_identity", task.identity().evidenceHash());
            node.put("energy_hartree", evidence.energyHartree().orElseThrow());
            node.putPOJO("gradient_hartree_per_bohr", evidence.gradientHartreePerBohr().orElseThrow());
            node.put("provenance_path", evidence.provenance().sourcePath());
            node.put("provenance_sha256", evidence.provenance().sha256());
        }
        Path dataset = campaign.resolve("FROZEN_QM_TARGET_DATASET.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(dataset.toFile(), root);
        try (FileChannel channel = FileChannel.open(dataset, StandardOpenOption.WRITE)) { channel.force(true); }
        Files.writeString(campaign.resolve("FROZEN_QM_TARGET_DATASET.sha256"),
                ArtifactChecksums.sha256(dataset) + "  FROZEN_QM_TARGET_DATASET.json\n", StandardCharsets.UTF_8);
    }

    private static CalculationSpecification specification(int index, CanonicalAtomMap map,
            GeometryIdentity geometry, QmProtocol protocol) {
        return new CalculationSpecification(String.format("force-campaign-%02d", index),
                "prospectively frozen QM-native energy/force development target", map.molecule(), geometry,
                0, 1, protocol, List.of(), CalculationType.FORCE_EVALUATION, OUTPUTS,
                List.of("SCF converged", "finite energy and Cartesian gradient", "force equals negative gradient",
                        "atom order and geometry checksum preserved"), DatasetRole.DEVELOPMENT,
                new CostEstimate(1, 1.5, 1.5, 1.5, 0.0));
    }

    private record Task(CalculationSpecification spec, EvidenceIdentity identity, String preflightResolution) { }

    private static final class Progress {
        private final Path path; private final int total; private final ObjectMapper mapper;
        private final Map<String, String> states = new LinkedHashMap<>();
        private Progress(Path path, int total, ObjectMapper mapper) { this.path=path; this.total=total; this.mapper=mapper; }
        synchronized void running(String id) throws IOException { states.put(id, "RUNNING"); write(null); }
        synchronized void complete(String id, String state, String reason) throws IOException {
            states.put(id, reason == null ? state : state + ": " + reason); write(id);
        }
        private void write(String last) throws IOException {
            ObjectNode root=mapper.createObjectNode(); root.put("total",total);
            root.put("completed_or_reused",states.values().stream().filter(v->v.startsWith("REUSE")||v.startsWith("REGISTERED")).count());
            root.put("running",states.values().stream().filter(v->v.equals("RUNNING")).count());
            root.put("failed",states.values().stream().filter(v->v.startsWith("FAILED")).count());
            root.put("last_updated_target",last); root.putPOJO("states",states);
            Path tmp=Files.createTempFile(path.getParent(),"campaign-progress-",".json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(tmp.toFile(),root);
            try(FileChannel c=FileChannel.open(tmp,StandardOpenOption.WRITE)){c.force(true);}
            Files.move(tmp,path,java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        }
    }
}
