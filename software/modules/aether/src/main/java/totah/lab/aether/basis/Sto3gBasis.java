package totah.lab.aether.basis;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.gaia.geometry.Point3D;

/** Pinned PySCF 2.10.0 STO-3G H/C/N/O/P/S/Cl data, preserving nuclear and upstream shell order. */
public final class Sto3gBasis {
    public static final String CNO_RESOURCE_SHA256 = "e7de8394ca8ff2fba0ee8750a890bfff98147d23faae1450261494c5f7ea1546";
    public static final String SPCL_RESOURCE_SHA256 = "db8e15f19d4b5a859b52dcc9c4b62392f510e10df7f396d8c7ab97df27b29586";
    private final Sto3gHydrogen hydrogen;
    private final List<PinnedBasisData.Row> rows;
    private Sto3gBasis(Sto3gHydrogen hydrogen,List<PinnedBasisData.Row> rows) { this.hydrogen=hydrogen;this.rows=List.copyOf(rows); }
    public static Sto3gBasis load() throws IOException {
        var rows = new ArrayList<PinnedBasisData.Row>();
        rows.addAll(PinnedBasisData.loadRows("sto-3g-cno.csv", CNO_RESOURCE_SHA256));
        rows.addAll(PinnedBasisData.loadRows("sto-3g-spcl.csv", SPCL_RESOURCE_SHA256));
        return new Sto3gBasis(Sto3gHydrogen.load(), rows);
    }

    public List<ContractedGaussian> atBohr(int atomicNumber,Point3D center) {
        if(atomicNumber==1) return List.of(hydrogen.atBohr(center));
        return PinnedBasisData.atBohr(rows,atomicNumber,center);
    }
    public List<ContractedGaussian> forSystem(QuantumSystem system) {
        var functions=new ArrayList<ContractedGaussian>();
        for(var nucleus:system.nuclei()) functions.addAll(atBohr((int)nucleus.charge(),nucleus.centerBohr()));
        return List.copyOf(functions);
    }
}
