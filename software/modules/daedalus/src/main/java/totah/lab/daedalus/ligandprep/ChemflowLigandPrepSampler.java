package totah.lab.daedalus.ligandprep;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Samples chemflow3 compounds that have a Meeko
 * ({@code mk_prepare_ligand.py}) prepared-ligand PDBQT linked to its
 * source SDF artifact via {@code artifact_metadata.source_artifact_id}.
 * Deterministic: ordered by compound id, then prepared-artifact id.
 * Read-only.
 */
public final class ChemflowLigandPrepSampler implements LigandPrepSampler {

    public static final String DEFAULT_URL =
            "jdbc:postgresql://localhost:5432/chemflow3";

    private final String url;
    private final String username;
    private final String password;

    public ChemflowLigandPrepSampler(
            String url,
            String username,
            String password
    ) {
        this.url = Objects.requireNonNull(url, "url");
        this.username = Objects.requireNonNull(username, "username");
        this.password = Objects.requireNonNull(password, "password");
    }

    @Override
    public List<LigandPrepSample> sample(int count) throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                url, username, password);
             PreparedStatement statement = connection.prepareStatement(
                     """
                     SELECT c.id, c.smiles,
                            si.storage_uri, sp.storage_uri
                     FROM artifacts sp
                     JOIN artifacts si
                         ON si.id::text =
                            sp.artifact_metadata->>'source_artifact_id'
                     JOIN compound_artifacts cp
                         ON cp.artifact_id = sp.id
                        AND cp.role = 'prepared_ligand'
                     JOIN compounds c ON c.id = cp.compound_id
                     WHERE sp.format = 'pdbqt'
                       AND sp.artifact_metadata->>'command'
                           LIKE '%mk_prepare_ligand.py'
                       AND si.format = 'sdf'
                     ORDER BY c.id, sp.id
                     LIMIT ?
                     """)) {
            statement.setInt(1, count);
            try (ResultSet rows = statement.executeQuery()) {
                List<LigandPrepSample> samples = new ArrayList<>();
                while (rows.next()) {
                    samples.add(new LigandPrepSample(
                            rows.getString(1),
                            rows.getString(2) == null
                                    ? ""
                                    : rows.getString(2),
                            rows.getString(3),
                            rows.getString(4)));
                }
                return List.copyOf(samples);
            }
        }
    }
}
