package totah.lab.aether.basis;

import java.io.IOException;
import java.util.List;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.gaia.geometry.Point3D;

/** Explicit supported basis selection for native and ghost-center calculations. */
public enum BasisFamily {
    STO_3G, DEF2_SVP;
    public List<ContractedGaussian> forSystem(QuantumSystem system) throws IOException {
        return this==STO_3G?Sto3gBasis.load().forSystem(system):Def2SvpBasis.load().forSystem(system);
    }
    public List<ContractedGaussian> atBohr(int atomicNumber,Point3D center) throws IOException {
        return this==STO_3G?Sto3gBasis.load().atBohr(atomicNumber,center):Def2SvpBasis.load().atBohr(atomicNumber,center);
    }
}
