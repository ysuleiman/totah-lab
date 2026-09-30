package totah.lab.aether.matrix;

import java.util.List;
import totah.lab.aether.basis.ContractedGaussian;

/** Observational setup costs, excluded from all scientific identities and receipt bytes. */
public record BasisPerformance(int basisFunctions,long primitiveGaussians,long uniqueEriSlots,
        long primitiveQuartets,long integralNanos,int gridPoints,long aoGridNanos,long numericalArrayBytes) {
    static BasisPerformance capture(List<ContractedGaussian> basis,ElectronRepulsionTensor eri,long integralNanos,int gridPoints,long aoGridNanos) {
        long primitives=basis.stream().mapToLong(f->f.terms().size()).sum();
        long arrays=8L*eri.uniqueQuartetCount()+8L*gridPoints*basis.size()+32L*gridPoints;
        return new BasisPerformance(basis.size(),primitives,eri.uniqueQuartetCount(),eri.performanceCounters().primitiveQuartetsEvaluated(),integralNanos,gridPoints,aoGridNanos,arrays);
    }
}
