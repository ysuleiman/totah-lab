package totah.lab.http.biohub.model;

import totah.lab.gaia.structure.Atom;

import java.util.Objects;

public record AtomComplex(
        Atom atom,
        boolean hetero
) {

    public AtomComplex {
        Objects.requireNonNull(atom, "atom");
    }
}
