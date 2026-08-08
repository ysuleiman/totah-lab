package totah.lab.web.assembly;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Gated, dry-run-by-default cohort importer; one transaction per assembly. */
@Component
@ConditionalOnProperty(name = "totah.rcsb-assembly-import.enabled",
        havingValue = "true")
public final class ExperimentalAssemblyImportRunner implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(
            ExperimentalAssemblyImportRunner.class);

    private final ExperimentalAssemblyImportService service;
    private final ObjectMapper objectMapper;
    private final Path cohortRoot;
    private final Path manifest;
    private final boolean dryRun;

    public ExperimentalAssemblyImportRunner(
            ExperimentalAssemblyImportService service,
            ObjectMapper objectMapper,
            @Value("${totah.rcsb-assembly-import.cohort-root}") Path cohortRoot,
            @Value("${totah.rcsb-assembly-import.manifest:${totah.rcsb-assembly-import.cohort-root}/fpocket/manifest.jsonl}")
            Path manifest,
            @Value("${totah.rcsb-assembly-import.dry-run:true}") boolean dryRun) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.cohortRoot = cohortRoot.toAbsolutePath();
        this.manifest = manifest.toAbsolutePath();
        this.dryRun = dryRun;
    }

    @Override
    public void run(String... args) throws Exception {
        List<JsonNode> records = new ArrayList<>();
        for (String line : Files.readAllLines(manifest)) {
            if (!line.isBlank()) records.add(objectMapper.readTree(line));
        }
        LOG.info("RCSB assembly import: records={}, dryRun={}, manifest={}",
                records.size(), dryRun, manifest);
        if (dryRun) {
            for (JsonNode record : records) validateRecord(record);
            LOG.info("Dry run complete; no database rows written");
            return;
        }
        int succeeded = 0;
        List<String> failures = new ArrayList<>();
        for (JsonNode record : records) {
            try {
                validateRecord(record);
                service.importAssembly(request(record));
                succeeded++;
                if (succeeded % 25 == 0 || succeeded == records.size()) {
                    LOG.info("Imported {} / {} assemblies", succeeded,
                            records.size());
                }
            } catch (Exception exception) {
                String pdb = record.path("pdb_id").asText("UNKNOWN");
                failures.add(pdb + ": " + exception.getMessage());
                LOG.error("Assembly import failed for {}", pdb, exception);
            }
        }
        LOG.info("RCSB assembly import finished: success={}, failures={}",
                succeeded, failures.size());
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Assembly import failures: " + failures);
        }
    }

    private void validateRecord(JsonNode record) {
        String status = required(record, "status");
        if (!status.equals("SUCCESS") && !status.equals("SKIPPED")) {
            throw new IllegalArgumentException("Manifest record is not complete: "
                    + required(record, "pdb_id") + " status=" + status);
        }
        Path source = Path.of(required(record, "source_mmcif"));
        Path output = Path.of(required(record, "output_directory"))
                .resolve(required(record, "pdb_id") + "-assembly"
                        + record.path("assembly_id").asInt() + "_out");
        if (!Files.isRegularFile(source) || !Files.isDirectory(output)) {
            throw new IllegalArgumentException("Missing source/output for "
                    + required(record, "pdb_id"));
        }
    }

    private ExperimentalAssemblyImportService.ImportRequest request(
            JsonNode record) {
        String pdb = required(record, "pdb_id");
        String assembly = record.path("assembly_id").asText();
        Path output = Path.of(required(record, "output_directory"))
                .resolve(pdb + "-assembly" + assembly + "_out");
        return new ExperimentalAssemblyImportService.ImportRequest(
                pdb, assembly, cohortRoot.resolve(pdb + ".cif"),
                Path.of(required(record, "source_mmcif")),
                required(record, "input_sha256"), output,
                required(record, "fpocket_version"),
                required(record, "fpocket_command"),
                Instant.parse(required(record, "started_at")),
                Instant.parse(required(record, "completed_at")));
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException(
                "Missing manifest field " + field);
        return value;
    }
}
