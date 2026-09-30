package totah.lab.aether.matrix;

import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Canonicalized KS orbitals from the shared validated Lowdin/eigensolver path. */
public final class KsOrbitals {
    private final IntegralMatrixData coefficients;private final SpectralValues energies;private final String hash;
    private final double residual,orthonormality;
    KsOrbitals(DensityMatrix p,OverlapMatrix s,KsFockMatrix f,OneShotNumerics.Solution solution) {
        String sources=p.systemHash()+"\n"+p.densityHash()+"\n"+s.receipt().receiptHash()+"\n"+f.receiptHash();
        coefficients=IntegralMatrixData.capture(p.functions(),solution.coefficients()::getEntry,"aether-KS-orbitals-1",BasisScope.protocol(KohnShamCalculator.PROTOCOL,p.functions()),"aether-KS-C-v1","SCREENING_ONLY",sources);
        energies=SpectralValues.capture(solution.energies(),"aether-KS-eps-v1");residual=solution.generalizedResidual();orthonormality=solution.orthonormalityError();
        hash=ContentHash.sha256(sources+"\n"+coefficients.identity().receiptHash()+"\n"+energies.resultHash()+"\n"+ContentHash.number(residual)+"\n"+ContentHash.number(orthonormality));
    }
    public int size(){return coefficients.size();}public double coefficient(int ao,int orbital){return coefficients.get(ao,orbital);}
    public double energy(int orbital){return energies.values().get(orbital);}public String receiptHash(){return hash;}
    public double generalizedResidual(){return residual;}public double orthonormalityError(){return orthonormality;}
    public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
