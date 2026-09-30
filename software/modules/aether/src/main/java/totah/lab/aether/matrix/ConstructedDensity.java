package totah.lab.aether.matrix;

import java.util.OptionalDouble;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** M14 occupied density with explicit measured-versus-construction evidence. */
public final class ConstructedDensity {
    public enum Mode { CONSTRUCTION, AUDIT }
    public enum IdempotencyStatus { GUARANTEED_BY_CONSTRUCTION_EXACT_ARITHMETIC, NUMERICALLY_AUDITED }
    private ConstructedDensity(){}
    public static Result build(QuantumSystem system,OverlapMatrix overlap,ValidatedOrbitals c,Mode mode) {
        long start=System.nanoTime();java.util.Objects.requireNonNull(mode);
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(system),c.systemHash(),"occupied system");
        OccupiedDensityCalculator.requireEqual(overlap.receipt().basisGeometryHash(),c.basisGeometryHash(),"occupied basis/geometry/order");
        OccupiedDensityCalculator.requireEqual(overlap.receipt().receiptHash(),c.overlapReceiptHash(),"occupied overlap");
        int n=overlap.size();if(c.size()!=n)throw new IllegalArgumentException("Orbital dimension mismatch");
        var occupation=OccupiedDensityCalculator.occupation(system,n);
        for(int i=0;i<n;i++) {
            if(!Double.isFinite(c.energy(i))||(i>0&&c.energy(i)<c.energy(i-1)))throw new IllegalArgumentException("Invalid orbital ordering");
            for(int j=0;j<n;j++)if(!Double.isFinite(c.coefficient(i,j)))throw new IllegalArgumentException("Nonfinite coefficient");
        }
        var density=OccupiedDensityCalculator.formDensity(system,overlap,occupation,c::coefficient);
        double trace=0;for(int i=0;i<n;i++)for(int j=0;j<n;j++)trace+=density.get(i,j)*overlap.get(j,i);
        if(!Double.isFinite(trace)||Math.abs(trace-occupation.electrons())>1e-10)throw new ArithmeticException("Failed electron count");
        OptionalDouble idempotency=OptionalDouble.empty();
        if(mode==Mode.AUDIT) {
            // Deliberately independent M13 audit order; never run on the construction path.
            var audited=OccupiedDensityCalculator.construct(system,overlap,n,n,c::coefficient,c::energy);
            OccupiedDensityCalculator.requireEqual(density.densityHash(),audited.density().densityHash(),"audit density");
            idempotency=OptionalDouble.of(audited.idempotency());
        }
        var status=mode==Mode.AUDIT?IdempotencyStatus.NUMERICALLY_AUDITED:IdempotencyStatus.GUARANTEED_BY_CONSTRUCTION_EXACT_ARITHMETIC;
        String measured=idempotency.isPresent()?ContentHash.number(idempotency.getAsDouble()):"NOT_MEASURED";
        String hash=ContentHash.sha256("aether-constructed-density-14-1\n"+c.receiptHash()+"\n"+c.overlapReceiptHash()+"\n"
                +occupation.receiptHash()+"\n"+density.densityHash()+"\n"+ContentHash.number(trace)+"\n"+status+"\n"+measured+"\nSCREENING_ONLY");
        return new Result(density,occupation,c.receiptHash(),trace,status,idempotency,hash,System.nanoTime()-start);
    }
    public record Result(DensityMatrix density,OccupiedDensityCalculator.Occupation occupation,String orbitalReceiptHash,
                         double tracePS,IdempotencyStatus idempotencyStatus,OptionalDouble idempotencyResidual,
                         String receiptHash,long elapsedNanos) {
        public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
    }
}
