package totah.lab.proteus.protein.mutation.geometry;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.ZMatrixMath;
import totah.lab.gaia.structure.AlternateLocationProvenance;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.proteus.protein.mutation.rotamer.Rotamer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class SideChainBuilder {

    public List<Atom> build(
            Residue source,
            SideChainTemplate template,
            Rotamer rotamer) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(rotamer, "rotamer");
        // GLY has an empty side-chain template: nothing to build.
        if (template.atoms().isEmpty()) {
            return List.of();
        }
        Map<String, Point3D> positions = new LinkedHashMap<>();
        source.getAtoms().forEach(atom -> positions.put(atom.getName(), atom.getPosition()));
        var built = new java.util.ArrayList<Atom>();
        int nextSerial = source.getAtoms().stream()
                .mapToInt(Atom::getPdbSerial).max().orElse(0) + 1;
        for (InternalCoordinate coordinate : template.atoms()) {
            Point3D bond = require(positions, coordinate.bondReference());
            Point3D angle = require(positions, coordinate.angleReference());
            Point3D dihedral = require(positions, coordinate.dihedralReference());
            double torsion = coordinate.dihedralRadians()
                    + (coordinate.applyFirstChi() ? rotamer.firstChiOrZero() : 0.0);
            Point3D position = ZMatrixMath.calculatePosition(
                    bond, angle, dihedral, coordinate.bondLength(),
                    coordinate.bondAngleRadians(), torsion);
            Atom atom = Atom.builder()
                    .pdbSerial(nextSerial++)
                    .name(coordinate.atomName())
                    .position(position)
                    .charge(0.0)
                    .occupancy(1.0)
                    .bFactor(source.findAtom("CA").orElseThrow().getBFactor())
                    .element(coordinate.element())
                    .alternateLocationProvenance(AlternateLocationProvenance.NONE)
                    .build();
            built.add(atom);
            positions.put(atom.getName(), position);
        }
        return List.copyOf(built);
    }

    private Point3D require(Map<String, Point3D> positions, String atomName) {
        Point3D position = positions.get(atomName);
        if (position == null) {
            throw new IllegalArgumentException("Missing geometry reference atom: " + atomName);
        }
        return position;
    }
}
