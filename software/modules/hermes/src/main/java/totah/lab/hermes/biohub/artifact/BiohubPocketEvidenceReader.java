package totah.lab.hermes.biohub.artifact;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.http.biohub.model.BiohubPocketEvidence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class BiohubPocketEvidenceReader {

    public static final double DIRECT_CONTACT_CUTOFF = 4.5;
    private static final String POCKET_SUFFIX = "_pocket_6A.json";

    private final ObjectMapper objectMapper = new ObjectMapper();

    public BiohubPocketEvidence read(Path pocketArtifact) throws IOException {
        Objects.requireNonNull(pocketArtifact, "pocketArtifact");
        JsonNode pocket = readDocument(pocketArtifact);
        String filename = pocketArtifact.getFileName().toString();
        if (!filename.endsWith(POCKET_SUFFIX)) {
            throw new IOException(
                    "Unsupported BioHub pocket artifact name: " + filename
            );
        }
        Path predictionArtifact = pocketArtifact.resolveSibling(
                filename.substring(
                        0,
                        filename.length() - POCKET_SUFFIX.length()
                ) + ".json"
        );
        JsonNode prediction = readDocument(predictionArtifact)
                .path("prediction");

        String ligandCcd = requiredText(pocket, "ligandCcd");
        if (!ligandCcd.equals(requiredText(prediction, "ligandCcd"))) {
            throw new IOException(
                    "Pocket and prediction ligand identifiers do not match"
            );
        }
        List<BiohubPocketEvidence.ResidueContact> contacts =
                readContacts(pocket.path("residues"));
        return new BiohubPocketEvidence(
                ligandCcd,
                requiredText(prediction, "model"),
                requiredDouble(pocket, "cutoff"),
                DIRECT_CONTACT_CUTOFF,
                nullableDouble(prediction.get("ptm")),
                nullableDouble(prediction.get("interfacePtm")),
                contacts
        );
    }

    private List<BiohubPocketEvidence.ResidueContact> readContacts(
            JsonNode residues
    ) throws IOException {
        if (!residues.isArray()) {
            throw new IOException(
                    "BioHub pocket artifact has no residue array"
            );
        }
        List<BiohubPocketEvidence.ResidueContact> contacts =
                new ArrayList<>(residues.size());
        for (JsonNode residue : residues) {
            double minimumDistance = requiredDouble(
                    residue,
                    "minimumDistance"
            );
            contacts.add(new BiohubPocketEvidence.ResidueContact(
                    requiredText(residue, "chain"),
                    requiredInt(residue, "residueNumber"),
                    requiredText(residue, "residueName"),
                    minimumDistance,
                    requiredInt(residue, "contactingAtomPairCount"),
                    minimumDistance <= DIRECT_CONTACT_CUTOFF
            ));
        }
        return List.copyOf(contacts);
    }

    private JsonNode readDocument(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return objectMapper.readTree(input);
        }
    }

    private String requiredText(JsonNode node, String field)
            throws IOException {
        String value = node.path(field).asText();
        if (value.isBlank()) {
            throw new IOException("Missing BioHub field: " + field);
        }
        return value;
    }

    private double requiredDouble(JsonNode node, String field)
            throws IOException {
        if (!node.has(field) || !node.get(field).isNumber()) {
            throw new IOException("Missing BioHub field: " + field);
        }
        return node.get(field).asDouble();
    }

    private int requiredInt(JsonNode node, String field)
            throws IOException {
        if (!node.has(field) || !node.get(field).canConvertToInt()) {
            throw new IOException("Missing BioHub field: " + field);
        }
        return node.get(field).asInt();
    }

    private Double nullableDouble(JsonNode node) {
        return node == null || node.isNull() ? null : node.asDouble();
    }
}
