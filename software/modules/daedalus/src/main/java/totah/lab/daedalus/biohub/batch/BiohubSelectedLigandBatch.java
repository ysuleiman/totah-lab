package totah.lab.daedalus.biohub.batch;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import totah.lab.hermes.biohub.BiohubClientConfig;
import totah.lab.hermes.biohub.BiohubComplexMapper;
import totah.lab.hermes.biohub.BiohubEsmFold2Client;
import totah.lab.hermes.biohub.BiohubEsmFold2Config;
import totah.lab.hermes.biohub.artifact.MolecularComplexPredictionArtifactWriter;
import totah.lab.hermes.biohub.model.MolecularComplexPrediction;
import totah.lab.athena.pocket.geometry.PocketGeometry;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Runs a named ligand subset from an existing BioHub batch manifest. */
public final class BiohubSelectedLigandBatch {

    private static final double CONTACT_CUTOFF = 6.0;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final MolecularComplexPredictionArtifactWriter artifactWriter =
            new MolecularComplexPredictionArtifactWriter();
    private final BiohubComplexMapper complexMapper = new BiohubComplexMapper();

    public static void main(String[] arguments) throws Exception {
        new BiohubSelectedLigandBatch().run(Arguments.parse(arguments));
    }

    void run(Arguments arguments) throws Exception {
        Map<String, SourceLigand> sourceLigands = readSourceLigands(
                arguments.sourceManifest()
        );
        String sequence = readSequence(arguments.sequenceArtifact());
        String token = Files.readString(arguments.apiKeyFile()).trim();
        if (token.isBlank()) {
            throw new IOException("BioHub API key file is empty");
        }
        BiohubEsmFold2Client client = new BiohubEsmFold2Client(
                new BiohubClientConfig(
                        URI.create("https://biohub.ai"),
                        token,
                        "esmc-300m-2024-12",
                        Duration.ofMinutes(5)
                )
        );
        Files.createDirectories(arguments.outputDirectory());
        List<ResultEntry> completed = new ArrayList<>();
        for (String ligandId : arguments.ligandIds()) {
            SourceLigand source = sourceLigands.get(ligandId);
            if (source == null) {
                throw new IOException("Ligand is absent from source manifest: " + ligandId);
            }
            completed.add(predict(client, arguments, source));
            writeManifest(arguments, completed);
        }
    }

    private ResultEntry predict(
            BiohubEsmFold2Client client,
            Arguments arguments,
            SourceLigand source
    ) throws IOException, InterruptedException {
        Path ligandDirectory = arguments.outputDirectory().resolve(source.ligandId());
        Files.createDirectories(ligandDirectory);
        String prefix = source.ligandId() + "_esmfold2_complex";
        Path json = ligandDirectory.resolve(prefix + ".json");
        Path pdb = ligandDirectory.resolve(prefix + ".pdb");
        Path pocket = ligandDirectory.resolve(prefix + "_pocket_6A.json");
        MolecularComplexPrediction prediction = client.foldProteinSmiles(
                readSequence(arguments.sequenceArtifact()),
                source.ligandId(),
                source.smiles(),
                BiohubEsmFold2Config.quality()
        );
        artifactWriter.writeJson(json, prediction);
        artifactWriter.writePdb(pdb, prediction);
        Structure structure = complexMapper.proteinStructure(prediction, "A");
        Ligand ligand = complexMapper.ligand(prediction, "L");
        List<Contact> contacts = structure.findChain("A").orElseThrow().residues().stream()
                .filter(residue -> PocketGeometry.areNeighbors(
                        residue, ligand, CONTACT_CUTOFF
                ))
                .map(residue -> contact("A", residue, ligand))
                .toList();
        objectMapper.writeValue(pocket.toFile(), new PocketArtifact(
                "A", "L", source.ligandId(), source.smiles(),
                CONTACT_CUTOFF, contacts
        ));
        return new ResultEntry(
                source, prediction.provider(), prediction.model(),
                prediction.generatedAt(), prediction.ptm(),
                prediction.interfacePtm(), contacts.size(),
                relative(arguments, json), relative(arguments, pdb),
                relative(arguments, pocket)
        );
    }

    private Contact contact(String chainId, Residue residue, Ligand ligand) {
        return new Contact(
                chainId, residue.getNumber(), residue.getName(),
                PocketGeometry.calculateDistance(residue, ligand),
                PocketGeometry.contactingAtomPairCount(
                        residue, ligand, CONTACT_CUTOFF
                )
        );
    }

    private String relative(Arguments arguments, Path path) {
        return arguments.outputDirectory().relativize(path).toString();
    }

    private void writeManifest(Arguments arguments, List<ResultEntry> entries)
            throws IOException {
        objectMapper.writeValue(
                arguments.outputDirectory().resolve("manifest.json").toFile(),
                new BatchManifest(
                        "1.0", "BIOHUB_SELECTED_LIGAND_BATCH", Instant.now(),
                        arguments.targetId(), arguments.targetName(),
                        arguments.sourceManifest().toString(), List.copyOf(entries)
                )
        );
    }

    Map<String, SourceLigand> readSourceLigands(Path manifest) throws IOException {
        JsonNode entries = objectMapper.readTree(manifest.toFile()).path("entries");
        if (!entries.isArray()) {
            throw new IOException("Source manifest has no entries array");
        }
        Map<String, SourceLigand> result = new LinkedHashMap<>();
        for (JsonNode entry : entries) {
            JsonNode candidate = entry.path("candidate");
            String ligandId = candidate.path("ligandId").asText();
            String smiles = entry.path("smiles").asText();
            if (ligandId.isBlank() || smiles.isBlank()) {
                throw new IOException("Source manifest entry lacks ligandId or SMILES");
            }
            result.put(ligandId, new SourceLigand(
                    ligandId, smiles,
                    candidate.path("scorePrimary").asDouble(),
                    candidate.path("scoreComparison").asDouble(),
                    candidate.path("delta").asDouble()
            ));
        }
        return Map.copyOf(result);
    }

    private String readSequence(Path artifact) throws IOException {
        String sequence = objectMapper.readTree(artifact.toFile())
                .path("analysis").path("sequence").asText();
        if (sequence.isBlank()) {
            throw new IOException("Sequence artifact has no analysis.sequence");
        }
        return sequence;
    }

    record SourceLigand(
            String ligandId,
            String smiles,
            double mettl7bVinaScore,
            double mettl7aVinaScore,
            double selectivityDelta
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
            String ligandId,
            String smiles,
            double cutoff,
            List<Contact> residues
    ) {
    }

    record ResultEntry(
            SourceLigand source,
            String provider,
            String model,
            Instant generatedAt,
            Double ptm,
            Double interfacePtm,
            int pocketResidueCount,
            String predictionJson,
            String predictionPdb,
            String pocketJson
    ) {
    }

    record BatchManifest(
            String schemaVersion,
            String analysisType,
            Instant updatedAt,
            String targetId,
            String targetName,
            String sourceManifest,
            List<ResultEntry> entries
    ) {
    }

    record Arguments(
            String targetId,
            String targetName,
            Path sourceManifest,
            Path sequenceArtifact,
            Path apiKeyFile,
            Path outputDirectory,
            List<String> ligandIds
    ) {
        Arguments {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(targetName, "targetName");
            Objects.requireNonNull(sourceManifest, "sourceManifest");
            Objects.requireNonNull(sequenceArtifact, "sequenceArtifact");
            Objects.requireNonNull(apiKeyFile, "apiKeyFile");
            Objects.requireNonNull(outputDirectory, "outputDirectory");
            ligandIds = List.copyOf(ligandIds);
            if (ligandIds.isEmpty()) {
                throw new IllegalArgumentException("At least one ligand is required");
            }
        }

        static Arguments parse(String[] arguments) {
            Map<String, String> values = new LinkedHashMap<>();
            for (String argument : arguments) {
                int separator = argument.indexOf('=');
                if (!argument.startsWith("--") || separator < 3) {
                    throw new IllegalArgumentException("Expected --name=value: " + argument);
                }
                values.put(argument.substring(2, separator), argument.substring(separator + 1));
            }
            return new Arguments(
                    required(values, "target-id"),
                    required(values, "target-name"),
                    Path.of(required(values, "source-manifest")),
                    Path.of(required(values, "sequence-artifact")),
                    Path.of(required(values, "api-key-file")),
                    Path.of(required(values, "output")),
                    Arrays.stream(required(values, "ligands").split(","))
                            .map(String::trim).filter(value -> !value.isBlank()).toList()
            );
        }

        private static String required(Map<String, String> values, String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Missing required --" + name + "=...");
            }
            return value;
        }
    }
}
