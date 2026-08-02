package totah.lab.pocket.visualization;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record LocatedResidue(
        ResidueId id,
        Residue residue) {

    public LocatedResidue {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(residue, "residue");
        if (residue.getNumber() != id.residueNumber()
                || !Objects.equals(
                        residue.getInsertionCode(),
                        id.insertionCode())) {
            throw new IllegalArgumentException(
                    "Residue does not match its identifier: " + id);
        }
    }

    public String chainId() {
        return id.chainId();
    }

    public String name() {
        return residue.getName();
    }

    public int number() {
        return id.residueNumber();
    }

    public List<Atom> atoms() {
        return residue.getAtoms();
    }

    public Optional<Point3D> alphaCarbonPosition() {
        return residue.getAlphaCarbonPosition();
    }

    public String getChain() {
        return chainId();
    }

    public String getName() {
        return name();
    }

    public int getNumber() {
        return number();
    }

    public Character getInsertionCode() {
        return id.insertionCode();
    }

    public List<Atom> getAtoms() {
        return atoms();
    }

    public Point3D getAlphaCarbonPosition() {
        return alphaCarbonPosition().orElse(null);
    }

    public int getAtomCount() {
        return residue.getAtomCount();
    }

    public Optional<Atom> findAtom(String name) {
        return residue.findAtom(name);
    }
}
