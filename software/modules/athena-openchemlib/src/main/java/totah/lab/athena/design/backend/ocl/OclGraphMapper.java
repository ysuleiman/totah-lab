package totah.lab.athena.design.backend.ocl;

import com.actelion.research.chem.Molecule;
import com.actelion.research.chem.StereoMolecule;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Package-private mapping boundary. OCL indices never escape this package. */
final class OclGraphMapper {
    Mapping toOcl(MolecularGraph graph) throws MolecularBackendException {
        try {
            var molecule = new StereoMolecule(graph.atoms().size(), graph.bonds().size());
            var indexById = new LinkedHashMap<String, Integer>();
            var idByMapNumber = new LinkedHashMap<Integer, String>();
            int mapNumber = 1;
            for (var atom : graph.atoms()) {
                int index = molecule.addAtom(atom.element());
                molecule.setAtomMapNo(index, mapNumber, false);
                molecule.setAtomCharge(index, atom.formalCharge());
                if (atom.isotope() != null) molecule.setAtomMass(index, atom.isotope());
                if (atom.coordinates() != null) {
                    molecule.setAtomX(index, atom.coordinates().x());
                    molecule.setAtomY(index, atom.coordinates().y());
                    molecule.setAtomZ(index, atom.coordinates().z());
                }
                indexById.put(atom.id(), index); idByMapNumber.put(mapNumber, atom.id()); mapNumber++;
            }
            for (var bond : graph.bonds()) {
                Integer first = indexById.get(bond.firstAtomId()); Integer second = indexById.get(bond.secondAtomId());
                if (first == null || second == null) throw new MolecularBackendException("bond endpoint missing: " + bond.id());
                molecule.addBond(first, second, bondType(bond));
            }
            for (var atom : graph.atoms()) {
                applyParity(molecule, indexById.get(atom.id()), atom.stereochemistry());
            }
            return new Mapping(molecule, graph, idByMapNumber);
        } catch (MolecularBackendException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new MolecularBackendException("OCL graph conversion failed", exception);
        }
    }

    MolecularGraph fromOcl(Mapping mapping, StereoMolecule molecule) throws MolecularBackendException {
        var originalAtoms = new HashMap<String, MolecularGraph.Atom>();
        mapping.source().atoms().forEach(atom -> originalAtoms.put(atom.id(), atom));
        var atoms = new ArrayList<MolecularGraph.Atom>();
        var idByIndex = new HashMap<Integer, String>();
        var hydrogenOrdinals = new HashMap<String, Integer>();
        for (int index = 0; index < molecule.getAllAtoms(); index++) {
            String id = mapping.idByMapNumber().get(molecule.getAtomMapNo(index));
            if (id == null) {
                int neighbor = molecule.getConnAtoms(index) == 0 ? -1 : molecule.getConnAtom(index, 0);
                String parent = neighbor < 0 ? "unconnected" : idByIndex.getOrDefault(neighbor, "index" + neighbor);
                int ordinal = hydrogenOrdinals.merge(parent, 1, Integer::sum);
                id = "backend:H:" + parent + ":" + ordinal;
            }
            idByIndex.put(index, id);
            var original = originalAtoms.get(id);
            atoms.add(new MolecularGraph.Atom(id, molecule.getAtomLabel(index),
                    molecule.getAtomMass(index) == 0 ? null : molecule.getAtomMass(index),
                    molecule.getAtomCharge(index), original == null ? 0 : original.explicitHydrogens(),
                    molecule.isAromaticAtom(index), parity(molecule.getAtomParity(index)),
                    molecule.is3D() ? new MolecularGraph.Coordinates(
                            molecule.getAtomX(index), molecule.getAtomY(index), molecule.getAtomZ(index)) : null,
                    original == null ? Map.of("origin", "backend-added") : original.properties()));
        }
        var originalBonds = new HashMap<String, MolecularGraph.Bond>();
        for (var bond : mapping.source().bonds()) originalBonds.put(pair(bond.firstAtomId(), bond.secondAtomId()), bond);
        var bonds = new ArrayList<MolecularGraph.Bond>();
        for (int index = 0; index < molecule.getAllBonds(); index++) {
            String first = idByIndex.get(molecule.getBondAtom(0, index));
            String second = idByIndex.get(molecule.getBondAtom(1, index));
            var original = originalBonds.get(pair(first, second));
            String id = original == null ? "backend:bond:" + pair(first, second) : original.id();
            var order = order(molecule, index);
            bonds.add(new MolecularGraph.Bond(id, first, second, order,
                    order == MolecularGraph.BondOrder.AROMATIC, "UNSPECIFIED",
                    original == null ? Map.of("origin", "backend-added") : original.properties()));
        }
        return new MolecularGraph(atoms, bonds, mapping.source().properties());
    }

    private static int bondType(MolecularGraph.Bond bond) {
        if (bond.aromatic() || bond.order() == MolecularGraph.BondOrder.AROMATIC) return Molecule.cBondTypeDelocalized;
        return switch (bond.order()) {
            case SINGLE -> Molecule.cBondTypeSingle; case DOUBLE -> Molecule.cBondTypeDouble;
            case TRIPLE -> Molecule.cBondTypeTriple; case AROMATIC -> Molecule.cBondTypeDelocalized;
        };
    }
    private static MolecularGraph.BondOrder order(StereoMolecule molecule, int bond) {
        if (molecule.isAromaticBond(bond)) return MolecularGraph.BondOrder.AROMATIC;
        return switch (molecule.getBondOrder(bond)) {
            case 1 -> MolecularGraph.BondOrder.SINGLE; case 2 -> MolecularGraph.BondOrder.DOUBLE;
            case 3 -> MolecularGraph.BondOrder.TRIPLE;
            default -> throw new IllegalArgumentException("unsupported OCL bond order: " + molecule.getBondOrder(bond));
        };
    }
    private static void applyParity(StereoMolecule molecule, int index, String stereo) throws MolecularBackendException {
        switch (stereo == null ? "UNSPECIFIED" : stereo) {
            case "UNSPECIFIED", "NONE" -> { }
            case "PARITY_1" -> { molecule.setAtomParity(index, Molecule.cAtomParity1, false); molecule.setAtomESR(index, Molecule.cESRTypeAbs, -1); }
            case "PARITY_2" -> { molecule.setAtomParity(index, Molecule.cAtomParity2, false); molecule.setAtomESR(index, Molecule.cESRTypeAbs, -1); }
            case "UNKNOWN" -> molecule.setAtomParity(index, Molecule.cAtomParityUnknown, false);
            default -> throw new MolecularBackendException("unsupported project-neutral stereo descriptor: " + stereo);
        }
    }
    private static String parity(int parity) {
        return switch (parity) {
            case Molecule.cAtomParity1 -> "PARITY_1"; case Molecule.cAtomParity2 -> "PARITY_2";
            case Molecule.cAtomParityUnknown -> "UNKNOWN"; default -> "UNSPECIFIED";
        };
    }
    private static String pair(String first, String second) { return first.compareTo(second) <= 0 ? first + "|" + second : second + "|" + first; }
    record Mapping(StereoMolecule molecule, MolecularGraph source, Map<Integer, String> idByMapNumber) { }
}
