package totah.lab.web.evidence;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PocketEvidenceControllerTest {
    @Test
    void delegatesPocketIdentityToApplicationQuery() throws Exception {
        PocketEvidenceView expected = new PocketEvidenceView(
                null, null, null, null, null, null);
        long[] requested = {-1};
        PocketEvidenceController controller = new PocketEvidenceController(id -> {
            requested[0] = id;
            return expected;
        });

        assertThat(controller.evidence(42)).isSameAs(expected);
        assertThat(requested[0]).isEqualTo(42);
    }
}
