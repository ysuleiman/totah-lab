package totah.lab.prometheus.candidate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

/**
 * Jackson round-trip of a fully-provenanced {@link DerivedParameter}: the JSON
 * form is the persistence boundary, so serialize-then-deserialize must be a
 * perfect identity.
 */
class ParameterProvenanceRoundTripTest {

    @Test
    void derivedParameterSurvivesJsonRoundTrip() throws Exception {
        DerivedParameter parameter = new DerivedParameter(
                "tsl-angle-9-10-26",
                TslFixtures.TSL,
                List.of(9, 10, 26),
                ParameterKind.ANGLE_BEND,
                "harmonic",
                91.0,
                "kcal/mol/rad^2",
                new ParameterProvenance(
                        "modified-Seminario",
                        List.of("abc123"),
                        "dev-1",
                        "prometheus-0.1",
                        "none",
                        "line-1",
                        ValidationStatus.UNVALIDATED));

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        String json = mapper.writeValueAsString(parameter);
        DerivedParameter restored = mapper.readValue(json, DerivedParameter.class);

        assertThat(restored).isEqualTo(parameter);
        assertThat(restored.provenance()).isEqualTo(parameter.provenance());
    }
}
