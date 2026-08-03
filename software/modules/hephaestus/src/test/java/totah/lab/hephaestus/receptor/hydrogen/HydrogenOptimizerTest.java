package totah.lab.hephaestus.receptor.hydrogen;

import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Chain;
import totah.lab.gaia.structure.Residue;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.amber.AmberParameterSet;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Within one residue, a hydrogen moved earlier in the optimization pass must
 * be seen at its updated position when later sibling hydrogens are scored.
 * This fixture makes the first hydrogen rotate into the immediate vicinity
 * of the second hydrogen's best stale-scored candidate; scoring against the
 * stale position would leave the two hydrogens clashing.
 */
class HydrogenOptimizerTest {

    private static final double CLASH_CUTOFF = 0.9;

    private final HydrogenOptimizer optimizer = new HydrogenOptimizer(
            name -> null,
            AmberParameterSet.createEmpty(),
            CLASH_CUTOFF);

    @Test
    void siblingHydrogenIsScoredAgainstUpdatedPositions() {
        Atom anchor = heavy("CB", 0.0, 0.0, -1.5);
        Atom parent = heavy("OG", 0.0, 0.0, 0.0);
        Atom first = hydrogen("HG1", 1.0, 0.0, 0.0);
        Atom second = hydrogen("HG2", -1.05, 0.0, 0.0);
        Residue target = new Residue(
                "SER", 1, List.of(anchor, parent, first, second));

        // Blockers in a separate environment residue steer the rotatable
        // hydrogens: the first must rotate away, the second must not rotate
        // into the first hydrogen's new position.
        Residue blockers = new Residue("ENV", 2, List.of(
                heavy("C1", 1.6, 0.0, 0.5),
                heavy("C2", -1.75, 0.0, 0.45),
                heavy("C3", -0.84, -1.454, 0.5),
                heavy("C4", 0.84, -1.454, 0.5)));

        Structure environment = new Structure(List.of(
                new Chain("A", List.of(target, blockers))));

        List<Atom> optimized = optimizer.optimize(
                "A", target, environment, null, Map.of());

        Atom optimizedFirst = optimized.get(2);
        Atom optimizedSecond = optimized.get(3);
        double siblingDistance = optimizedFirst.getPosition()
                .distance(optimizedSecond.getPosition());

        assertTrue(siblingDistance > CLASH_CUTOFF,
                "moved sibling hydrogens must not clash; distance is "
                        + siblingDistance);
    }

    private Atom heavy(String name, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .element(Element.C)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .build();
    }

    private Atom hydrogen(String name, double x, double y, double z) {
        return Atom.builder()
                .name(name)
                .element(Element.H)
                .position(new Point3D(x, y, z))
                .charge(0.0)
                .occupancy(1.0)
                .build();
    }
}
