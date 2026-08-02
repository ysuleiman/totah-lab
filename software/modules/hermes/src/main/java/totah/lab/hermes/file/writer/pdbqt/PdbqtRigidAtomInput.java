package totah.lab.hermes.file.writer.pdbqt;

import java.util.Objects;

public record PdbqtRigidAtomInput(PdbqtAtomInput atom) {
    public PdbqtRigidAtomInput { Objects.requireNonNull(atom, "atom"); }
}
