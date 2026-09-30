package totah.lab.aether.matrix;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import totah.lab.aether.basis.*;
import totah.lab.gaia.geometry.Point3D;

/** Basis-family recognition uses pinned radial/angular definitions, never dimensions or element guesses. */
final class BasisScope {
    private BasisScope() {}
    private static String signature(ContractedGaussian f) {
        var s=new StringBuilder(f.angularMomentum().name());
        for(var t:f.terms())s.append('|').append(Double.toHexString(t.primitive().exponent())).append(':').append(Double.toHexString(t.coefficient()));
        return s.toString();
    }
    private static final class Definitions {
        static final Set<String> DEF2=load();
        static Set<String> load() {
            try {
                var result=new HashSet<String>();var basis=Def2SvpBasis.load();
                for(int z:new int[]{1,6,7,8,15,16,17})for(var f:basis.atBohr(z,new Point3D(0,0,0)))result.add(signature(f));
                return Set.copyOf(result);
            }catch(IOException e){throw new UncheckedIOException(e);}
        }
    }
    static boolean def2(List<ContractedGaussian> basis) {
        return !basis.isEmpty()&&basis.stream().allMatch(f->Definitions.DEF2.contains(signature(f)));
    }
    static String protocol(String legacy,List<ContractedGaussian> basis) {
        if(!def2(basis))return legacy;
        return legacy.replaceAll("STO-3G(?:-H(?:-C-N-O(?:-P-S-Cl)?)?)?", "def2-SVP-H-C-N-O-P-S-Cl")
                .replace("s-only","s/p/d-Cartesian").replace("s/p-Cartesian","s/p/d-Cartesian").replace(";s/p;",";s/p/d-Cartesian;")
                .replace("Boys0-4=","Boys0-8=").replace("no-d-f-AOs","no-f-AOs")
                +";def2-SVP-sha256="+Def2SvpBasis.RESOURCE_SHA256+";unit-normalized-Cartesian-AOs";
    }
}
