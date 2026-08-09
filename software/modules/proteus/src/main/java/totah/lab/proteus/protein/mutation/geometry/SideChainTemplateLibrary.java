package totah.lab.proteus.protein.mutation.geometry;

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
        templates = Map.ofEntries(
                Map.entry("GLY", template("GLY", List.of(), List.of())),
                Map.entry("ALA", template("ALA", List.of(cb()), List.of(b("CA", "CB")))),
                Map.entry("CYS", template("CYS", List.of(cb(), ic("SG", Element.S, "CB", "CA", "N", 1.81, 114.0, 180.0, true)),
                        List.of(b("CA", "CB"), b("CB", "SG")))),
                Map.entry("SER", template("SER", List.of(cb(), ic("OG", Element.O, "CB", "CA", "N", 1.41, 109.5, 180.0, true)),
                        List.of(b("CA", "CB"), b("CB", "OG")))),
                Map.entry("ASN", template("ASN", List.of(cb(),
                                ic("CG", Element.C, "CB", "CA", "N", 1.52, 113.0, 180.0, true),
                                ic("OD1", Element.O, "CG", "CB", "CA", 1.23, 120.0, 0.0, false),
                                ic("ND2", Element.N, "CG", "CB", "CA", 1.33, 116.0, 180.0, false)),
                        List.of(b("CA", "CB"), b("CB", "CG"),
                                b("CG", "OD1", BondOrder.DOUBLE), b("CG", "ND2")))),
                Map.entry("GLN", template("GLN", List.of(cb(),
                                ic("CG", Element.C, "CB", "CA", "N", 1.52, 113.0, 180.0, true),
                                ic("CD", Element.C, "CG", "CB", "CA", 1.52, 112.0, 180.0, false),
                                ic("OE1", Element.O, "CD", "CG", "CB", 1.23, 120.0, 0.0, false),
                                ic("NE2", Element.N, "CD", "CG", "CB", 1.33, 116.0, 180.0, false)),
                        List.of(b("CA", "CB"), b("CB", "CG"), b("CG", "CD"),
                                b("CD", "OE1", BondOrder.DOUBLE), b("CD", "NE2")))),
                Map.entry("PRO", template("PRO", List.of(cb(),
                                ic("CG", Element.C, "CB", "CA", "N", 1.52, 104.0, 30.0, true),
                                ic("CD", Element.C, "CG", "CB", "CA", 1.47, 103.0, -30.0, false)),
                        List.of(b("CA", "CB"), b("CB", "CG"), b("CG", "CD"), b("CD", "N")))),
                Map.entry("LYS", linear("LYS", List.of("CG", "CD", "CE", "NZ"),
                        List.of(Element.C, Element.C, Element.C, Element.N),
                        List.of(1.52, 1.52, 1.52, 1.49))),
                Map.entry("LEU", template("LEU", List.of(cb(),
                                ic("CG", Element.C, "CB", "CA", "N", 1.53, 113.0, 180.0, true),
                                ic("CD1", Element.C, "CG", "CB", "CA", 1.52, 110.0, 60.0, false),
                                ic("CD2", Element.C, "CG", "CB", "CA", 1.52, 110.0, -60.0, false)),
                        List.of(b("CA", "CB"), b("CB", "CG"),
                                b("CG", "CD1"), b("CG", "CD2")))),
                Map.entry("VAL", template("VAL", List.of(cb(),
                                ic("CG1", Element.C, "CB", "CA", "N", 1.52, 110.5, 180.0, true),
                                ic("CG2", Element.C, "CB", "CG1", "CA", 1.52, 110.5, 180.0, false)),
                        List.of(b("CA", "CB"),
                                b("CB", "CG1"), b("CB", "CG2")))),
                Map.entry("MET", linear("MET", List.of("CG", "SD", "CE"),
                        List.of(Element.C, Element.S, Element.C),
                        List.of(1.52, 1.81, 1.78))),
                Map.entry("PHE", aromatic("PHE", false)),
                Map.entry("TYR", aromatic("TYR", true)),
                Map.entry("TRP", template("TRP", List.of(cb(),
                                ic("CG", Element.C, "CB", "CA", "N", 1.50, 113.0, 180.0, true),
                                ic("CD1", Element.C, "CG", "CB", "CA", 1.37, 126.0, 0.0, false),
                                ic("CD2", Element.C, "CG", "CB", "CA", 1.43, 126.0, 180.0, false),
                                ic("NE1", Element.N, "CD1", "CG", "CB", 1.38, 110.0, 180.0, false),
                                ic("CE2", Element.C, "NE1", "CD1", "CG", 1.38, 108.0, 0.0, false),
                                ic("CE3", Element.C, "CD2", "CG", "CB", 1.40, 120.0, 180.0, false),
                                ic("CZ3", Element.C, "CE3", "CD2", "CG", 1.39, 120.0, 180.0, false),
                                ic("CH2", Element.C, "CZ3", "CE3", "CD2", 1.39, 120.0, 180.0, false),
                                ic("CZ2", Element.C, "CH2", "CZ3", "CE3", 1.39, 120.0, 180.0, false)),
                        List.of(b("CA", "CB"), b("CB", "CG"),
                                b("CG", "CD1", BondOrder.AROMATIC),
                                b("CG", "CD2", BondOrder.AROMATIC),
                                b("CD1", "NE1", BondOrder.AROMATIC),
                                b("NE1", "CE2", BondOrder.AROMATIC),
                                b("CD2", "CE2", BondOrder.AROMATIC),
                                b("CD2", "CE3", BondOrder.AROMATIC),
                                b("CE3", "CZ3", BondOrder.AROMATIC),
                                b("CZ3", "CH2", BondOrder.AROMATIC),
                                b("CH2", "CZ2", BondOrder.AROMATIC),
                                b("CZ2", "CE2", BondOrder.AROMATIC)))));
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
