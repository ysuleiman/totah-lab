package totah.lab.http.biohub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import totah.lab.http.biohub.model.ResidueConstraintAnalysis;
import totah.lab.http.biohub.model.ResidueConstraintEvidence;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class BiohubEsmcClient {

    private static final String PROVIDER = "BIOHUB_ESMC";

    private final BiohubClientConfig config;
    private final BiohubHttpTransport transport;
    private final ObjectMapper objectMapper;
    private final EsmcResidueConstraintCalculator calculator;
    private final Clock clock;

    public BiohubEsmcClient(BiohubClientConfig config) {
        this(
                config,
                new JdkBiohubHttpTransport(config.requestTimeout()),
                new ObjectMapper(),
                new EsmcResidueConstraintCalculator(),
                Clock.systemUTC()
        );
    }

    BiohubEsmcClient(
            BiohubClientConfig config,
            BiohubHttpTransport transport,
            ObjectMapper objectMapper,
            EsmcResidueConstraintCalculator calculator,
            Clock clock
    ) {
        this.config = Objects.requireNonNull(config, "config");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(
                objectMapper,
                "objectMapper"
        );
        this.calculator = Objects.requireNonNull(calculator, "calculator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ResidueConstraintAnalysis analyze(String sequence)
            throws IOException, InterruptedException {
        String normalizedSequence = normalizeSequence(sequence);
        int[] encodedTokens = encode(normalizedSequence);
        validateEncodedSequence(normalizedSequence, encodedTokens);
        double[][] logits = requestLogits(encodedTokens);
        List<ResidueConstraintEvidence> residues = calculator.calculate(
                normalizedSequence,
                logits
        );
        return new ResidueConstraintAnalysis(
                PROVIDER,
                config.esmcModel(),
                normalizedSequence,
                Instant.now(clock),
                residues
        );
    }

    private int[] encode(String sequence)
            throws IOException, InterruptedException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", config.esmcModel());
        request.put("potential_sequence_of_concern", false);
        request.putObject("inputs").put("sequence", sequence);

        JsonNode response = post("encode", request);
        JsonNode sequenceNode = response.path("outputs").path("sequence");
        sequenceNode = unwrapSingleBatch(sequenceNode);
        if (!sequenceNode.isArray()) {
            throw new IOException(
                    "BioHub encode response has no sequence token array"
            );
        }
        int[] tokens = new int[sequenceNode.size()];
        for (int index = 0; index < tokens.length; index++) {
            tokens[index] = sequenceNode.get(index).asInt();
        }
        return tokens;
    }

    private double[][] requestLogits(int[] encodedTokens)
            throws IOException, InterruptedException {
        ObjectNode request = objectMapper.createObjectNode();
        request.put("model", config.esmcModel());
        request.put("potential_sequence_of_concern", false);
        ArrayNode sequenceTokens = request.putObject("inputs")
                .putArray("sequence");
        for (int token : encodedTokens) {
            sequenceTokens.add(token);
        }
        ObjectNode logitsConfig = request.putObject("logits_config");
        logitsConfig.put("sequence", true);
        logitsConfig.put("return_embeddings", false);
        logitsConfig.put("return_mean_embedding", false);
        logitsConfig.put("return_mean_hidden_states", false);
        logitsConfig.put("return_hidden_states", false);
        logitsConfig.put("ith_hidden_layer", -1);
        logitsConfig.putNull("sae_config");

        JsonNode response = post("logits", request);
        JsonNode sequenceLogits = response.path("logits").path("sequence");
        sequenceLogits = unwrapSingleBatch(sequenceLogits);
        if (!sequenceLogits.isArray()) {
            throw new IOException(
                    "BioHub logits response has no sequence logits array"
            );
        }
        double[][] logits = new double[sequenceLogits.size()][];
        for (int position = 0; position < sequenceLogits.size(); position++) {
            JsonNode row = sequenceLogits.get(position);
            if (!row.isArray()) {
                throw new IOException(
                        "BioHub logits response contains a non-array row"
                );
            }
            logits[position] = new double[row.size()];
            for (int token = 0; token < row.size(); token++) {
                logits[position][token] = row.get(token).asDouble();
            }
        }
        return logits;
    }

    private JsonNode post(String endpoint, JsonNode body)
            throws IOException, InterruptedException {
        URI endpointUri = config.baseUri().resolve("/api/v1/" + endpoint);
        BiohubHttpTransport.Response response = transport.post(
                endpointUri,
                config.apiToken(),
                config.requestTimeout(),
                objectMapper.writeValueAsString(body)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException(
                    "BioHub " + endpoint + " failed with HTTP "
                            + response.statusCode() + ": "
                            + abbreviate(response.body())
            );
        }
        JsonNode payload = objectMapper.readTree(response.body());
        if (!payload.has("outputs") && payload.has("data")) {
            payload = payload.get("data");
        }
        return payload;
    }

    private void validateEncodedSequence(String sequence, int[] tokens)
            throws IOException {
        if (tokens.length != sequence.length() + 2) {
            throw new IOException(
                    "BioHub encoded token count does not match sequence length"
            );
        }
        for (int index = 0; index < sequence.length(); index++) {
            int expectedToken = EsmcVocabulary.tokenFor(
                    sequence.charAt(index)
            );
            if (tokens[index + 1] != expectedToken) {
                throw new IOException(
                        "BioHub ESMC vocabulary mismatch at residue "
                                + (index + 1)
                );
            }
        }
    }

    private JsonNode unwrapSingleBatch(JsonNode value) {
        if (value.isArray()
                && value.size() == 1
                && value.get(0).isArray()) {
            return value.get(0);
        }
        return value;
    }

    private String normalizeSequence(String sequence) {
        Objects.requireNonNull(sequence, "sequence");
        String normalized = sequence.replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("sequence must not be empty");
        }
        for (int index = 0; index < normalized.length(); index++) {
            EsmcVocabulary.tokenFor(normalized.charAt(index));
        }
        return normalized;
    }

    private String abbreviate(String body) {
        if (body == null) return "";
        return body.length() <= 500 ? body : body.substring(0, 500) + "…";
    }
}
