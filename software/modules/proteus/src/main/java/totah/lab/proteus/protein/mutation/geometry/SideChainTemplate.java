package totah.lab.proteus.protein.mutation.geometry;

import totah.lab.gaia.chemistry.BondOrder;

import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record SideChainTemplate(
        String residueName,
        List<InternalCoordinate> atoms,
        List<TemplateBond> bonds) {

    public SideChainTemplate {
        residueName = Objects.requireNonNull(residueName, "residueName")
                .trim().toUpperCase(Locale.ROOT);
        atoms = List.copyOf(Objects.requireNonNull(atoms, "atoms"));
        bonds = List.copyOf(Objects.requireNonNull(bonds, "bonds"));
    }

    public record TemplateBond(String atom1, String atom2, BondOrder order) {
        public TemplateBond(String atom1, String atom2) {
            this(atom1, atom2, BondOrder.SINGLE);
        }

        public TemplateBond {
            atom1 = Objects.requireNonNull(atom1, "atom1").trim();
            atom2 = Objects.requireNonNull(atom2, "atom2").trim();
            order = Objects.requireNonNull(order, "order");
            if (atom1.isEmpty() || atom2.isEmpty() || atom1.equals(atom2)) {
                throw new IllegalArgumentException("Template bond endpoints must be distinct names");
            }
        }
    }
}
