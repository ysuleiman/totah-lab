package totah.lab.gaia.geometry;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResidueGeometryTest {

    @Test
    void measuresSelectedAtomsAndCentroid() {
        Residue first = residue(
                atom("CA", Element.C, 0.0),
                atom("H", Element.H, 100.0));
        Residue second = residue(atom("CA", Element.C, 3.0));

        assertEquals(
                3.0,
                ResidueGeometry.minimumAtomDistance(
                        first, second, AtomSelection.HEAVY)
                        .orElseThrow());
        assertEquals(
                new Point3D(50.0, 0.0, 0.0),
                ResidueGeometry.centroid(first, AtomSelection.ALL)
                        .orElseThrow());
        assertEquals(
                new Point3D(0.0, 0.0, 0.0),
                ResidueGeometry.centroid(first, AtomSelection.HEAVY)
                        .orElseThrow());
    }

    @Test
    void reportsUnavailableMeasurements() {
        Residue empty = residue();
        Residue withoutCa = residue(atom("CB", Element.C, 1.0));

        assertTrue(ResidueGeometry.minimumAtomDistance(
                empty, withoutCa, AtomSelection.ALL).isEmpty());
        assertTrue(ResidueGeometry.centroid(
                empty, AtomSelection.ALL).isEmpty());
        assertTrue(ResidueGeometry.alphaCarbonDistance(
                empty, withoutCa).isEmpty());
    }

    private static Residue residue(Atom... atoms) {
        return new Residue("ALA", 1, List.of(atoms));
    }

    private static Atom atom(String name, Element element, double x) {
        return Atom.builder()
                .pdbSerial((int) x + 1)
                .name(name)
                .position(new Point3D(x, 0.0, 0.0))
                .element(element)
                .build();
    }
}
