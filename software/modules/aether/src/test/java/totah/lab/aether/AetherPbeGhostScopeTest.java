package totah.lab.aether;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.matrix.*;
import totah.lab.aether.model.QuantumSystem;
import static org.junit.jupiter.api.Assertions.*;

public class AetherPbeGhostScopeTest {
    @TempDir Path cache;

    @Test void chargedMonomerOccupationIgnoresDonorCharge() throws Exception {
        var f = D3TestSupport.fragments("ammonium_benzene");
        var real = f.a().system();
        var donor = f.b().system();
        var chargedDonor = new QuantumSystem(donor.nuclei(), donor.molecularCharge()+2, 1);
        var first = GhostBasis.withDonor(real, donor, BasisFamily.DEF2_SVP);
        var second = GhostBasis.withDonor(real, chargedDonor, BasisFamily.DEF2_SVP);
        assertEquals(1, real.molecularCharge());
        assertEquals(first.identity(), second.identity());
        assertSame(real, first.system());
        var occupation = OccupiedDensityCalculator.occupation(first.system(), first.functions().size());
        assertEquals(10, occupation.electrons());
        assertEquals(5, occupation.occupiedOrbitals());
        assertEquals(NuclearRepulsion.calculate(real).hartree(), NuclearRepulsion.calculate(first.system()).hartree());
    }

    @Test void ghostOverloadRejectsOpenShellBeforeIntegralGeneration() throws Exception {
        var f = D3TestSupport.fragments("water_dimer");
        var open = new QuantumSystem(f.a().system().nuclei(), 1, 2);
        var ghost = GhostBasis.withDonor(open, f.b().system(), BasisFamily.DEF2_SVP);
        assertThrows(IllegalArgumentException.class, () -> PbeScf.solve(ghost, new PbeScf.Options(cache), x -> fail("SCF must not start")));
    }

    @Test void existingEntryPointStillRequiresAnExplicitGhostContext() throws Exception {
        var f = D3TestSupport.fragments("water_dimer");
        var ghost = GhostBasis.withDonor(f.a().system(), f.b().system(), BasisFamily.DEF2_SVP);
        assertThrows(IllegalArgumentException.class, () -> PbeScf.solve(ghost.system(), ghost.functions(),
                new PbeScf.Options(cache), x -> fail("Existing entry point must reject ghost AOs")));
    }
}
