package totah.lab.prometheus.diagnosis;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

class FunctionalFormDiagnosticTest {

    @Test
    void atLeastOneReasonIsRequired() {
        assertThatThrownBy(() -> new FunctionalFormDiagnostic(
                FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT,
                List.of(),
                List.of("abc123"),
                "prometheus-0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one reason");

        assertThatThrownBy(() -> new FunctionalFormDiagnostic(
                FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT,
                List.of("   "),
                List.of(),
                "prometheus-0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-blank");
    }

    @Test
    void diagnosticVersionMustBeNonBlank() {
        assertThatThrownBy(() -> new FunctionalFormDiagnostic(
                FunctionalFormClassification.MODEL_ACCEPTABLE,
                List.of("holdout RMSE within tolerance"),
                List.of(),
                " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("diagnosticVersion");
    }

    @Test
    void insufficientEvidenceFactoryWorks() {
        FunctionalFormDiagnostic d =
                FunctionalFormDiagnostic.insufficientEvidence("no QM scan covering the C-S-H angle");

        assertThat(d.classification()).isEqualTo(FunctionalFormClassification.INSUFFICIENT_EVIDENCE);
        assertThat(d.reasons()).containsExactly("no QM scan covering the C-S-H angle");
        assertThat(d.supportingEvidenceHashes()).isEmpty();
        assertThat(d.diagnosticVersion()).isNotBlank();
    }

    @Test
    void modelAcceptableFactoryWorks() {
        FunctionalFormDiagnostic d =
                FunctionalFormDiagnostic.modelAcceptable("holdout energies reproduced within 1 kcal/mol");

        assertThat(d.classification()).isEqualTo(FunctionalFormClassification.MODEL_ACCEPTABLE);
        assertThat(d.reasons()).hasSize(1);
    }

    @Test
    void diagnosisReportFiltersByClassification() {
        FunctionalFormDiagnostic insufficient =
                FunctionalFormDiagnostic.insufficientEvidence("missing torsion scan");
        FunctionalFormDiagnostic harmonicFailure = new FunctionalFormDiagnostic(
                FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT,
                List.of("bimodal angle distribution cannot come from one harmonic minimum"),
                List.of("abc123"),
                "prometheus-0.1");

        DiagnosisReport report = new DiagnosisReport(
                TslFixtures.TSL, List.of(insufficient, harmonicFailure), Instant.parse("2026-08-14T00:00:00Z"));

        assertThat(report.byClassification(FunctionalFormClassification.INSUFFICIENT_EVIDENCE))
                .containsExactly(insufficient);
        assertThat(report.byClassification(FunctionalFormClassification.HARMONIC_FORM_INSUFFICIENT))
                .containsExactly(harmonicFailure);
        assertThat(report.byClassification(FunctionalFormClassification.MODEL_ACCEPTABLE)).isEmpty();
    }
}
