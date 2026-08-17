package totah.lab.gaia.geometry;

import totah.lab.gaia.structure.Atom;

import java.util.Objects;

/** Neutral atom subsets used by structural geometry queries. */
public enum AtomSelection {
    ALL {
        @Override
        public boolean includes(Atom atom) {
            Objects.requireNonNull(atom, "atom");
            return true;
        }
    },
    HEAVY {
        @Override
        public boolean includes(Atom atom) {
            Objects.requireNonNull(atom, "atom");
            return atom.isHeavyAtom();
        }
    };

    public abstract boolean includes(Atom atom);
}
