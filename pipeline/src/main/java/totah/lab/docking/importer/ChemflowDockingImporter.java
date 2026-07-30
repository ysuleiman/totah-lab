package totah.lab.docking.importer;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Idempotently imports Chemflow docking runs, poses and residue contacts for
 * structures that already exist in the canonical docking schema.
 */
public final class ChemflowDockingImporter {

    private static final String SOURCE_SYSTEM = "chemflow3";
    private static final int BATCH_SIZE = 2_000;

    private final Path sourceArtifactRoot;

    public ChemflowDockingImporter(Path sourceArtifactRoot) {
        this.sourceArtifactRoot = Objects.requireNonNull(
                sourceArtifactRoot,
                "sourceArtifactRoot"
        ).toAbsolutePath().normalize();
    }

    public ChemflowImportResult importData(
            Connection source,
            Connection destination
    ) throws SQLException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(destination, "destination");

        boolean originalAutoCommit = destination.getAutoCommit();
        destination.setAutoCommit(false);
        try {
            Map<String, DestinationStructure> structures =
                    loadDestinationStructures(destination);
            int runCount = importRuns(source, destination, structures);
            Map<UUID, Long> runIds = loadImportedIds(
                    destination,
                    "docking.docking_run"
            );
            PoseImport poseImport = importPoses(
                    source,
                    destination,
                    structures,
                    runIds
            );
            Map<UUID, Long> poseIds = loadImportedIds(
                    destination,
                    "docking.docking_pose"
            );
            Map<ResidueKey, Long> residues =
                    loadDestinationResidues(destination);
            int contacts = importContacts(
                    source,
                    destination,
                    structures,
                    poseIds,
                    residues
            );
            destination.commit();
            return new ChemflowImportResult(
                    runCount,
                    poseImport.imported(),
                    contacts,
                    poseImport.rejected()
            );
        } catch (SQLException | RuntimeException exception) {
            destination.rollback();
            throw exception;
        } finally {
            destination.setAutoCommit(originalAutoCommit);
        }
    }

    private int importRuns(
            Connection source,
            Connection destination,
            Map<String, DestinationStructure> structures
    ) throws SQLException {
        String select = """
                SELECT r.id,
                       t.uniprot_id,
                       r.engine,
                       r.created_at,
                       r.run_metadata::text,
                       (r.run_metadata->'box'->>'center_x')::double precision,
                       (r.run_metadata->'box'->>'center_y')::double precision,
                       (r.run_metadata->'box'->>'center_z')::double precision,
                       (r.run_metadata->'box'->>'size_x')::double precision,
                       (r.run_metadata->'box'->>'size_y')::double precision,
                       (r.run_metadata->'box'->>'size_z')::double precision
                FROM docking_runs r
                JOIN target_structures ts ON ts.id = r.target_structure_id
                JOIN targets t ON t.id = ts.target_id
                WHERE t.uniprot_id IN ('Q6UX53', 'Q9H8H3')
                ORDER BY r.created_at, r.id
                """;
        String insert = """
                INSERT INTO docking.docking_run (
                    receptor_id, structure_id,
                    grid_center_x, grid_center_y, grid_center_z,
                    grid_size_x, grid_size_y, grid_size_z,
                    vina_version, created_at,
                    source_system, source_id, source_metadata
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                ON CONFLICT (source_system, source_id)
                    WHERE source_system IS NOT NULL AND source_id IS NOT NULL
                DO NOTHING
                """;

        int count = 0;
        try (Statement query = source.createStatement();
             ResultSet rows = query.executeQuery(select);
             PreparedStatement write = destination.prepareStatement(insert)) {
            while (rows.next()) {
                DestinationStructure structure =
                        requireStructure(structures, rows.getString(2));
                write.setLong(1, structure.receptorId());
                write.setLong(2, structure.structureId());
                setNullableDouble(write, 3, rows, 6);
                setNullableDouble(write, 4, rows, 7);
                setNullableDouble(write, 5, rows, 8);
                setNullableDouble(write, 6, rows, 9);
                setNullableDouble(write, 7, rows, 10);
                setNullableDouble(write, 8, rows, 11);
                write.setString(9, rows.getString(3));
                write.setTimestamp(10, rows.getTimestamp(4));
                write.setString(11, SOURCE_SYSTEM);
                write.setObject(12, rows.getObject(1));
                write.setString(13, rows.getString(5));
                count += write.executeUpdate();
            }
        }
        return count;
    }

    private PoseImport importPoses(
            Connection source,
            Connection destination,
            Map<String, DestinationStructure> structures,
            Map<UUID, Long> runIds
    ) throws SQLException {
        String select = """
                SELECT p.id,
                       p.docking_run_id,
                       t.uniprot_id,
                       replace(c.id::text, '-', ''),
                       p.score,
                       a.id,
                       a.storage_uri,
                       p.created_at,
                       c.id
                FROM docking_poses p
                JOIN docking_runs r ON r.id = p.docking_run_id
                JOIN target_structures ts ON ts.id = r.target_structure_id
                JOIN targets t ON t.id = ts.target_id
                JOIN artifacts a ON a.id = p.pose_artifact_id
                LEFT JOIN compounds c
                  ON c.id::text = p.pose_metadata->>'compound_id'
                WHERE t.uniprot_id IN ('Q6UX53', 'Q9H8H3')
                ORDER BY p.id
                """;
        String insert = """
                INSERT INTO docking.docking_pose (
                    ligand_id, vina_score, pose_file, created_at,
                    receptor_id, run_id,
                    source_system, source_id, source_artifact_id,
                    source_compound_id
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (source_system, source_id)
                    WHERE source_system IS NOT NULL AND source_id IS NOT NULL
                DO NOTHING
                """;

        int imported = 0;
        int rejected = 0;
        int pending = 0;
        LocalArtifactUriResolver resolver =
                new LocalArtifactUriResolver(sourceArtifactRoot);
        try (Statement query = source.createStatement();
             ResultSet rows = query.executeQuery(select);
             PreparedStatement write = destination.prepareStatement(insert)) {
            query.setFetchSize(BATCH_SIZE);
            while (rows.next()) {
                String compoundId = rows.getString(4);
                if (compoundId == null || compoundId.isBlank()) {
                    rejected++;
                    continue;
                }
                UUID sourceRunId = rows.getObject(2, UUID.class);
                Long destinationRunId = runIds.get(sourceRunId);
                if (destinationRunId == null) {
                    throw new SQLException(
                            "No destination run for source run " + sourceRunId
                    );
                }
                DestinationStructure structure =
                        requireStructure(structures, rows.getString(3));
                Path posePath = resolver.resolve(URI.create(rows.getString(7)));
                if (!Files.isRegularFile(posePath)) {
                    throw new SQLException(
                            "Missing pose artifact " + posePath
                    );
                }

                write.setString(1, compoundId);
                write.setDouble(2, rows.getDouble(5));
                write.setString(3, posePath.toString());
                write.setTimestamp(4, rows.getTimestamp(8));
                write.setString(5, structure.uniprotId());
                write.setLong(6, destinationRunId);
                write.setString(7, SOURCE_SYSTEM);
                write.setObject(8, rows.getObject(1));
                write.setObject(9, rows.getObject(6));
                write.setObject(10, rows.getObject(9));
                write.addBatch();
                pending++;
                if (pending == BATCH_SIZE) {
                    imported += sum(write.executeBatch());
                    pending = 0;
                }
            }
            if (pending > 0) {
                imported += sum(write.executeBatch());
            }
        }
        return new PoseImport(imported, rejected);
    }

    private int importContacts(
            Connection source,
            Connection destination,
            Map<String, DestinationStructure> structures,
            Map<UUID, Long> poseIds,
            Map<ResidueKey, Long> residues
    ) throws SQLException {
        String select = """
                SELECT c.docking_pose_id,
                       t.uniprot_id,
                       tr.chain_id,
                       tr.residue_number,
                       coalesce(tr.insertion_code, ''),
                       count(*)::integer,
                       min(c.min_distance)
                FROM docking_pose_contacts c
                JOIN docking_poses p ON p.id = c.docking_pose_id
                JOIN docking_runs r ON r.id = p.docking_run_id
                JOIN target_structures ts ON ts.id = r.target_structure_id
                JOIN targets t ON t.id = ts.target_id
                JOIN target_residues tr ON tr.id = c.target_residue_id
                WHERE t.uniprot_id IN ('Q6UX53', 'Q9H8H3')
                  AND p.pose_metadata->>'compound_id' IS NOT NULL
                  AND c.min_distance <= 4.0
                GROUP BY c.docking_pose_id, t.uniprot_id, tr.chain_id,
                         tr.residue_number, coalesce(tr.insertion_code, '')
                ORDER BY c.docking_pose_id
                """;
        String insert = """
                INSERT INTO docking.pose_residue_contact (
                    pose_id, residue_id, atom_contact_count, min_distance
                ) VALUES (?, ?, ?, ?)
                ON CONFLICT (pose_id, residue_id) DO UPDATE SET
                    atom_contact_count = EXCLUDED.atom_contact_count,
                    min_distance = EXCLUDED.min_distance
                """;

        int count = 0;
        int pending = 0;
        try (Statement query = source.createStatement();
             ResultSet rows = query.executeQuery(select);
             PreparedStatement write = destination.prepareStatement(insert)) {
            query.setFetchSize(BATCH_SIZE);
            while (rows.next()) {
                UUID sourcePoseId = rows.getObject(1, UUID.class);
                Long poseId = poseIds.get(sourcePoseId);
                if (poseId == null) {
                    throw new SQLException(
                            "No destination pose for source pose " + sourcePoseId
                    );
                }
                DestinationStructure structure =
                        requireStructure(structures, rows.getString(2));
                ResidueKey key = new ResidueKey(
                        structure.structureId(),
                        rows.getString(3),
                        rows.getInt(4),
                        rows.getString(5)
                );
                Long residueId = residues.get(key);
                if (residueId == null) {
                    throw new SQLException("No destination residue for " + key);
                }
                write.setLong(1, poseId);
                write.setLong(2, residueId);
                write.setInt(3, rows.getInt(6));
                write.setDouble(4, rows.getDouble(7));
                write.addBatch();
                pending++;
                if (pending == BATCH_SIZE) {
                    count += sum(write.executeBatch());
                    pending = 0;
                }
            }
            if (pending > 0) {
                count += sum(write.executeBatch());
            }
        }
        return count;
    }

    private static Map<String, DestinationStructure>
    loadDestinationStructures(Connection connection) throws SQLException {
        String sql = """
                SELECT r.uniprot_id, r.id, s.id
                FROM docking.receptor r
                JOIN docking.structure s ON s.receptor_id = r.id
                WHERE r.uniprot_id IS NOT NULL
                """;
        Map<String, DestinationStructure> result = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                String uniprotId = rows.getString(1);
                DestinationStructure previous = result.put(
                        uniprotId,
                        new DestinationStructure(
                                uniprotId,
                                rows.getLong(2),
                                rows.getLong(3)
                        )
                );
                if (previous != null) {
                    throw new SQLException(
                            "Multiple destination structures for " + uniprotId
                    );
                }
            }
        }
        return result;
    }

    private static Map<UUID, Long> loadImportedIds(
            Connection connection,
            String table
    ) throws SQLException {
        String sql = "SELECT source_id, id FROM " + table
                + " WHERE source_system = ?";
        Map<UUID, Long> result = new HashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SOURCE_SYSTEM);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    result.put(
                            rows.getObject(1, UUID.class),
                            rows.getLong(2)
                    );
                }
            }
        }
        return result;
    }

    private static Map<ResidueKey, Long> loadDestinationResidues(
            Connection connection
    ) throws SQLException {
        String sql = """
                SELECT id, structure_id, chain, residue_number, insertion_code
                FROM docking.residue
                """;
        Map<ResidueKey, Long> result = new HashMap<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) {
                result.put(
                        new ResidueKey(
                                rows.getLong(2),
                                rows.getString(3),
                                rows.getInt(4),
                                rows.getString(5)
                        ),
                        rows.getLong(1)
                );
            }
        }
        return result;
    }

    private static DestinationStructure requireStructure(
            Map<String, DestinationStructure> structures,
            String uniprotId
    ) throws SQLException {
        DestinationStructure result = structures.get(uniprotId);
        if (result == null) {
            throw new SQLException(
                    "No destination structure for UniProt " + uniprotId
            );
        }
        return result;
    }

    private static void setNullableDouble(
            PreparedStatement statement,
            int parameter,
            ResultSet rows,
            int column
    ) throws SQLException {
        double value = rows.getDouble(column);
        if (rows.wasNull()) {
            statement.setNull(parameter, java.sql.Types.DOUBLE);
        } else {
            statement.setDouble(parameter, value);
        }
    }

    private static int sum(int[] counts) {
        int result = 0;
        for (int count : counts) {
            if (count == Statement.SUCCESS_NO_INFO) {
                result++;
            } else if (count > 0) {
                result += count;
            }
        }
        return result;
    }

    private record DestinationStructure(
            String uniprotId,
            long receptorId,
            long structureId
    ) {
    }

    private record ResidueKey(
            long structureId,
            String chain,
            int residueNumber,
            String insertionCode
    ) {
    }

    private record PoseImport(int imported, int rejected) {
    }
}
