package totah.lab.prometheus.ingest;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class QmProtocolParserTest {

    @Test
    void unavailableMethodDoesNotBecomeMethodNAndBasisA() {
        var protocol = QmProtocolParser.fromMethodString("N/A", "unknown", "unknown");

        assertThat(protocol.method()).isEqualTo("unknown");
        assertThat(protocol.basis()).isEqualTo("none");
    }
}
