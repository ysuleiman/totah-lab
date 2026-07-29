package totah.lab.http.biohub.model;

import totah.lab.protein.Atom;

import java.util.Objects;

public record AtomComplex(
        Atom atom,
        boolean hetero
) {

    public AtomComplex {
        Objects.requireNonNull(atom, "atom");
    }
}
