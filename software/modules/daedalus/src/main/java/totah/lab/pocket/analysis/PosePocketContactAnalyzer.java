package totah.lab.pocket.analysis;

import totah.lab.euclid.spatial.SimpleKDTree;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class PosePocketContactAnalyzer {
    private static final double CONTACT_CUTOFF = 4.0;
    private static final double CONTACT_CUTOFF_SQ = CONTACT_CUTOFF * CONTACT_CUTOFF;
    private static final int BATCH_SIZE = 1000;
    public void buildContacts(Connection conn) throws SQLException {

        // --- SELECT pocket atoms (ignore coords column) ---
        List<PocketAtom> pocketAtoms = new ArrayList<>();
        List<double[]> pocketCoords = new ArrayList<>();

        String pocketSql = """
        SELECT id, x, y, z, pocket_residue_id, atom_name, element
        FROM docking.pocket_atom
        """;

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(pocketSql)) {

            while (rs.next()) {
                PocketAtom pa = new PocketAtom(
                        rs.getLong("id"),
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getLong("pocket_residue_id"),
                        rs.getString("atom_name"),
                        rs.getString("element")
                );
                pocketAtoms.add(pa);
                pocketCoords.add(new double[]{pa.x(), pa.y(), pa.z()});
            }
        }

        // Build k-d tree on pocket atoms
        SimpleKDTree<PocketAtom> tree = new SimpleKDTree<>(3);
        tree.build(pocketCoords, pocketAtoms);

        // --- SELECT pose atoms, compute contacts, INSERT ---
        String insertSql = """
        INSERT INTO docking.pose_atom_contact 
        (pose_id, pose_atom_id, pocket_atom_id, pocket_residue_id, distance_angstroms)
        VALUES (?, ?, ?, ?, ?)
        """;

        List<Contact> contactBatch = new ArrayList<>();
        int totalContacts = 0;

        String poseSql = "SELECT id, x, y, z, pose_id FROM docking.pose_atom";

        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(poseSql)) {

            while (rs.next()) {
                double[] query = {
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z")
                };
                long poseAtomId = rs.getLong("id");
                long poseId = rs.getLong("pose_id");

                // Range search within 4Å
                var neighbors = tree.rangeSearch(query, CONTACT_CUTOFF);

                for (var neighbor : neighbors) {
                    PocketAtom pa = neighbor.value();
                    double dist = neighbor.distance();

                    contactBatch.add(new Contact(
                            poseId, poseAtomId, pa.id(), pa.pocketResidueId(), dist
                    ));

                    if (contactBatch.size() >= BATCH_SIZE) {
                        insertBatch(conn, insertSql, contactBatch);
                        totalContacts += contactBatch.size();
                        contactBatch.clear();
                        System.out.println("Inserted " + totalContacts + " contacts...");
                    }
                }
            }
        }

        // Final batch
        if (!contactBatch.isEmpty()) {
            insertBatch(conn, insertSql, contactBatch);
            totalContacts += contactBatch.size();
        }

        System.out.println("Total contacts: " + totalContacts);
    }

    private void insertBatch(Connection conn, String sql, List<Contact> contacts) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            for (Contact c : contacts) {
                ps.setLong(1, c.poseId);
                ps.setLong(2, c.poseAtomId);
                ps.setLong(3, c.pocketAtomId);
                ps.setLong(4, c.pocketResidueId);
                ps.setDouble(5, c.distance);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // --- Data classes ---
    private record PocketAtom(
            long id,
            double x, double y, double z,
            long pocketResidueId,
            String atomName,
            String element
    ) {}

    private record Contact(
            long poseId, long poseAtomId, long pocketAtomId,
            long pocketResidueId, double distance
    ) {}
}
