package totah.lab.prometheus.validation;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.variational.*;

final class WaterMoleculeStep3InfrastructureTest {
    @TempDir Path temp;

    @Test void authoritativeReaderPreservesOrderAndConvertsPublishedUnits()throws Exception{
        Path source=temp.resolve("water.xyz");Files.writeString(source,"""
                3
                OH1=0.95000000 OH2=0.95000000 HOH=105 PBE0_energy=-2078.6 PS_energy=0.00315412 Properties=x
                O 0 0 0 8 0 0 0 0 1 2 3 -0.31353239 -0.40860343 0 0 0 0
                H 0.95 0 0 1 0 0 0 0 1 2 3 0.40617080 0.01292665 0 0 0 0
                H -0.24587809 0.91762953 0 1 0 0 0 0 1 2 3 -0.09263843 0.39567676 0 0 0 0
                """);
        var reference=new H2o13ReferenceReader().read(source,.95,.95,105);
        assertEquals(List.of("O","H","H"),reference.atoms().stream().map(H2o13ReferenceReader.Atom::element).toList());
        assertEquals(-76.4390+.00315412/H2o13ReferenceReader.EV_PER_HARTREE,reference.absoluteEnergyHartree(),1e-15);
        assertEquals(-.31353239*H2o13ReferenceReader.EV_PER_ANGSTROM_TO_HARTREE_PER_BOHR,reference.atoms().getFirst().fxHartreePerBohr(),1e-15);
    }

    @Test void boundedSamplerIsDeterministicAndPreservesSpinAndCount(){Molecule molecule=water();var first=new GeneralMolecularImportanceBatches(molecule,16,4,101,8);var second=new GeneralMolecularImportanceBatches(molecule,16,4,101,8);assertEquals(first.provenanceHash(),second.provenanceHash());List<String> a=collect(first),b=collect(second);assertEquals(a,b);assertEquals(16,a.size());}

    @Test void frozenStep3FailureIsPcgPreconditionedResidualNotStateEvaluation(){Molecule molecule=water();var state=GeneralSlaterJastrowState.cuspInitialized(molecule);var source=new GeneralMolecularImportanceBatches(molecule,512,4,101,64);long[] count={0};source.forEach((weight,coordinates)->{state.evaluateWithLocalEnergy(coordinates,new GeneralMolecularCoulombHamiltonian(molecule));count[0]++;});assertEquals(512,count[0]);var configuration=new GeneralMolecularMatrixFreeSrOptimizer.Configuration(.02,.01,.10,2,200,1e-10,1e-12);var failure=assertThrows(IllegalArgumentException.class,()->new GeneralMolecularMatrixFreeSrOptimizer().oneIteration(state,new GeneralMolecularCoulombHamiltonian(molecule),source,configuration));assertEquals("PCG invalid preconditioned residual",failure.getMessage());}

    private static List<String> collect(GeneralMolecularImportanceBatches source){List<String> values=new java.util.ArrayList<>();source.forEach((weight,c)->{assertEquals(10,c.particles().size());for(int i=0;i<10;i++)assertEquals(i<5?SpinProjection.ALPHA:SpinProjection.BETA,c.particles().get(i).spin());values.add(Double.toHexString(weight)+c);});return List.copyOf(values);}
    private static Molecule water(){double a=1.8897261254578281;return new Molecule("step3-water-test",List.of(new NuclearCenter(0,"O",new NuclearCharge(8),new CartesianPosition(0,0,0,LengthUnit.BOHR)),new NuclearCenter(1,"H",new NuclearCharge(1),new CartesianPosition(.95*a,0,0,LengthUnit.BOHR)),new NuclearCenter(2,"H",new NuclearCharge(1),new CartesianPosition(-.24587809*a,.91762953*a,0,LengthUnit.BOHR))),new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1));}
}
