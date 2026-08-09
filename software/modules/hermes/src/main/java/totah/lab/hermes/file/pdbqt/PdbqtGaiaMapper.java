package totah.lab.hermes.file.pdbqt;

import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Maps parsed PDBQT content onto gaia domain objects so the Athena
 * ligand analysis (contacts, pose comparison) can consume it: a
 * receptor file becomes a {@link Structure} grouped by chain and
 * residue, a ligand model becomes a single-residue {@link Ligand}.
 * Bond orders are not recovered from PDBQT; the gaia objects carry
 * geometry, charges, and AutoDock types only.
 */
public final class PdbqtGaiaMapper {

    private PdbqtGaiaMapper() {
    }

    /**
     * Maps a prepared Gaia structure to the canonical rigid PDBQT model.
     * No charge or atom-type calculation is performed here.
     */
    public static PdbqtFile fromStructure(Structure structure) {
        Objects.requireNonNull(structure, "structure");
        List<PdbqtAtom> atoms = new ArrayList<>();
        int serial = 1;
        for (Chain chain : structure.getChains()) {
            for (Residue residue : chain.residues()) {
                for (Atom atom : residue.getAtoms()) {
                    if (atom.getPosition() == null) {
                        throw new IllegalArgumentException(
                                "Missing coordinates on " + atom.getName());
                    }
                    atoms.add(new PdbqtAtom(
                            AtomRecordType.ATOM,
                            serial++,
                            atom.getName(),
                            residue.getName(),
                            chain.id(),
                            residue.getNumber(),
                            residue.getInsertionCode(),
                            atom.getPosition().x(),
                            atom.getPosition().y(),
                            atom.getPosition().z(),
                            atom.getOccupancy(),
                            atom.getBFactor(),
                            atom.getCharge(),
                            atom.getAutoDockType()));
                }
            }
        }
        PdbqtModel model = new PdbqtModel(
                1,
                List.copyOf(atoms),
                new PdbqtTorsionTree(List.of(), List.of(), null),
                List.of());
        return new PdbqtFile(List.of(model));
    }

    /**
     * Maps one ligand model (a pose) to a gaia ligand.
     */
    public static Ligand toLigand(PdbqtModel model, String name) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(name, "name");
        List<Atom> atoms = new ArrayList<>();
        // gaia requires unique (chain, residue, atom name) references;
        // meeko atom names repeat (several carbons named "C"), so the
        // serial is appended whenever a name was already used
        Map<String, Integer> seen = new LinkedHashMap<>();
        for (PdbqtAtom atom : model.atoms()) {
            int occurrences = seen.merge(atom.atomName(), 1, Integer::sum);
            atoms.add(occurrences == 1
                    ? toAtom(atom)
                    : toAtom(atom, atom.atomName() + atom.serial()));
        }
        Residue residue = new Residue(
                ligandResidueName(model),
                1,
                atoms
        );
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))));
        return new Ligand(name, name, null, null, null, null, structure);
    }

    /**
     * Maps a receptor file to a gaia structure: every atom record
     * grouped by (chain, residue number), in first-seen order.
     */
    public static Structure toStructure(PdbqtFile file) {
        Objects.requireNonNull(file, "file");
        Map<String, Map<Integer, List<Atom>>> byChain =
                new LinkedHashMap<>();
        Map<String, Map<Integer, String>> names = new LinkedHashMap<>();
        for (PdbqtModel model : file.models()) {
            for (PdbqtAtom atom : model.atoms()) {
                if (atom.residueNumber() == null) {
                    continue;
                }
                String chain = atom.chainId() == null
                        || atom.chainId().isBlank()
                        ? "A"
                        : atom.chainId();
                byChain.computeIfAbsent(chain, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(atom.residueNumber(),
                                ignored -> new ArrayList<>())
                        .add(toAtom(atom));
                names.computeIfAbsent(chain, ignored -> new LinkedHashMap<>())
                        .putIfAbsent(atom.residueNumber(),
                                atom.residueName());
            }
        }
        List<Chain> chains = new ArrayList<>();
        for (Map.Entry<String, Map<Integer, List<Atom>>> chainEntry
                : byChain.entrySet()) {
            List<Residue> residues = new ArrayList<>();
            for (Map.Entry<Integer, List<Atom>> residueEntry
                    : chainEntry.getValue().entrySet()) {
                residues.add(new Residue(
                        names.get(chainEntry.getKey())
                                .get(residueEntry.getKey()),
                        residueEntry.getKey(),
                        residueEntry.getValue()
                ));
            }
            chains.add(new Chain(chainEntry.getKey(), residues));
        }
        return new Structure(chains);
    }

    private static Atom toAtom(PdbqtAtom atom) {
        return toAtom(atom, atom.atomName());
    }

    private static Atom toAtom(PdbqtAtom atom, String name) {
        return Atom.builder()
                .pdbSerial(atom.serial())
                .name(name)
                .autoDockType(atom.autodockType())
                .position(atom.position())
                .charge(atom.partialCharge())
                .occupancy(atom.occupancy() != null ? atom.occupancy() : 1.0)
                .bFactor(atom.temperatureFactor() != null
                        ? atom.temperatureFactor()
                        : 0.0)
                .element(Element.fromSymbol(atom.element()))
                .build();
    }

    private static String ligandResidueName(PdbqtModel model) {
        for (PdbqtAtom atom : model.atoms()) {
            if (atom.residueName() != null
                    && !atom.residueName().isBlank()) {
                return atom.residueName();
            }
        }
        return "LIG";
    }
}
