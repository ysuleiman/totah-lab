package totah.lab.aether.matrix;

import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Immutable solver-issued evidence: construction is inaccessible to unchecked callers. */
public final class ValidatedOrbitals {
    private final IntegralMatrixData coefficients;
    private final SpectralValues energies;
    private final String systemHash,overlapHash,hash;
    private final double orthonormality,residual;
    private ValidatedOrbitals(QuantumSystem system,OverlapMatrix overlap,OneShotNumerics.Solution solved,String source) {
        systemHash=IntegralMatrixData.systemHash(system);overlapHash=overlap.receipt().receiptHash();
        String inputs=systemHash+"\n"+overlapHash+"\n"+source;
        coefficients=IntegralMatrixData.capture(overlap.functions(),solved.coefficients()::getEntry,
                "aether-validated-orbitals-14-1",OneShotNumerics.PROTOCOL,"aether-M14-C-v1","SCREENING_ONLY",inputs);
        energies=SpectralValues.capture(solved.energies(),"aether-M14-orbital-energies-v1");
        orthonormality=solved.orthonormalityError();residual=solved.generalizedResidual();
        if(!Double.isFinite(residual)||!Double.isFinite(orthonormality)||orthonormality>OneShotNumerics.VALIDATION_TOLERANCE)
            throw new ArithmeticException("Unvalidated orbital normalization");
        hash=ContentHash.sha256(inputs+"\n"+coefficients.identity().receiptHash()+"\n"+energies.resultHash()
                +"\n"+ContentHash.number(orthonormality)+"\n"+ContentHash.number(residual));
    }
    static ValidatedOrbitals fromSolve(QuantumSystem system,OverlapMatrix overlap,OneShotNumerics.Solution solved,String source) {
        return new ValidatedOrbitals(system,overlap,solved,source);
    }
    public int size(){return coefficients.size();}
    public double coefficient(int ao,int orbital){return coefficients.get(ao,orbital);}
    public double energy(int orbital){return energies.values().get(orbital);}
    public double orthonormalityResidual(){return orthonormality;}
    public double generalizedResidual(){return residual;}
    public String systemHash(){return systemHash;}
    public String basisGeometryHash(){return coefficients.identity().basisGeometryHash();}
    public String overlapReceiptHash(){return overlapHash;}
    public String receiptHash(){return hash;}
    public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
