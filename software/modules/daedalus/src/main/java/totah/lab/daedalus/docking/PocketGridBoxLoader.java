package totah.lab.daedalus.docking;

import totah.lab.gaia.geometry.Point3D;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Loads the geometry of a docking.pocket row and derives a vina grid
 * box from it. Alpha spheres (docking.pocket_alpha_sphere) are
 * preferred; when a pocket has none, pocket-lining atom coordinates
 * (docking.pocket_residue → docking.pocket_atom) are used — the same
 * preference rule as the point-cloud loader elsewhere in the system.
 * Pockets with neither (for example P2RANK or BIOHUB pockets) are
 * rejected with a clear error.
 *
 * <p>Connection configuration follows the web-api env convention:
 * {@code DB_URL} (default the local dev database), {@code
 * DB_USERNAME} (default postgres), and the password from
 * {@code PGPASSWORD} with no fallback.</p>
 */
public final class PocketGridBoxLoader {

    private static final String DEFAULT_URL =
            "jdbc:postgresql://localhost:5432/totah_lab_db";

    private final DatabaseConfig config;

    public PocketGridBoxLoader(DatabaseConfig config) {
        this.config = Objects.requireNonNull(config, "config");
    }

    public record DatabaseConfig(
            String url,
            String username,
            String password
    ) {
        public DatabaseConfig {
            Objects.requireNonNull(url, "url");
            Objects.requireNonNull(username, "username");
            Objects.requireNonNull(password, "password");
        }

        public static DatabaseConfig fromEnvironment() {
            String password = System.getenv("PGPASSWORD");
            if (password == null || password.isBlank()) {
                throw new IllegalStateException(
                        "PGPASSWORD is not set; the database password is"
                                + " read from PGPASSWORD with no default");
            }
            return new DatabaseConfig(
                    environment("DB_URL", DEFAULT_URL),
                    environment("DB_USERNAME", "postgres"),
                    password
            );
        }

        private static String environment(String name, String fallback) {
            String value = System.getenv(name);
            return value == null || value.isBlank() ? fallback : value;
        }
    }

    public PocketGridBox load(long pocketId, double padding)
            throws SQLException {

        try (Connection connection = DriverManager.getConnection(
                config.url(), config.username(), config.password())) {
            return load(connection, pocketId, padding);
        }
    }

    PocketGridBox load(
            Connection connection,
            long pocketId,
            double padding
    ) throws SQLException {

        String source = pocketSource(connection, pocketId);
        if (source == null) {
            throw new IllegalStateException(
                    "Pocket not found in docking.pocket: " + pocketId);
        }

        List<Point3D> sphereCenters = new ArrayList<>();
        List<Double> sphereRadii = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT center_x, center_y, center_z, radius
                FROM docking.pocket_alpha_sphere
                WHERE pocket_id = ?
                ORDER BY sphere_index
                """)) {
            statement.setLong(1, pocketId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    sphereCenters.add(new Point3D(
                            rows.getDouble(1),
                            rows.getDouble(2),
                            rows.getDouble(3)));
                    sphereRadii.add(rows.getDouble(4));
                }
            }
        }
        if (!sphereCenters.isEmpty()) {
            return PocketGridBox.fromPoints(
                    sphereCenters, sphereRadii, padding);
        }

        List<Point3D> atoms = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT atom.x, atom.y, atom.z
                FROM docking.pocket_atom atom
                JOIN docking.pocket_residue membership
                    ON membership.id = atom.pocket_residue_id
                WHERE membership.pocket_id = ?
                ORDER BY atom.id
                """)) {
            statement.setLong(1, pocketId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    atoms.add(new Point3D(
                            rows.getDouble(1),
                            rows.getDouble(2),
                            rows.getDouble(3)));
                }
            }
        }
        if (!atoms.isEmpty()) {
            return PocketGridBox.fromPoints(atoms, null, padding);
        }

        throw new IllegalStateException(
                "Pocket " + pocketId + " (source " + source + ") has no"
                        + " alpha spheres and no pocket atoms; a grid box"
                        + " cannot be derived");
    }

    private String pocketSource(Connection connection, long pocketId)
            throws SQLException {

        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT source::text FROM docking.pocket WHERE id = ?")) {
            statement.setLong(1, pocketId);
            try (ResultSet rows = statement.executeQuery()) {
                return rows.next() ? rows.getString(1) : null;
            }
        }
    }
}
