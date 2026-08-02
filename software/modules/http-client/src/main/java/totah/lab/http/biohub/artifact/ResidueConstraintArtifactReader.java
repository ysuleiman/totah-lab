package totah.lab.http.biohub.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import totah.lab.http.biohub.model.ResidueConstraintAnalysis;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ResidueConstraintArtifactReader {

    private final ObjectMapper objectMapper;

    public ResidueConstraintArtifactReader() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
    }

    public ResidueConstraintAnalysis read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            ResidueConstraintArtifact artifact = objectMapper.readValue(
                    input,
                    ResidueConstraintArtifact.class
            );
            validateHeader(artifact);
            return artifact.analysis();
        } catch (IllegalArgumentException exception) {
            throw new IOException(
                    "Invalid residue constraint artifact: "
                            + exception.getMessage(),
                    exception
            );
        }
    }

    private void validateHeader(ResidueConstraintArtifact artifact)
            throws IOException {
        if (artifact == null) {
            throw new IOException("Residue constraint artifact is empty");
        }
        if (!ResidueConstraintArtifact.SCHEMA_VERSION.equals(
                artifact.schemaVersion()
        )) {
            throw new IOException(
                    "Unsupported residue constraint schema version: "
                            + artifact.schemaVersion()
            );
        }
        if (!ResidueConstraintArtifact.ANALYSIS_TYPE.equals(
                artifact.analysisType()
        )) {
            throw new IOException(
                    "Unexpected analysis type: " + artifact.analysisType()
            );
        }
        if (artifact.analysis() == null) {
            throw new IOException("Residue constraint analysis is missing");
        }
    }
}
