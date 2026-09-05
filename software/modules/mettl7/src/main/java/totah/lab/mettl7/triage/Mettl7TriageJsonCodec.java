package totah.lab.mettl7.triage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic JSON boundary for batch ingestion and durable results. */
public final class Mettl7TriageJsonCodec {
    private final ObjectMapper mapper;

    public Mettl7TriageJsonCodec() {
        mapper = JsonMapper.builder()
                .findAndAddModules()
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .build();
    }

    public Mettl7TriageInput readInput(Path path) throws IOException {
        try (var input = Files.newInputStream(path)) {
            return mapper.readValue(input, Mettl7TriageInput.class);
        }
    }

    public void writeResult(Path path, Mettl7TriageResult result) throws IOException {
        try (var output = Files.newOutputStream(path)) {
            mapper.writeValue(output, result);
        }
    }

    public String writeResult(Mettl7TriageResult result) throws IOException {
        return mapper.writeValueAsString(result) + System.lineSeparator();
    }

    public Mettl7TriageResult readResult(String json) throws IOException {
        return mapper.readValue(json, Mettl7TriageResult.class);
    }
}
