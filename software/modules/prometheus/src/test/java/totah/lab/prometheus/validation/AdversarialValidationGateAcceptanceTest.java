package totah.lab.prometheus.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.candidate.DecisionState;
import totah.lab.prometheus.candidate.ModelDecision;
import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.ferminet.force.FermiNetNuclearForceValidation;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceEstimatorType;
import totah.lab.prometheus.neural.ferminet.force.NuclearForceResult;

/**
 * Adversarial acceptance tests for the independent-validation gate layer.
 *
 * <p>Seams under test (both public, both with the contract "a gate that can
 * fail"): the preregistered holdout gate chain
 * {@link ValidationGate} → {@link GateOutcome} → {@link ValidationResult}
 * (whose constructor invariant is "never promote while preregistered gates
 * fail"), and the validation-metric assembly in
 * {@link FermiNetNuclearForceValidation}. The protocol-qualification report
 * generator was considered and rejected as the D1 seam: it is a file-driven
 * reporter for one historical pilot, not the candidate-vs-holdout gate.
 */
final class AdversarialValidationGateAcceptanceTest {

    private static final double GATE_THRESHOLD = 1.0e-3;

    /**
     * TEST_ID: D1 — absurd held-out values must fail validation.
     *
     * <p>Fixture: one held-out configuration of a 2-atom molecule. Reference
     * energy 1e6 hartree, reference gradient −1e4 hartree/bohr in every
     * component (so the reference force is +1e4 in every component); shapes
     * are valid (6 force components). The model under test predicts energy
     * 0.0 and zero forces. Hand-computed observed metrics: energy
     * max-abs-error = |0.0 − 1e6| = 1e6; force RMS =
     * sqrt(mean((0 − 1e4)^2)) = 1e4. Both are ~9 orders of magnitude outside
     * the preregistered 1e-3 gates.
     *
     * <p>Oracle: both gates fail; each failure outcome names its metric; a
     * result carrying these outcomes cannot be constructed with an accepting
     * decision (nothing is promoted), and is constructible with
     * FAILED_HOLDOUT. A control with model == reference passes both gates,
     * proving the gate discriminates rather than being hardwired either way.
     */
    @Test
    void d1AbsurdHoldoutFailsAndNamesTheFailedMetric() {
        ValidationGate energyGate = new ValidationGate(
                "gate-holdout-energy",
                "holdout energy max abs error within 1e-3 hartree",
                "holdout_energy_max_abs_error_hartree", GATE_THRESHOLD,
                Comparison.AT_MOST);
        ValidationGate forceGate = new ValidationGate(
                "gate-holdout-force",
                "holdout force RMS within 1e-3 hartree/bohr",
                "holdout_force_rms_hartree_per_bohr", GATE_THRESHOLD,
                Comparison.AT_MOST);

        double observedEnergyError = Math.abs(0.0 - 1.0e6);
        double observedForceRms = 1.0e4;
        assertEquals(1.0e6, observedEnergyError, 0.0);
        assertFalse(energyGate.passes(observedEnergyError));
        assertFalse(forceGate.passes(observedForceRms));

        GateOutcome energyOutcome = new GateOutcome(energyGate, observedEnergyError,
                false, "observed holdout_energy_max_abs_error_hartree 1.0E6 > 1.0E-3");
        GateOutcome forceOutcome = new GateOutcome(forceGate, observedForceRms,
                false, "observed holdout_force_rms_hartree_per_bohr 1.0E4 > 1.0E-3");
        assertEquals("holdout_energy_max_abs_error_hartree",
                energyOutcome.gate().metric());
        assertEquals("holdout_force_rms_hartree_per_bohr",
                forceOutcome.gate().metric());

        FrozenCandidate frozen = FrozenCandidate.freeze(
                ValidationTestData.candidate("cand-d1", 91.0),
                ValidationPlan.preregister(
                        "plan-d1", List.of(energyGate, forceGate), "holdout-d1"));

        assertThrows(IllegalArgumentException.class, () -> ValidationResult.of(
                frozen, List.of(energyOutcome, forceOutcome),
                new ModelDecision(DecisionState.VALIDATED_FOR_PRODUCTION,
                        List.of("attempting promotion over absurd holdout"),
                        ValidationTestData.T0)),
                "never promote while preregistered gates fail");

        ValidationResult failed = ValidationResult.of(
                frozen, List.of(energyOutcome, forceOutcome),
                new ModelDecision(DecisionState.FAILED_HOLDOUT,
                        List.of("holdout energy error 1e6 hartree and force RMS 1e4"
                                + " hartree/bohr are absurd"), ValidationTestData.T0));
        assertFalse(failed.allPassed());
        assertEquals(DecisionState.FAILED_HOLDOUT, failed.decision().state());

        GateOutcome energyPass = new GateOutcome(energyGate, 0.0, true,
                "model reproduces the reference energy exactly");
        GateOutcome forcePass = new GateOutcome(forceGate, 0.0, true,
                "model reproduces the reference forces exactly");
        ValidationResult passed = ValidationResult.of(
                frozen, List.of(energyPass, forcePass),
                new ModelDecision(DecisionState.VALIDATED_WITH_LIMITATIONS,
                        List.of("exact reproduction on the holdout"),
                        ValidationTestData.T0));
        assertTrue(passed.allPassed());
    }

    /**
     * TEST_ID: D7 — NaN force component in the reference.
     *
     * <p>Fixture: an N=2 force result with exactly one NaN component
     * (nucleus 1, axis z), all other components finite. Oracle at the
     * metric-assembly seam: the comparison is reported as incomplete —
     * {@code finiteComponents == 3N-1 == 5}, {@code nonfiniteComponents == 1},
     * {@code completeFiniteVector == false}; the physical diagnostics mark the
     * vector non-finite and report NaN net force/torque rather than silently
     * dropping the poisoned component. Oracle at the gate seam: a NaN observed
     * value fails both AT_MOST and AT_LEAST gates (Java NaN comparisons are
     * false), and {@link GateOutcome} refuses a caller claiming a NaN pass.
     */
    @Test
    void d7SingleNaNReferenceComponentIsCountedAndFailsTheGate() {
        int atomCount = 2;
        NuclearForceResult result = forceResultWithOneNaN(atomCount);

        FermiNetNuclearForceValidation.Result metrics =
                FermiNetNuclearForceValidation.validate(hydrogenMolecule(), result);
        assertEquals(3 * atomCount - 1, metrics.finiteComponents(),
                "3N-1 compared components is not 3N");
        assertEquals(1, metrics.nonfiniteComponents());
        assertFalse(metrics.completeFiniteVector(),
                "a NaN reference component is a failed comparison, not a skipped one");

        FermiNetNuclearForceValidation.PhysicalDiagnostics diagnostics =
                FermiNetNuclearForceValidation.physicalDiagnostics(
                        hydrogenMolecule(), result);
        assertFalse(diagnostics.finiteVector());
        assertTrue(Double.isNaN(diagnostics.netForceHartreePerBohr().z()),
                "non-finite input propagates as NaN, never as a dropped component");
        assertTrue(Double.isNaN(diagnostics.torqueHartree().z()));

        ValidationGate atMost = new ValidationGate("gate-nan-at-most",
                "NaN observed value must fail", "holdout_force_max_abs_error",
                GATE_THRESHOLD, Comparison.AT_MOST);
        ValidationGate atLeast = new ValidationGate("gate-nan-at-least",
                "NaN observed value must fail", "holdout_energy_reproduction",
                GATE_THRESHOLD, Comparison.AT_LEAST);
        assertFalse(atMost.passes(Double.NaN));
        assertFalse(atLeast.passes(Double.NaN));
        GateOutcome nanOutcome = new GateOutcome(atMost, Double.NaN, false,
                "observed value is NaN: the reference component is not finite");
        assertFalse(nanOutcome.passed());
        assertThrows(IllegalArgumentException.class, () -> new GateOutcome(
                atMost, Double.NaN, true, "caller claims NaN passes the gate"),
                "GateOutcome enforces passed == gate.passes(observedValue)");
    }

    private static Molecule hydrogenMolecule() {
        return new Molecule(
                "adversarial-gate-h2",
                List.of(
                        new NuclearCenter(0, "H", new NuclearCharge(1),
                                new CartesianPosition(-0.7, 0.0, 0.0, LengthUnit.BOHR)),
                        new NuclearCenter(1, "H", new NuclearCharge(1),
                                new CartesianPosition(0.7, 0.0, 0.0, LengthUnit.BOHR))),
                new MolecularCharge(0), new ElectronCount(2), new SpinSector(1, 1, 1));
    }

    private static NuclearForceResult forceResultWithOneNaN(int atomCount) {
        double[] means = {0.031, -0.017, 0.044, -0.029, 0.021, Double.NaN};
        String[] axisNames = {"x", "y", "z"};
        List<NuclearForceResult.Component> components = new ArrayList<>();
        for (int nucleus = 0; nucleus < atomCount; nucleus++) {
            for (int axis = 0; axis < 3; axis++) {
                double mean = means[nucleus * 3 + axis];
                components.add(new NuclearForceResult.Component(
                        nucleus, axis, axisNames[axis],
                        mean, 0.001, 1.0e-6, 100, 0,
                        new NuclearForceResult.TailDiagnostics(
                                mean - 0.01, mean - 0.005, mean - 0.002, mean,
                                mean + 0.002, mean + 0.005, mean + 0.01, 0, 0),
                        "d7-component-" + nucleus + "-" + axis,
                        new double[]{mean}));
            }
        }
        return new NuclearForceResult(
                NuclearForceEstimatorType.CORRELATED_FD, "adversarial-d7",
                "parameter-checksum", "geometry-identity", "dataset-checksum",
                "checkpoint-checksum", "estimator-configuration",
                100, 4, 25, List.copyOf(components),
                new NuclearForceResult.CorrelatedFdDiagnostics(1.0e-3, List.of()));
    }
}
