package totah.lab.prometheus.validation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.identity.CanonicalHashing;
import totah.lab.prometheus.identity.EvidenceAtomMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvidenceValidatorTest {

    private final EvidenceValidator validator = new EvidenceValidator();

    private static QuantumEvidence evidenceWith(ConvergenceStatus convergence) {
        EvidenceAcceptanceState acceptance = convergence == ConvergenceStatus.CONVERGED
                ? EvidenceAcceptanceState.ACCEPTED
                : EvidenceAcceptanceState.PENDING;
        return ValidationTestData.withStates(
                convergence, acceptance, TslFixtures.geometryIdentityA());
    }

    @Test
    void checksumMatchIsAccepted(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("evidence.log");
        String content = "scf energy -100.5\nconverged\n";
        Files.writeString(file, content);

        EvidenceValidator.ChecksumOutcome outcome =
                validator.verifyChecksum(file, CanonicalHashing.sha256Hex(content));

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
    }

    @Test
    void checksumMismatchInvalidatesReuse(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("evidence.log");
        Files.writeString(file, "actual content");

        EvidenceValidator.ChecksumOutcome outcome = validator.verifyChecksum(
                file, CanonicalHashing.sha256Hex("expected content"));

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.CHECKSUM_INVALID);
        assertThat(outcome.reason()).contains("checksum mismatch invalidates reuse");
    }

    @Test
    void missingFileIsChecksumInvalid(@TempDir Path dir) {
        EvidenceValidator.ChecksumOutcome outcome = validator.verifyChecksum(
                dir.resolve("missing.log"), CanonicalHashing.sha256Hex("anything"));

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.CHECKSUM_INVALID);
        assertThat(outcome.reason()).contains("cannot read evidence artifact");
    }

    @Test
    void convergenceMapsToAcceptanceStates() {
        assertThat(validator.verifyConvergence(evidenceWith(ConvergenceStatus.CONVERGED)))
                .isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(validator.verifyConvergence(evidenceWith(ConvergenceStatus.FAILED)))
                .isEqualTo(EvidenceAcceptanceState.FAILED_NUMERICALLY);
        assertThat(validator.verifyConvergence(evidenceWith(ConvergenceStatus.NOT_CONVERGED)))
                .isEqualTo(EvidenceAcceptanceState.FAILED_NUMERICALLY);
        assertThat(validator.verifyConvergence(evidenceWith(ConvergenceStatus.EMPTY_OUTPUT)))
                .isEqualTo(EvidenceAcceptanceState.PROTOCOL_INCOMPLETE);
        assertThat(validator.verifyConvergence(evidenceWith(ConvergenceStatus.UNKNOWN)))
                .isEqualTo(EvidenceAcceptanceState.PENDING);
    }

    @Test
    void atomMapMismatchIsAtomMapInvalid() {
        EvidenceAtomMap expected = TslFixtures.evidenceMapReordered();

        assertThat(validator.verifyAtomMap(expected, TslFixtures.evidenceMapReordered()))
                .isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(validator.verifyAtomMap(
                expected,
                new EvidenceAtomMap(TslFixtures.canonicalMap(), List.of(9, 10, 11, 26, 56))))
                .isEqualTo(EvidenceAcceptanceState.ATOM_MAP_INVALID);
    }

    @Test
    void symmetricHessianIsAcceptedAndMassWeightingNotGuessed() {
        // One atom: 3x3, symmetric.
        List<Double> hessian = List.of(
                1.0, 2.0, 3.0,
                2.0, 4.0, 5.0,
                3.0, 5.0, 6.0);

        EvidenceValidator.HessianOutcome outcome = validator.verifyHessian(hessian, 1);

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(outcome.massWeighted()).isFalse();
    }

    @Test
    void asymmetricHessianFailsNumerically() {
        List<Double> hessian = List.of(
                1.0, 2.0, 3.0,
                2.5, 4.0, 5.0,
                3.0, 5.0, 6.0);

        EvidenceValidator.HessianOutcome outcome = validator.verifyHessian(hessian, 1);

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.FAILED_NUMERICALLY);
        assertThat(outcome.reason()).contains("not symmetric");
    }

    @Test
    void wrongSizeHessianIsProtocolIncomplete() {
        List<Double> hessian = List.of(1.0, 2.0, 3.0, 4.0);

        EvidenceValidator.HessianOutcome outcome = validator.verifyHessian(hessian, 1);

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.PROTOCOL_INCOMPLETE);
        assertThat(outcome.reason()).contains("expected 3N×3N");
    }

    @Test
    void nonFiniteSymmetricHessianFailsNumerically() {
        List<Double> hessian = List.of(
                Double.NaN, 0.0, 0.0,
                0.0, 1.0, 0.0,
                0.0, 0.0, 1.0);

        EvidenceValidator.HessianOutcome outcome = validator.verifyHessian(hessian, 1);

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.FAILED_NUMERICALLY);
        assertThat(outcome.reason()).contains("non-finite");
    }

    @Test
    void finalAcceptanceFollowsPrecedenceOrder() {
        assertThat(validator.finalAcceptance(List.of(
                EvidenceAcceptanceState.ACCEPTED, EvidenceAcceptanceState.ACCEPTED)))
                .isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(validator.finalAcceptance(List.of(
                EvidenceAcceptanceState.ACCEPTED, EvidenceAcceptanceState.PENDING)))
                .isEqualTo(EvidenceAcceptanceState.PENDING);
        assertThat(validator.finalAcceptance(List.of(
                EvidenceAcceptanceState.PROTOCOL_INCOMPLETE,
                EvidenceAcceptanceState.GEOMETRY_INVALID,
                EvidenceAcceptanceState.FAILED_NUMERICALLY)))
                .isEqualTo(EvidenceAcceptanceState.GEOMETRY_INVALID);
        assertThat(validator.finalAcceptance(List.of(
                EvidenceAcceptanceState.ATOM_MAP_INVALID,
                EvidenceAcceptanceState.CHECKSUM_INVALID)))
                .isEqualTo(EvidenceAcceptanceState.CHECKSUM_INVALID);
        assertThat(validator.finalAcceptance(List.of(
                EvidenceAcceptanceState.PENDING,
                EvidenceAcceptanceState.EXCLUDED_BY_PROTOCOL)))
                .isEqualTo(EvidenceAcceptanceState.EXCLUDED_BY_PROTOCOL);
        assertThatThrownBy(() -> validator.finalAcceptance(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
