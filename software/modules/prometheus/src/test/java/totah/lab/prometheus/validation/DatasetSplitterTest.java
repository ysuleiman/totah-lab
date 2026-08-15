package totah.lab.prometheus.validation;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DatasetSplitterTest {

    private static QuantumEvidence acceptedOptimization() {
        return ValidationTestData.accepted(
                CalculationType.OPTIMIZATION, TslFixtures.geometryIdentityA());
    }

    private static QuantumEvidence acceptedSinglePoint() {
        return ValidationTestData.accepted(
                CalculationType.SINGLE_POINT, TslFixtures.geometryIdentityA());
    }

    private static QuantumEvidence acceptedHessian() {
        return ValidationTestData.accepted(
                CalculationType.HESSIAN, TslFixtures.geometryIdentityA());
    }

    private static String hash(QuantumEvidence evidence) {
        return evidence.identity().evidenceHash();
    }

    @Test
    void validSplitKeepsDevelopmentAndHoldoutSeparate() {
        QuantumEvidence dev1 = acceptedOptimization();
        QuantumEvidence dev2 = acceptedSinglePoint();
        QuantumEvidence hold = acceptedHessian();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(dev1);
        bundle.add(dev2);
        bundle.add(hold);

        DatasetSplitter.DatasetSplit split = DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(dev1), hash(dev2)),
                "holdout-1", List.of(hash(hold)));

        assertThat(split.development().datasetId()).isEqualTo("dev-1");
        assertThat(split.development().size()).isEqualTo(2);
        assertThat(split.development().contains(hash(dev1))).isTrue();
        assertThat(split.development().hashes())
                .containsExactly(hash(dev1), hash(dev2));
        assertThatThrownBy(() -> split.development().hashes().add("x"))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(split.holdout().datasetId()).isEqualTo("holdout-1");
        assertThat(split.holdout().size()).isEqualTo(1);
        assertThat(split.holdout().containsHash(hash(hold))).isTrue();
        assertThat(split.holdout().containsHash(hash(dev1))).isFalse();
    }

    @Test
    void overlappingHashThrows_developmentCannotMasqueradeAsHoldout() {
        QuantumEvidence dev = acceptedOptimization();
        QuantumEvidence hold = acceptedHessian();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(dev);
        bundle.add(hold);

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(dev)),
                "holdout-1", List.of(hash(dev), hash(hold))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("development data cannot masquerade as holdout");
    }

    @Test
    void unknownHashThrows() {
        QuantumEvidence dev = acceptedOptimization();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(dev);

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(dev)),
                "holdout-1", List.of("0".repeat(64))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown evidence hash");
    }

    @Test
    void emptyHoldoutThrows() {
        QuantumEvidence dev = acceptedOptimization();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(dev);

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(dev)),
                "holdout-1", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("holdout dataset must be non-empty");
    }

    @Test
    void failedEvidenceCannotEnterHoldout() {
        QuantumEvidence dev = acceptedOptimization();
        QuantumEvidence failed = ValidationTestData.withStates(
                ConvergenceStatus.FAILED,
                EvidenceAcceptanceState.FAILED_NUMERICALLY,
                TslFixtures.geometryIdentityB());
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(dev);
        bundle.add(failed);

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(dev)),
                "holdout-1", List.of(hash(failed))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(hash(failed))
                .hasMessageContaining("FAILED_NUMERICALLY");
    }

    @Test
    void geometryInvalidEvidenceCannotEnterDevelopment() {
        QuantumEvidence geometryInvalid = ValidationTestData.withStates(
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.GEOMETRY_INVALID,
                TslFixtures.geometryIdentityA());
        QuantumEvidence hold = acceptedHessian();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(geometryInvalid);
        bundle.add(hold);

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(geometryInvalid)),
                "holdout-1", List.of(hash(hold))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(hash(geometryInvalid))
                .hasMessageContaining("GEOMETRY_INVALID");
    }

    @Test
    void notConvergedEvidenceCannotEnterEitherDataset() {
        QuantumEvidence notConverged = ValidationTestData.withStates(
                ConvergenceStatus.NOT_CONVERGED,
                EvidenceAcceptanceState.PENDING,
                TslFixtures.geometryIdentityB());
        QuantumEvidence hold = acceptedHessian();
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(notConverged);
        bundle.add(hold);

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle,
                "dev-1", List.of(hash(notConverged)),
                "holdout-1", Set.of(hash(hold))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(hash(notConverged))
                .hasMessageContaining("NOT_CONVERGED");
    }
}
