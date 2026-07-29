package totah.lab.http.biohub.artifact;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import totah.lab.http.biohub.model.ResidueConstraintAnalysis;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class ResidueConstraintArtifactWriter {

    private final ObjectMapper objectMapper;

    public ResidueConstraintArtifactWriter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void write(Path path, ResidueConstraintAnalysis analysis)
            throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(analysis, "analysis");
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        ResidueConstraintArtifact artifact =
                ResidueConstraintArtifact.from(analysis);
        try (OutputStream output = Files.newOutputStream(path)) {
            objectMapper.writeValue(output, artifact);
        }
    }
}
