package totah.lab.hephaestus.receptor.hydrogen;

import totah.lab.gaia.structure.Atom;

import java.util.List;
import java.util.Objects;

public final class HydrogenInsertion {

    private final SpatialClashChecker clashChecker;
    private final double clashCutoff;

    public HydrogenInsertion(
            SpatialClashChecker clashChecker,
            double clashCutoff) {

        this.clashChecker = Objects.requireNonNull(
                clashChecker,
                "clashChecker");

        if (!Double.isFinite(clashCutoff)
                || clashCutoff <= 0.0) {
            throw new IllegalArgumentException(
                    "clashCutoff must be finite and positive.");
        }

        this.clashCutoff = clashCutoff;
    }

    public boolean tryAdd(
            List<Atom> atoms,
            Atom candidate,
            Atom bondedParent) {

        Objects.requireNonNull(atoms, "atoms");

        if (candidate == null
                || candidate.getPosition() == null) {
            return false;
        }

        if (clashChecker.hasClash(
                candidate.getPosition(),
                clashCutoff,
                bondedParent)) {
            return false;
        }

        atoms.add(candidate);
        clashChecker.addAtom(candidate);

        return true;
    }
}