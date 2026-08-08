package totah.lab.web.evidence;

import org.junit.jupiter.api.Test;
import totah.lab.athena.pocket.evidence.*;
import totah.lab.gaia.geometry.Point3D;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PocketEvidenceViewMapperTest {
    private static final EvidenceMethod SOURCE =
            new EvidenceMethod("fpocket", "4.0");
    private static final EvidenceMethod DERIVED =
            new EvidenceMethod("centroid", "1", Map.of("weighting", "uniform"));

    @Test
    void preservesValuesProvenanceAndEveryEvaluationState() {
        PocketEvidence evidence = new PocketEvidence(
                new StructureEvidence("1ABC", "RCSB", "A", 1, "1",
                        StructureEvidence.StructureKind.EXPERIMENTAL, null,
                        present("X-RAY DIFFRACTION", EvidenceOrigin.SOURCE_REPORTED,
                                SOURCE),
                        new EvidenceChannel.Empty<>(EvidenceOrigin.SOURCE_REPORTED,
                                SOURCE),
                        new EvidenceChannel.NotApplicable<>(
                                "experimental structure")),
                new PocketGeometryEvidence("pocket1",
                        new EvidenceChannel.NotEvaluated<>("rank unavailable"),
                        new EvidenceChannel.Failed<>("PARSE_ERROR",
                                "score was malformed"),
                        new EvidenceChannel.Empty<>(EvidenceOrigin.SOURCE_REPORTED,
                                SOURCE),
                        present(123.4, EvidenceOrigin.SOURCE_REPORTED, SOURCE),
                        present(List.of(new Point3D(1, 2, 3)),
                                EvidenceOrigin.SOURCE_OBSERVED, SOURCE),
                        present(new Point3D(4, 5, 6), EvidenceOrigin.DERIVED,
                                DERIVED),
                        new EvidenceChannel.NotEvaluated<>(
                                "shape representation not requested")),
                new ResidueContextEvidence(
                        new EvidenceChannel.Empty<>(EvidenceOrigin.SOURCE_OBSERVED,
                                SOURCE),
                        new EvidenceChannel.NotEvaluated<>("not classified"),
                        new EvidenceChannel.NotEvaluated<>("not evaluated"),
                        new EvidenceChannel.NotApplicable<>("no alignment"),
                        new EvidenceChannel.Failed<>("LOOKUP_FAILED",
                                "annotation provider unavailable")),
                new EvidenceChannel.Empty<>(EvidenceOrigin.SOURCE_OBSERVED,
                        SOURCE),
                new EvidenceChannel.Failed<>("CCD_UNAVAILABLE",
                        "component lookup failed"),
                new EvidenceProvenance("RCSB", "1ABC", "2026-08",
                        new EvidenceMethod("extractor", "2.1"),
                        Instant.parse("2026-08-08T12:00:00Z"),
                        Map.of("assembly", "1")));

        PocketEvidenceView view = new PocketEvidenceViewMapper().toView(evidence);

        assertThat(view.structure().experimentalMethod().status())
                .isEqualTo("PRESENT");
        assertThat(view.structure().experimentalMethod().value())
                .isEqualTo("X-RAY DIFFRACTION");
        assertThat(view.structure().experimentalMethod().origin())
                .isEqualTo("SOURCE_REPORTED");
        assertThat(view.structure().resolutionAngstrom().status())
                .isEqualTo("EMPTY");
        assertThat(view.structure().predictedConfidence().status())
                .isEqualTo("NOT_APPLICABLE");
        assertThat(view.pocket().reportedRank().status())
                .isEqualTo("NOT_EVALUATED");
        assertThat(view.pocket().reportedScore().status()).isEqualTo("FAILED");
        assertThat(view.pocket().reportedScore().failureCode())
                .isEqualTo("PARSE_ERROR");
        assertThat(view.pocket().reportedVolume().value()).isEqualTo(123.4);
        assertThat(view.pocket().alphaSpheres().value())
                .containsExactly(new PocketEvidenceView.PointView(1, 2, 3));
        assertThat(view.pocket().centroid().method().parameters())
                .containsEntry("weighting", "uniform");
        assertThat(view.ligandEvidence().status()).isEqualTo("EMPTY");
        assertThat(view.chemistry().status()).isEqualTo("FAILED");
        assertThat(view.provenance().sourceIdentifier()).isEqualTo("1ABC");
        assertThat(view.provenance().extractedAt())
                .isEqualTo(Instant.parse("2026-08-08T12:00:00Z"));
    }

    private static <T> EvidenceChannel<T> present(
            T value, EvidenceOrigin origin, EvidenceMethod method) {
        return new EvidenceChannel.Present<>(value, origin, method);
    }
}
