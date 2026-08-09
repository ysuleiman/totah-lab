package totah.lab.web.assembly;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Gated, dry-run-by-default component-pocket annotation batch. */
@Component
@ConditionalOnProperty(name = "totah.component-pocket-annotation.enabled",
        havingValue = "true")
public final class ComponentPocketAnnotationRunner implements CommandLineRunner {
    private static final Logger LOG = LoggerFactory.getLogger(
            ComponentPocketAnnotationRunner.class);

    private final JdbcTemplate jdbc;
    private final ComponentPocketAnnotationService service;
    private final boolean dryRun;
    private final String pdbIds;
    private final String schema;

    public ComponentPocketAnnotationRunner(JdbcTemplate jdbc,
            ComponentPocketAnnotationService service,
            @Value("${totah.component-pocket-annotation.dry-run:true}")
            boolean dryRun,
            @Value("${totah.component-pocket-annotation.pdb-ids:}") String pdbIds,
            @Value("${totah.persistence.docking-schema:docking}") String schema) {
        this.jdbc = jdbc;
        this.service = service;
        this.dryRun = dryRun;
        this.pdbIds = pdbIds;
        if (!schema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid persistence schema");
        }
        this.schema = schema;
    }

    @Override
    public void run(String... args) {
        List<String> selected = java.util.Arrays.stream(pdbIds.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(String::toUpperCase).toList();
        List<Long> assemblies = selected.isEmpty()
                ? jdbc.queryForList("SELECT id FROM " + schema
                        + ".experimental_assembly ORDER BY pdb_id, assembly_id",
                        Long.class)
                : jdbc.queryForList("SELECT id FROM " + schema
                        + ".experimental_assembly WHERE pdb_id IN ("
                        + String.join(",", java.util.Collections.nCopies(
                                selected.size(), "?"))
                        + ") ORDER BY pdb_id, assembly_id", Long.class,
                        selected.toArray());
        LOG.info("Component-pocket annotation: assemblies={}, dryRun={}",
                assemblies.size(), dryRun);
        if (dryRun) {
            LOG.info("Dry run complete; no annotations written");
            return;
        }
        int succeeded = 0;
        long pairs = 0;
        List<String> failures = new ArrayList<>();
        for (long assembly : assemblies) {
            try {
                var result = service.annotate(assembly);
                succeeded++;
                pairs += result.evaluatedPairs();
                if (succeeded % 25 == 0 || succeeded == assemblies.size()) {
                    LOG.info("Annotated {} / {} assemblies; evaluatedPairs={}",
                            succeeded, assemblies.size(), pairs);
                }
            } catch (Exception exception) {
                failures.add(assembly + ": " + exception.getMessage());
                LOG.error("Component-pocket annotation failed for assembly {}",
                        assembly, exception);
            }
        }
        LOG.info("Annotation finished: success={}, failures={}, pairs={}",
                succeeded, failures.size(), pairs);
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Annotation failures: " + failures);
        }
    }
}
