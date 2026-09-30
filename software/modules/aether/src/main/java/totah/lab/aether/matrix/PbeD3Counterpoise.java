package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.Objects;
import totah.lab.aether.basis.BasisFamily;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Electronic Boys–Bernardi CP plus the unmodified physical-geometry D3 difference. */
public final class PbeD3Counterpoise {
    public static final String CONVENTION = "PBE_AB-PBE_A_WITH_GHOST_B-PBE_B_WITH_GHOST_A+D3_AB-D3_A-D3_B";
    private final PbeD3Energy complex, a, b;
    private final String receipt;

    private PbeD3Counterpoise(FragmentPair fragments, PbeD3Energy complex,
            PbeD3Energy a, PbeD3Energy b, GhostBasis ga, GhostBasis gb) {
        this.complex = complex; this.a = a; this.b = b;
        receipt = ContentHash.accumulator().line("aether-PBE-D3-CP-16-1").line(CONVENTION)
                .line(InteractionEnergyCalculator.fragmentIdentity(fragments.a()))
                .line(InteractionEnergyCalculator.fragmentIdentity(fragments.b()))
                .line(ga.identity()).line(gb.identity()).line(complex.receiptHash())
                .line(a.receiptHash()).line(b.receiptHash())
                .line(ContentHash.number(totalHartree())).line("SCREENING_ONLY").finish();
    }

    public static PbeD3Counterpoise combine(FragmentPair fragments, PbeD3Energy complex,
            PbeD3Energy aWithGhostB, PbeD3Energy bWithGhostA) throws IOException {
        Objects.requireNonNull(fragments); Objects.requireNonNull(complex);
        Objects.requireNonNull(aWithGhostB); Objects.requireNonNull(bWithGhostA);
        var full = complex.pbe().convergedState().orElseThrow().density().functions();
        RhfScfCalculator.validateScope(fragments.complex(), full);
        PbeD3Energy.combine(fragments.complex(), complex.pbe(), complex.dispersion());
        var family = BasisScope.def2(full) ? BasisFamily.DEF2_SVP : BasisFamily.STO_3G;
        var ga = GhostBasis.withDonor(fragments.a().system(), fragments.b().system(), family);
        var gb = GhostBasis.withDonor(fragments.b().system(), fragments.a().system(), family);
        validate(ga, aWithGhostB); validate(gb, bWithGhostA);
        return new PbeD3Counterpoise(fragments, complex, aWithGhostB, bWithGhostA, ga, gb);
    }

    private static void validate(GhostBasis ghost, PbeD3Energy energy) {
        ghost.validate(ghost.system(), energy.pbe().convergedState().orElseThrow().density().functions());
        PbeD3Energy.combine(ghost.system(), energy.pbe(), energy.dispersion());
    }
    public double pbeHartree() { return InteractionEnergyCalculator.subtract(complex.pbeHartree(), a.pbeHartree(), b.pbeHartree()); }
    public double dispersionHartree() { return InteractionEnergyCalculator.subtract(complex.dispersionHartree(), a.dispersionHartree(), b.dispersionHartree()); }
    public double totalHartree() {
        double result = pbeHartree() + dispersionHartree();
        if (!Double.isFinite(result)) throw new ArithmeticException("Nonfinite CP interaction");
        return result;
    }
    public String receiptHash() { return receipt; }
    public String counterpoiseConvention() { return CONVENTION; }
    public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
}
