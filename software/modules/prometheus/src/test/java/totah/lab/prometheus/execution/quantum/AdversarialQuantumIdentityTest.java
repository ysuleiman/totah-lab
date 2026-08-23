package totah.lab.prometheus.execution.quantum;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;
import totah.lab.prometheus.planning.CalculationSpecification;
import totah.lab.prometheus.planning.CostEstimate;
import totah.lab.prometheus.planning.DatasetRole;

/**
 * TEST_ID: B3 (execution-identity level) — same invariant as
 * {@code AdversarialIdentityCanonicalizationTest}, exercised through
 * {@link QuantumScientificIdentity#calculate} (package-private seam, same
 * package).
 *
 * <p>Reordered set-like inputs (constraints, requiredOutputs, acceptanceGates,
 * observables) must yield identical identity; removing or changing an element
 * must change it. Both directions asserted against the specification
 * (docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md, B3); the test records actual
 * behavior rather than adapting the invariant.
 */
class AdversarialQuantumIdentityTest {

    private static final String ATOM_MAP_HASH = TslFixtures.canonicalMap().canonicalHash();

    /**
     * TEST_ID: B3 (equality direction) — identical scientific content with
     * constraints, required outputs and gates listed in a different order is
     * the same request and MUST hash identically.
     */
    @Test
    void reorderedListFieldsProduceIdenticalIdentity() {
        CalculationSpecification x = specification(
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));
        CalculationSpecification y = specification(
                List.of("freeze_bond=1-2", "freeze_dihedral=60"),
                List.of("gradient", "energy"),
                List.of("gradient_norm<=1e-5", "convergence=CONVERGED"));

        String idX = QuantumScientificIdentity.calculate(
                x, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY, QuantumObservable.CARTESIAN_GRADIENT));
        String idY = QuantumScientificIdentity.calculate(
                y, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY, QuantumObservable.CARTESIAN_GRADIENT));

        assertThat(idX)
                .as("identical science in different list order is the same quantum request")
                .isEqualTo(idY);
    }

    /**
     * TEST_ID: B3 (observables, equality direction) — the observable set
     * presented in a different iteration order is the same request.
     */
    @Test
    void reorderedObservablesProduceIdenticalIdentity() {
        CalculationSpecification spec = specification(
                List.of("freeze_dihedral=60"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED"));

        String first = QuantumScientificIdentity.calculate(
                spec, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                new LinkedHashSet<>(List.of(
                        QuantumObservable.ABSOLUTE_ENERGY, QuantumObservable.CARTESIAN_GRADIENT)));
        String second = QuantumScientificIdentity.calculate(
                spec, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                new LinkedHashSet<>(List.of(
                        QuantumObservable.CARTESIAN_GRADIENT, QuantumObservable.ABSOLUTE_ENERGY)));

        assertThat(first).isEqualTo(second);
    }

    /**
     * TEST_ID: B3 (difference direction) — dropping a gate, changing a
     * required output, or changing the observable set MUST change identity.
     */
    @Test
    void removingAGateOrChangingContentChangesIdentity() {
        CalculationSpecification x = specification(
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));
        CalculationSpecification gateRemoved = specification(
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "gradient"),
                List.of("convergence=CONVERGED"));
        CalculationSpecification outputChanged = specification(
                List.of("freeze_dihedral=60", "freeze_bond=1-2"),
                List.of("energy", "hessian"),
                List.of("convergence=CONVERGED", "gradient_norm<=1e-5"));

        String idX = QuantumScientificIdentity.calculate(
                x, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY));
        String idGateRemoved = QuantumScientificIdentity.calculate(
                gateRemoved, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY));
        String idOutputChanged = QuantumScientificIdentity.calculate(
                outputChanged, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY));
        String idObservableChanged = QuantumScientificIdentity.calculate(
                x, ATOM_MAP_HASH, QuantumSolverMode.CONVENTIONAL_ELECTRONIC_STRUCTURE,
                Set.of(QuantumObservable.ABSOLUTE_ENERGY, QuantumObservable.HESSIAN));

        assertThat(idX).isNotEqualTo(idGateRemoved);
        assertThat(idX).isNotEqualTo(idOutputChanged);
        assertThat(idX).isNotEqualTo(idObservableChanged);
    }

    private static CalculationSpecification specification(
            List<String> constraints,
            List<String> requiredOutputs,
            List<String> acceptanceGates) {
        return new CalculationSpecification(
                "quantum-identity-probe",
                "adversarial identity probe",
                TslFixtures.TSL,
                TslFixtures.geometryIdentityA(),
                0,
                1,
                EvidenceFixtures.PBE_DEF2_SVP,
                constraints,
                CalculationType.SINGLE_POINT,
                requiredOutputs,
                acceptanceGates,
                DatasetRole.DEVELOPMENT,
                CostEstimate.zero());
    }
}
