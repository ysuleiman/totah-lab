package totah.lab.hermes.ccd;

import org.biojava.nbio.structure.chem.ChemComp;
import org.biojava.nbio.structure.chem.ChemCompAtom;
import org.biojava.nbio.structure.chem.ChemCompBond;
import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

/** Isolates BioJava CCD types at the Hermes source boundary. */
public final class BioJavaCcdComponentMapper {

    public CcdComponent map(ChemComp chemComp) {
        Objects.requireNonNull(chemComp, "chemComp");
        if (chemComp.getAtoms() == null || chemComp.getAtoms().isEmpty()) {
            throw new IllegalArgumentException(
                    "Complete CCD atom definitions are required for " + chemComp.getId());
        }
        List<CcdComponentAtom> atoms = chemComp.getAtoms().stream()
                .map(this::mapAtom)
                .toList();
        List<CcdComponentBond> bonds = chemComp.getBonds() == null ? List.of()
                : chemComp.getBonds().stream().map(this::mapBond).toList();
        return new CcdComponent(chemComp.getId(), atoms, bonds);
    }

    private CcdComponentAtom mapAtom(ChemCompAtom atom) {
        return new CcdComponentAtom(
                atom.getAtomId(), atom.getTypeSymbol(), atom.getCharge(),
                yes(atom.getPdbxAromaticFlag()), yes(atom.getPdbxLeavingAtomFlag()),
                point(atom.getModelCartnX(), atom.getModelCartnY(), atom.getModelCartnZ()),
                point(atom.getPdbxModelCartnXIdeal(), atom.getPdbxModelCartnYIdeal(),
                        atom.getPdbxModelCartnZIdeal()));
    }

    private CcdComponentBond mapBond(ChemCompBond bond) {
        BondOrder order = switch (normalize(bond.getValueOrder())) {
            case "SING" -> BondOrder.SINGLE;
            case "DOUB" -> BondOrder.DOUBLE;
            case "TRIP" -> BondOrder.TRIPLE;
            case "AROM" -> BondOrder.AROMATIC;
            default -> throw new IllegalArgumentException(
                    "Unsupported CCD bond order: " + bond.getValueOrder());
        };
        return new CcdComponentBond(
                bond.getAtomId1(), bond.getAtomId2(), order,
                yes(bond.getPdbxAromaticFlag()) || order == BondOrder.AROMATIC);
    }

    private Point3D point(double x, double y, double z) {
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                ? new Point3D(x, y, z) : null;
    }

    private boolean yes(String value) {
        return "Y".equalsIgnoreCase(value == null ? "" : value.trim());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
