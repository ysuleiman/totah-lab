package totah.lab.biohub.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import totah.lab.http.biohub.BiohubClientConfig;
import totah.lab.http.biohub.BiohubComplexMapper;
import totah.lab.http.biohub.BiohubEsmFold2Client;
import totah.lab.http.biohub.BiohubEsmFold2Config;
import totah.lab.http.biohub.artifact.MolecularComplexPredictionArtifactWriter;
import totah.lab.http.biohub.model.MolecularComplexPrediction;
import totah.lab.ligand.Ligand;
import totah.lab.pocket.geometry.PocketGeometry;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Reproducible BioHub batch for top differential docking candidates that
 * contact a selected receptor residue.
 */
public final class BiohubCysteineCandidateBatch {

    private static final double POCKET_CUTOFF = 6.0;
    private static final String SQL = """
            WITH contacted AS (
                SELECT DISTINCT pose.ligand_id
                FROM docking.docking_pose pose
                JOIN docking.pose_residue_contact contact
                  ON contact.pose_id = pose.id
                JOIN docking.residue residue
                  ON residue.id = contact.residue_id
                WHERE pose.run_id = ?
                  AND pose.vina_score < ?
                  AND residue.structure_id = ?
                  AND residue.chain = ?
                  AND residue.residue_number = ?
                  AND residue.residue_name = ?
            ),
            best_primary AS (
                SELECT DISTINCT ON (ligand_id)
                    ligand_id, id AS pose_id, vina_score
                FROM docking.docking_pose
                WHERE run_id = ?
                ORDER BY ligand_id, vina_score, id
            ),
            best_comparison AS (
                SELECT DISTINCT ON (ligand_id)
                    ligand_id, id AS pose_id, vina_score
                FROM docking.docking_pose
                WHERE run_id = ?
                ORDER BY ligand_id, vina_score, id
            )
            SELECT
                contacted.ligand_id,
                primary_pose.vina_score AS score_primary,
                comparison_pose.vina_score AS score_comparison,
                comparison_pose.vina_score - primary_pose.vina_score AS delta,
                primary_pose.pose_id AS pose_id_primary,
                comparison_pose.pose_id AS pose_id_comparison
            FROM contacted
            JOIN best_primary primary_pose USING (ligand_id)
            JOIN best_comparison comparison_pose USING (ligand_id)
            ORDER BY delta DESC, contacted.ligand_id
            LIMIT ?
            """;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final MolecularComplexPredictionArtifactWriter predictionWriter =
            new MolecularComplexPredictionArtifactWriter();
    private final BiohubComplexMapper complexMapper = new BiohubComplexMapper();

    public static void main(String[] args) throws Exception {
        new BiohubCysteineCandidateBatch().run(Arguments.parse(args));
    }

    void run(Arguments arguments) throws Exception {
        List<Candidate> candidates = readCandidates(arguments);
        Map<String, String> smiles = readMculeSmiles(arguments.workbook());
        String sequence = readSequence(arguments.sequenceArtifact());
        String apiToken = Files.readString(arguments.apiKeyFile()).trim();
        if (apiToken.isBlank()) {
            throw new IOException("BioHub API key file is empty");
        }

        BiohubClientConfig clientConfig = new BiohubClientConfig(
                URI.create("https://biohub.ai"),
                apiToken,
                "esmc-300m-2024-12",
                Duration.ofMinutes(5)
        );
        BiohubEsmFold2Client client = new BiohubEsmFold2Client(clientConfig);
        Files.createDirectories(arguments.outputDirectory());

        List<ManifestEntry> manifest = new ArrayList<>();
        for (Candidate candidate : candidates) {
            String ligandSmiles = smiles.get(candidate.ligandId());
            if (ligandSmiles == null) {
                throw new IOException(
                        "No SMILES found for " + candidate.ligandId()
                );
            }
            manifest.add(analyze(
                    client,
                    sequence,
                    candidate,
                    ligandSmiles,
                    arguments.outputDirectory()
            ));
            writeManifest(arguments, manifest);
        }
    }

    private ManifestEntry analyze(
            BiohubEsmFold2Client client,
            String sequence,
            Candidate candidate,
            String smiles,
            Path outputDirectory
    ) throws IOException, InterruptedException {
        Path ligandDirectory = outputDirectory.resolve(candidate.ligandId());
        Files.createDirectories(ligandDirectory);
        Path predictionJson = ligandDirectory.resolve(
                candidate.ligandId() + "_esmfold2_complex.json"
        );
        Path predictionPdb = ligandDirectory.resolve(
                candidate.ligandId() + "_esmfold2_complex.pdb"
        );
        Path pocketJson = ligandDirectory.resolve(
                candidate.ligandId() + "_esmfold2_complex_pocket_6A.json"
        );

        MolecularComplexPrediction prediction = client.foldProteinSmiles(
                sequence,
                candidate.ligandId(),
                smiles,
                BiohubEsmFold2Config.quality()
        );
        predictionWriter.writeJson(predictionJson, prediction);
        predictionWriter.writePdb(predictionPdb, prediction);

        Structure structure = complexMapper.proteinStructure(prediction, "A");
        Ligand ligand = complexMapper.ligand(prediction, "L");
        List<Contact> contacts = structure.getResidues().stream()
                .filter(residue -> PocketGeometry.areNeighbors(
                        residue,
                        ligand,
                        POCKET_CUTOFF
                ))
                .map(residue -> toContact(residue, ligand))
                .toList();
        PocketArtifact pocket = new PocketArtifact(
                "A",
                "L",
                candidate.ligandId(),
                smiles,
                POCKET_CUTOFF,
                contacts
        );
        objectMapper.writeValue(pocketJson.toFile(), pocket);

        return new ManifestEntry(
                candidate,
                smiles,
                prediction.provider(),
                prediction.model(),
                prediction.generatedAt(),
                prediction.ptm(),
                prediction.interfacePtm(),
                contacts.size(),
                contacts.stream().anyMatch(contact ->
                        contact.residueNumber() == 202
                                && contact.residueName().equals("CYS")),
                outputDirectory.relativize(predictionJson).toString(),
                outputDirectory.relativize(predictionPdb).toString(),
                outputDirectory.relativize(pocketJson).toString()
        );
    }

    private Contact toContact(Residue residue, Ligand ligand) {
        return new Contact(
                residue.getChain(),
                residue.getNumber(),
                residue.getName(),
                PocketGeometry.calculateDistance(residue, ligand),
                PocketGeometry.contactingAtomPairCount(
                        residue,
                        ligand,
                        POCKET_CUTOFF
                )
        );
    }

    private void writeManifest(
            Arguments arguments,
            List<ManifestEntry> entries
    ) throws IOException {
        BatchManifest manifest = new BatchManifest(
                "1.0",
                "BIOHUB_TOP_DIFFERENTIAL_CYS_CONTACT_BATCH",
                Instant.now(),
                arguments.primaryRunId(),
                arguments.comparisonRunId(),
                arguments.structureId(),
                arguments.chain(),
                arguments.residueNumber(),
                arguments.scoreThreshold(),
                List.copyOf(entries)
        );
        objectMapper.writeValue(
                arguments.outputDirectory().resolve("manifest.json").toFile(),
                manifest
        );
    }

    private List<Candidate> readCandidates(Arguments arguments)
            throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                arguments.jdbcUrl(),
                arguments.databaseUser(),
                arguments.databasePassword()
        ); PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setLong(1, arguments.primaryRunId());
            statement.setDouble(2, arguments.scoreThreshold());
            statement.setLong(3, arguments.structureId());
            statement.setString(4, arguments.chain());
            statement.setInt(5, arguments.residueNumber());
            statement.setString(6, arguments.residueName());
            statement.setLong(7, arguments.primaryRunId());
            statement.setLong(8, arguments.comparisonRunId());
            statement.setInt(9, arguments.limit());
            try (ResultSet resultSet = statement.executeQuery()) {
                List<Candidate> result = new ArrayList<>();
                while (resultSet.next()) {
                    result.add(new Candidate(
                            resultSet.getString("ligand_id"),
                            resultSet.getDouble("score_primary"),
                            resultSet.getDouble("score_comparison"),
                            resultSet.getDouble("delta"),
                            resultSet.getLong("pose_id_primary"),
                            resultSet.getLong("pose_id_comparison")
                    ));
                }
                if (result.size() != arguments.limit()) {
                    throw new SQLException(
                            "Expected " + arguments.limit()
                                    + " candidates, found " + result.size()
                    );
                }
                return List.copyOf(result);
            }
        }
    }

    Map<String, String> readMculeSmiles(Path workbook)
            throws IOException {
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Map<String, String> result = new HashMap<>();
        try (InputStream input = Files.newInputStream(workbook);
             var excel = WorkbookFactory.create(input)) {
            for (Sheet sheet : excel) {
                for (Row row : sheet) {
                    List<String> values = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            values.add(value);
                        }
                    }
                    String ligandId = values.stream()
                            .filter(value -> value.startsWith("MCULE-"))
                            .findFirst()
                            .orElse(null);
                    if (ligandId == null) {
                        continue;
                    }
                    String smiles = values.stream()
                            .filter(value -> isSmiles(value, ligandId))
                            .findFirst()
                            .orElse(null);
                    if (smiles != null) {
                        result.put(ligandId, smiles);
                    }
                }
            }
        }
        return Map.copyOf(result);
    }

    boolean isSmiles(String value, String ligandId) {
        if (value.equals(ligandId)
                || value.startsWith("out")
                || value.length() < 5) {
            return false;
        }
        return value.matches(".*[BCNOFPSIbcnost].*")
                && value.matches(".*[()=\\[\\]#].*");
    }

    private String readSequence(Path sequenceArtifact) throws IOException {
        JsonNode root = objectMapper.readTree(sequenceArtifact.toFile());
        String sequence = root.path("analysis").path("sequence").asText();
        if (sequence.isBlank()) {
            throw new IOException(
                    "Sequence artifact has no analysis.sequence"
            );
        }
        return sequence;
    }

    record Candidate(
            String ligandId,
            double scorePrimary,
            double scoreComparison,
            double delta,
            long poseIdPrimary,
            long poseIdComparison
    ) {
    }

    record Contact(
            String chain,
            int residueNumber,
            String residueName,
            double minimumDistance,
            int contactingAtomPairCount
    ) {
    }

    record PocketArtifact(
            String proteinChain,
            String ligandChain,
            String ligandCcd,
            String smiles,
            double cutoff,
            List<Contact> residues
    ) {
    }

    record ManifestEntry(
            Candidate candidate,
            String smiles,
            String provider,
            String model,
            Instant generatedAt,
            Double ptm,
            Double interfacePtm,
            int pocketResidueCount,
            boolean contactsCys202,
            String predictionJson,
            String predictionPdb,
            String pocketJson
    ) {
    }

    record BatchManifest(
            String schemaVersion,
            String analysisType,
            Instant updatedAt,
            long primaryRunId,
            long comparisonRunId,
            long structureId,
            String chain,
            int residueNumber,
            double scoreThreshold,
            List<ManifestEntry> entries
    ) {
    }

    record Arguments(
            String jdbcUrl,
            String databaseUser,
            String databasePassword,
            long primaryRunId,
            long comparisonRunId,
            long structureId,
            String chain,
            int residueNumber,
            String residueName,
            double scoreThreshold,
            int limit,
            Path workbook,
            Path sequenceArtifact,
            Path apiKeyFile,
            Path outputDirectory
    ) {
        Arguments {
            Objects.requireNonNull(jdbcUrl, "jdbcUrl");
            Objects.requireNonNull(databaseUser, "databaseUser");
            Objects.requireNonNull(databasePassword, "databasePassword");
            Objects.requireNonNull(chain, "chain");
            Objects.requireNonNull(residueName, "residueName");
            Objects.requireNonNull(workbook, "workbook");
            Objects.requireNonNull(sequenceArtifact, "sequenceArtifact");
            Objects.requireNonNull(apiKeyFile, "apiKeyFile");
            Objects.requireNonNull(outputDirectory, "outputDirectory");
        }

        static Arguments parse(String[] args) {
            Map<String, String> values = new HashMap<>();
            for (String argument : args) {
                int separator = argument.indexOf('=');
                if (!argument.startsWith("--") || separator < 3) {
                    throw new IllegalArgumentException(
                            "Expected --name=value, got " + argument
                    );
                }
                values.put(
                        argument.substring(2, separator),
                        argument.substring(separator + 1)
                );
            }
            return new Arguments(
                    values.getOrDefault(
                            "jdbc-url",
                            "jdbc:postgresql://localhost:5432/totah_lab_db"
                    ),
                    values.getOrDefault("db-user", "postgres"),
                    required(values, "db-password"),
                    Long.parseLong(values.getOrDefault("primary-run", "1")),
                    Long.parseLong(values.getOrDefault("comparison-run", "3")),
                    Long.parseLong(values.getOrDefault("structure", "2")),
                    values.getOrDefault("chain", "A"),
                    Integer.parseInt(values.getOrDefault("residue", "202")),
                    values.getOrDefault("residue-name", "CYS"),
                    Double.parseDouble(values.getOrDefault("score", "-5")),
                    Integer.parseInt(values.getOrDefault("limit", "20")),
                    Path.of(required(values, "workbook")),
                    Path.of(required(values, "sequence-artifact")),
                    Path.of(required(values, "api-key-file")),
                    Path.of(required(values, "output"))
            );
        }

        private static String required(
                Map<String, String> values,
                String name
        ) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(
                        "Missing required --" + name + "=..."
                );
            }
            return value;
        }
    }
}
