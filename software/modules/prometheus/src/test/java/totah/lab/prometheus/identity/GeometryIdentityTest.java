package totah.lab.prometheus.identity;

import org.junit.jupiter.api.Test;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.prometheus.fixtures.TslFixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

class GeometryIdentityTest {

    @Test
    void sameCoordinatesProduceSameHash() {
        GeometryIdentity a = GeometryIdentity.of(TslFixtures.canonicalMap(), TslFixtures.geometryA());
        GeometryIdentity b = GeometryIdentity.of(TslFixtures.canonicalMap(), TslFixtures.geometryA());

        assertThat(a).isEqualTo(b);
        assertThat(a.sha256()).hasSize(64);
        assertThat(a.atomCount()).isEqualTo(5);
    }

    @Test
    void differentCoordinatesProduceDifferentHash() {
        assertThat(TslFixtures.geometryIdentityA().sha256())
                .isNotEqualTo(TslFixtures.geometryIdentityB().sha256());
    }

    @Test
    void atomOrderMatters() {
        CanonicalAtomMap map = TslFixtures.canonicalMap();
        List<Point3D> canonicalOrder = TslFixtures.geometryA();
        // same points, but the first two swapped: no longer matches canonical order
        List<Point3D> swapped = List.of(
                canonicalOrder.get(1), canonicalOrder.get(0), canonicalOrder.get(2),
                canonicalOrder.get(3), canonicalOrder.get(4));

        assertThat(GeometryIdentity.of(map, swapped).sha256())
                .isNotEqualTo(GeometryIdentity.of(map, canonicalOrder).sha256());
    }

    @Test
    void serializationIsStableAtEightDecimals() {
        CanonicalAtomMap map = TslFixtures.canonicalMap();
        List<Point3D> base = TslFixtures.geometryA();

        // perturbation below the %.8f resolution must not change the hash
        List<Point3D> perturbed = base.stream()
                .map(p -> new Point3D(p.x() + 1e-12, p.y(), p.z()))
                .toList();
        assertThat(GeometryIdentity.of(map, perturbed).sha256())
                .isEqualTo(GeometryIdentity.of(map, base).sha256());

        // perturbation above the %.8f resolution must change the hash
        List<Point3D> moved = base.stream()
                .map(p -> new Point3D(p.x() + 1e-6, p.y(), p.z()))
                .toList();
        assertThat(GeometryIdentity.of(map, moved).sha256())
                .isNotEqualTo(GeometryIdentity.of(map, base).sha256());
    }

    @Test
    void coordinateCountMustMatchAtomCount() {
        assertThatThrownBy(() -> GeometryIdentity.of(
                TslFixtures.canonicalMap(), List.of(new Point3D(0, 0, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match atom count");
    }
}
