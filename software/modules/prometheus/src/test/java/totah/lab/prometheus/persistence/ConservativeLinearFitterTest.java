package totah.lab.prometheus.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ConservativeLinearFitterTest {
    @TempDir Path temp;

    @Test
    void fitSuccessIncludesVerifiedReceiptAndReadBackReproducesPredictions() throws Exception {
        var request = request(new double[][] {{1, 0}, {1, 1}, {1, 2}}, new double[] {2, 5, 8});
        var success = new ConservativeLinearFitter().fitAndPersist(temp.resolve("fit"), request);
        assertThat(success.parameters()).containsExactly(new double[] {2.0, 3.0}, offset(1.0e-14));
        FitArtifact reloaded = new FitArtifactWriter().readVerified(temp.resolve("fit")).artifact();
        assertThat(reloaded.predictions()).isEqualTo(success.receipt().artifact().predictions());
        assertThat(reloaded.predictions().stream().mapToDouble(Double::doubleValue).toArray())
                .containsExactly(new double[] {2.0, 5.0, 8.0}, offset(1.0e-14));
        assertThat(success.receipt().artifactSha256()).hasSize(64);
    }

    @Test
    void rankDeficiencyFailsBeforeAnySuccessArtifactExists() {
        var request = request(new double[][] {{1, 1}, {2, 2}, {3, 3}}, new double[] {1, 2, 3});
        Path target = temp.resolve("rank-deficient");
        assertThatThrownBy(() -> new ConservativeLinearFitter().fitAndPersist(target, request))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("rank-deficient");
        assertThat(target).doesNotExist();
    }

    private static ConservativeLinearFitRequest request(double[][] design, double[] target) {
        return new ConservativeLinearFitRequest(
                "TEST", "1", "intercept+x", List.of("intercept", "x"),
                List.of("unit", "unit"), design, target, new double[] {1, 2, 1}, Map.of(),
                "weighted least squares", Map.of("energy", 1.0), List.of("A", "B", "C"),
                List.of("V"), Map.of("target", "none"), Map.of("rank_tolerance", "1e-12"),
                240824L, Map.of("input", "abc"), "bead81c05d7252caf7273ce09c9a1cf1502e7d21");
    }
}
