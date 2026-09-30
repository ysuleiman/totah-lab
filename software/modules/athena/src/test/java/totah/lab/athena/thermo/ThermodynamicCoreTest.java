package totah.lab.athena.thermo;

import org.junit.jupiter.api.Test;
import totah.lab.athena.energy.ScientificMethod;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ThermodynamicCoreTest {
    private static final Temperature T = new Temperature(298.15);

    @Test
    void kdRoundTripRetainsExplicitStandardState() {
        var kd = new DissociationConstant(40.0e-6);
        var freeEnergy = ThermodynamicConversions.fromKd(
                kd, StandardState.oneMolar(), T);
        assertThat(ThermodynamicConversions.toKd(freeEnergy, T).molesPerLitre())
                .isCloseTo(kd.molesPerLitre(), within(1.0e-16));
        assertThat(kd.associationConstant().dissociationConstant()).isEqualTo(kd);
    }

    @Test
    void freezesBMinusASignForTenHundredAndThousandFoldRatios() {
        for (double ratio : List.of(0.1, 0.01, 0.001, 10.0, 100.0, 1000.0)) {
            var freeEnergy = ThermodynamicConversions.selectivity(
                    new SelectivityRatio(ratio), T);
            assertThat(ThermodynamicConversions.selectivityRatio(freeEnergy, T)
                    .kdBOverKdA()).isCloseTo(ratio, within(1.0e-12));
            assertThat(Math.signum(freeEnergy.bMinusA().value()))
                    .isEqualTo(Math.signum(Math.log(ratio)));
        }
    }

    @Test
    void partitionFunctionRequiresPositiveMeasuresAndProvenance() {
        var states = List.of(new WeightedEnergyState("a",
                new MolarEnergy(0.0, EnergyUnit.KILOJOULES_PER_MOLE), 1.0));
        var result = DiscretePartitionFunction.evaluate(states, T, "uniform grid cell");
        assertThat(result.logPartition()).isZero();
        assertThat(result.freeEnergy().value()).isZero();
        assertThat(result.method()).isEqualTo(
                ScientificMethod.DISCRETE_STATISTICAL_ESTIMATE);
        assertThatThrownBy(() -> DiscretePartitionFunction.evaluate(states, T, " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WeightedEnergyState("bad",
                new MolarEnergy(0, EnergyUnit.JOULES_PER_MOLE), 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zwanzigZeroPerturbationIsZeroAndRejectsUnnormalizedWeights() {
        var zero = new MolarEnergy(0.0, EnergyUnit.KILOCALORIES_PER_MOLE);
        var states = List.of(
                new ZwanzigFreeEnergyEstimator.MatchedEnergy("a", zero, 0.5),
                new ZwanzigFreeEnergyEstimator.MatchedEnergy("b", zero, 0.5));
        var result = ZwanzigFreeEnergyEstimator.estimate(states, T,
                "equilibrium A fixture");
        assertThat(result.deltaFreeEnergy().value()).isCloseTo(0.0, within(1e-12));
        assertThat(result.effectiveSampleSize()).isEqualTo(2.0);
        assertThatThrownBy(() -> ZwanzigFreeEnergyEstimator.estimate(
                List.of(new ZwanzigFreeEnergyEstimator.MatchedEnergy("a", zero, 0.8)),
                T, "invalid fixture")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void zwanzigEffectiveSampleSizeUsesExponentialImportanceWeights() {
        double rtLnNine = ThermodynamicConversions.GAS_CONSTANT_J_PER_MOL_K
                * T.kelvin() * Math.log(9.0);
        var result = ZwanzigFreeEnergyEstimator.estimate(List.of(
                new ZwanzigFreeEnergyEstimator.MatchedEnergy("a",
                        new MolarEnergy(0, EnergyUnit.JOULES_PER_MOLE), .5),
                new ZwanzigFreeEnergyEstimator.MatchedEnergy("b",
                        new MolarEnergy(rtLnNine, EnergyUnit.JOULES_PER_MOLE), .5)),
                T, "hand-verifiable 9:1 importance ratio");
        assertThat(result.effectiveSampleSize())
                .isCloseTo(1.0 / (.9 * .9 + .1 * .1), within(1e-12));
    }

    @Test
    void tiRejectsDuplicateLambdaAndEnergyTaxonomyContainsSeparatedTerms() {
        var zero = new MolarEnergy(0, EnergyUnit.JOULES_PER_MOLE);
        assertThatThrownBy(() -> ThermodynamicIntegrationEstimator.integrate(List.of(
                new ThermodynamicIntegrationEstimator.LambdaObservation(0, zero),
                new ThermodynamicIntegrationEstimator.LambdaObservation(.5, zero),
                new ThermodynamicIntegrationEstimator.LambdaObservation(.5, zero),
                new ThermodynamicIntegrationEstimator.LambdaObservation(1, zero)), "fixture"))
                .hasMessageContaining("unique");
        assertThat(java.util.EnumSet.allOf(totah.lab.athena.energy.EnergyComponent.class))
                .contains(totah.lab.athena.energy.EnergyComponent.BOND,
                        totah.lab.athena.energy.EnergyComponent.ANGLE,
                        totah.lab.athena.energy.EnergyComponent.TORSIONAL,
                        totah.lab.athena.energy.EnergyComponent.VDW,
                        totah.lab.athena.energy.EnergyComponent.ELECTROSTATIC,
                        totah.lab.athena.energy.EnergyComponent.SAM_INTERACTION);
    }

    @Test
    void tiAndLieKeepMethodIdentityExplicit() {
        var ti = ThermodynamicIntegrationEstimator.integrate(List.of(
                new ThermodynamicIntegrationEstimator.LambdaObservation(0,
                        new MolarEnergy(2, EnergyUnit.KILOJOULES_PER_MOLE)),
                new ThermodynamicIntegrationEstimator.LambdaObservation(1,
                        new MolarEnergy(4, EnergyUnit.KILOJOULES_PER_MOLE))), "fixture");
        assertThat(ti.deltaFreeEnergy().value()).isEqualTo(3.0);
        assertThat(ti.method()).isEqualTo(ScientificMethod.TI_ESTIMATE);
        var lie = new LinearInteractionEnergyModel(0.5, 0.25,
                new MolarEnergy(1, EnergyUnit.KILOJOULES_PER_MOLE), "published fixture")
                .evaluate(new MolarEnergy(4, EnergyUnit.KILOJOULES_PER_MOLE),
                        new MolarEnergy(8, EnergyUnit.KILOJOULES_PER_MOLE));
        assertThat(lie.estimate().value()).isEqualTo(5.0);
        assertThat(lie.method()).isEqualTo(ScientificMethod.LIE_EMPIRICAL);
    }
}
