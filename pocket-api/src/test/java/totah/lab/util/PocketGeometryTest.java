package totah.lab.util;

import org.junit.jupiter.api.Test;
import totah.lab.pocket.PocketSource;
import totah.lab.pocket.ResidueRef;
import totah.lab.pocket.Sphere;
import totah.lab.protein.Atom;
import totah.lab.protein.Element;
import totah.lab.pocket.Pocket;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PocketGeometryTest {

    @Test
    void calculateCenterPrefersResolvedHeavyAtomCentroid() {
        Residue residue = Residue.builder()
                .name("ALA")
                .chain("A")
                .number(1)
                .atoms(List.of(
                        atom("N", "N", 0.0, 0.0, 0.0),
                        atom("CA", "C", 2.0, 0.0, 0.0),
                        atom("H", "H", 100.0, 100.0, 100.0)))
                .build();
        Pocket pocket = pocket(List.of(new ResidueRef("A", 1, "ALA")),
                Map.of("alpha_spheres", List.of(new Sphere(1, 50.0, 50.0, 50.0, 1.0))),
                new Point3D(20.0, 20.0, 20.0),
                ref -> residue);

        Point3D center = PocketGeometry.calculateCenter(pocket);

        assertEquals(1.0, center.x(), 1e-9);
        assertEquals(0.0, center.y(), 1e-9);
        assertEquals(0.0, center.z(), 1e-9);
    }

    @Test
    void calculateCenterForP2RankStylePocketDoesNotRequireAlphaSpheres() {
        Residue residue = Residue.builder()
                .name("SER")
                .chain("A")
                .number(42)
                .atoms(List.of(
                        atom("N", "N", 1.0, 1.0, 1.0),
                        atom("CA", "C", 3.0, 5.0, 7.0)))
                .build();
        Pocket pocket = pocket(List.of(new ResidueRef("A", 42, null)),
                Map.of(),
                new Point3D(99.0, 99.0, 99.0),
                ref -> residue);

        Point3D center = PocketGeometry.calculateCenter(pocket);

        assertEquals(2.0, center.x(), 1e-9);
        assertEquals(3.0, center.y(), 1e-9);
        assertEquals(4.0, center.z(), 1e-9);
    }

    @Test
    void calculateCenterFallsBackToFpocketAlphaSphereCentroid() {
        Pocket pocket = pocket(List.of(), Map.of("alpha_spheres", List.of(
                new Sphere(1, 0.0, 0.0, 0.0, 1.0),
                new Sphere(2, 2.0, 4.0, 6.0, 1.0))),
                new Point3D(20.0, 20.0, 20.0),
                ref -> null);

        Point3D center = PocketGeometry.calculateCenter(pocket);

        assertEquals(1.0, center.x(), 1e-9);
        assertEquals(2.0, center.y(), 1e-9);
        assertEquals(3.0, center.z(), 1e-9);
    }

    @Test
    void calculateCenterFallsBackToStoredCenter() {
        Point3D storedCenter = new Point3D(3.0, 4.0, 5.0);
        Pocket pocket = pocket(List.of(), Map.of(), storedCenter, ref -> null);

        assertEquals(storedCenter, PocketGeometry.calculateCenter(pocket));
    }

    @Test
    void calculateCenterRejectsPocketWithoutAnyCenterSource() {
        Pocket pocket = pocket(List.of(), Map.of(), null, ref -> null);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> PocketGeometry.calculateCenter(pocket));
        assertTrue(error.getMessage().contains("Cannot calculate pocket center"));
    }

    private Pocket pocket(List<ResidueRef> residueRefs, Map<String, Object> attributes,
                          Point3D center, java.util.function.Function<ResidueRef, Residue> resolver) {
        return new Pocket(1L, "pocket-1", center, 1.0, residueRefs,
                PocketSource.builder().source("TEST").build(), attributes, resolver);
    }

    private Atom atom(String name, String element, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .bFactor(10.0)
                .element(Element.fromSymbol(element))
                .build();
    }
}
