package totah.lab.hephaestus.mutation.geometry;

import totah.lab.gaia.chemistry.BondOrder;
import totah.lab.gaia.chemistry.Element;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class SideChainTemplateLibrary {
    private static final double TETRAHEDRAL = Math.toRadians(109.5);
    private final Map<String, SideChainTemplate> templates;

    public SideChainTemplateLibrary() {
        templates = Map.of(
                "ALA", template("ALA", List.of(cb()), List.of(b("CA", "CB"))),
                "CYS", template("CYS", List.of(cb(), ic("SG", Element.S, "CB", "CA", "N", 1.81, 114.0, 180.0, true)),
                        List.of(b("CA", "CB"), b("CB", "SG"))),
                "ASN", template("ASN", List.of(cb(),
                                ic("CG", Element.C, "CB", "CA", "N", 1.52, 113.0, 180.0, true),
                                ic("OD1", Element.O, "CG", "CB", "CA", 1.23, 120.0, 0.0, false),
                                ic("ND2", Element.N, "CG", "CB", "CA", 1.33, 116.0, 180.0, false)),
                        List.of(b("CA", "CB"), b("CB", "CG"),
                                b("CG", "OD1", BondOrder.DOUBLE), b("CG", "ND2"))),
                "LYS", linear("LYS", List.of("CG", "CD", "CE", "NZ"),
                        List.of(Element.C, Element.C, Element.C, Element.N),
                        List.of(1.52, 1.52, 1.52, 1.49)),
                "PHE", aromatic("PHE", false),
                "TYR", aromatic("TYR", true));
    }

    public Optional<SideChainTemplate> find(String residueName) {
        if (residueName == null) return Optional.empty();
        return Optional.ofNullable(templates.get(residueName.trim().toUpperCase(Locale.ROOT)));
    }

    private static SideChainTemplate linear(
            String name, List<String> names, List<Element> elements, List<Double> lengths) {
        var atoms = new java.util.ArrayList<InternalCoordinate>();
        var bonds = new java.util.ArrayList<SideChainTemplate.TemplateBond>();
        atoms.add(cb());
        bonds.add(b("CA", "CB"));
        String previous = "CB";
        String angle = "CA";
        String dihedral = "N";
        for (int index = 0; index < names.size(); index++) {
            String atom = names.get(index);
            atoms.add(ic(atom, elements.get(index), previous, angle, dihedral,
                    lengths.get(index), 112.0, 180.0, index == 0));
            bonds.add(b(previous, atom));
            dihedral = angle;
            angle = previous;
            previous = atom;
        }
        return template(name, atoms, bonds);
    }

    private static SideChainTemplate aromatic(String name, boolean hydroxyl) {
        var atoms = new java.util.ArrayList<InternalCoordinate>(List.of(
                cb(),
                ic("CG", Element.C, "CB", "CA", "N", 1.50, 113.0, 180.0, true),
                ic("CD1", Element.C, "CG", "CB", "CA", 1.39, 120.0, 0.0, false),
                ic("CD2", Element.C, "CG", "CB", "CA", 1.39, 120.0, 180.0, false),
                ic("CE1", Element.C, "CD1", "CG", "CB", 1.39, 120.0, 180.0, false),
                ic("CE2", Element.C, "CD2", "CG", "CB", 1.39, 120.0, 180.0, false),
                ic("CZ", Element.C, "CE1", "CD1", "CG", 1.39, 120.0, 0.0, false)));
        var bonds = new java.util.ArrayList<>(List.of(
                b("CA", "CB"), b("CB", "CG"),
                b("CG", "CD1", BondOrder.AROMATIC), b("CG", "CD2", BondOrder.AROMATIC),
                b("CD1", "CE1", BondOrder.AROMATIC), b("CD2", "CE2", BondOrder.AROMATIC),
                b("CE1", "CZ", BondOrder.AROMATIC), b("CE2", "CZ", BondOrder.AROMATIC)));
        if (hydroxyl) {
            atoms.add(ic("OH", Element.O, "CZ", "CE1", "CD1", 1.36, 120.0, 180.0, false));
            bonds.add(b("CZ", "OH"));
        }
        return template(name, atoms, bonds);
    }

    private static InternalCoordinate cb() {
        return new InternalCoordinate("CB", Element.C, "CA", "N", "C",
                1.53, TETRAHEDRAL, Math.toRadians(122.5), false);
    }

    private static InternalCoordinate ic(
            String name, Element element, String bond, String angle, String dihedral,
            double length, double angleDegrees, double dihedralDegrees, boolean firstChi) {
        return new InternalCoordinate(name, element, bond, angle, dihedral,
                length, Math.toRadians(angleDegrees), Math.toRadians(dihedralDegrees), firstChi);
    }

    private static SideChainTemplate.TemplateBond b(String atom1, String atom2) {
        return new SideChainTemplate.TemplateBond(atom1, atom2);
    }

    private static SideChainTemplate.TemplateBond b(
            String atom1, String atom2, BondOrder order) {
        return new SideChainTemplate.TemplateBond(atom1, atom2, order);
    }

    private static SideChainTemplate template(
            String name, List<InternalCoordinate> atoms,
            List<SideChainTemplate.TemplateBond> bonds) {
        return new SideChainTemplate(name, atoms, bonds);
    }
}
