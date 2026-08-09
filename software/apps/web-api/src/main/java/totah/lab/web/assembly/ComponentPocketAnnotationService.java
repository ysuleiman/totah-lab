package totah.lab.web.assembly;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import totah.lab.athena.pocket.component.ComponentPocketGeometry;
import totah.lab.athena.pocket.component.ComponentPocketGeometryAnalyzer;
import totah.lab.athena.pocket.component.ComponentPocketGeometryThresholds;
import totah.lab.athena.pocket.component.GeometryAtom;
import totah.lab.athena.pocket.component.PocketSphere;
import totah.lab.hermes.component.LigandClassifier;
import totah.lab.hermes.file.mmcif.BoundComponentAtom;
import totah.lab.hermes.file.mmcif.BoundComponentOccurrence;
import totah.lab.hermes.file.pocket.FpocketAtomObservation;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Computes and persists geometry-only component-to-pocket annotations. */
@Service
public class ComponentPocketAnnotationService {
    public static final String METHOD = "COMPONENT_POCKET_GEOMETRY";
    public static final String METHOD_VERSION = "1.0";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final String persistenceSchema;
    private final ComponentPocketGeometryThresholds thresholds =
            ComponentPocketGeometryThresholds.defaults();
    private final ComponentPocketGeometryAnalyzer analyzer =
            new ComponentPocketGeometryAnalyzer(thresholds);
    private final ComponentPocketSourceLoader sourceLoader;
    private final LigandClassifier classifier = new LigandClassifier();

    public ComponentPocketAnnotationService(JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            ComponentPocketSourceLoader sourceLoader,
            @Value("${totah.persistence.docking-schema:docking}")
            String persistenceSchema) {
        this.jdbc = Objects.requireNonNull(jdbc);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.sourceLoader = Objects.requireNonNull(sourceLoader);
        if (!persistenceSchema.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException("Invalid persistence schema");
        }
        this.persistenceSchema = persistenceSchema;
    }

    @Transactional(rollbackFor = IOException.class)
    public AnnotationResult annotate(long assemblyDatabaseId) throws IOException {
        jdbc.execute("SET LOCAL search_path TO " + persistenceSchema + ", public");
        AssemblySource source = assemblySource(assemblyDatabaseId);
        List<BoundComponentOccurrence> sourceOccurrences = sourceLoader.components(
                source.sourceMmcif(), source.pdbId(), source.assemblyId());
        Map<OccurrenceKey, PersistedOccurrence> persisted =
                persistedOccurrences(assemblyDatabaseId);
        List<ComponentOccurrence> occurrences = expandOccurrences(
                sourceOccurrences, persisted);
        Map<Integer, ComponentPocketSourceLoader.PocketSource> parsedPockets =
                new LinkedHashMap<>();
        for (var pocket : sourceLoader.pockets(source.fpocketOutput())) {
            parsedPockets.put(pocket.pocketNumber(), pocket);
        }
        List<PersistedPocket> pockets = persistedPockets(assemblyDatabaseId,
                source.fpocketOutput(), parsedPockets);

        persistSourceAtoms(occurrences);
        persistPocketAtoms(pockets);
        String thresholdsJson = thresholdsJson();
        UUID runToken = UUID.randomUUID();
        int associations = 0;
        List<Object[]> annotationRows = new ArrayList<>();
        for (ComponentOccurrence occurrence : occurrences) {
            List<GeometryAtom> componentAtoms = occurrence.atoms().stream()
                    .map(atom -> new GeometryAtom(atom.element(), atom.position(), null))
                    .toList();
            if (componentAtoms.stream().noneMatch(GeometryAtom::heavy)) {
                continue;
            }
            List<EvaluatedPocket> evaluated = new ArrayList<>();
            for (PersistedPocket pocket : pockets) {
                ComponentPocketGeometry geometry = analyzer.analyze(componentAtoms,
                        pocket.geometryAtoms(), pocket.spheres());
                evaluated.add(new EvaluatedPocket(pocket.id(), geometry));
            }
            EvaluatedPocket nearest = evaluated.stream().min(
                    java.util.Comparator.comparingDouble(pair ->
                            pair.geometry().minimumPocketAtomDistance()))
                    .orElseThrow();
            for (EvaluatedPocket pair : evaluated) {
                ComponentPocketGeometry geometry = pair.geometry();
                boolean associated = !geometry.relationshipClass().name()
                        .equals("NOT_ASSOCIATED");
                boolean plausible = geometry.minimumPocketAtomDistance()
                        <= thresholds.plausiblePocketDistanceAngstrom();
                if (!associated && !plausible && pair != nearest) {
                    continue;
                }
                if (associated) {
                    associations++;
                }
                annotationRows.add(annotationRow(occurrence.id(), pair.pocketId(),
                        geometry, thresholdsJson, runToken));
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO component_pocket_annotation
                    (occurrence_id, pocket_id, relationship_class,
                     minimum_pocket_atom_distance,
                     minimum_alpha_sphere_center_distance,
                     minimum_alpha_sphere_surface_distance,
                     component_centroid_pocket_centroid_distance,
                     heavy_atoms_inside_sphere_cloud,
                     heavy_atoms_near_sphere_cloud,
                     heavy_atom_inside_fraction,
                     heavy_atom_near_fraction,
                     contacting_pocket_residues, method, method_version,
                     thresholds, run_token)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                ON CONFLICT (occurrence_id, pocket_id) DO UPDATE SET
                    relationship_class=EXCLUDED.relationship_class,
                    minimum_pocket_atom_distance=EXCLUDED.minimum_pocket_atom_distance,
                    minimum_alpha_sphere_center_distance=EXCLUDED.minimum_alpha_sphere_center_distance,
                    minimum_alpha_sphere_surface_distance=EXCLUDED.minimum_alpha_sphere_surface_distance,
                    component_centroid_pocket_centroid_distance=EXCLUDED.component_centroid_pocket_centroid_distance,
                    heavy_atoms_inside_sphere_cloud=EXCLUDED.heavy_atoms_inside_sphere_cloud,
                    heavy_atoms_near_sphere_cloud=EXCLUDED.heavy_atoms_near_sphere_cloud,
                    heavy_atom_inside_fraction=EXCLUDED.heavy_atom_inside_fraction,
                    heavy_atom_near_fraction=EXCLUDED.heavy_atom_near_fraction,
                    contacting_pocket_residues=EXCLUDED.contacting_pocket_residues,
                    method=EXCLUDED.method, method_version=EXCLUDED.method_version,
                    thresholds=EXCLUDED.thresholds, run_token=EXCLUDED.run_token,
                    evaluated_at=now()
                """, annotationRows);
        jdbc.update("""
                DELETE FROM component_pocket_annotation annotation
                USING assembly_component_occurrence occurrence
                WHERE annotation.occurrence_id=occurrence.id
                  AND occurrence.assembly_id=?
                  AND annotation.run_token<>?
                """, assemblyDatabaseId, runToken);
        return new AnnotationResult(assemblyDatabaseId, occurrences.size(),
                pockets.size(), annotationRows.size(), associations);
    }

    private AssemblySource assemblySource(long assembly) {
        return jdbc.queryForObject("""
                SELECT a.pdb_id, a.assembly_id,
                       source.storage_location, fpocket.storage_location
                FROM experimental_assembly a
                JOIN assembly_artifact source ON source.assembly_id=a.id
                  AND source.artifact_type='SOURCE_MMCIF'
                JOIN assembly_artifact fpocket ON fpocket.assembly_id=a.id
                  AND fpocket.artifact_type='FPOCKET_OUTPUT'
                WHERE a.id=?
                """, (rs, row) -> new AssemblySource(rs.getString(1),
                rs.getString(2), Path.of(rs.getString(3)),
                Path.of(rs.getString(4))), assembly);
    }

    private Map<OccurrenceKey, PersistedOccurrence> persistedOccurrences(
            long assembly) {
        Map<OccurrenceKey, PersistedOccurrence> result = new LinkedHashMap<>();
        jdbc.query("""
                SELECT id, component_id, label_asym_id, auth_asym_id,
                       auth_sequence_id, insertion_code, alternate_location,
                       model_number
                FROM assembly_component_occurrence WHERE assembly_id=?
                """, rs -> {
            var occurrence = new PersistedOccurrence(rs.getLong("id"),
                    rs.getString("component_id"));
            result.put(new OccurrenceKey(rs.getString("component_id"),
                    rs.getString("label_asym_id"),
                    normalize(rs.getString("auth_asym_id")),
                    normalize(rs.getString("auth_sequence_id")),
                    normalize(rs.getString("insertion_code")),
                    normalize(rs.getString("alternate_location")),
                    rs.getInt("model_number")), occurrence);
        }, assembly);
        return result;
    }

    private List<ComponentOccurrence> expandOccurrences(
            List<BoundComponentOccurrence> source,
            Map<OccurrenceKey, PersistedOccurrence> persisted) {
        List<ComponentOccurrence> result = new ArrayList<>();
        for (BoundComponentOccurrence occurrence : source) {
            Set<String> alternates = new LinkedHashSet<>();
            occurrence.atoms().stream()
                    .map(atom -> normalize(atom.alternateLocation()))
                    .filter(alternate -> !alternate.isEmpty())
                    .forEach(alternates::add);
            if (alternates.isEmpty()) alternates.add("");
            for (String alternate : alternates) {
                OccurrenceKey key = new OccurrenceKey(occurrence.componentId(),
                        occurrence.asymId(), normalize(occurrence.authAsymId()),
                        normalize(occurrence.authSequenceId()),
                        normalize(occurrence.insertionCode()), alternate,
                        occurrence.modelNumber());
                PersistedOccurrence stored = persisted.get(key);
                if (stored == null) {
                    throw new IllegalStateException("No persisted occurrence for " + key);
                }
                List<BoundComponentAtom> atoms = occurrence.atoms().stream()
                        .filter(atom -> alternate.isEmpty()
                                || normalize(atom.alternateLocation()).isEmpty()
                                || normalize(atom.alternateLocation()).equals(alternate))
                        .toList();
                result.add(new ComponentOccurrence(stored.id(),
                        stored.componentId(), alternate, atoms));
            }
        }
        return result;
    }

    private List<PersistedPocket> persistedPockets(long assembly,
            Path output,
            Map<Integer, ComponentPocketSourceLoader.PocketSource> parsed)
            throws IOException {
        List<PersistedPocket> result = new ArrayList<>();
        List<long[]> rows = jdbc.query("""
                SELECT id, pocket_number FROM assembly_pocket
                WHERE assembly_id=? ORDER BY pocket_number
                """, (rs, row) -> new long[]{rs.getLong(1), rs.getInt(2)}, assembly);
        for (long[] row : rows) {
            int number = Math.toIntExact(row[1]);
            var pocket = Objects.requireNonNull(parsed.get(number));
            List<FpocketAtomObservation> atoms = pocket.atoms().stream()
                    .filter(atom -> "ATOM".equals(atom.groupPdb()))
                    .toList();
            List<PocketSphere> spheres = pocket.spheres();
            List<GeometryAtom> geometryAtoms = atoms.stream().map(atom ->
                    new GeometryAtom(atom.element(), atom.position(),
                            residueIdentity(atom))).toList();
            result.add(new PersistedPocket(row[0], number, atoms,
                    geometryAtoms, spheres));
        }
        return result;
    }

    private void persistSourceAtoms(List<ComponentOccurrence> occurrences) {
        for (ComponentOccurrence occurrence : occurrences) {
            int heavy = (int) occurrence.atoms().stream()
                    .filter(ComponentPocketAnnotationService::heavy).count();
            String category = classifier.classify(occurrence.componentId(),
                    occurrence.atoms()).name();
            jdbc.update("""
                    UPDATE assembly_component_occurrence SET category=?,
                        atom_count=?, heavy_atom_count=? WHERE id=?
                    """, category, occurrence.atoms().size(), heavy, occurrence.id());
            jdbc.update("DELETE FROM assembly_component_atom WHERE occurrence_id=?",
                    occurrence.id());
            List<Object[]> rows = occurrence.atoms().stream().map(atom -> new Object[]{
                    occurrence.id(), atom.sourceAtomId(), atom.name(), atom.element(),
                    atom.position().x(), atom.position().y(), atom.position().z(),
                    atom.occupancy(), atom.bFactor(), atom.formalCharge(),
                    normalize(atom.alternateLocation())}).toList();
            jdbc.batchUpdate("""
                    INSERT INTO assembly_component_atom
                        (occurrence_id, source_atom_id, atom_name, element,
                         x, y, z, occupancy, b_factor, formal_charge,
                         alternate_location)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, rows);
        }
    }

    private void persistPocketAtoms(List<PersistedPocket> pockets) {
        for (PersistedPocket pocket : pockets) {
            jdbc.update("DELETE FROM assembly_pocket_atom WHERE pocket_id=?",
                    pocket.id());
            List<Object[]> rows = pocket.sourceAtoms().stream().map(atom ->
                    new Object[]{pocket.id(), atom.sourceAtomId(), atom.atomName(),
                            atom.element(), atom.authAsymId(), atom.residueNumber(),
                            atom.insertionCode(), atom.residueName(),
                            atom.position().x(), atom.position().y(),
                            atom.position().z()}).toList();
            jdbc.batchUpdate("""
                    INSERT INTO assembly_pocket_atom
                        (pocket_id, source_atom_id, atom_name, element,
                         auth_asym_id, residue_number, insertion_code,
                         residue_name, x, y, z)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, rows);
        }
    }

    private Object[] annotationRow(long occurrence, long pocket,
            ComponentPocketGeometry geometry, String thresholdsJson,
            UUID runToken) {
        return new Object[]{occurrence, pocket, geometry.relationshipClass().name(),
                geometry.minimumPocketAtomDistance(),
                geometry.minimumAlphaSphereCenterDistance(),
                geometry.minimumAlphaSphereSurfaceDistance(),
                geometry.componentCentroidPocketCentroidDistance(),
                geometry.heavyAtomsInsideSphereCloud(),
                geometry.heavyAtomsNearSphereCloud(),
                geometry.heavyAtomInsideFraction(),
                geometry.heavyAtomNearFraction(),
                geometry.contactingPocketResidues(), METHOD, METHOD_VERSION,
                thresholdsJson, runToken};
    }

    private String thresholdsJson() throws JsonProcessingException {
        return objectMapper.writeValueAsString(thresholds);
    }

    private static boolean heavy(BoundComponentAtom atom) {
        String element = atom.element().toUpperCase(Locale.ROOT);
        return !element.equals("H") && !element.equals("D")
                && !element.equals("T");
    }

    private static String residueIdentity(FpocketAtomObservation atom) {
        return atom.authAsymId() + ":" + atom.residueNumber() + ":"
                + normalize(atom.insertionCode());
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() || value.equals(".")
                || value.equals("?") ? "" : value;
    }

    private record AssemblySource(String pdbId, String assemblyId,
            Path sourceMmcif, Path fpocketOutput) {}
    private record PersistedOccurrence(long id, String componentId) {}
    private record OccurrenceKey(String componentId, String labelAsymId,
            String authAsymId, String authSequenceId, String insertionCode,
            String alternateLocation, int modelNumber) {}
    private record ComponentOccurrence(long id, String componentId,
            String alternateLocation, List<BoundComponentAtom> atoms) {}
    private record PersistedPocket(long id, int number,
            List<FpocketAtomObservation> sourceAtoms,
            List<GeometryAtom> geometryAtoms, List<PocketSphere> spheres) {}
    private record EvaluatedPocket(long pocketId,
            ComponentPocketGeometry geometry) {}

    public record AnnotationResult(long assemblyDatabaseId,
            int componentOccurrences, int pockets, int evaluatedPairs,
            int associatedPairs) {}
}
