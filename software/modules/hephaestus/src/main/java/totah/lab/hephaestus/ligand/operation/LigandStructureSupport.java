package totah.lab.hephaestus.ligand.operation;

import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        Chain updatedChain = new Chain(chain.id(), List.of(updatedResidue));
        Structure original = ligand.structure();
        List<Bond> carriedBonds = carryBonds(original, atoms);
        Structure structure = new Structure(
                List.of(updatedChain),
                carriedBonds,
                carryConnectivityMetadata(original, carriedBonds));
        return new Ligand(
                ligand.id(), ligand.name(), ligand.componentCode().orElse(null),
                ligand.smiles().orElse(null), ligand.inchiKey().orElse(null),
                ligand.formalCharge(), structure);
    }

    // Bond references are name-based and the chain id and residue number are
    // unchanged, so a bond survives atom replacement whenever both endpoint
    // atom names still exist among the replacement atoms.
    private static List<Bond> carryBonds(Structure original, List<Atom> atoms) {
        Set<String> atomNames = new HashSet<>();
        for (Atom atom : atoms) {
            atomNames.add(atom.getName());
        }
        List<Bond> carried = new ArrayList<>(original.bonds().size());
        for (Bond bond : original.bonds()) {
            if (atomNames.contains(bond.atom1().atomName())
                    && atomNames.contains(bond.atom2().atomName())) {
                carried.add(bond);
            }
        }
        return List.copyOf(carried);
    }

    private static ConnectivityMetadata carryConnectivityMetadata(
            Structure original, List<Bond> carriedBonds) {
        ConnectivityMetadata metadata = original.getConnectivityMetadata();
        if (metadata.provenance() == ConnectivityProvenance.ABSENT
                || original.bonds().isEmpty()) {
            return metadata;
        }
        int dropped = original.bonds().size() - carriedBonds.size();
        if (dropped == 0) {
            return metadata;
        }
        List<String> diagnostics = new ArrayList<>(metadata.diagnostics());
        diagnostics.add(
                "Atom replacement dropped "
                        + dropped
                        + " bond(s) whose endpoint atoms no longer exist.");
        return new ConnectivityMetadata(
                ConnectivityProvenance.PARTIAL,
                List.copyOf(diagnostics));
    }
}
