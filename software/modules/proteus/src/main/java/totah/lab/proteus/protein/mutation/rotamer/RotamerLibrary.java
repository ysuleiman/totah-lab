package totah.lab.proteus.protein.mutation.rotamer;

import java.util.List;

/**
 * Stub rotamer library: three fixed chi1 candidates for side chains with
 * torsional freedom, single trivial rotamers for ALA/GLY. A richer,
 * backbone-dependent library is a documented later phase.
 */
public final class RotamerLibrary {
    private static final List<Rotamer> STANDARD = List.of(
            new Rotamer("gauche-plus", List.of(Math.toRadians(60.0)), 0.34),
            new Rotamer("trans", List.of(Math.toRadians(180.0)), 0.33),
            new Rotamer("gauche-minus", List.of(Math.toRadians(-60.0)), 0.33));

    public List<Rotamer> rotamers(String residueName, BackboneConformation backbone) {
        if ("ALA".equals(residueName)) {
            return List.of(new Rotamer("alanine", List.of(0.0), 1.0));
        }
        if ("GLY".equals(residueName)) {
            return List.of(new Rotamer("glycine", List.of(0.0), 1.0));
        }
        return STANDARD;
    }
}
