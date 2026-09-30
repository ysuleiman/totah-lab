package totah.lab.aether;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;
import static org.junit.jupiter.api.Assertions.*;

public class AetherPbeGhostTest {
    @TempDir Path cache;

    @Test void electronicCounterpoisePreservesPhysicalSystem() throws Exception {
        var f = D3TestSupport.fragments("water_dimer");
        var d3 = D3Dispersion.load();
        var options = new PbeScf.Options(cache);
        var a = GhostBasis.withDonor(f.a().system(), f.b().system(), BasisFamily.DEF2_SVP);
        var b = GhostBasis.withDonor(f.b().system(), f.a().system(), BasisFamily.DEF2_SVP);
        var ra = PbeScf.solve(a, options, x -> {});
        var rb = PbeScf.solve(b, options, x -> {});
        for (var r : new PbeScf.Result[]{ra, rb}) {
            assertEquals(RhfScfResult.Status.CONVERGED, r.convergenceStatus());
            var state = r.convergedState().orElseThrow();
            var overlap = OverlapMatrix.compute(state.density().functions());
            double trace = 0;
            for (int i = 0; i < overlap.size(); i++)
                for (int j = 0; j < overlap.size(); j++) trace += state.density().get(i,j) * overlap.get(j,i);
            assertEquals(10, trace, 1e-10);
        }
        assertSame(f.a().system(), a.system());
        assertEquals(3, a.system().nuclei().size());
        assertEquals(NuclearRepulsion.calculate(f.a().system()).hartree(), ra.convergedState().orElseThrow().nuclearHartree());
        assertEquals(NuclearRepulsion.calculate(f.b().system()).hartree(), rb.convergedState().orElseThrow().nuclearHartree());
        var ea = PbeD3Energy.combine(f.a().system(), ra, d3.calculate(f.a().system()));
        var eb = PbeD3Energy.combine(f.b().system(), rb, d3.calculate(f.b().system()));
        assertEquals(3, ea.dispersion().pairs().size());
        var full = PbeScf.solve(f.complex(), BasisFamily.DEF2_SVP.forSystem(f.complex()), options, x -> {});
        var ab = PbeD3Energy.combine(f.complex(), full, d3.calculate(f.complex()));
        var cp = PbeD3Counterpoise.combine(f, ab, ea, eb);
        // Fresh independent PySCF values are retained in M16 proposal/reference.
        assertEquals(-0.008374410964222534, cp.pbeHartree(), 1e-9);
        assertEquals(-0.009034565246174742, cp.totalHartree(), 1e-9);
        assertEquals(ab.dispersionHartree()-ea.dispersionHartree()-eb.dispersionHartree(), cp.dispersionHartree(), 1e-16);
        assertThrows(IllegalArgumentException.class, () -> PbeD3Interaction.combine(f, ab, ea, eb));
        assertThrows(IllegalArgumentException.class, () -> PbeD3Counterpoise.combine(f, ab, eb, ea));
    }
}
