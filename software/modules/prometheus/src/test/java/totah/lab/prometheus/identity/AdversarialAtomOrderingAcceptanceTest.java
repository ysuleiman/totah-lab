package totah.lab.prometheus.identity;

import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.prometheus.evidence.CalculationType;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QmProtocol;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Adversarial acceptance test for atom ordering — A6 of
 * docs/TSL_RSH_ADVERSARIAL_TEST_SUITE.md. Gradient row i belongs to the atom in
 * row i of the declared atom order; permuted rows are different science and
 * must never be silently re-keyed onto the unpermuted identity. The fixture
 * uses distinct elements (C, N, H) and distinct per-row gradient magnitudes
 * (0.05, 0.11, 0.23) so a row swap is always visible.
 */
class AdversarialAtomOrderingAcceptanceTest {

    private static CanonicalAtomMap cnhMap() {
        return new CanonicalAtomMap(
                new MoleculeIdentity("CNH", "C/N/H ordering fixture", "CHN"),
                List.of(new CanonicalAtomId(1, "C1", "C"),
                        new CanonicalAtomId(2, "N2", "N"),
                        new CanonicalAtomId(3, "H3", "H")));
    }

    private static List<Point3D> coordinates() {
        return List.of(new Point3D(0.0, 0.0, 0.0),
                new Point3D(1.53, 0.0, 0.0),
                new Point3D(2.29, 1.21, 0.0));
    }

    /**
     * TEST_ID: A6 — two geometries identical except two atom rows swapped have
     * different identity hashes; identical inputs hash identically.
     */
    @Test
    void a6_swappedAtomRowsChangeGeometryIdentity() {
        CanonicalAtomMap map = cnhMap();
        List<Point3D> canonical = coordinates();
        List<Point3D> swapped = List.of(canonical.get(1), canonical.get(0), canonical.get(2));

        assertThat(GeometryIdentity.of(map, coordinates()).sha256())
                .isEqualTo(GeometryIdentity.of(map, canonical).sha256());
        assertThat(GeometryIdentity.of(map, swapped).sha256())
                .isNotEqualTo(GeometryIdentity.of(map, canonical).sha256());
        assertThat(GeometryIdentity.of(map, swapped).atomCount()).isEqualTo(3);
    }

    /**
     * TEST_ID: A6 — a gradient artifact whose rows are permuted relative to the
     * declared atom order must not match the unpermuted evidence identity, and
     * re-keying its rows under the canonical labels hands atom C1 atom N2's
     * force — the exact reassignment the invariant forbids silently.
     */
    @Test
    void a6_permutedRowsNeverMatchUnpermutedEvidenceIdentity() {
        CanonicalAtomMap map = cnhMap();
        QmProtocol protocol = new QmProtocol("PBE", "def2-SVP", "D3(BJ)", "none", false, "PYSCF", "2.14.0");
        List<Point3D> canonical = coordinates();
        GeometryIdentity unpermuted = GeometryIdentity.of(map, canonical);
        GeometryIdentity permuted =
                GeometryIdentity.of(map, List.of(canonical.get(1), canonical.get(0), canonical.get(2)));

        EvidenceIdentity unpermutedEvidence = new EvidenceIdentity(map.molecule(), map.canonicalHash(),
                unpermuted, 0, 1, CalculationType.FORCE_EVALUATION, protocol, List.of(),
                List.of("gradient hartree/bohr"));
        EvidenceIdentity permutedEvidence = new EvidenceIdentity(map.molecule(), map.canonicalHash(),
                permuted, 0, 1, CalculationType.FORCE_EVALUATION, protocol, List.of(),
                List.of("gradient hartree/bohr"));

        assertThat(permutedEvidence.evidenceHash()).isNotEqualTo(unpermutedEvidence.evidenceHash());
        assertThat(permutedEvidence.isExactDuplicateOf(unpermutedEvidence)).isFalse();

        // Labeled-row oracle over the spec fixture: rows with distinct
        // magnitudes 0.05 / 0.11 / 0.23 zipped against the declared C,N,H order.
        double[][] gradient = {{0.05, 0.0, 0.0}, {0.11, 0.0, 0.0}, {0.23, 0.0, 0.0}};
        String declared = labeledGradient(map, gradient);
        String relabeled = labeledGradient(map, new double[][]{gradient[1], gradient[0], gradient[2]});

        assertThat(labeledGradient(map, gradient)).isEqualTo(declared);   // stable keying
        assertThat(CanonicalHashing.sha256Hex(relabeled))
                .isNotEqualTo(CanonicalHashing.sha256Hex(declared));
        // the permuted artifact would label atom N2's force (0.11) as C1's:
        assertThat(relabeled.lines().findFirst().orElseThrow()).contains("C1").contains("0.11");
        assertThat(declared.lines().findFirst().orElseThrow()).contains("C1").contains("0.05");
    }

    /** Zips gradient row i with the atom in row i of the declared canonical order. */
    private static String labeledGradient(CanonicalAtomMap map, double[][] rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.length; i++) {
            sb.append(map.atoms().get(i).label()).append(' ')
                    .append(rows[i][0]).append(' ').append(rows[i][1]).append(' ').append(rows[i][2]);
            if (i + 1 < rows.length) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }
}
