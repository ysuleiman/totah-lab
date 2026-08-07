package totah.lab.hephaestus.ligand.operation;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.hephaestus.ligand.LigandPreparationOperation;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.topology.LigandTopology;
import totah.lab.hephaestus.model.PreparedLigand;
import totah.lab.hephaestus.preparation.OperationResult;
import totah.lab.hephaestus.typing.AssignedAtomType;
import totah.lab.hephaestus.typing.AtomTypeAssignment;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Assigns AutoDock4 ligand atom types from the prepared CCD topology. */
public final class LigandAD4AtomTypingOperation implements LigandPreparationOperation {
    private static final Set<String> SUPPORTED_METALS = Set.of("Mg", "Ca", "Mn", "Fe", "Zn");

    @Override
    public OperationResult<PreparedLigand> apply(
            PreparedLigand preparedLigand, LigandPreparationOptions options) {
        Objects.requireNonNull(preparedLigand, "preparedLigand");
        Objects.requireNonNull(options, "options");
        if (!options.assignAtomTypes()) {
            return OperationResult.success(preparedLigand);
        }
        if (!(preparedLigand.topology() instanceof LigandTopology topology)) {
            throw new IllegalStateException("Ligand CCD topology is required before atom typing.");
        }
        Chain chain = LigandStructureSupport.singleChain(preparedLigand.ligand());
        Residue residue = LigandStructureSupport.singleResidue(preparedLigand.ligand());
        List<Atom> atoms = residue.getAtoms();
        List<List<BondedAtom>> adjacency = adjacency(atoms.size(), topology.bonds());
        List<Atom> typedAtoms = new ArrayList<>();
        List<AssignedAtomType> assignments = new ArrayList<>();
        for (int index = 0; index < atoms.size(); index++) {
            Atom atom = atoms.get(index);
            if (options.assignCharges() && !Double.isFinite(atom.getCharge())) {
                throw new IllegalStateException("Non-finite charge at atom index " + index);
            }
            String type = type(topology, atoms, adjacency, index);
            typedAtoms.add(atom.toBuilder().autoDockType(type).build());
            assignments.add(new AssignedAtomType(
                    index, chain.id(), residue.getNumber(), residue.getInsertionCode(),
                    atom.getName(), type));
        }
        var ligand = LigandStructureSupport.replaceAtoms(preparedLigand.ligand(), typedAtoms);
        return OperationResult.success(preparedLigand.withLigand(ligand)
                .withAtomTypes(new AtomTypeAssignment("AutoDock4", assignments)));
    }

    private String type(
            LigandTopology topology, List<Atom> atoms,
            List<List<BondedAtom>> adjacency, int index) {
        String element = atoms.get(index).getElement().symbol();
        return switch (element) {
            case "H" -> hydrogenType(atoms, adjacency, index);
            case "C" -> aromatic(topology, adjacency, index) ? "A" : "C";
            case "N" -> nitrogenType(topology, atoms, adjacency, index);
            // Meeko's ad4_types.json types every oxygen OA; there is no
            // oxygen override rule.
            case "O" -> "OA";
            case "S" -> sulfurType(topology, atoms, adjacency, index);
            case "P" -> "P";
            case "F", "Cl", "Br", "I" -> element;
            default -> {
                if (SUPPORTED_METALS.contains(element)) {
                    yield element;
                }
                throw new IllegalArgumentException(
                        "Unsupported AutoDock4 ligand element '" + element
                                + "' at atom index " + index);
            }
        };
    }

    private String hydrogenType(List<Atom> atoms, List<List<BondedAtom>> adjacency, int index) {
        if (adjacency.get(index).size() != 1) {
            throw new IllegalStateException("Hydrogen must have exactly one bonded parent.");
        }
        String parent = atoms.get(adjacency.get(index).getFirst().atomIndex()).getElement().symbol();
        // Meeko: HD when the parent is N, O, F, P or S.
        return Set.of("N", "O", "S", "F", "P").contains(parent) ? "HD" : "H";
    }

    /*
     * Meeko ad4_types.json nitrogen rules (later rules win):
     * default NA (acceptor); N (donor) only for [#7+1] charged
     * nitrogens or neutral [#7X3v3] nitrogens attached to an aromatic
     * atom (aniline, pyrrole), a [#6X3v4] carbon (amide and friends),
     * or an [NX2]=[*] nitrogen (triazene).
     */
    private String nitrogenType(
            LigandTopology topology, List<Atom> atoms,
            List<List<BondedAtom>> adjacency, int index) {
        if (formalCharge(topology, index) > 0) {
            return "N";
        }
        List<BondedAtom> neighbors = adjacency.get(index);
        if (neighbors.size() == 3 && valence(neighbors) == 3.0) {
            for (BondedAtom neighbor : neighbors) {
                int neighborIndex = neighbor.atomIndex();
                if (aromatic(topology, adjacency, neighborIndex)) {
                    return "N";
                }
                String element = atoms.get(neighborIndex)
                        .getElement().symbol();
                if ("C".equals(element)
                        && adjacency.get(neighborIndex).size() == 3
                        && valence(adjacency.get(neighborIndex)) == 4.0) {
                    return "N";
                }
                if ("N".equals(element)
                        && adjacency.get(neighborIndex).size() == 2
                        && valence(adjacency.get(neighborIndex)) == 3.0) {
                    return "N";
                }
            }
        }
        return "NA";
    }

    /*
     * Meeko ad4_types.json sulfur rules: SA only for ALIPHATIC
     * two-connected sulfur (SMARTS [SX2] does not match aromatic
     * sulfur); everything else — aromatic S (thiophene), sulfoxides,
     * sulfones, charged sulfur — is S.
     */
    private String sulfurType(
            LigandTopology topology, List<Atom> atoms,
            List<List<BondedAtom>> adjacency, int index) {
        if (aromatic(topology, adjacency, index)) {
            return "S";
        }
        return adjacency.get(index).size() == 2 ? "SA" : "S";
    }

    private boolean aromatic(
            LigandTopology topology, List<List<BondedAtom>> adjacency, int index) {
        return topology.atomProperties().get(index).aromatic()
                || adjacency.get(index).stream().anyMatch(bonded -> bonded.bond().aromatic()
                || bonded.bond().order() == BondOrder.AROMATIC);
    }

    private int formalCharge(LigandTopology topology, int index) {
        return topology.atomProperties().get(index).formalCharge();
    }

    /*
     * Effective valence for the Meeko connectivity tests ([#7X3v3],
     * [#6X3v4], [NX2]): aromatic bonds count 1.0, matching RDKit's
     * valence of aromatic-perceived atoms (pyrrole N = 3).
     */
    private double valence(List<BondedAtom> atoms) {
        return atoms.stream().mapToDouble(atom -> switch (atom.bond().order()) {
            case SINGLE -> 1.0;
            case DOUBLE -> 2.0;
            case TRIPLE -> 3.0;
            case AROMATIC -> 1.0;
            case UNKNOWN -> throw new IllegalArgumentException(
                    "Cannot assign AD4 ligand types with UNKNOWN bond order");
        }).sum();
    }

    private List<List<BondedAtom>> adjacency(int count, List<ChemicalBond> bonds) {
        List<List<BondedAtom>> result = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            result.add(new ArrayList<>());
        }
        for (ChemicalBond bond : bonds) {
            result.get(bond.atomIndexA()).add(new BondedAtom(bond.atomIndexB(), bond));
            result.get(bond.atomIndexB()).add(new BondedAtom(bond.atomIndexA(), bond));
        }
        return result.stream().map(List::copyOf).toList();
    }

    private record BondedAtom(int atomIndex, ChemicalBond bond) {
    }
}
