package totah.lab.ligand;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import totah.lab.protein.Atom;
import totah.lab.chemistry.AtomChemicalProperties;
import totah.lab.chemistry.BondOrder;
import totah.lab.chemistry.ChemicalBond;
import totah.lab.chemistry.MolecularGraph;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class CcdLigandGraphBuilder {

    public CcdLigandGraphResult build(Residue residue, ChemComp chemComp) {
        Objects.requireNonNull(residue, "residue is null");
        Objects.requireNonNull(chemComp, "chemComp is null");
        if (chemComp.getAtoms() == null || chemComp.getAtoms().isEmpty()) {
            throw new IllegalArgumentException("Complete CCD entry is required for " + residue.getName());
        }

        Map<String, ChemCompAtom> ccdAtoms = indexedCcdAtoms(chemComp);
        Map<String, Integer> depositedIndices = indexedDepositedAtoms(residue);
        List<String> missingHeavy = new ArrayList<>();
        List<String> missingHydrogens = new ArrayList<>();
        for (ChemCompAtom ccdAtom : ccdAtoms.values()) {
            if (!depositedIndices.containsKey(normalize(ccdAtom.getAtomId()))) {
                if (isHydrogen(ccdAtom.getTypeSymbol())) {
                    missingHydrogens.add(ccdAtom.getAtomId());
                } else {
                    missingHeavy.add(ccdAtom.getAtomId());
                }
            }
        }

        List<String> extraHeavy = new ArrayList<>();
        for (Atom atom : residue.getAtoms()) {
            if (!ccdAtoms.containsKey(normalize(atom.getName())) && !isHydrogen(atom)) {
                extraHeavy.add(atom.getName());
            }
        }

        int mappedCount = depositedIndices.size() - extraHeavy.size();
        LigandGraphValidationReport report = new LigandGraphValidationReport(
                chemComp.getId(), residue.getAtomCount(), mappedCount,
                missingHeavy, extraHeavy, missingHydrogens);
        if (!report.valid()) {
            throw new LigandGraphValidationException(report);
        }

        List<AtomChemicalProperties> properties = new ArrayList<>(residue.getAtomCount());
        List<CcdAtomCoordinates> coordinates = new ArrayList<>(residue.getAtomCount());
        for (int index = 0; index < residue.getAtomCount(); index++) {
            Atom deposited = residue.getAtoms().get(index);
            ChemCompAtom ccdAtom = ccdAtoms.get(normalize(deposited.getName()));
            if (ccdAtom == null) {
                throw new IllegalArgumentException(
                        "Deposited hydrogen is absent from CCD: " + deposited.getName());
            }
            properties.add(new AtomChemicalProperties(
                    ccdAtom.getAtomId(),
                    ccdAtom.getCharge(),
                    yes(ccdAtom.getPdbxAromaticFlag()),
                    yes(ccdAtom.getPdbxLeavingAtomFlag()),
                    index));
            coordinates.add(new CcdAtomCoordinates(
                    index,
                    modelPosition(ccdAtom),
                    idealPosition(ccdAtom)));
        }

        List<ChemicalBond> bonds = buildBonds(chemComp, depositedIndices);
        return new CcdLigandGraphResult(
                new MolecularGraph(residue.getAtoms(), bonds, properties),
                report,
                missingHydrogens(chemComp, ccdAtoms, depositedIndices),
                coordinates);
    }

    private Map<String, ChemCompAtom> indexedCcdAtoms(ChemComp chemComp) {
        Map<String, ChemCompAtom> indexed = new LinkedHashMap<>();
        for (ChemCompAtom atom : chemComp.getAtoms()) {
            String id = normalize(atom.getAtomId());
            if (id.isEmpty() || indexed.putIfAbsent(id, atom) != null) {
                throw new IllegalArgumentException(
                        "CCD contains a blank or duplicate atom identifier: " + atom.getAtomId());
            }
        }
        return indexed;
    }

    private Map<String, Integer> indexedDepositedAtoms(Residue residue) {
        Map<String, Integer> indexed = new LinkedHashMap<>();
        for (int index = 0; index < residue.getAtomCount(); index++) {
            String name = normalize(residue.getAtoms().get(index).getName());
            if (name.isEmpty() || indexed.putIfAbsent(name, index) != null) {
                throw new IllegalArgumentException(
                        "Ligand contains a blank or duplicate deposited atom name: "
                                + residue.getAtoms().get(index).getName());
            }
        }
        return indexed;
    }

    private List<ChemicalBond> buildBonds(
            ChemComp chemComp,
            Map<String, Integer> depositedIndices) {
        List<ChemicalBond> bonds = new ArrayList<>();
        Set<Long> endpoints = new HashSet<>();
        if (chemComp.getBonds() == null) {
            return bonds;
        }
        for (ChemCompBond ccdBond : chemComp.getBonds()) {
            Integer first = depositedIndices.get(normalize(ccdBond.getAtomId1()));
            Integer second = depositedIndices.get(normalize(ccdBond.getAtomId2()));
            if (first == null || second == null) {
                continue;
            }
            int low = Math.min(first, second);
            int high = Math.max(first, second);
            long key = ((long) low << 32) | (high & 0xffffffffL);
            if (!endpoints.add(key)) {
                throw new IllegalArgumentException(
                        "CCD contains duplicate bond endpoints: "
                                + ccdBond.getAtomId1() + "-" + ccdBond.getAtomId2());
            }
            boolean aromatic = yes(ccdBond.getPdbxAromaticFlag())
                    || "AROM".equals(normalize(ccdBond.getValueOrder()));
            bonds.add(new ChemicalBond(first, second, bondOrder(ccdBond), aromatic));
        }
        return bonds;
    }

    private List<MissingLigandHydrogen> missingHydrogens(
            ChemComp chemComp,
            Map<String, ChemCompAtom> ccdAtoms,
            Map<String, Integer> depositedIndices) {
        List<MissingLigandHydrogen> missing = new ArrayList<>();
        for (ChemCompAtom atom : ccdAtoms.values()) {
            String atomId = normalize(atom.getAtomId());
            if (!isHydrogen(atom.getTypeSymbol()) || depositedIndices.containsKey(atomId)) {
                continue;
            }
            List<ChemCompBond> hydrogenBonds = chemComp.getBonds() == null
                    ? List.of()
                    : chemComp.getBonds().stream()
                    .filter(bond -> atomId.equals(normalize(bond.getAtomId1()))
                            || atomId.equals(normalize(bond.getAtomId2())))
                    .toList();
            if (hydrogenBonds.size() != 1) {
                throw new IllegalArgumentException(
                        "CCD hydrogen " + atom.getAtomId()
                                + " must have exactly one bond, found " + hydrogenBonds.size());
            }
            ChemCompBond bond = hydrogenBonds.getFirst();
            String parentId = atomId.equals(normalize(bond.getAtomId1()))
                    ? normalize(bond.getAtomId2())
                    : normalize(bond.getAtomId1());
            Integer parentIndex = depositedIndices.get(parentId);
            if (parentIndex == null) {
                throw new IllegalArgumentException(
                        "CCD hydrogen " + atom.getAtomId()
                                + " is bonded to missing atom " + parentId);
            }
            boolean aromatic = yes(bond.getPdbxAromaticFlag())
                    || "AROM".equals(normalize(bond.getValueOrder()));
            missing.add(new MissingLigandHydrogen(
                    atom.getAtomId(),
                    parentIndex,
                    bondOrder(bond),
                    atom.getCharge(),
                    yes(atom.getPdbxAromaticFlag()),
                    yes(atom.getPdbxLeavingAtomFlag()),
                    modelPosition(atom),
                    idealPosition(atom)));
        }
        return missing;
    }

    private BondOrder bondOrder(ChemCompBond bond) {
        return switch (normalize(bond.getValueOrder())) {
            case "SING" -> BondOrder.SINGLE;
            case "DOUB" -> BondOrder.DOUBLE;
            case "TRIP" -> BondOrder.TRIPLE;
            case "AROM" -> BondOrder.AROMATIC;
            default -> throw new IllegalArgumentException(
                    "Unsupported CCD bond order '" + bond.getValueOrder() + "' for "
                            + bond.getAtomId1() + "-" + bond.getAtomId2());
        };
    }

    private boolean isHydrogen(Atom atom) {
        return atom.getElement() != null
                && "H".equalsIgnoreCase(atom.getElement().getSymbol());
    }

    private boolean isHydrogen(String element) {
        return "H".equalsIgnoreCase(element == null ? "" : element.trim());
    }

    private boolean yes(String flag) {
        return "Y".equalsIgnoreCase(flag == null ? "" : flag.trim());
    }

    private Point3D modelPosition(ChemCompAtom atom) {
        return point(atom.getModelCartnX(), atom.getModelCartnY(), atom.getModelCartnZ());
    }

    private Point3D idealPosition(ChemCompAtom atom) {
        return point(
                atom.getPdbxModelCartnXIdeal(),
                atom.getPdbxModelCartnYIdeal(),
                atom.getPdbxModelCartnZIdeal());
    }

    private Point3D point(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Point3D(x, y, z)
                : null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
