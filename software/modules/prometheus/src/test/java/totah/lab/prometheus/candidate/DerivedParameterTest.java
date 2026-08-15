package totah.lab.prometheus.candidate;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.identity.ForceFieldAtomMap;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

/**
 * The core molecule-specificity case: the two c6-c6-sh angles of TSL share the
 * SAME generic GAFF types yet receive DIFFERENT molecule-specific force
 * constants, distinguished only by their canonical atom index tuples.
 */
class DerivedParameterTest {

    private static ParameterProvenance provenance(String lineageId) {
        return new ParameterProvenance(
                "modified-Seminario",
                List.of("abc123"),
                "dev-1",
                "prometheus-0.1",
                "none",
                lineageId,
                ValidationStatus.UNVALIDATED);
    }

    @Test
    void twoAnglesOfTheSameGenericTypeCoexistWithDistinctValues() {
        ForceFieldAtomMap gaff2 = TslFixtures.forceFieldMapGaff2();

        // C8-C9-S26, canonical serials (9, 10, 26)
        DerivedParameter angleA = new DerivedParameter(
                "tsl-angle-9-10-26",
                TslFixtures.TSL,
                List.of(9, 10, 26),
                ParameterKind.ANGLE_BEND,
                "harmonic",
                91.0,
                "kcal/mol/rad^2",
                provenance("line-1"));

        // C10-C9-S26, canonical serials (11, 10, 26)
        DerivedParameter angleB = new DerivedParameter(
                "tsl-angle-11-10-26",
                TslFixtures.TSL,
                List.of(11, 10, 26),
                ParameterKind.ANGLE_BEND,
                "harmonic",
                62.66,
                "kcal/mol/rad^2",
                provenance("line-2"));

        ParameterCandidate candidate = new ParameterCandidate(
                "cand-1",
                TslFixtures.TSL,
                gaff2,
                List.of(angleA, angleB),
                null,
                0,
                EvidenceClass.EVIDENCE,
                Instant.parse("2026-08-14T00:00:00Z"));

        // the generic typing still groups all three carbons under "c6" ...
        assertThat(gaff2.atomsByType().get("c6")).containsExactly(9, 10, 11);

        // ... yet both molecule-specific angles coexist and stay distinguishable
        // by their canonical index tuples
        List<DerivedParameter> angles = candidate.parametersByKind(ParameterKind.ANGLE_BEND);
        assertThat(angles).containsExactly(angleA, angleB);
        assertThat(angles.stream().map(DerivedParameter::value)).containsExactly(91.0, 62.66);
        assertThat(angles.stream().map(DerivedParameter::canonicalAtomIndices))
                .containsExactly(List.of(9, 10, 26), List.of(11, 10, 26));

        // both touch S26; only the first touches serial 9, only the second serial 11
        assertThat(candidate.parametersTouching(26)).containsExactly(angleA, angleB);
        assertThat(candidate.parametersTouching(9)).containsExactly(angleA);
        assertThat(candidate.parametersTouching(11)).containsExactly(angleB);
        assertThat(candidate.parametersTouching(56)).isEmpty();

        // distinct provenance lineage is preserved
        assertThat(angleA.provenance().candidateLineageId()).isEqualTo("line-1");
        assertThat(angleB.provenance().candidateLineageId()).isEqualTo("line-2");
    }
}
