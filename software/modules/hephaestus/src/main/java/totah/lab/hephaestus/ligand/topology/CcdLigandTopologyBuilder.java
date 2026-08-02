package totah.lab.hephaestus.ligand.topology;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.ChemicalBond;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Residue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Builds immutable ligand topology by matching deposited atoms to a CCD entry. */
public final class CcdLigandTopologyBuilder {

    public LigandTopology build(Residue residue, ChemComp chemComp) {
        Objects.requireNonNull(residue, "residue");
        Objects.requireNonNull(chemComp, "chemComp");
        if (residue.isEmpty()) {
            throw new IllegalArgumentException("Cannot build topology for an empty ligand.");
        }
        if (chemComp.getAtoms() == null || chemComp.getAtoms().isEmpty()) {
            throw new IllegalArgumentException(
                    "Complete CCD entry is required for " + residue.getName());
        }

        Map<String, ChemCompAtom> ccdAtoms = indexCcdAtoms(chemComp);
        Map<String, Integer> deposited = indexDepositedAtoms(residue);
        List<String> missingHeavy = new ArrayList<>();
        List<String> extraHeavy = new ArrayList<>();
        for (ChemCompAtom atom : ccdAtoms.values()) {
            if (!deposited.containsKey(normalize(atom.getAtomId()))
                    && !isHydrogen(atom.getTypeSymbol())) {
                missingHeavy.add(atom.getAtomId());
            }
        }
        for (Atom atom : residue.getAtoms()) {
            if (!ccdAtoms.containsKey(normalize(atom.getName())) && !atom.isHydrogen()) {
                extraHeavy.add(atom.getName());
            }
        }
        if (!missingHeavy.isEmpty() || !extraHeavy.isEmpty()) {
            throw new IllegalArgumentException(
                    "Ligand does not match CCD " + chemComp.getId()
                            + "; missing heavy atoms=" + missingHeavy
                            + ", extra heavy atoms=" + extraHeavy);
        }

        List<LigandAtomProperties> properties = new ArrayList<>();
        List<CcdAtomCoordinates> coordinates = new ArrayList<>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            Atom depositedAtom = residue.getAtoms().get(index);
            ChemCompAtom ccdAtom = ccdAtoms.get(normalize(depositedAtom.getName()));
            if (ccdAtom == null) {
                throw new IllegalArgumentException(
                        "Deposited atom is absent from CCD: " + depositedAtom.getName());
            }
            properties.add(new LigandAtomProperties(
                    ccdAtom.getAtomId(), ccdAtom.getCharge(),
                    yes(ccdAtom.getPdbxAromaticFlag()),
                    yes(ccdAtom.getPdbxLeavingAtomFlag())));
            coordinates.add(new CcdAtomCoordinates(
                    index, modelPosition(ccdAtom), idealPosition(ccdAtom)));
        }

        return new LigandTopology(
                chemComp.getId(), residue.getAtomCount(),
                buildBonds(chemComp, deposited), properties,
                missingHydrogens(chemComp, ccdAtoms, deposited), coordinates);
    }

    private Map<String, ChemCompAtom> indexCcdAtoms(ChemComp chemComp) {
        Map<String, ChemCompAtom> result = new LinkedHashMap<>();
        for (ChemCompAtom atom : chemComp.getAtoms()) {
            String key = normalize(atom.getAtomId());
            if (key.isEmpty() || result.putIfAbsent(key, atom) != null) {
                throw new IllegalArgumentException("CCD has a blank or duplicate atom id.");
            }
        }
        return result;
    }

    private Map<String, Integer> indexDepositedAtoms(Residue residue) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            String key = normalize(residue.getAtoms().get(index).getName());
            if (key.isEmpty() || result.putIfAbsent(key, index) != null) {
                throw new IllegalArgumentException(
                        "Ligand has a blank or duplicate atom name: " + key);
            }
        }
        return result;
    }

    private List<ChemicalBond> buildBonds(
            ChemComp chemComp, Map<String, Integer> deposited) {
        List<ChemicalBond> result = new ArrayList<>();
        Set<Long> endpoints = new HashSet<>();
        if (chemComp.getBonds() == null) {
            return result;
        }
        for (ChemCompBond bond : chemComp.getBonds()) {
            Integer first = deposited.get(normalize(bond.getAtomId1()));
            Integer second = deposited.get(normalize(bond.getAtomId2()));
            if (first == null || second == null) {
                continue;
            }
            int low = Math.min(first, second);
            int high = Math.max(first, second);
            long key = ((long) low << 32) | (high & 0xffffffffL);
            if (!endpoints.add(key)) {
                throw new IllegalArgumentException("CCD has duplicate bond endpoints.");
            }
            BondOrder order = bondOrder(bond);
            result.add(new ChemicalBond(first, second, order,
                    yes(bond.getPdbxAromaticFlag()) || order == BondOrder.AROMATIC));
        }
        return result;
    }

    private List<MissingLigandHydrogen> missingHydrogens(
            ChemComp chemComp,
            Map<String, ChemCompAtom> ccdAtoms,
            Map<String, Integer> deposited) {
        List<MissingLigandHydrogen> result = new ArrayList<>();
        for (ChemCompAtom atom : ccdAtoms.values()) {
            String atomId = normalize(atom.getAtomId());
            if (!isHydrogen(atom.getTypeSymbol()) || deposited.containsKey(atomId)) {
                continue;
            }
            List<ChemCompBond> bonds = chemComp.getBonds() == null ? List.of()
                    : chemComp.getBonds().stream()
                    .filter(bond -> atomId.equals(normalize(bond.getAtomId1()))
                            || atomId.equals(normalize(bond.getAtomId2())))
                    .toList();
            if (bonds.size() != 1) {
                throw new IllegalArgumentException(
                        "CCD hydrogen " + atom.getAtomId() + " must have exactly one bond.");
            }
            ChemCompBond bond = bonds.getFirst();
            String parentId = atomId.equals(normalize(bond.getAtomId1()))
                    ? normalize(bond.getAtomId2()) : normalize(bond.getAtomId1());
            Integer parentIndex = deposited.get(parentId);
            if (parentIndex == null) {
                throw new IllegalArgumentException(
                        "CCD hydrogen " + atom.getAtomId() + " has a missing parent.");
            }
            result.add(new MissingLigandHydrogen(
                    atom.getAtomId(), parentIndex, bondOrder(bond), atom.getCharge(),
                    yes(atom.getPdbxAromaticFlag()), yes(atom.getPdbxLeavingAtomFlag()),
                    modelPosition(atom), idealPosition(atom)));
        }
        return result;
    }

    private BondOrder bondOrder(ChemCompBond bond) {
        return switch (normalize(bond.getValueOrder())) {
            case "SING" -> BondOrder.SINGLE;
            case "DOUB" -> BondOrder.DOUBLE;
            case "TRIP" -> BondOrder.TRIPLE;
            case "AROM" -> BondOrder.AROMATIC;
            default -> throw new IllegalArgumentException(
                    "Unsupported CCD bond order: " + bond.getValueOrder());
        };
    }

    private Point3D modelPosition(ChemCompAtom atom) {
        return point(atom.getModelCartnX(), atom.getModelCartnY(), atom.getModelCartnZ());
    }

    private Point3D idealPosition(ChemCompAtom atom) {
        return point(atom.getPdbxModelCartnXIdeal(), atom.getPdbxModelCartnYIdeal(),
                atom.getPdbxModelCartnZIdeal());
    }

    private Point3D point(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Point3D(x, y, z) : null;
    }

    private boolean isHydrogen(String symbol) {
        return "H".equalsIgnoreCase(symbol == null ? "" : symbol.trim());
    }

    private boolean yes(String value) {
        return "Y".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
