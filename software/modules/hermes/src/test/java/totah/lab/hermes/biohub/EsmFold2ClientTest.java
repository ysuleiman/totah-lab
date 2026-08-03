package totah.lab.hermes.biohub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.biohub.config.EsmHttpClientConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EsmFold2ClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsRequestJsonWithEscapedSequenceCharacters() throws Exception {
        EsmFold2Client client = new EsmFold2Client(config());
        String sequence = "ACD\"EF\\GH\nIJK\tLM";

        String payload = client.buildRequestJson(sequence);

        JsonNode root = objectMapper.readTree(payload);
        assertEquals(sequence, root.path("sequence").asText());
        assertEquals("esmfold2-fast", root.path("model_variant").asText());
        assertEquals("balanced", root.path("compute_budget").asText());
        assertTrue(root.path("return_pocket_features").asBoolean());
    }

    @Test
    void buildsRequestJsonForPlainSequence() throws Exception {
        EsmFold2Client client = new EsmFold2Client(config());

        JsonNode root = objectMapper.readTree(
                client.buildRequestJson("MSHHWGYGK"));

        assertEquals("MSHHWGYGK", root.path("sequence").asText());
    }

    private EsmHttpClientConfig config() {
        return new EsmHttpClientConfig() {
            @Override
            public String getApiUrl() { return "https://biohub.ai"; }
            @Override
            public String getApiKey() { return "secret"; }
        };
    }
}
