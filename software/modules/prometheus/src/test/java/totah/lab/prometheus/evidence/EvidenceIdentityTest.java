package totah.lab.prometheus.evidence;

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
}
