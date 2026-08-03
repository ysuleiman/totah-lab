package totah.lab.hephaestus.mutation.rotamer;

import java.util.List;

public final class RotamerLibrary {
    private static final List<Rotamer> STANDARD = List.of(
            new Rotamer("gauche-plus", List.of(Math.toRadians(60.0)), 0.34),
            new Rotamer("trans", List.of(Math.toRadians(180.0)), 0.33),
            new Rotamer("gauche-minus", List.of(Math.toRadians(-60.0)), 0.33));

    public List<Rotamer> rotamers(String residueName, BackboneConformation backbone) {
        return "ALA".equals(residueName)
                ? List.of(new Rotamer("alanine", List.of(0.0), 1.0))
                : STANDARD;
    }
}
