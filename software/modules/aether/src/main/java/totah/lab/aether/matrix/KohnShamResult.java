package totah.lab.aether.matrix;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import totah.lab.aether.provenance.ScientificStatus;

/** Fixed-grid pure LDA SCF evidence, deliberately separate from RHF energy evidence. */
public final class KohnShamResult {
    public record Energy(double electronicHartree,double nuclearHartree,double totalHartree,String receiptHash){}
    public record State(DensityMatrix density,XcIntegration.Result xc,KsFockMatrix fock,KsOrbitals orbitals,Energy energy){}
    public record DiisEvidence(List<Integer> history,List<String> fockHashes,List<String> errorHashes,List<Double> coefficients,
                               List<String> events,double errorMaximum,double reciprocalCondition,boolean extrapolated,String updateFockHash) {
        public DiisEvidence{history=List.copyOf(history);fockHashes=List.copyOf(fockHashes);errorHashes=List.copyOf(errorHashes);coefficients=List.copyOf(coefficients);events=List.copyOf(events);}
    }
    public record Iteration(int number,double electronicEnergy,double totalEnergy,OptionalDouble deltaEnergy,double densityResidual,
                            boolean energyPassed,boolean densityPassed,List<Double> orbitalEnergies,String inputDensityHash,
                            String outputDensityHash,String fockHash,String xcHash,String orbitalHash,String energyHash,Optional<DiisEvidence> diis) {
        public Iteration{orbitalEnergies=List.copyOf(orbitalEnergies);}
    }
    public record Receipt(String protocol,String systemHash,String basisGeometryHash,String gridHash,String functional,
                          String initialDensityHash,int maximumIterations,RhfScfResult.Status termination,String reason,
                          List<Iteration> trajectory,String receiptHash) {
        public Receipt{trajectory=List.copyOf(trajectory);}public ScientificStatus scientificStatus(){return ScientificStatus.SCREENING_ONLY;}
    }
    public record Performance(int dimension,int gridPoints,int iterations,long totalNanos,long jkNanos,long xcNanos,long eigensolveNanos){}
    private final Receipt receipt;private final Optional<State> last;private final Performance performance;
    private final Optional<BasisPerformance> basisPerformance;
    KohnShamResult(Receipt receipt,State last,Performance performance){this(receipt,last,performance,null);}
    private KohnShamResult(Receipt receipt,State last,Performance performance,BasisPerformance profile){this.receipt=receipt;this.last=Optional.ofNullable(last);this.performance=performance;this.basisPerformance=Optional.ofNullable(profile);}
    KohnShamResult withBasisPerformance(BasisPerformance profile){return new KohnShamResult(receipt,last.orElse(null),performance,profile);}
    public Optional<BasisPerformance> basisPerformance(){return basisPerformance;}
    public RhfScfResult.Status status(){return receipt.termination();}public Receipt receipt(){return receipt;}
    public Optional<State> convergedState(){return status()==RhfScfResult.Status.CONVERGED?last:Optional.empty();}
    public Optional<State> lastDiagnosticState(){return last;}public Performance performance(){return performance;}
    public ScientificStatus scientificStatus(){return ScientificStatus.SCREENING_ONLY;}
}
