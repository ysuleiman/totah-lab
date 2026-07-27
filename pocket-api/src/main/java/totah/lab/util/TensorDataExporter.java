package totah.lab.util;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public final class TensorDataExporter {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private TensorDataExporter() {}

    /**
     * Serializes your high-fidelity comparative java metrics directly into a
     * tensor-ready JSON file. Automatically handles missing path generations dynamically.
     */
    public static void exportDataset(Map<String, Object> analyticalPayload, Path targetDirectory, String fileName) throws IOException {
        Objects.requireNonNull(analyticalPayload, "Payload matrix cannot be null");
        Objects.requireNonNull(targetDirectory, "Output directory path cannot be null");

        // Self-Healing Step: Build directory paths if missing
        if (!Files.exists(targetDirectory)) {
            System.out.println("📁 Directory path not found. Natively constructing: " + targetDirectory);
            Files.createDirectories(targetDirectory);
        }

        // 1. Pack the multi-row structural dataset arrays from your analytical snapshot payload
        Map<String, Object> tfRecord = TensorFlowDatasetPacker.packMultiRowDataset(analyticalPayload);

        // Resolve local file destinations safely
        File outputFile = targetDirectory.resolve(fileName + ".json").toFile();

        // 2. FIXED: Write 'tfRecord' (NOT analyticalPayload) straight to your disk!
        mapper.writeValue(outputFile, tfRecord);
        System.out.println("🚀 TensorFlow Dataset Asset successfully compiled and written to: " + outputFile.getAbsolutePath());
    }
}