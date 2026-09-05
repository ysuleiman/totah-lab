package totah.lab.athena.interaction;

import totah.lab.athena.interaction.perception.AromaticRing;
import totah.lab.athena.interaction.perception.ChargeSign;
import totah.lab.athena.interaction.perception.ChargedGroup;
import totah.lab.athena.interaction.perception.ChargedGroupType;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.Vector3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.List;

/** Hand-built synthetic fixtures with known geometry for detector tests. */
final class InteractionFixtures {

    static final double RING_RADIUS = 1.4;

    private InteractionFixtures() {
    }

    static Atom atom(
            int serial,
            String name,
            Element element,
            String autoDockType,
            double x,
            double y,
            double z) {

        return Atom.builder()
                .pdbSerial(serial)
                .name(name)
                .element(element)
                .autoDockType(autoDockType)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .build();
    }

    static Atom atom(
            int serial,
            String name,
            Element element,
            double x,
            double y,
            double z) {

        return atom(serial, name, element, null, x, y, z);
    }

    static Residue residue(String name, int number, List<Atom> atoms) {
        return new Residue(name, number, atoms);
    }

    static Chain chain(String id, Residue... residues) {
        return new Chain(id, List.of(residues));
    }

    static Structure structure(Chain... chains) {
        return new Structure(List.of(chains));
    }

    static Structure structure(List<Chain> chains, List<Bond> bonds) {
        return new Structure(chains, bonds, ConnectivityProvenance.EXPLICIT);
    }

    static Bond bond(String chainId, int residueNumber, String atom1,
            String atom2) {
        return new Bond(
                new AtomReference(chainId, residueNumber, ' ', atom1),
                new AtomReference(chainId, residueNumber, ' ', atom2),
                BondOrder.SINGLE);
    }

    /**
     * A regular hexagonal ring (radius {@value #RING_RADIUS}) in the plane
     * spanned by the orthonormal vectors {@code u} and {@code v} around
     * {@code center}. Atoms are named C1..C6, element carbon.
     */
    static AromaticRing hexRing(
            String ringId,
            ResidueId owner,
            int serialBase,
            Point3D center,
            Vector3D u,
            Vector3D v) {

        List<Atom> atoms = new ArrayList<>();
        for (int k = 0; k < 6; k++) {
            double angle = Math.toRadians(60.0 * k);
            Point3D position = center
                    .add(u.scale(RING_RADIUS * Math.cos(angle)))
                    .add(v.scale(RING_RADIUS * Math.sin(angle)));
            atoms.add(atom(serialBase + k, "C" + (k + 1), Element.C,
                    position.x(), position.y(), position.z()));
        }
        return new AromaticRing(
                ringId,
                owner,
                atoms,
                centroid(atoms),
                PerceptionProvenance.BOND_GRAPH,
                "synthetic hexagonal test ring");
    }

    /** Ring flagged as perceived via the degraded AD4 fallback. */
    static AromaticRing degradedRing(
            String ringId,
            ResidueId owner,
            int serialBase,
            Point3D center) {

        AromaticRing ring = hexRing(ringId, owner, serialBase, center,
                new Vector3D(1, 0, 0), new Vector3D(0, 1, 0));
        return new AromaticRing(
                ring.ringId(),
                ring.owner(),
                ring.atoms(),
                ring.centroid(),
                PerceptionProvenance.AD4_FALLBACK,
                "synthetic degraded test ring");
    }

    /** Charged group whose charge center is the centroid of its atoms. */
    static ChargedGroup chargedGroup(
            ChargeSign sign,
            ChargedGroupType type,
            ResidueId owner,
            List<Atom> atoms) {

        return new ChargedGroup(
                sign,
                type,
                owner,
                atoms,
                centroid(atoms),
                PerceptionProvenance.BOND_GRAPH,
                "synthetic test group");
    }

    static Point3D centroid(List<Atom> atoms) {
        double x = 0.0;
        double y = 0.0;
        double z = 0.0;
        for (Atom atom : atoms) {
            x += atom.getPosition().x();
            y += atom.getPosition().y();
            z += atom.getPosition().z();
        }
        return new Point3D(
                x / atoms.size(), y / atoms.size(), z / atoms.size());
    }
}
