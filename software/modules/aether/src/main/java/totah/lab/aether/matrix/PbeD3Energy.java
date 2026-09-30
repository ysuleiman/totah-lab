package totah.lab.aether.matrix;

import java.util.Objects;
import java.util.OptionalDouble;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Adds geometry-only dispersion to a converged PBE result, without changing its state. */
public final class PbeD3Energy {
    private final PbeScf.Result pbe;
    private final D3Dispersion.Result dispersion;
    private final String receiptHash;
    private PbeD3Energy(PbeScf.Result pbe,D3Dispersion.Result dispersion) {
        this.pbe=pbe;this.dispersion=dispersion;
        receiptHash=ContentHash.accumulator().line("aether-PBE-D3-energy-16-1;E=PBE+D3;SCREENING_ONLY")
                .line(pbe.receiptHash()).line(dispersion.receiptHash()).line(ContentHash.number(totalHartree())).finish();
    }
    public static PbeD3Energy combine(QuantumSystem system,PbeScf.Result pbe,D3Dispersion.Result dispersion) {
        Objects.requireNonNull(system);Objects.requireNonNull(pbe);Objects.requireNonNull(dispersion);
        var state=pbe.convergedState().orElseThrow(()->new IllegalArgumentException("PBE must be converged"));
        String expected=IntegralMatrixData.systemHash(system);
        OccupiedDensityCalculator.requireEqual(expected,state.density().systemHash(),"PBE-D3 electronic system");
        OccupiedDensityCalculator.requireEqual(expected,dispersion.systemHash(),"PBE-D3 nuclear system");
        if(!Double.isFinite(state.totalHartree()+dispersion.totalHartree()))throw new ArithmeticException("Nonfinite PBE-D3 energy");
        return new PbeD3Energy(pbe,dispersion);
    }
    public double pbeHartree(){return pbe.convergedState().orElseThrow().totalHartree();}
    public double pairwiseHartree(){return dispersion.pairwiseHartree();}
    public OptionalDouble threeBodyHartree(){return dispersion.threeBodyHartree();}
    public double dispersionHartree(){return dispersion.totalHartree();}
    public double totalHartree(){return pbeHartree()+dispersionHartree();}
    public PbeScf.Result pbe(){return pbe;} public D3Dispersion.Result dispersion(){return dispersion;}
    public String receiptHash(){return receiptHash;}
    public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
