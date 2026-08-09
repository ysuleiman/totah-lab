package totah.lab.web.pocketmatch;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import totah.lab.athena.pocket.pocketmatch.DefaultPocketMatchSignatureFactory;
import totah.lab.athena.pocket.pocketmatch.PocketMatchSignature;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketId;
import totah.lab.gaia.pocket.PocketSource;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdb.reader.PdbReader;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * Loads full-fidelity PocketMatch signatures: pocket residue identities
 * come from {@code docking.pocket_residue}, atom coordinates from the
 * pocket's structure artifact (PDB/mmCIF, optionally gzipped).
 *
 * <p>{@code docking.pocket_atom} is deliberately not used: it stores
 * only pocket-lining contact atoms (about one atom per residue), which
 * cannot supply CA/CB/side-chain-centroid representative points.</p>
 *
 * <p>Part of Athena's independent, clean-room implementation of the
 * PocketMatch representation described by Yeturu &amp; Chandra (2008);
 * see the {@code totah.lab.athena.pocket.pocketmatch} package
 * documentation for the full citation and provenance.</p>
 */
@Component
public class PocketMatchSignatureLoader {

    private static final String STORAGE_LOCATION_SQL = """
            SELECT a.storage_location
            FROM docking.pocket p
            JOIN docking.structure s ON s.id = p.structure_id
            JOIN docking.artifacts a ON a.id = s.artifact_id
            WHERE p.id = ?
            """;

    private static final String RESIDUE_IDENTITY_SQL = """
            SELECT pr.chain, pr.residue_number, r.insertion_code,
                   pr.residue_name
            FROM docking.pocket_residue pr
            LEFT JOIN docking.residue r ON r.id = pr.residue_id
            WHERE pr.pocket_id = ?
            ORDER BY pr.id
            """;

    private final DataSource dataSource;
    private final Path artifactRoot;
    private final Path externalRoot;
    private final DefaultPocketMatchSignatureFactory signatureFactory =
            new DefaultPocketMatchSignatureFactory();

    @Autowired
    public PocketMatchSignatureLoader(
            DataSource dataSource,
            @Value("${totah.artifacts.root}") String artifactRoot,
            @Value("${totah.artifacts.external-root:}") String externalRoot
    ) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.artifactRoot = Path.of(
                Objects.requireNonNull(artifactRoot, "artifactRoot")
        ).toAbsolutePath().normalize();
        this.externalRoot = externalRoot == null || externalRoot.isBlank()
                ? null
                : Path.of(externalRoot).toAbsolutePath().normalize();
    }

    /**
     * Stable pocket-residue identity used to bind pocket membership to
     * parsed structure residues.
     */
    public record ResidueIdentity(
            String chain,
            int residueNumber,
            Character insertionCode,
            String residueName
    ) {
    }

    /**
     * Builds the full-fidelity signature of one pocket.
     */
    public PocketMatchSignature load(long pocketId)
            throws SQLException, IOException {
        String storageLocation = storageLocationOf(pocketId);
        List<ResidueIdentity> residues = residueIdentitiesOf(pocketId);
        if (residues.isEmpty()) {
            throw new SQLException(
                    "pocket " + pocketId + " has no residues"
            );
        }
        Structure structure = readStructure(storageLocation);
        return signatureFactory.describe(
                structure,
                pocketOf(pocketId, residues)
        );
    }

    /**
     * Parses a structure artifact, transparently decompressing gzip
     * payloads. A fresh reader is used per call, so concurrent calls
     * are safe.
     */
    public Structure readStructure(String storageLocation)
            throws IOException {
        Path path = resolveStorageLocation(storageLocation);
        if (!path.getFileName().toString().endsWith(".gz")) {
            return new PdbReader().read(path);
        }

        Path decompressed = Files.createTempFile(
                "pocket-match-structure-", ".pdb");
        try {
            try (InputStream input = new GZIPInputStream(
                    Files.newInputStream(path));
                 OutputStream output = Files.newOutputStream(
                         decompressed)) {
                input.transferTo(output);
            }
            return new PdbReader().read(decompressed);
        } finally {
            Files.deleteIfExists(decompressed);
        }
    }

    /**
     * Builds a domain pocket from residue identities. The center is the
     * CA centroid when available from the identities alone it cannot be
     * known, so it is simply unused placeholder geometry — the
     * PocketMatch factory only consumes residue membership.
     */
    public static Pocket pocketOf(
            long pocketId,
            List<ResidueIdentity> residues
    ) {
        List<ResidueId> residueIds = new ArrayList<>(residues.size());
        for (ResidueIdentity identity : residues) {
            residueIds.add(new ResidueId(
                    identity.chain(),
                    identity.residueNumber(),
                    identity.insertionCode()
            ));
        }
        return new Pocket(
                PocketId.of(pocketId),
                "pocket-" + pocketId,
                PocketSource.FPOCKET,
                new Point3D(0.0, 0.0, 0.0),
                residueIds,
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of()
        );
    }

    /**
     * Describes one pocket whose parent structure has already been
     * parsed, so batch callers pay the parse cost once per structure.
     */
    public PocketMatchSignature describe(
            Structure structure,
            long pocketId,
            List<ResidueIdentity> residues
    ) {
        return signatureFactory.describe(
                structure,
                pocketOf(pocketId, residues)
        );
    }

    private String storageLocationOf(long pocketId) throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     STORAGE_LOCATION_SQL)
        ) {
            statement.setLong(1, pocketId);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException(
                            "pocket " + pocketId
                                    + " has no structure artifact"
                    );
                }
                return rows.getString(1);
            }
        }
    }

    private List<ResidueIdentity> residueIdentitiesOf(long pocketId)
            throws SQLException {
        List<ResidueIdentity> residues = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     RESIDUE_IDENTITY_SQL)
        ) {
            statement.setLong(1, pocketId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    residues.add(new ResidueIdentity(
                            rows.getString("chain").trim(),
                            rows.getInt("residue_number"),
                            insertionCode(rows.getString(
                                    "insertion_code")),
                            rows.getString("residue_name")
                    ));
                }
            }
        }
        return residues;
    }

    private Path resolveStorageLocation(String storageLocation)
            throws IOException {
        if (storageLocation == null || storageLocation.isBlank()) {
            throw new IOException("Artifact storage location is required");
        }
        Path relative = Path.of(storageLocation);
        if (relative.isAbsolute()) {
            Path absolute = relative.toAbsolutePath().normalize();
            if (externalRoot == null
                    || !absolute.startsWith(externalRoot)) {
                throw new IOException(
                        "Artifact storage location is outside the"
                                + " allowed roots: " + storageLocation);
            }
            return absolute;
        }
        Path resolved = artifactRoot.resolve(relative).normalize();
        if (!resolved.startsWith(artifactRoot)) {
            throw new IOException(
                    "Artifact storage location escapes configured root: "
                            + storageLocation);
        }
        return resolved;
    }

    private static Character insertionCode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw.trim().charAt(0);
    }
}
