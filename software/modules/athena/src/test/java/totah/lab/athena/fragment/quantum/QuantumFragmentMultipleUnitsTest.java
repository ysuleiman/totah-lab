package totah.lab.athena.fragment.quantum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.*;
import static org.junit.jupiter.api.Assertions.*;

class QuantumFragmentMultipleUnitsTest {
    private static QuantumEnvironment environment(int count) throws Exception {
        var source = M18Fixtures.ethane();
        var original = source.getChains().getFirst().residues().getFirst();
        var residues = new ArrayList<Residue>();
        var bonds = new ArrayList<Bond>();
        for (int i = 0; i < count; i++) {
            int number = 149 + i;
            double offset = i * 8.;
            residues.add(new Residue("ETH", number, original.getAtoms().stream()
                    .map(a -> a.toBuilder().position(new Point3D(a.getPosition().x(),
                            a.getPosition().y() + offset, a.getPosition().z())).build()).toList()));
            for (var b : source.getBonds()) bonds.add(new Bond(
                    new AtomReference("R", number, ' ', b.atom1().atomName()),
                    new AtomReference("R", number, ' ', b.atom2().atomName()), b.order()));
        }
        return M18Fixtures.environment(new Structure(List.of(new Chain("R", residues)), bonds),
                M18Fixtures.water(true).state().ligand(), false, true,
                QuantumEnvironment.InteractionClass.dispersion);
    }

    @Test void arbitraryDisconnectedUnitsKeepIndependentCapsAndSourceOrder() throws Exception {
        for (int count : List.of(3, 4, 12)) {
            var e = environment(count);
            var units = new ArrayList<ResidueId>();
            for (int i = 0; i < count; i++) units.add(new ResidueId("R", 149 + i, null));
            var f = QuantumFragmentBuilder.receptorUnits(e, units, true, "CONTROL");
            assertTrue(f.quantum().isPresent(), f.unavailableReasons().toString());
            assertEquals(count, f.caps().size());
            assertEquals(4 * count, f.retained().size());
            assertEquals(5 * count, f.quantum().orElseThrow().system().nuclei().size());
            assertEquals(count, f.caps().stream().map(PreparedQuantumFragment.CapAtom::id).distinct().count());
            assertEquals(0, f.formalCharge().orElseThrow());
            for (int i = 0; i < count; i++) {
                var single = QuantumFragmentBuilder.receptorUnits(e, List.of(units.get(i)), true, "SINGLE");
                assertEquals(single.caps().getFirst().capAngstrom(), f.caps().get(i).capAngstrom());
                assertEquals(single.retained(), f.retained().subList(i * 4, i * 4 + 4));
            }
            Collections.reverse(units);
            var reversed = QuantumFragmentBuilder.receptorUnits(e, units, true, "CONTROL");
            assertEquals(f, reversed);
        }
    }

    @Test void emptyDuplicateAndUnknownSelectionsStillFailClosed() throws Exception {
        var e = environment(4);
        var u = new ResidueId("R", 149, null);
        assertThrows(IllegalArgumentException.class, () -> QuantumFragmentBuilder.receptorUnits(e, List.of(), true, "x"));
        assertThrows(IllegalArgumentException.class, () -> QuantumFragmentBuilder.receptorUnits(e, List.of(u, u), true, "x"));
        assertThrows(IllegalArgumentException.class, () -> QuantumFragmentBuilder.receptorUnits(e,
                List.of(u, new ResidueId("R", 150, null), u), true, "x"));
        assertThrows(IllegalArgumentException.class, () -> QuantumFragmentBuilder.receptorUnits(e,
                List.of(u, new ResidueId("R", 999, null)), true, "x"));
    }
}
