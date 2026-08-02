package totah.lab.hermes.file.writer.pdbqt;

import java.util.List;

public record PdbqtFlexibleReceptorInput(
        List<PdbqtRigidAtomInput> rigidAtoms,
        List<PdbqtFlexibleResidueInput> flexibleResidues,
        int preparedAtomCount) {
    public PdbqtFlexibleReceptorInput {
        rigidAtoms = List.copyOf(rigidAtoms); flexibleResidues = List.copyOf(flexibleResidues);
    }
}
