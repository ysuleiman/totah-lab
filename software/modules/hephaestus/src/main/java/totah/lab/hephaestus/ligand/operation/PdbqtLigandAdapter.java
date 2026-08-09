package totah.lab.hephaestus.ligand.operation;

import totah.lab.hephaestus.ligand.flexibility.LigandFlexibilityModel;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hermes.file.pdbqt.PdbqtAtomReference;
import totah.lab.hermes.file.pdbqt.PdbqtLigandFragment;
import totah.lab.hermes.file.pdbqt.PdbqtLigand;

import java.util.ArrayList;

final class PdbqtLigandAdapter {
    PdbqtLigand adapt(PreparedLigand preparedLigand) {
        if (preparedLigand.charges() == null || preparedLigand.atomTypes() == null) {
            throw new IllegalStateException("Charges and AD4 atom types are required for export.");
        }
        Object value = preparedLigand.attributes().get(LigandFlexibilityModel.ATTRIBUTE_KEY);
        if (!(value instanceof LigandFlexibilityModel flexibility)) {
            throw new IllegalStateException("Ligand torsion model is required for export.");
        }
        var chain = LigandStructureSupport.singleChain(preparedLigand.ligand());
        var residue = LigandStructureSupport.singleResidue(preparedLigand.ligand());
        if (flexibility.atomCount() != residue.getAtomCount()) {
            throw new IllegalStateException("Ligand torsion model is stale.");
        }
        var atoms = new ArrayList<PdbqtAtomReference>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            var atom = residue.getAtoms().get(index);
            if (atom.getPosition() == null || !Double.isFinite(atom.getCharge())
                    || atom.getAutoDockType() == null || atom.getAutoDockType().isBlank()) {
                throw new IllegalStateException("Ligand atom is not export-ready at index " + index);
            }
            atoms.add(new PdbqtAtomReference(
                    index, index + 1, atom.getName(), residue.getName(), chain.id(),
                    residue.getNumber(), residue.getInsertionCode(), atom.getPosition(),
                    atom.getOccupancy(), atom.getBFactor(), atom.getCharge(), atom.getAutoDockType()));
        }
        var fragments = flexibility.fragments().stream().map(fragment ->
                new PdbqtLigandFragment(fragment.id(), fragment.atomIndices(),
                        fragment.parentFragmentId(), fragment.parentAtomIndex(),
                        fragment.childAtomIndex())).toList();
        return new PdbqtLigand(atoms, flexibility.rootFragmentId(), fragments);
    }
}
