package totah.lab.hephaestus.ligand.operation;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.List;

final class LigandStructureSupport {
    private LigandStructureSupport() {
    }

    static Chain singleChain(Ligand ligand) {
        List<Chain> chains = ligand.structure().getChains();
        if (chains.size() != 1) {
            throw new IllegalArgumentException(
                    "A ligand must contain exactly one chain; found " + chains.size());
        }
        return chains.getFirst();
    }

    static Residue singleResidue(Ligand ligand) {
        Chain chain = singleChain(ligand);
        if (chain.residues().size() != 1) {
            throw new IllegalArgumentException(
                    "A ligand must contain exactly one residue; found " + chain.residues().size());
        }
        return chain.residues().getFirst();
    }

    static Ligand replaceAtoms(Ligand ligand, List<Atom> atoms) {
        Chain chain = singleChain(ligand);
        Residue residue = singleResidue(ligand);
        Residue updatedResidue = residue.toBuilder().atoms(atoms).build();
        Structure structure = new Structure(List.of(new Chain(chain.id(), List.of(updatedResidue))));
        return new Ligand(
                ligand.id(), ligand.name(), ligand.componentCode().orElse(null),
                ligand.smiles().orElse(null), ligand.inchiKey().orElse(null),
                ligand.formalCharge(), structure);
    }
}
