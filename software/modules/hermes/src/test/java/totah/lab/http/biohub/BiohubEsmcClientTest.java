package totah.lab.hermes.biohub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import totah.lab.hermes.biohub.model.ResidueConstraintAnalysis;

import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BiohubEsmcClientTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final List<RecordedRequest> requests = new ArrayList<>();

    @Test
    void calculatesConstraintEvidenceFromForgeJsonProtocol()
            throws Exception {
        URI baseUri = URI.create("https://biohub.test");
        BiohubClientConfig config = new BiohubClientConfig(
                baseUri,
                "test-token",
                "esmc-test",
                Duration.ofSeconds(5)
        );
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-29T20:00:00Z"),
                ZoneOffset.UTC
        );
        BiohubEsmcClient client = new BiohubEsmcClient(
                config,
                this::respond,
                OBJECT_MAPPER,
                new EsmcResidueConstraintCalculator(),
                clock
        );

        ResidueConstraintAnalysis analysis = client.analyze("a c");

        assertThat(analysis.provider()).isEqualTo("BIOHUB_ESMC");
        assertThat(analysis.model()).isEqualTo("esmc-test");
        assertThat(analysis.sequence()).isEqualTo("AC");
        assertThat(analysis.generatedAt())
                .isEqualTo(Instant.parse("2026-07-29T20:00:00Z"));
        assertThat(analysis.residues()).hasSize(2);
        assertThat(analysis.residues().getFirst().wildType()).isEqualTo('A');
        assertThat(analysis.residues().getFirst().wildTypeRank()).isEqualTo(1);
        assertThat(analysis.residues().getFirst().bestAlternative())
                .isEqualTo('C');
        assertThat(analysis.residues().getFirst()
                .wildTypeMinusBestAlternative()).isEqualTo(2.0);
        assertThat(analysis.residues().get(1).wildType()).isEqualTo('C');
        assertThat(analysis.residues().get(1).wildTypeRank()).isEqualTo(1);

        assertThat(requests).hasSize(2);
        assertThat(requests).allSatisfy(request ->
                assertThat(request.authorization())
                        .isEqualTo("Bearer test-token")
        );
        JsonNode logitsRequest = OBJECT_MAPPER.readTree(
                requests.get(1).body()
        );
        assertThat(logitsRequest.path("model").asText())
                .isEqualTo("esmc-test");
        assertThat(logitsRequest.path("inputs").path("sequence").size())
                .isEqualTo(4);
        assertThat(logitsRequest.path("logits_config").path("sequence")
                .asBoolean()).isTrue();
        assertThat(logitsRequest.path("logits_config")
                .path("ith_hidden_layer").asInt()).isEqualTo(-1);
    }

    private String logitsResponse() throws IOException {
        double[][][] logits = new double[1][4][24];
        logits[0][1][EsmcVocabulary.tokenFor('A')] = 3.0;
        logits[0][1][EsmcVocabulary.tokenFor('C')] = 1.0;
        logits[0][2][EsmcVocabulary.tokenFor('C')] = 4.0;
        logits[0][2][EsmcVocabulary.tokenFor('A')] = 1.0;

        return OBJECT_MAPPER.writeValueAsString(
                java.util.Map.of(
                        "logits",
                        java.util.Map.of("sequence", logits),
                        "embeddings",
                        java.util.List.of(),
                        "hidden_states",
                        java.util.List.of(),
                        "mean_embedding",
                        java.util.List.of(),
                        "mean_hidden_state",
                        java.util.List.of(),
                        "sae_outputs",
                        java.util.Map.of()
                )
        );
    }

    private BiohubHttpTransport.Response respond(
            URI uri,
            String token,
            Duration timeout,
            String body
    ) throws IOException {
        requests.add(new RecordedRequest(
                uri.getPath(),
                "Bearer " + token,
                body
        ));
        String responseBody = uri.getPath().endsWith("/encode")
                ? """
                  {
                    "outputs": {"sequence": [0, 5, 23, 2]},
                    "potential_sequence_of_concern": false
                  }
                  """
                : logitsResponse();
        return new BiohubHttpTransport.Response(200, responseBody);
    }

    private record RecordedRequest(
            String path,
            String authorization,
            String body
    ) {
    }
}
