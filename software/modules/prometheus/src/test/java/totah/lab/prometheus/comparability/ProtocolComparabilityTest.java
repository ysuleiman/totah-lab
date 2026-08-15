package totah.lab.prometheus.comparability;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QmProtocol;
import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The TSL separations: PBE-D3(BJ)/def2-SVP conformational energies,
 * PBE0-D3(BJ)/def2-TZVP counterpoise interaction energies, and HF/6-31G(d) ESP
 * evidence are mutually incompatible.
 */
class ProtocolComparabilityTest {

    private final ProtocolComparability comparability = new ProtocolComparability();

    @Test
    void conformationalVsCounterpoiseInteractionIsIncompatibleEnergyTarget() {
        EvidenceIdentity conformational = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity interaction = EvidenceFixtures.identity(
                CalculationType.COUNTERPOISE_INTERACTION,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityA());

        ComparabilityDecision decision = comparability.compare(conformational, interaction);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.INCOMPATIBLE_ENERGY_TARGET);
        assertThat(decision.reason())
                .contains(EnergyTarget.CONFORMATIONAL.name())
                .contains(EnergyTarget.INTERACTION.name());
    }

    @Test
    void hfEspVsPbeEspOnDifferentGeometriesIsIncompatibleProtocol() {
        EvidenceIdentity hfEsp = EvidenceFixtures.identity(
                CalculationType.ESP,
                EvidenceFixtures.HF_631Gd,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity pbeEsp = EvidenceFixtures.identity(
                CalculationType.ESP,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityB());

        ComparabilityDecision decision = comparability.compare(hfEsp, pbeEsp);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.INCOMPATIBLE_PROTOCOL);
        assertThat(decision.reason()).contains("method").contains("basis");
    }

    @Test
    void sameGeometryUnderDifferentQmMethodIsSameGeometryDifferentMethod() {
        EvidenceIdentity pbe = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity pbe0 = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityA());

        ComparabilityDecision decision = comparability.compare(pbe, pbe0);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.SAME_GEOMETRY_DIFFERENT_METHOD);
        assertThat(decision.reason()).isNotBlank();
    }

    @Test
    void identicalProtocolConformationalSinglePointsNeedReferenceShift() {
        EvidenceIdentity a = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity b = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityB());

        ComparabilityDecision decision = comparability.compare(a, b);

        assertThat(decision.verdict())
                .isEqualTo(ComparabilityVerdict.COMPARABLE_AFTER_REFERENCE_SHIFT);
        assertThat(decision.reason()).isNotBlank();
    }

    @Test
    void blankProtocolMetadataIsIncomplete() {
        QmProtocol blankMetadata =
                new QmProtocol("PBE", "def2-SVP", "", "none", false, "ORCA", "5.0.4");
        EvidenceIdentity a = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                blankMetadata,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity b = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());

        ComparabilityDecision decision = comparability.compare(a, b);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.INCOMPLETE_METADATA);
        assertThat(decision.reason()).contains("dispersion");
    }

    @Test
    void differentMoleculeIsIncompatibleProtocol() {
        EvidenceIdentity a = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity b = new EvidenceIdentity(
                new totah.lab.prometheus.identity.MoleculeIdentity("OTHER", "other", "CH4"),
                a.atomMapHash(),
                a.geometry(),
                0,
                1,
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                a.constraints(),
                a.requestedOutputs());

        ComparabilityDecision decision = comparability.compare(a, b);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.INCOMPATIBLE_PROTOCOL);
        assertThat(decision.reason()).isEqualTo("different molecule");
    }

    @Test
    void identicalInteractionProtocolsAreComparable() {
        EvidenceIdentity a = EvidenceFixtures.identity(
                CalculationType.COUNTERPOISE_INTERACTION,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity b = EvidenceFixtures.identity(
                CalculationType.COUNTERPOISE_INTERACTION,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityB());

        ComparabilityDecision decision = comparability.compare(a, b);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.COMPARABLE);
    }

    @Test
    void sameProtocolDifferentCalculationTypesInSameTargetNeedReferenceShift() {
        EvidenceIdentity singlePoint = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity torsionScan = EvidenceFixtures.identity(
                CalculationType.TORSION_SCAN,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());

        ComparabilityDecision decision = comparability.compare(singlePoint, torsionScan);

        assertThat(decision.verdict())
                .isEqualTo(ComparabilityVerdict.COMPARABLE_AFTER_REFERENCE_SHIFT);
    }

    @Test
    void differentProtocolDifferentCalculationTypesIsIncompatibleProtocol() {
        EvidenceIdentity singlePoint = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity torsionScan = EvidenceFixtures.identity(
                CalculationType.TORSION_SCAN,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityA());

        ComparabilityDecision decision = comparability.compare(singlePoint, torsionScan);

        assertThat(decision.verdict()).isEqualTo(ComparabilityVerdict.INCOMPATIBLE_PROTOCOL);
        assertThat(decision.reason()).contains("method");
    }
}
