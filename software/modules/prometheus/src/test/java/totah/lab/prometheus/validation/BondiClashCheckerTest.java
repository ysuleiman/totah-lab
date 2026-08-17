package totah.lab.prometheus.validation;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.validation.GeometryClashChecker.ClashAtom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * MIN01 probe-collision regression: a water probe placed on top of the TSL
 * scaffold sulfur must be flagged as a clash and must poison the evidence so it
 * can never enter a validation dataset.
 */
class BondiClashCheckerTest {

    private static final ClashAtom S26 = new ClashAtom("S26", "S", 0.0, 0.0, 0.0);

    private final EvidenceValidator validator = new EvidenceValidator();

    @Test
    void waterOxygenAtOneAngstromFromS26Clashes() {
        // Bondi sum S+O = 3.32 Å; at scale 0.75 the clash limit is 2.49 Å.
        ClashAtom ow = new ClashAtom("OW", "O", 1.0, 0.0, 0.0);
        BondiClashChecker checker = new BondiClashChecker(0.75, Set.of());

        List<String> clashes = checker.clashes(List.of(S26, ow));

        assertThat(clashes).hasSize(1);
        assertThat(clashes.getFirst()).contains("S26").contains("OW");
    }

    @Test
    void collidingProbeMakesEvidenceGeometryInvalidAndUnsplittable() {
        ClashAtom ow = new ClashAtom("OW", "O", 1.0, 0.0, 0.0);
        BondiClashChecker checker = new BondiClashChecker(0.75, Set.of());

        EvidenceValidator.GeometryOutcome outcome =
                validator.verifyProbeGeometry(List.of(S26, ow), Set.of(), checker);

        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.GEOMETRY_INVALID);
        assertThat(outcome.clashes()).isNotEmpty();

        // Evidence poisoned by the audit cannot enter a holdout dataset.
        QuantumEvidence poisoned = ValidationTestData.withStates(
                ConvergenceStatus.CONVERGED,
                EvidenceAcceptanceState.GEOMETRY_INVALID,
                TslFixtures.geometryIdentityA());
        EvidenceBundle bundle = new EvidenceBundle();
        bundle.add(poisoned);
        String hash = poisoned.identity().evidenceHash();

        assertThatThrownBy(() -> DatasetSplitter.split(
                bundle, "dev-1", List.of(), "holdout-1", List.of(hash)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(hash)
                .hasMessageContaining("GEOMETRY_INVALID");
    }

    @Test
    void waterOxygenAtTwoPointEightAngstromIsClean() {
        ClashAtom ow = new ClashAtom("OW", "O", 2.8, 0.0, 0.0);
        BondiClashChecker checker = new BondiClashChecker(0.75, Set.of());

        assertThat(checker.clashes(List.of(S26, ow))).isEmpty();

        EvidenceValidator.GeometryOutcome outcome =
                validator.verifyProbeGeometry(List.of(S26, ow), Set.of(), checker);
        assertThat(outcome.state()).isEqualTo(EvidenceAcceptanceState.ACCEPTED);
        assertThat(outcome.clashes()).isEmpty();
    }

    @Test
    void bondedSulfurHydrogenPairAtBondLengthIsExcluded() {
        // S-H bond at 1.34 Å is far below the scaled vdW limit
        // (0.75 * (1.80 + 1.20) = 2.25 Å) but is covalently bound, not a clash.
        ClashAtom h56 = new ClashAtom("H56", "H", 1.34, 0.0, 0.0);
        Set<Set<String>> bonded = Set.of(Set.of("S26", "H56"));
        BondiClashChecker checker = new BondiClashChecker(0.75, bonded);

        assertThat(checker.clashes(List.of(S26, h56))).isEmpty();
    }

    @Test
    void unknownElementFallsBackToCarbonRadiusAndIsStillEvaluated() {
        ClashAtom xe = new ClashAtom("XE1", "Xe", 1.0, 0.0, 0.0);
        BondiClashChecker checker = new BondiClashChecker(0.75, Set.of());

        assertThat(checker.clashes(List.of(S26, xe))).hasSize(1);
    }

    @Test
    void nonFiniteConfigurationCannotSilentlyDisableClashDetection() {
        assertThatThrownBy(() -> new BondiClashChecker(Double.NaN, Set.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClashAtom("OW", "O", Double.NaN, 0.0, 0.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
