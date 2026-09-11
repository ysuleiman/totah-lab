package totah.lab.hermes.file.pdbqt;

import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.SmilesParser;
import com.actelion.research.chem.StereoMolecule;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.molecule.Ligand;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.gaia.structure.Bond;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.ConnectivityMetadata;
import totah.lab.gaia.structure.ConnectivityProvenance;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hermes.file.pdbqt.meeko.MeekoResult;
import totah.lab.hermes.file.pdbqt.meeko.MeekoResultParser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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
     * Maps a Meeko-produced ligand pose and reconstructs its complete bond
     * graph from the embedded stereochemical SMILES, SMILES-to-PDBQT atom
     * indices, and explicit-hydrogen parent records.
     *
     * <p>This method never guesses bonds from coordinates. Missing,
     * incomplete, or contradictory metadata causes an exception instead of
     * silently returning degraded connectivity. The legacy
     * {@link #toLigand(PdbqtModel, String)} atom-only path is unchanged.</p>
     *
     * @throws IllegalArgumentException if the Meeko topology evidence is not
     *                                  complete and internally consistent
     */
    public static Ligand toLigandWithMeekoTopology(
            PdbqtModel model, String name) {
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(name, "name");

        MeekoResult meeko = new MeekoResultParser().parse(model.remarks())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Missing Meeko topology remarks"));
        if (meeko.smiles() == null || meeko.smiles().isBlank()) {
            throw new IllegalArgumentException("Missing Meeko SMILES");
        }

        StereoMolecule molecule;
        try {
            molecule = new SmilesParser().parseMolecule(meeko.smiles());
            molecule.ensureHelperArrays(Molecule.cHelperCIP);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid Meeko SMILES: " + meeko.smiles(), exception);
        }

        MappedAtoms mapped = mappedAtoms(model);
        Map<Integer, Integer> serialBySmilesIndex = validateHeavyAtomMap(
                model, molecule, meeko.smilesIndices());
        Map<Integer, Integer> hydrogenParentBySerial = validateHydrogenMap(
                model, serialBySmilesIndex, meeko.hydrogenParents());

        List<Bond> bonds = new ArrayList<>();
        Set<SerialPair> serialBonds = new HashSet<>();
        for (int bondIndex = 0;
             bondIndex < molecule.getAllBonds(); bondIndex++) {
            int firstSmilesIndex = molecule.getBondAtom(0, bondIndex) + 1;
            int secondSmilesIndex = molecule.getBondAtom(1, bondIndex) + 1;
            int firstSerial = serialBySmilesIndex.get(firstSmilesIndex);
            int secondSerial = serialBySmilesIndex.get(secondSmilesIndex);
            addBond(bonds, serialBonds, mapped.namesBySerial(),
                    firstSerial, secondSerial, bondOrder(molecule, bondIndex));
        }
        for (Map.Entry<Integer, Integer> entry
                : hydrogenParentBySerial.entrySet()) {
            addBond(bonds, serialBonds, mapped.namesBySerial(),
                    entry.getValue(), entry.getKey(), BondOrder.SINGLE);
        }

        for (int[] branch : model.rotatableBondSerials()) {
            if (!serialBonds.contains(new SerialPair(branch[0], branch[1]))) {
                throw new IllegalArgumentException(
                        "Torsion-tree bond is absent from Meeko topology: "
                                + branch[0] + "-" + branch[1]);
            }
        }

        Residue residue = new Residue(ligandResidueName(model), 1,
                mapped.atoms());
        ConnectivityMetadata metadata = new ConnectivityMetadata(
                ConnectivityProvenance.EXPLICIT,
                List.of("SOURCE=MEEKO_SMILES_IDX",
                        "SMILES=" + meeko.smiles(),
                        "HEAVY_ATOM_MAP=COMPLETE",
                        "EXPLICIT_HYDROGEN_MAP=COMPLETE",
                        "TORSION_TREE=CONSISTENT"));
        Structure structure = new Structure(
                List.of(new Chain("L", List.of(residue))), bonds, metadata);
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

    private static MappedAtoms mappedAtoms(PdbqtModel model) {
        List<Atom> atoms = new ArrayList<>();
        Map<Integer, String> namesBySerial = new LinkedHashMap<>();
        Map<String, Integer> seen = new LinkedHashMap<>();
        Set<Integer> serials = new HashSet<>();
        for (PdbqtAtom atom : model.atoms()) {
            if (!serials.add(atom.serial())) {
                throw new IllegalArgumentException(
                        "Duplicate PDBQT atom serial: " + atom.serial());
            }
            int occurrences = seen.merge(atom.atomName(), 1, Integer::sum);
            String mappedName = occurrences == 1
                    ? atom.atomName() : atom.atomName() + atom.serial();
            atoms.add(toAtom(atom, mappedName));
            namesBySerial.put(atom.serial(), mappedName);
        }
        return new MappedAtoms(List.copyOf(atoms), Map.copyOf(namesBySerial));
    }

    private static Map<Integer, Integer> validateHeavyAtomMap(
            PdbqtModel model, StereoMolecule molecule,
            List<MeekoResult.IndexPair> pairs) {
        Map<Integer, PdbqtAtom> atomBySerial = atomBySerial(model);
        Map<Integer, Integer> serialBySmilesIndex = new LinkedHashMap<>();
        Set<Integer> mappedSerials = new HashSet<>();
        for (MeekoResult.IndexPair pair : pairs) {
            int smilesIndex = pair.first();
            int serial = pair.second();
            if (smilesIndex < 1 || smilesIndex > molecule.getAtoms()) {
                throw new IllegalArgumentException(
                        "SMILES atom index out of range: " + smilesIndex);
            }
            if (serialBySmilesIndex.putIfAbsent(smilesIndex, serial) != null) {
                throw new IllegalArgumentException(
                        "Duplicate SMILES atom index: " + smilesIndex);
            }
            if (!mappedSerials.add(serial)) {
                throw new IllegalArgumentException(
                        "Duplicate mapped PDBQT serial: " + serial);
            }
            PdbqtAtom atom = requiredAtom(atomBySerial, serial);
            if (atom.hydrogen()) {
                throw new IllegalArgumentException(
                        "SMILES heavy atom maps to hydrogen serial: " + serial);
            }
            int expectedAtomicNumber = molecule.getAtomicNo(smilesIndex - 1);
            int actualAtomicNumber = Element.fromSymbol(atom.element())
                    .getAtomicNumber();
            if (actualAtomicNumber != expectedAtomicNumber) {
                throw new IllegalArgumentException(
                        "Element mismatch for SMILES atom " + smilesIndex
                                + " and PDBQT serial " + serial);
            }
        }
        long pdbqtHeavyAtoms = model.atoms().stream()
                .filter(atom -> !atom.hydrogen()).count();
        if (serialBySmilesIndex.size() != molecule.getAtoms()
                || mappedSerials.size() != pdbqtHeavyAtoms) {
            throw new IllegalArgumentException(
                    "Incomplete Meeko heavy-atom mapping: SMILES atoms="
                            + molecule.getAtoms() + ", mapped="
                            + serialBySmilesIndex.size() + ", PDBQT heavy atoms="
                            + pdbqtHeavyAtoms);
        }
        return Map.copyOf(serialBySmilesIndex);
    }

    private static Map<Integer, Integer> validateHydrogenMap(
            PdbqtModel model,
            Map<Integer, Integer> serialBySmilesIndex,
            List<MeekoResult.HydrogenParent> parents) {
        Map<Integer, PdbqtAtom> atomBySerial = atomBySerial(model);
        Map<Integer, Integer> parentByHydrogen = new LinkedHashMap<>();
        for (MeekoResult.HydrogenParent pair : parents) {
            Integer parentSerial = serialBySmilesIndex.get(pair.parentAtom());
            if (parentSerial == null) {
                throw new IllegalArgumentException(
                        "Hydrogen parent references unmapped SMILES atom: "
                                + pair.parentAtom());
            }
            PdbqtAtom parent = requiredAtom(atomBySerial, parentSerial);
            PdbqtAtom hydrogen = requiredAtom(atomBySerial, pair.hydrogenAtom());
            if (parent.hydrogen() || !hydrogen.hydrogen()) {
                throw new IllegalArgumentException(
                        "Invalid Meeko hydrogen-parent mapping: "
                                + pair.parentAtom() + "-" + pair.hydrogenAtom());
            }
            if (parentByHydrogen.putIfAbsent(
                    pair.hydrogenAtom(), parentSerial) != null) {
                throw new IllegalArgumentException(
                        "Duplicate mapped hydrogen serial: "
                                + pair.hydrogenAtom());
            }
        }
        long explicitHydrogens = model.atoms().stream()
                .filter(PdbqtAtom::hydrogen).count();
        if (parentByHydrogen.size() != explicitHydrogens) {
            throw new IllegalArgumentException(
                    "Incomplete Meeko explicit-hydrogen mapping: PDBQT hydrogens="
                            + explicitHydrogens + ", mapped="
                            + parentByHydrogen.size());
        }
        return Map.copyOf(parentByHydrogen);
    }

    private static Map<Integer, PdbqtAtom> atomBySerial(PdbqtModel model) {
        Map<Integer, PdbqtAtom> atoms = new LinkedHashMap<>();
        for (PdbqtAtom atom : model.atoms()) {
            if (atoms.putIfAbsent(atom.serial(), atom) != null) {
                throw new IllegalArgumentException(
                        "Duplicate PDBQT atom serial: " + atom.serial());
            }
        }
        return atoms;
    }

    private static PdbqtAtom requiredAtom(
            Map<Integer, PdbqtAtom> atomBySerial, int serial) {
        PdbqtAtom atom = atomBySerial.get(serial);
        if (atom == null) {
            throw new IllegalArgumentException(
                    "Meeko mapping references missing PDBQT serial: " + serial);
        }
        return atom;
    }

    private static void addBond(
            List<Bond> bonds, Set<SerialPair> serialBonds,
            Map<Integer, String> namesBySerial,
            int firstSerial, int secondSerial, BondOrder order) {
        SerialPair pair = new SerialPair(firstSerial, secondSerial);
        if (!serialBonds.add(pair)) {
            throw new IllegalArgumentException(
                    "Duplicate reconstructed bond: " + pair.first()
                            + "-" + pair.second());
        }
        bonds.add(new Bond(reference(namesBySerial, firstSerial),
                reference(namesBySerial, secondSerial), order));
    }

    private static AtomReference reference(
            Map<Integer, String> namesBySerial, int serial) {
        String name = namesBySerial.get(serial);
        if (name == null) {
            throw new IllegalArgumentException(
                    "Bond references missing PDBQT serial: " + serial);
        }
        return new AtomReference("L", 1, ' ', name);
    }

    private static BondOrder bondOrder(
            StereoMolecule molecule, int bondIndex) {
        if (molecule.isAromaticBond(bondIndex)) {
            return BondOrder.AROMATIC;
        }
        return switch (molecule.getBondOrder(bondIndex)) {
            case 1 -> BondOrder.SINGLE;
            case 2 -> BondOrder.DOUBLE;
            case 3 -> BondOrder.TRIPLE;
            default -> throw new IllegalArgumentException(
                    "Unsupported SMILES bond order: "
                            + molecule.getBondOrder(bondIndex));
        };
    }

    private record MappedAtoms(
            List<Atom> atoms, Map<Integer, String> namesBySerial) {
    }

    private record SerialPair(int first, int second) {
        private SerialPair {
            if (first > second) {
                int swap = first;
                first = second;
                second = swap;
            }
        }
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
