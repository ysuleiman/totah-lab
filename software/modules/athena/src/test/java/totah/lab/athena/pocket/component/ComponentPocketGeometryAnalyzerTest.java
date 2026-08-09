package totah.lab.athena.pocket.component;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ComponentPocketGeometryAnalyzerTest {
    private final ComponentPocketGeometryAnalyzer analyzer =
            new ComponentPocketGeometryAnalyzer(
                    ComponentPocketGeometryThresholds.defaults());
    private final List<GeometryAtom> pocketAtoms = List.of(
            atom("C", 3, 0, 0, "A:10"),
            atom("N", 10, 0, 0, "A:20"));
    private final List<PocketSphere> spheres = List.of(
            new PocketSphere(new Point3D(0, 0, 0), 2.0));

    @Test
    void classifiesOccupancyByHeavyAtomFractionInsideSphereCloud() {
        var result = analyzer.analyze(List.of(
                atom("C", 0, 0, 0, null), atom("O", 1, 0, 0, null)),
                pocketAtoms, spheres);
        assertEquals(ComponentPocketRelationshipClass.OCCUPIES_POCKET,
                result.relationshipClass());
        assertEquals(1.0, result.heavyAtomInsideFraction());
    }

    @Test
    void distinguishesContactNearAndNotAssociated() {
        assertEquals(ComponentPocketRelationshipClass.CONTACTS_POCKET,
                analyzeAt(6.5).relationshipClass());
        assertEquals(ComponentPocketRelationshipClass.NEAR_POCKET,
                analyzeAt(15).relationshipClass());
        assertEquals(ComponentPocketRelationshipClass.NOT_ASSOCIATED,
                analyzeAt(30).relationshipClass());
    }

    @Test
    void hydrogenDoesNotDiluteHeavyAtomFractions() {
        var result = analyzer.analyze(List.of(atom("C", 0, 0, 0, null),
                atom("H", 20, 0, 0, null)), pocketAtoms, spheres);
        assertEquals(1.0, result.heavyAtomInsideFraction());
    }

    @Test
    void rejectsInvalidThresholdOrdering() {
        assertThrows(IllegalArgumentException.class, () ->
                new ComponentPocketGeometryThresholds(6, 4, 12, 2, 0.5));
    }

    private ComponentPocketGeometry analyzeAt(double x) {
        return analyzer.analyze(List.of(atom("C", x, 0, 0, null)),
                pocketAtoms, spheres);
    }

    private static GeometryAtom atom(String element, double x, double y,
            double z, String residue) {
        return new GeometryAtom(element, new Point3D(x, y, z), residue);
    }
}
