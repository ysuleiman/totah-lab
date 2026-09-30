package totah.lab.aether.matrix;
import java.util.*;
import java.io.IOException;
import totah.lab.aether.model.FragmentPair;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;
/** Own-basis electronic interaction with separately exposed geometry-only D3 difference. */
public final class PbeD3Interaction {
 private final PbeD3Energy complex,a,b;private final String receipt;
 private PbeD3Interaction(FragmentPair fragments,PbeD3Energy complex,PbeD3Energy a,PbeD3Energy b){
  this.complex=complex;this.a=a;this.b=b;
  receipt=ContentHash.accumulator().line("aether-PBE-D3-interaction-16-1;OWN_BASIS;NO_CP;fixed-geometries;SCREENING_ONLY")
   .line(InteractionEnergyCalculator.fragmentIdentity(fragments.a())).line(InteractionEnergyCalculator.fragmentIdentity(fragments.b()))
   .line(complex.receiptHash()).line(a.receiptHash()).line(b.receiptHash()).line(ContentHash.number(totalHartree())).finish();
 }
 public static PbeD3Interaction combine(FragmentPair fragments,PbeD3Energy complex,PbeD3Energy a,PbeD3Energy b)throws IOException{
  Objects.requireNonNull(fragments);var energies=List.of(complex,a,b);var systems=List.of(fragments.complex(),fragments.a().system(),fragments.b().system());
  boolean def2=BasisScope.def2(complex.pbe().convergedState().orElseThrow().density().functions());
  for(int i=0;i<3;i++) {
   OccupiedDensityCalculator.requireEqual(IntegralMatrixData.systemHash(systems.get(i)),energies.get(i).dispersion().systemHash(),"interaction component");
   var basis=energies.get(i).pbe().convergedState().orElseThrow().density().functions();
   RhfScfCalculator.validateScope(systems.get(i),basis); // Own-basis label must reject ghost-augmented states.
   if(BasisScope.def2(basis)!=def2)throw new IllegalArgumentException("Interaction basis families differ");
  }
  return new PbeD3Interaction(fragments,complex,a,b);
 }
 public double pbeHartree(){return InteractionEnergyCalculator.subtract(complex.pbeHartree(),a.pbeHartree(),b.pbeHartree());}
 public double dispersionHartree(){return InteractionEnergyCalculator.subtract(complex.dispersionHartree(),a.dispersionHartree(),b.dispersionHartree());}
 public double totalHartree(){double v=pbeHartree()+dispersionHartree();if(!Double.isFinite(v))throw new ArithmeticException("Nonfinite interaction");return v;}
 public String receiptHash(){return receipt;}public String counterpoiseConvention(){return "OWN_BASIS_NO_COUNTERPOISE";}
 public ScientificStatus status(){return ScientificStatus.SCREENING_ONLY;}
}
