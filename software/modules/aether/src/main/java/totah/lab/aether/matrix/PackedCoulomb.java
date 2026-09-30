package totah.lab.aether.matrix;

/** Exact M14 Coulomb-only contraction; retains the reference lambda/sigma summation order. */
public final class PackedCoulomb {
    private PackedCoulomb() {}
    public static Result calculate(DensityMatrix density,ElectronRepulsionTensor eri) {
        long start=System.nanoTime();
        OccupiedDensityCalculator.requireEqual(density.systemHash(),eri.receipt().systemHash(),"Coulomb system");
        OccupiedDensityCalculator.requireEqual(density.basisGeometryHash(),eri.receipt().basisGeometryHash(),"Coulomb basis/order");
        OccupiedDensityCalculator.requireEqual(ElectronRepulsionTensor.IMPLEMENTATION,eri.receipt().implementation(),"ERI implementation");
        OccupiedDensityCalculator.requireEqual(IntegralMatrixData.protocol(ElectronRepulsionTensor.PROTOCOL,density.functions()),eri.receipt().protocol(),"ERI protocol");
        long[] counts={0};
        var j=new ExecutionMatrix(ExecutionMatrix.Kind.COULOMB,density.functions(),density.systemHash(),density.densityHash(),
                "aether-packed-coulomb-14-1;COULOMB_ONLY;ordered-lambda-sigma;no-screening",eri.receipt().receiptHash(),
                (mu,nu)->JkCalculator.contract(density,(lambda,sigma)->eri.get(mu,nu,lambda,sigma),counts,0));
        return new Result(j,counts[0],System.nanoTime()-start);
    }
    public record Result(ExecutionMatrix coulomb,long eriSlotsRead,long elapsedNanos) {}
}
