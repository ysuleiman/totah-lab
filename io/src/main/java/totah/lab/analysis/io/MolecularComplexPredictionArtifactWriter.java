package totah.lab.analysis.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import totah.lab.protein.analysis.ComplexAtom;
import totah.lab.protein.analysis.ComplexToken;
import totah.lab.protein.analysis.MolecularComplexPrediction;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

public final class MolecularComplexPredictionArtifactWriter {

    private static final String SCHEMA_VERSION = "1.0";
    private static final String ANALYSIS_TYPE =
            "LIGAND_CONDITIONED_COMPLEX_PREDICTION";

    private final ObjectMapper objectMapper;

    public MolecularComplexPredictionArtifactWriter() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void writeJson(
            Path path,
            MolecularComplexPrediction prediction
    ) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(prediction, "prediction");
        createParent(path);
        PredictionArtifact artifact = new PredictionArtifact(
                SCHEMA_VERSION,
                ANALYSIS_TYPE,
                prediction
        );
        try (OutputStream output = Files.newOutputStream(path)) {
            objectMapper.writeValue(output, artifact);
        }
    }

    public void writePdb(
            Path path,
            MolecularComplexPrediction prediction
    ) throws IOException {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(prediction, "prediction");
        createParent(path);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            int serial = 1;
            for (ComplexToken token : prediction.tokens()) {
                for (ComplexAtom atom : token.atoms()) {
                    writer.write(pdbLine(serial++, token, atom));
                    writer.newLine();
                }
            }
            writer.write("END");
            writer.newLine();
        }
    }

    private String pdbLine(
            int serial,
            ComplexToken token,
            ComplexAtom atom
    ) {
        String record = atom.hetero() ? "HETATM" : "ATOM";
        String atomName = atom.name().length() > 4
                ? atom.name().substring(0, 4)
                : atom.name();
        String residueName = token.residueName().length() > 3
                ? token.residueName().substring(0, 3)
                : token.residueName();
        char chain = token.chain().charAt(0);
        return String.format(
                Locale.ROOT,
                "%-6s%5d %-4s %-3s %1s%4d    "
                        + "%8.3f%8.3f%8.3f%6.2f%6.2f          %2s",
                record,
                serial,
                atomName,
                residueName,
                chain,
                token.chainPosition(),
                atom.x(),
                atom.y(),
                atom.z(),
                1.0,
                token.confidence() * 100.0,
                atom.element()
        );
    }

    private void createParent(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private record PredictionArtifact(
            String schemaVersion,
            String analysisType,
            MolecularComplexPrediction prediction
    ) {
    }
}
