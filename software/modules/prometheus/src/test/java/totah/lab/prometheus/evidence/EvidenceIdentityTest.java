package totah.lab.prometheus.evidence;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.fixtures.EvidenceFixtures;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;

class EvidenceIdentityTest {

    @Test
    void identicalFieldsAreExactDuplicatesWithEqualHashes() {
        EvidenceIdentity a = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity b = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());

        assertThat(a.isExactDuplicateOf(b)).isTrue();
        assertThat(a.evidenceHash()).isEqualTo(b.evidenceHash());
        assertThat(a.sameGeometryDifferentProtocol(b)).isFalse();
    }

    @Test
    void sameGeometryUnderDifferentQmMethodIsNotEquivalentEvidence() {
        EvidenceIdentity pbe = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity pbe0 = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityA());

        assertThat(pbe.isExactDuplicateOf(pbe0)).isFalse();
        assertThat(pbe.evidenceHash()).isNotEqualTo(pbe0.evidenceHash());
        assertThat(pbe.sameGeometryDifferentProtocol(pbe0)).isTrue();
        assertThat(pbe0.sameGeometryDifferentProtocol(pbe)).isTrue();
    }

    @Test
    void differentGeometryIsNotSameGeometryDifferentProtocol() {
        EvidenceIdentity a = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP,
                TslFixtures.geometryIdentityA());
        EvidenceIdentity b = EvidenceFixtures.identity(
                CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE0_DEF2_TZVP,
                TslFixtures.geometryIdentityB());

        assertThat(a.sameGeometryDifferentProtocol(b)).isFalse();
        assertThat(a.isExactDuplicateOf(b)).isFalse();
    }

    @Test
    void listDelimitersCannotCollapseDifferentConstraintStructures() {
        EvidenceIdentity base = EvidenceFixtures.identity(CalculationType.SINGLE_POINT,
                EvidenceFixtures.PBE_DEF2_SVP, TslFixtures.geometryIdentityA());
        EvidenceIdentity oneConstraint = new EvidenceIdentity(base.molecule(), base.atomMapHash(), base.geometry(),
                base.formalCharge(), base.multiplicity(), base.calculationType(), base.protocol(),
                List.of("freeze=a,b"), base.requestedOutputs());
        EvidenceIdentity twoConstraints = new EvidenceIdentity(base.molecule(), base.atomMapHash(), base.geometry(),
                base.formalCharge(), base.multiplicity(), base.calculationType(), base.protocol(),
                List.of("freeze=a", "b"), base.requestedOutputs());

        assertThat(oneConstraint.evidenceHash()).isNotEqualTo(twoConstraints.evidenceHash());
    }

    @Test
    void protocolDelimitersCannotCollapseDifferentFields() {
        QmProtocol first = new QmProtocol("a|b", "c", "none", "none", false, "PySCF", "2.8");
        QmProtocol second = new QmProtocol("a", "b|c", "none", "none", false, "PySCF", "2.8");

        assertThat(first.protocolKey()).isNotEqualTo(second.protocolKey());
    }
}
