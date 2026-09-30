package totah.lab.aether.matrix;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.*;
import totah.lab.gaia.geometry.Point3D;

class M17FixtureTest {
    @ParameterizedTest
    @ValueSource(strings={"nenci_001","nenci_003","nenci_024","nenci_047","nenci_030","nenci_054",
            "nenci_067","nenci_096","nenci_080","nenci_087","nenci_092","nenci_095",
            "des_ammonium_benzene","des_methylammonium_benzene","des_methanethiol_benzene",
            "des_h2s_benzene","des_methane_benzene"})
    void fragmentsAreClosedShellAndGhostsRetainPhysicalIdentity(String name) throws Exception {
        String text;
        try(var input=getClass().getResourceAsStream("/totah/lab/aether/reference/m17/"+name+".atoms")) {
            text=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8);
        }
        var lines=text.lines().toList();var h=lines.getFirst().split(",");int cut=Integer.parseInt(h[0]);
        var nuclei=new ArrayList<NuclearCenter>();
        for(var line:lines.subList(1,lines.size())) {
            var c=line.split(",");nuclei.add(new NuclearCenter(new Point3D(Double.parseDouble(c[1]),
                    Double.parseDouble(c[2]),Double.parseDouble(c[3])),Double.parseDouble(c[0])));
        }
        var a=new QuantumSystem(nuclei.subList(0,cut),Integer.parseInt(h[1]),1);
        var b=new QuantumSystem(nuclei.subList(cut,nuclei.size()),Integer.parseInt(h[2]),1);
        var full=BasisFamily.DEF2_SVP.forSystem(new FragmentPair(new MolecularFragment("A",a),new MolecularFragment("B",b)).complex());
        for(var pair:List.of(List.of(a,b),List.of(b,a))) {
            var real=pair.getFirst();var ghost=GhostBasis.withDonor(real,pair.get(1),BasisFamily.DEF2_SVP);
            assertSame(real,ghost.system());assertEquals(full.size(),ghost.functions().size());
            assertDoesNotThrow(()->SemiDirectScf.requireSameFunctions(ghost.functions(),full));
            assertTrue(Double.isFinite(NuclearRepulsion.calculate(real).hartree()));
            var occupation=OccupiedDensityCalculator.occupation(real,ghost.functions().size());
            assertNotNull(occupation);
        }
    }
}
