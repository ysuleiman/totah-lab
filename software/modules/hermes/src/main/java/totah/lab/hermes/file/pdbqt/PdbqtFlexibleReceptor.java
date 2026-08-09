package totah.lab.hermes.file.pdbqt;

import java.util.List;

public record PdbqtFlexibleReceptor(
        List<PdbqtRigidAtom> rigidAtoms,
        List<PdbqtFlexibleResidue> flexibleResidues,
        int preparedAtomCount) {
    public PdbqtFlexibleReceptor {
        rigidAtoms = List.copyOf(rigidAtoms); flexibleResidues = List.copyOf(flexibleResidues);
    }
}
