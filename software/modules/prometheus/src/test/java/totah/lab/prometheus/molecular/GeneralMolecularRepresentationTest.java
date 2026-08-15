package totah.lab.prometheus.molecular;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.variational.QuantumCoordinates;
import totah.lab.prometheus.variational.SpinProjection;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.ParameterVector;

final class GeneralMolecularRepresentationTest {
    @Test void validatesChargeElectronAndSpinConsistency(){
        assertThrows(IllegalArgumentException.class,()->new Molecule("bad",List.of(nucleus(0,"H",1,0,0,0)),new MolecularCharge(0),new ElectronCount(2),new SpinSector(1,1,1)));
        assertThrows(IllegalArgumentException.class,()->SpinSector.fromElectronCountAndMultiplicity(new ElectronCount(2),2));
        assertEquals(2,helium().electrons().value());assertEquals(1,helium().spin().multiplicity());
    }

    @Test void hydrogenGeneralPathReproducesExactLocalEnergy(){
        Molecule h=hydrogen();var state=GeneralSlaterJastrowState.cuspInitialized(h);var coordinates=coordinates(p(0,.7,.2,-.1,SpinProjection.ALPHA));
        var energy=state.evaluateWithLocalEnergy(coordinates,new GeneralMolecularCoulombHamiltonian(h)).localEnergy().orElseThrow();
        assertEquals(-.5,energy.totalHartree(),2e-12);assertEquals(0,energy.electronElectronHartree());assertEquals(0,energy.nuclearNuclearHartree());
    }

    @Test void heliumAndH2UseSameGeneralHamiltonianAndExposeFiniteComponents(){
        var he=helium();var heCoordinates=coordinates(p(0,.6,0,0,SpinProjection.ALPHA),p(1,-.7,.1,0,SpinProjection.BETA));var heEnergy=GeneralSlaterJastrowState.cuspInitialized(he).evaluateWithLocalEnergy(heCoordinates,new GeneralMolecularCoulombHamiltonian(he)).localEnergy().orElseThrow();assertTrue(Double.isFinite(heEnergy.totalHartree()));assertTrue(heEnergy.electronElectronHartree()>0);double r12=Math.sqrt(1.3*1.3+.1*.1);assertEquals(-2/.6-2/Math.sqrt(.5)+1/r12,heEnergy.electronNuclearHartree()+heEnergy.electronElectronHartree(),1e-12);
        var h2=h2(1.4);var h2Coordinates=coordinates(p(0,.3,.2,.4,SpinProjection.ALPHA),p(1,-.4,-.2,-.3,SpinProjection.BETA));var h2Energy=GeneralSlaterJastrowState.cuspInitialized(h2).evaluateWithLocalEnergy(h2Coordinates,new GeneralMolecularCoulombHamiltonian(h2)).localEnergy().orElseThrow();assertTrue(Double.isFinite(h2Energy.totalHartree()));assertEquals(1/1.4,h2Energy.nuclearNuclearHartree(),1e-12);assertEquals(new HydrogenMoleculeHamiltonian(1.4).potential(h2Coordinates),h2Energy.electronNuclearHartree()+h2Energy.electronElectronHartree()+h2Energy.nuclearNuclearHartree(),1e-12);
    }

    @Test void sameSpinExchangeFlipsSignWhileProbabilityAndLocalEnergyAreInvariant(){
        Molecule triplet=new Molecule("h2-triplet",h2(1.6).nuclei(),new MolecularCharge(0),new ElectronCount(2),new SpinSector(2,0,3));var state=GeneralSlaterJastrowState.cuspInitialized(triplet);var h=new GeneralMolecularCoulombHamiltonian(triplet);
        var first=coordinates(p(0,.45,.3,.2,SpinProjection.ALPHA),p(1,-.35,-.25,-.4,SpinProjection.ALPHA));var swapped=coordinates(p(0,-.35,-.25,-.4,SpinProjection.ALPHA),p(1,.45,.3,.2,SpinProjection.ALPHA));
        var a=state.evaluateWithLocalEnergy(first,h);var b=state.evaluateWithLocalEnergy(swapped,h);assertEquals(-a.derivatives().value().real(),b.derivatives().value().real(),1e-12);assertEquals(a.derivatives().value().real()*a.derivatives().value().real(),b.derivatives().value().real()*b.derivatives().value().real(),1e-12);assertEquals(a.localEnergy().orElseThrow().totalHartree(),b.localEnergy().orElseThrow().totalHartree(),1e-10);
    }

    @Test void energyIsTranslationAndRotationInvariant(){
        Molecule base=h2(1.4);var c=coordinates(p(0,.3,.2,.4,SpinProjection.ALPHA),p(1,-.4,-.2,-.3,SpinProjection.BETA));double e=energy(base,c);
        Molecule translated=transform(base,(x,y,z)->new double[]{x+2,y-1,z+.7});var tc=transform(c,(x,y,z)->new double[]{x+2,y-1,z+.7});assertEquals(e,energy(translated,tc),1e-10);
        Molecule rotated=transform(base,(x,y,z)->new double[]{-y,x,z});var rc=transform(c,(x,y,z)->new double[]{-y,x,z});assertEquals(e,energy(rotated,rc),1e-10);
    }

    @Test void scientificIdentityIncludesHamiltonianStateSpinAndOrderedGeometry(){var a=new GeneralMolecularRequestIdentity(h2(1.4),"coulomb-bo-v1",GeneralSlaterJastrowState.REPRESENTATION_ID,"BLOCK_PRECONDITIONED_MATRIX_FREE_SR");var b=new GeneralMolecularRequestIdentity(h2(1.5),"coulomb-bo-v1",GeneralSlaterJastrowState.REPRESENTATION_ID,"BLOCK_PRECONDITIONED_MATRIX_FREE_SR");assertEquals(a.sha256(),new GeneralMolecularRequestIdentity(h2(1.4),"coulomb-bo-v1",GeneralSlaterJastrowState.REPRESENTATION_ID,"BLOCK_PRECONDITIONED_MATRIX_FREE_SR").sha256());assertNotEquals(a.sha256(),b.sha256());}

    @Test void cuspAndSingularityFixturesAreExplicit(){
        Molecule h=hydrogen();var state=GeneralSlaterJastrowState.cuspInitialized(h);double delta=1e-5;double logNear=state.evaluate(coordinates(p(0,delta,0,0,SpinProjection.ALPHA))).logAbsoluteWavefunction();double logFar=state.evaluate(coordinates(p(0,2*delta,0,0,SpinProjection.ALPHA))).logAbsoluteWavefunction();assertEquals(-1,(logFar-logNear)/delta,2e-8);
        assertThrows(IllegalArgumentException.class,()->state.evaluateWithLocalEnergy(coordinates(p(0,0,0,0,SpinProjection.ALPHA)),new GeneralMolecularCoulombHamiltonian(h)));
        Molecule he=helium();var correlated=GeneralSlaterJastrowState.cuspInitialized(he);double separation=1e-5;double at=correlated.evaluate(coordinates(p(0,-separation/2,1,0,SpinProjection.ALPHA),p(1,separation/2,1,0,SpinProjection.BETA))).logAbsoluteWavefunction();double farther=correlated.evaluate(coordinates(p(0,-separation,1,0,SpinProjection.ALPHA),p(1,separation,1,0,SpinProjection.BETA))).logAbsoluteWavefunction();assertEquals(.5,(farther-at)/separation,2e-4);
    }

    @Test void equivalentNuclearPermutationPreservesLocalEnergy(){Molecule a=h2(1.4);Molecule b=new Molecule(a.moleculeId(),List.of(nucleus(0,"H",1,0,0,.7),nucleus(1,"H",1,0,0,-.7)),a.charge(),a.electrons(),a.spin());var c=coordinates(p(0,.3,.2,.4,SpinProjection.ALPHA),p(1,-.4,-.2,-.3,SpinProjection.BETA));assertEquals(energy(a,c),energy(b,c),1e-10);}

    @Test void explicitResourceLimitsAndFeatureSizesPreventHiddenPopulationStorage(){Molecule molecule=h2(1.4);var evaluation=GeneralSlaterJastrowState.cuspInitialized(molecule).evaluate(coordinates(p(0,.3,.2,.4,SpinProjection.ALPHA),p(1,-.4,-.2,-.3,SpinProjection.BETA)));assertEquals(2,evaluation.features().electronNuclearDistancesBohr().size());assertEquals(2,evaluation.features().electronNuclearDistancesBohr().getFirst().size());assertEquals(1,evaluation.features().electronElectronDistancesBohr().size());assertEquals(1,evaluation.features().nuclearNuclearDistancesBohr().size());assertEquals(16,Molecule.MAX_SUPPORTED_ELECTRONS);
        Molecule synthetic=new Molecule("synthetic-four-electron",List.of(nucleus(0,"H",1,-1,0,0),nucleus(1,"H",1,0,0,0),nucleus(2,"H",1,1,0,0)),new MolecularCharge(-1),new ElectronCount(4),new SpinSector(2,2,1));var larger=GeneralSlaterJastrowState.cuspInitialized(synthetic).evaluate(coordinates(p(0,-.7,.2,.1,SpinProjection.ALPHA),p(1,.6,-.3,.2,SpinProjection.ALPHA),p(2,-.4,-.5,-.2,SpinProjection.BETA),p(3,.8,.4,-.1,SpinProjection.BETA)));assertEquals(4,larger.features().electronNuclearDistancesBohr().size());assertEquals(6,larger.features().electronElectronDistancesBohr().size());}

    @Test void sharedGraphParameterDerivativeMatchesIndependentFiniteDifference(){Molecule h=hydrogen();var state=GeneralSlaterJastrowState.cuspInitialized(h);var c=coordinates(p(0,.7,.2,-.1,SpinProjection.ALPHA));double analytic=state.evaluate(c).derivatives().parameterGradient().derivatives().getFirst().real(),step=1e-6;List<Double> plus=new java.util.ArrayList<>(state.parameters().values()),minus=new java.util.ArrayList<>(state.parameters().values());plus.set(0,plus.get(0)+step);minus.set(0,minus.get(0)-step);double finite=(state.withParameters(new ParameterVector(plus)).value(c).real()-state.withParameters(new ParameterVector(minus)).value(c).real())/(2*step);assertEquals(finite,analytic,1e-9);}

    static Molecule hydrogen(){return new Molecule("general-H",List.of(nucleus(0,"H",1,0,0,0)),new MolecularCharge(0),new ElectronCount(1),new SpinSector(1,0,2));}
    static Molecule helium(){return new Molecule("general-He",List.of(nucleus(0,"He",2,0,0,0)),new MolecularCharge(0),new ElectronCount(2),new SpinSector(1,1,1));}
    static Molecule h2(double r){return new Molecule("general-H2",List.of(nucleus(0,"H",1,0,0,-r/2),nucleus(1,"H",1,0,0,r/2)),new MolecularCharge(0),new ElectronCount(2),new SpinSector(1,1,1));}
    private static NuclearCenter nucleus(int i,String e,int z,double x,double y,double zz){return new NuclearCenter(i,e,new NuclearCharge(z),new CartesianPosition(x,y,zz,LengthUnit.BOHR));}
    private static QuantumCoordinates.ParticleCoordinate p(int i,double x,double y,double z,SpinProjection s){return new QuantumCoordinates.ParticleCoordinate(i,x,y,z,s);}
    private static QuantumCoordinates coordinates(QuantumCoordinates.ParticleCoordinate...p){return new QuantumCoordinates(List.of(p));}
    private static double energy(Molecule m,QuantumCoordinates c){return GeneralSlaterJastrowState.cuspInitialized(m).evaluateWithLocalEnergy(c,new GeneralMolecularCoulombHamiltonian(m)).localEnergy().orElseThrow().totalHartree();}
    private interface Transform{double[] apply(double x,double y,double z);}
    private static Molecule transform(Molecule m,Transform t){var nuclei=m.nuclei().stream().map(n->{var p=n.position().inBohr();double[] q=t.apply(p.x(),p.y(),p.z());return nucleus(n.orderedIndex(),n.element(),n.charge().atomicNumber(),q[0],q[1],q[2]);}).toList();return new Molecule(m.moleculeId(),nuclei,m.charge(),m.electrons(),m.spin());}
    private static QuantumCoordinates transform(QuantumCoordinates c,Transform t){var p=c.particles().stream().map(e->{double[] q=t.apply(e.xBohr(),e.yBohr(),e.zBohr());return new QuantumCoordinates.ParticleCoordinate(e.particleIndex(),q[0],q[1],q[2],e.spin());}).toList();return new QuantumCoordinates(p);}
}
