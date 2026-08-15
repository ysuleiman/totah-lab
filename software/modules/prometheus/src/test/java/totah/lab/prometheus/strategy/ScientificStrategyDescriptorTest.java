package totah.lab.prometheus.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class ScientificStrategyDescriptorTest {

    @Test
    void qubeMethodologyIsIndependentOfQubeKitExecutable() {
        ScientificStrategyDescriptor descriptor = new QubeLikeStrategy().scientificDescriptor();

        assertThat(descriptor.strategyId()).isEqualTo("qube-like");
        assertThat(descriptor.methodology()).contains("DDEC");
        assertThat(descriptor.externalDependencies())
                .extracting(ExternalDependencyDescriptor::capability)
                .doesNotContain("QUBEKit")
                .contains("DDEC/AIM partitioner", "QM electronic-structure engine");
        assertThat(descriptor.requirements())
                .extracting(ScientificRequirementDescriptor::evidenceKind)
                .contains(ScientificEvidenceKind.ELECTRON_DENSITY,
                        ScientificEvidenceKind.ATOM_IN_MOLECULE_VOLUMES,
                        ScientificEvidenceKind.HESSIAN,
                        ScientificEvidenceKind.TORSION_PROFILE);
        assertThat(descriptor.produces(ParameterizationCapability.VAN_DER_WAALS)).isTrue();
    }

    @Test
    void qForceDoesNotClaimToSolveNonbondedParameters() {
        ScientificStrategyDescriptor descriptor = new QForceLikeStrategy().scientificDescriptor();

        assertThat(descriptor.produces(ParameterizationCapability.VAN_DER_WAALS)).isFalse();
        assertThat(descriptor.produces(ParameterizationCapability.ATOMIC_CHARGES)).isFalse();
        assertThat(descriptor.knownLimitations())
                .anyMatch(value -> value.contains("coupled-coordinate defect"));
    }

    @Test
    void forceBalanceDeclaresThatCompatibilityAndOutputsDependOnChosenModel() {
        ScientificStrategyDescriptor descriptor = new ForceBalanceStyleStrategy().scientificDescriptor();

        assertThat(descriptor.openMmCompatibility())
                .isEqualTo(EngineCompatibility.DEPENDS_ON_SELECTED_FUNCTIONAL_FORM);
        assertThat(descriptor.amberCompatibility())
                .isEqualTo(EngineCompatibility.DEPENDS_ON_SELECTED_FUNCTIONAL_FORM);
        assertThat(descriptor.requirements())
                .anyMatch(requirement -> requirement.evidenceKind() == ScientificEvidenceKind.INITIAL_FORCE_FIELD)
                .anyMatch(ScientificRequirementDescriptor::validationOnly);
    }

    @Test
    void hessianRouteOnlyProducesHarmonicBondedFamilies() {
        ScientificStrategyDescriptor descriptor = new HessianBondedStrategy().scientificDescriptor();

        assertThat(descriptor.outputs())
                .containsExactlyInAnyOrder(ParameterizationCapability.BONDS, ParameterizationCapability.ANGLES);
        assertThat(descriptor.requirements())
                .filteredOn(requirement -> !requirement.validationOnly())
                .extracting(ScientificRequirementDescriptor::evidenceKind)
                .containsExactlyInAnyOrder(ScientificEvidenceKind.OPTIMIZED_GEOMETRY,
                        ScientificEvidenceKind.HESSIAN);
    }

    @Test
    void allDescriptorsAreMoleculeIndependentAndDeclareHoldoutAndLimitations() {
        List<ScientificParameterizationStrategy> strategies = List.of(
                new QubeLikeStrategy(),
                new QForceLikeStrategy(),
                new ForceBalanceStyleStrategy(),
                new HessianBondedStrategy());

        for (ScientificParameterizationStrategy strategy : strategies) {
            ScientificStrategyDescriptor descriptor = strategy.scientificDescriptor();
            assertThat(descriptor.strategyId()).doesNotContainIgnoringCase("TSL");
            assertThat(descriptor.requirements()).anyMatch(ScientificRequirementDescriptor::validationOnly);
            assertThat(descriptor.knownLimitations()).isNotEmpty();
            assertThat(descriptor.productionFunctionalForm()).isNotBlank();
        }
    }
}
