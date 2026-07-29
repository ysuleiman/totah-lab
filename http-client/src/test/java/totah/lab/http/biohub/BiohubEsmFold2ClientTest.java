package totah.lab.http.biohub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BiohubEsmFold2ClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void foldsProteinWithCcdLigandUsingOfficialContract() throws Exception {
        AtomicReference<JsonNode> requestBody = new AtomicReference<>();
        BiohubHttpTransport transport = (
                uri,
                token,
                timeout,
                body
        ) -> {
            assertEquals(
                    URI.create("https://biohub.ai/api/v1/fold_all_atom"),
                    uri
            );
            assertEquals("secret", token);
            requestBody.set(objectMapper.readTree(body));
            return new BiohubHttpTransport.Response(200, responseBody());
        };
        BiohubClientConfig config = new BiohubClientConfig(
                URI.create("https://biohub.ai"),
                "secret",
                "esmc-300m-2024-12",
                Duration.ofMinutes(10)
        );
        BiohubEsmFold2Client client = new BiohubEsmFold2Client(
                config,
                BiohubEsmFold2Client.DEFAULT_MODEL,
                transport,
                objectMapper,
                Clock.fixed(
                        Instant.parse("2026-07-29T22:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        var prediction = client.foldProteinLigand(
                "AC",
                "sah",
                BiohubEsmFold2Config.fast()
        );

        JsonNode request = requestBody.get();
        assertEquals(
                "esmfold2-fast-2026-05",
                request.path("model").asText()
        );
        assertEquals(
                "AC",
                request.path("all_atom_input")
                        .path("sequences").get(0).path("sequence").asText()
        );
        assertEquals(
                "SAH",
                request.path("all_atom_input")
                        .path("sequences").get(1).path("ccd").get(0).asText()
        );
        assertEquals(32, request.path("num_sampling_steps").asInt());
        assertEquals(3, request.path("num_loops").asInt());
        assertEquals("SAH", prediction.ligandCcd());
        assertEquals(0.72, prediction.interfacePtm());
        assertEquals(3, prediction.tokens().size());
        assertEquals("L", prediction.tokens().get(2).chain());
        assertEquals("SAH", prediction.tokens().get(2).residueName());
        assertTrue(prediction.tokens().get(2).atoms().getFirst().hetero());
    }

    private String responseBody() {
        return """
                {
                  "complex": {
                    "id": "complex",
                    "sequence": ["ALA", "CYS", "SAH"],
                    "atom_positions": [
                      [0.0, 0.0, 0.0],
                      [1.0, 0.0, 0.0],
                      [2.0, 0.0, 0.0]
                    ],
                    "atom_elements": ["C", "S", "C"],
                    "atom_names": ["CA", "SG", "C1"],
                    "atom_hetero": [false, false, true],
                    "token_to_atoms": [[0, 1], [1, 2], [2, 3]],
                    "chain_id": [0, 0, 1],
                    "plddt": [0.9, 0.8, 0.7],
                    "metadata": {
                      "entity_lookup": {"0": "1", "1": "2"},
                      "chain_lookup": {"0": "A", "1": "L"}
                    }
                  },
                  "ptm": 0.81,
                  "interface_ptm": 0.72
                }
                """;
    }
}
