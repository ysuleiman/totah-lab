package totah.lab.web.assembly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.hermes.file.mmcif.AssemblyChain;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.mmcif.EntryExperimentalMetadata;
import totah.lab.hermes.file.mmcif.StructureReference;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Persists one coordinate-owned RCSB assembly and its fpocket result. */
@Service
public class ExperimentalAssemblyImportService {
    public static final String TARGET_MAPPING_PROVENANCE =
            "RCSB_MMCIF_STRUCT_REF_UNIPROT";
    public static final String POCKET_ASSOCIATION_METHOD =
            "FPOCKET_RESIDUE_AUTH_CHAIN_TO_RCSB_POLYMER_CHAIN";
    public static final String POCKET_ASSOCIATION_VERSION = "1";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ExperimentalAssemblySourceLoader sourceLoader;
    private final String persistenceSchema;

    public ExperimentalAssemblyImportService(JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ExperimentalAssemblySourceLoader sourceLoader,
            @Value("${totah.persistence.docking-schema:docking}")
            String persistenceSchema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.sourceLoader = Objects.requireNonNull(sourceLoader);
        if (!persistenceSchema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid persistence schema: "
                    + persistenceSchema);
        }
        this.persistenceSchema = persistenceSchema;
    }

    @Transactional(rollbackFor = IOException.class)
    public ImportResult importAssembly(ImportRequest request) throws IOException {
        Objects.requireNonNull(request);
        jdbc.execute("SET LOCAL search_path TO " + persistenceSchema
                + ", public");
        var parsed = sourceLoader.load(request);
        EntryExperimentalMetadata metadata = parsed.metadata();
        List<StructureReference> references = parsed.references();
        List<AssemblyChain> chains = parsed.chains();
        List<BoundComponentOccurrence> components = parsed.components();
        List<Pocket> pockets = parsed.pockets();

        long assembly = upsertAssembly(request, metadata);
        long sourceArtifact = upsertArtifact(assembly, "SOURCE_MMCIF",
                request.assemblyMmcif().getFileName().toString(),
                request.assemblyMmcif(), request.inputSha256(), "RCSB", null,
                null, null, null);
        long fpocketArtifact = upsertArtifact(assembly, "FPOCKET_OUTPUT",
                request.fpocketOutput().getFileName().toString(),
                request.fpocketOutput(), null, "fpocket", request.fpocketVersion(),
                request.fpocketCommand(), request.startedAt(), request.completedAt());

        jdbc.update("DELETE FROM assembly_polymer_entity "
                + "WHERE assembly_id = ?", assembly);
        persistPolymers(assembly, chains, references);
        persistComponents(assembly, components);
        persistPockets(assembly, fpocketArtifact, pockets);
        deleteStalePockets(assembly, pockets);
        derivePocketTargets(assembly);

        int humanTargets = count("SELECT count(DISTINCT apt.target_id) "
                + "FROM assembly_polymer_target apt "
                + "JOIN assembly_polymer_entity ape "
                + "ON ape.id=apt.polymer_entity_id "
                + "WHERE ape.assembly_id=? AND apt.is_human IS TRUE", assembly);
        return new ImportResult(assembly, sourceArtifact, chains.size(),
                components.size(), pockets.size(), humanTargets);
    }

    private long upsertAssembly(ImportRequest request,
            EntryExperimentalMetadata metadata) {
        return id("""
                INSERT INTO experimental_assembly
                    (pdb_id, assembly_id, experimental_method,
                     resolution_angstrom)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (pdb_id, assembly_id) DO UPDATE SET
                    experimental_method=EXCLUDED.experimental_method,
                    resolution_angstrom=EXCLUDED.resolution_angstrom
                RETURNING id
                """, request.pdbId(), request.assemblyId(), metadata.method(),
                metadata.resolutionAngstrom());
    }

    private long upsertArtifact(long assembly, String type, String filename,
            Path location, String sha256, String producer, String version,
            String command, Instant started, Instant completed) {
        return id("""
                INSERT INTO assembly_artifact
                    (assembly_id, artifact_type, filename, storage_location,
                     sha256, producer, producer_version, command_line,
                     started_at, completed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (assembly_id, artifact_type, storage_location)
                DO UPDATE SET sha256=EXCLUDED.sha256,
                    producer=EXCLUDED.producer,
                    producer_version=EXCLUDED.producer_version,
                    command_line=EXCLUDED.command_line,
                    started_at=EXCLUDED.started_at,
                    completed_at=EXCLUDED.completed_at
                RETURNING id
                """, assembly, type, filename, location.toString(), sha256,
                producer, version, command, timestamp(started), timestamp(completed));
    }

    private void persistPolymers(long assembly, List<AssemblyChain> chains,
            List<StructureReference> references) {
        Map<String, List<StructureReference>> referencesByEntity =
                new LinkedHashMap<>();
        for (StructureReference reference : references) {
            referencesByEntity.computeIfAbsent(reference.entityId(),
                    ignored -> new java.util.ArrayList<>()).add(reference);
        }
        Map<String, Long> entityIds = new LinkedHashMap<>();
        for (AssemblyChain chain : chains) {
            if (entityIds.containsKey(chain.entityId())) {
                continue;
            }
            String description = referencesByEntity
                    .getOrDefault(chain.entityId(), List.of()).stream()
                    .map(StructureReference::description)
                    .filter(Objects::nonNull).findFirst().orElse(null);
            long entity = id("""
                    INSERT INTO assembly_polymer_entity
                        (assembly_id, source_entity_id, description)
                    VALUES (?, ?, ?)
                    RETURNING id
                    """, assembly, chain.entityId(), description);
            entityIds.put(chain.entityId(), entity);
        }
        for (AssemblyChain chain : chains) {
            jdbc.update("""
                    INSERT INTO assembly_polymer_chain
                        (polymer_entity_id, label_asym_id, auth_asym_id,
                         model_number)
                    VALUES (?, ?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, entityIds.get(chain.entityId()), chain.labelAsymId(),
                    chain.authAsymId(), chain.modelNumber());
        }
        Set<String> mappings = new LinkedHashSet<>();
        for (StructureReference reference : references) {
            Long entity = entityIds.get(reference.entityId());
            if (entity == null || reference.uniProtId() == null
                    || reference.uniProtId().isBlank()
                    || !mappings.add(entity + ":" + reference.uniProtId())) {
                continue;
            }
            long target = findOrCreateTarget(reference);
            Boolean human = reference.taxonomyId() == null ? null
                    : "9606".equals(reference.taxonomyId());
            jdbc.update("""
                    INSERT INTO assembly_polymer_target
                        (polymer_entity_id, target_id, uniprot_accession,
                         mapping_provenance, is_human)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (polymer_entity_id, target_id) DO UPDATE SET
                        uniprot_accession=EXCLUDED.uniprot_accession,
                        mapping_provenance=EXCLUDED.mapping_provenance,
                        is_human=EXCLUDED.is_human
                    """, entity, target, reference.uniProtId(),
                    TARGET_MAPPING_PROVENANCE, human);
            jdbc.update("""
                    INSERT INTO assembly_target
                        (assembly_id, target_id, association_provenance)
                    VALUES (?, ?, ?)
                    ON CONFLICT DO NOTHING
                    """, assembly, target, TARGET_MAPPING_PROVENANCE);
        }
    }

    private long findOrCreateTarget(StructureReference reference) {
        return id("""
                INSERT INTO targets (name, uniprot_id)
                VALUES (?, ?)
                ON CONFLICT (uniprot_id) DO UPDATE SET
                    name=COALESCE(targets.name, EXCLUDED.name)
                RETURNING id
                """, firstNonBlank(reference.description(),
                        reference.databaseCode(), reference.uniProtId()),
                reference.uniProtId());
    }

    private void persistComponents(long assembly,
            List<BoundComponentOccurrence> components) {
        Set<Long> retained = new LinkedHashSet<>();
        for (BoundComponentOccurrence component : components) {
            Set<String> alternates = new LinkedHashSet<>();
            component.atoms().stream()
                    .map(atom -> normalizeAlternate(atom.alternateLocation()))
                    .filter(alternate -> !alternate.isEmpty())
                    .forEach(alternates::add);
            if (alternates.isEmpty()) {
                alternates.add("");
            }
            for (String alternate : alternates) {
                retained.add(id("""
                        INSERT INTO assembly_component_occurrence
                            (assembly_id, component_id, label_asym_id,
                             auth_asym_id, auth_sequence_id, insertion_code,
                             alternate_location, model_number)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT ON CONSTRAINT
                            assembly_component_occurrence_unique
                        DO UPDATE SET component_id=EXCLUDED.component_id
                        RETURNING id
                        """, assembly, component.componentId(), component.asymId(),
                        component.authAsymId(), component.authSequenceId(),
                        component.insertionCode(), alternate,
                        component.modelNumber()));
            }
        }
        if (retained.isEmpty()) {
            jdbc.update("DELETE FROM assembly_component_occurrence "
                    + "WHERE assembly_id=?", assembly);
        } else {
            String placeholders = String.join(",", java.util.Collections
                    .nCopies(retained.size(), "?"));
            Object[] arguments = new Object[retained.size() + 1];
            arguments[0] = assembly;
            int index = 1;
            for (Long id : retained) arguments[index++] = id;
            jdbc.update("DELETE FROM assembly_component_occurrence "
                    + "WHERE assembly_id=? AND id NOT IN (" + placeholders + ")",
                    arguments);
        }
    }

    private static String normalizeAlternate(String value) {
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? "" : value;
    }

    private void persistPockets(long assembly, long artifact,
            List<Pocket> pockets) throws JsonProcessingException {
        for (Pocket pocket : pockets) {
            int number = Integer.parseInt(pocket.id().value());
            Map<String, Double> descriptors = new LinkedHashMap<>();
            pocket.metrics().forEach(metric -> descriptors.put(
                    metric.type().name(), metric.value()));
            long pocketId = id("""
                    INSERT INTO assembly_pocket
                        (assembly_id, artifact_id, pocket_number, fpocket_rank,
                         score, druggability_score, volume, descriptors)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                    ON CONFLICT (assembly_id, pocket_number) DO UPDATE SET
                        artifact_id=EXCLUDED.artifact_id,
                        fpocket_rank=EXCLUDED.fpocket_rank,
                        score=EXCLUDED.score,
                        druggability_score=EXCLUDED.druggability_score,
                        volume=EXCLUDED.volume,
                        descriptors=EXCLUDED.descriptors
                    RETURNING id
                    """, assembly, artifact, number, number,
                    metric(pocket, PocketMetricType.FPOCKET_SCORE),
                    metric(pocket, PocketMetricType.FPOCKET_DRUGGABILITY),
                    metric(pocket, PocketMetricType.VOLUME),
                    objectMapper.writeValueAsString(descriptors));
            jdbc.update("DELETE FROM assembly_pocket_residue "
                    + "WHERE pocket_id=?", pocketId);
            jdbc.update("DELETE FROM assembly_pocket_alpha_sphere "
                    + "WHERE pocket_id=?", pocketId);
            for (var residue : pocket.residues()) {
                jdbc.update("""
                        INSERT INTO assembly_pocket_residue
                            (pocket_id, auth_asym_id, residue_number,
                             insertion_code)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT DO NOTHING
                        """, pocketId, residue.chainId(),
                        residue.residueNumber(), residue.insertionCode() == null
                                ? null : residue.insertionCode().toString());
            }
            if (pocket.alphaSphereSet().isPresent()) {
                for (var sphere : pocket.alphaSphereSet().orElseThrow().spheres()) {
                    jdbc.update("""
                            INSERT INTO assembly_pocket_alpha_sphere
                                (pocket_id, sphere_number, x, y, z, radius)
                            VALUES (?, ?, ?, ?, ?, ?)
                            """, pocketId, sphere.id(), sphere.center().x(),
                            sphere.center().y(), sphere.center().z(), sphere.radius());
                }
            }
        }
    }

    private void deleteStalePockets(long assembly, List<Pocket> pockets) {
        Set<Integer> numbers = new LinkedHashSet<>();
        pockets.forEach(p -> numbers.add(Integer.parseInt(p.id().value())));
        if (numbers.isEmpty()) {
            jdbc.update("DELETE FROM assembly_pocket WHERE assembly_id=?",
                    assembly);
            return;
        }
        String placeholders = String.join(",", java.util.Collections.nCopies(
                numbers.size(), "?"));
        Object[] arguments = new Object[numbers.size() + 1];
        arguments[0] = assembly;
        int index = 1;
        for (Integer number : numbers) arguments[index++] = number;
        jdbc.update("DELETE FROM assembly_pocket WHERE assembly_id=? "
                + "AND pocket_number NOT IN (" + placeholders + ")", arguments);
    }

    private void derivePocketTargets(long assembly) {
        jdbc.update("DELETE FROM assembly_pocket_target WHERE pocket_id "
                + "IN (SELECT id FROM assembly_pocket WHERE assembly_id=?)",
                assembly);
        jdbc.update("""
                INSERT INTO assembly_pocket_target
                    (pocket_id, target_id, supporting_residue_count,
                     association_method, method_version)
                SELECT pr.pocket_id, pt.target_id,
                       count(DISTINCT (pr.auth_asym_id, pr.residue_number,
                                       pr.insertion_code)), ?, ?
                FROM assembly_pocket_residue pr
                JOIN assembly_pocket p ON p.id=pr.pocket_id
                JOIN assembly_polymer_entity pe
                  ON pe.assembly_id=p.assembly_id
                JOIN assembly_polymer_chain pc
                  ON pc.polymer_entity_id=pe.id
                 AND pc.auth_asym_id=pr.auth_asym_id
                JOIN assembly_polymer_target pt
                  ON pt.polymer_entity_id=pe.id
                WHERE p.assembly_id=?
                GROUP BY pr.pocket_id, pt.target_id
                """, POCKET_ASSOCIATION_METHOD, POCKET_ASSOCIATION_VERSION,
                assembly);
    }

    private long id(String sql, Object... arguments) {
        Long result = jdbc.queryForObject(sql, Long.class, arguments);
        return Objects.requireNonNull(result);
    }

    private int count(String sql, Object... arguments) {
        Integer result = jdbc.queryForObject(sql, Integer.class, arguments);
        return Objects.requireNonNull(result);
    }

    private static Double metric(Pocket pocket, PocketMetricType type) {
        var value = pocket.metric(type);
        return value.isPresent() ? value.getAsDouble() : null;
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        throw new IllegalArgumentException("At least one value is required");
    }

    public record ImportRequest(String pdbId, String assemblyId,
            Path entryMmcif, Path assemblyMmcif, String inputSha256,
            Path fpocketOutput, String fpocketVersion, String fpocketCommand,
            Instant startedAt, Instant completedAt) {}

    public record ImportResult(long assemblyDatabaseId, long sourceArtifactId,
            int polymerChains, int componentOccurrences, int pockets,
            int uniqueHumanTargets) {}
}
