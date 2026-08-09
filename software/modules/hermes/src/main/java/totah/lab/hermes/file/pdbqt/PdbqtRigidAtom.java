package totah.lab.hermes.file.pdbqt;

import java.util.Objects;

public record PdbqtRigidAtom(PdbqtAtomReference atom) {
    public PdbqtRigidAtom { Objects.requireNonNull(atom, "atom"); }
}
