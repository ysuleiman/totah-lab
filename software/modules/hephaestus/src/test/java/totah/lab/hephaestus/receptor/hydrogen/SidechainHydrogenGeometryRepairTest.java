package totah.lab.hephaestus.receptor.hydrogen;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.gaia.chemistry.*;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.*;
import static org.junit.jupiter.api.Assertions.*;

class SidechainHydrogenGeometryRepairTest {
    record Fixture(Residue residue, List<Bond> bonds) {}
    private Fixture fixture(String id) throws Exception {
        var atoms = new ArrayList<Atom>();
        var bonds = new ArrayList<Bond>();
        int number = Integer.parseInt(id.substring(1));
        try (var in = getClass().getResourceAsStream("/hydrogen-geometry-v2/" + id + ".csv");
             var reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line : reader.lines().toList()) {
                var s = line.split(",");
                if (s[0].equals("ATOM")) atoms.add(Atom.builder().name(s[1]).element(Element.valueOf(s[2]))
                        .position(new Point3D(Double.parseDouble(s[3]), Double.parseDouble(s[4]), Double.parseDouble(s[5]))).build());
                if (s[0].equals("BOND")) bonds.add(new Bond(new AtomReference("A", number, ' ', s[1]),
                        new AtomReference("A", number, ' ', s[2]), s[3].equals("1") ? BondOrder.SINGLE : BondOrder.DOUBLE));
            }
        }
        return new Fixture(new Residue(id.equals("B43") ? "LEU" : "SER", number, atoms), bonds);
    }

    @Test void repairsMethyleneAndMethineWithoutMovingOtherAtomsAndIsIdempotent() throws Exception {
        for (String id : List.of("B43", "B47")) {
            var f = fixture(id);
            var r = SidechainHydrogenGeometryRepair.repair("A", f.residue(), f.bonds());
            assertEquals(id.equals("B43") ? java.util.Set.of("HG") : java.util.Set.of("HB2", "HB3"), r.reconstructedHydrogens());
            assertTrue(r.after().stream().allMatch(SidechainHydrogenGeometryRepair.Check::valid));
            for (int i = 0; i < f.residue().getAtoms().size(); i++) {
                var old = f.residue().getAtoms().get(i);
                var now = r.residue().getAtoms().get(i);
                if (!r.reconstructedHydrogens().contains(old.getName())) assertSame(old, now);
                else assertTrue(old.isHydrogen());
            }
            var again = SidechainHydrogenGeometryRepair.repair("A", r.residue(), f.bonds());
            assertTrue(again.reconstructedHydrogens().isEmpty());
            assertSame(r.residue(), again.residue());
        }
    }

    @Test void missingOrDuplicateTopologyFailsClosed() throws Exception {
        var f = fixture("B47");
        assertThrows(IllegalArgumentException.class, () -> SidechainHydrogenGeometryRepair.repair("A", f.residue(), List.of()));
        var duplicate = new ArrayList<>(f.bonds()); duplicate.add(f.bonds().getFirst());
        assertThrows(IllegalArgumentException.class, () -> SidechainHydrogenGeometryRepair.repair("A", f.residue(), duplicate));
    }
}
