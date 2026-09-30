package totah.lab.aether.basis;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;

/** Pinned def2-SVP, H/C/N/O/P/S/Cl, normalized Cartesian s/p/d; source metadata accompanies the resource. */
public final class Def2SvpBasis {
    public static final String RESOURCE_SHA256 = "acfec282a0bf3e9160ffae2cda548d87b7474d72f48c8de2939a98db15f2d27a";
    private final List<PinnedBasisData.Row> rows;
    private Def2SvpBasis(List<PinnedBasisData.Row> rows) { this.rows=List.copyOf(rows); }
    public static Def2SvpBasis load() throws IOException {
        return new Def2SvpBasis(PinnedBasisData.loadRows("def2-svp.csv",RESOURCE_SHA256));
    }
    public List<ContractedGaussian> atBohr(int atomicNumber,Point3D center) {
        return PinnedBasisData.atBohr(rows,atomicNumber,center);
    }
    public List<ContractedGaussian> forSystem(QuantumSystem system) {
        var functions=new ArrayList<ContractedGaussian>();
        for(var nucleus:system.nuclei()) functions.addAll(atBohr((int)nucleus.charge(),nucleus.centerBohr()));
        return List.copyOf(functions);
    }
}
