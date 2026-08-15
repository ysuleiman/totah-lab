package totah.lab.prometheus.variational;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import totah.lab.prometheus.molecular.CartesianPosition;
import totah.lab.prometheus.molecular.ElectronCount;
import totah.lab.prometheus.molecular.LengthUnit;
import totah.lab.prometheus.molecular.MolecularCharge;
import totah.lab.prometheus.molecular.Molecule;
import totah.lab.prometheus.molecular.NuclearCenter;
import totah.lab.prometheus.molecular.NuclearCharge;
import totah.lab.prometheus.molecular.SpinSector;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;

final class WavefunctionMcmcSampleSetTest {
    @Test void randomWalkOracleSamplesHydrogenSquaredWavefunctionAndReplaysExactly(){
        var state=GeneralSlaterJastrowState.cuspInitialized(hydrogen());
        var config=new WavefunctionMcmcSampleSet.Configuration(WavefunctionMcmcSampleSet.Kernel.RANDOM_WALK_METROPOLIS,8,400,500,2,.8,.50,20,8128L);
        var first=WavefunctionMcmcSampleSet.generate(state,config);var second=WavefunctionMcmcSampleSet.generate(state,config);
        assertEquals(first.diagnostics().kernel(),second.diagnostics().kernel());assertEquals(first.diagnostics().proposedMoves(),second.diagnostics().proposedMoves());assertEquals(first.diagnostics().acceptedMoves(),second.diagnostics().acceptedMoves());assertEquals(first.diagnostics().warmupAcceptance(),second.diagnostics().warmupAcceptance());assertEquals(first.diagnostics().measurementAcceptance(),second.diagnostics().measurementAcceptance());assertEquals(first.diagnostics().frozenStepSizeBohr(),second.diagnostics().frozenStepSizeBohr());assertEquals(first.diagnostics().stateEvaluations(),second.diagnostics().stateEvaluations());assertEquals(first.size(),config.retainedSamples());
        assertEquals(first.diagnostics().replayHash(),second.diagnostics().replayHash());
        assertTrue(first.diagnostics().measurementAcceptance()>.25&&first.diagnostics().measurementAcceptance()<.75);
        assertEquals(1.5,meanRadius(first),.12,"1s |psi|^2 has <r>=3/2 bohr");
    }

    @Test void malaHastingsSamplerAgreesWithRandomWalkOracleAndUsesUnitDirectWeights(){
        var state=GeneralSlaterJastrowState.cuspInitialized(hydrogen());
        var rw=WavefunctionMcmcSampleSet.generate(state,new WavefunctionMcmcSampleSet.Configuration(WavefunctionMcmcSampleSet.Kernel.RANDOM_WALK_METROPOLIS,8,400,400,2,.8,.50,20,91));
        var mala=WavefunctionMcmcSampleSet.generate(state,new WavefunctionMcmcSampleSet.Configuration(WavefunctionMcmcSampleSet.Kernel.METROPOLIS_ADJUSTED_LANGEVIN,8,400,400,2,.3,.55,20,91));
        assertEquals(meanRadius(rw),meanRadius(mala),.15);
        List<Double> weights=new ArrayList<>();mala.forEach((w,c)->weights.add(w));
        assertEquals(mala.size(),weights.size());assertTrue(weights.stream().allMatch(w->w==1.0));
        List<QuantumCoordinates> walker=new ArrayList<>();mala.walkerSource(0).forEach((w,c)->walker.add(c));assertEquals(400,walker.size());assertThrows(IllegalArgumentException.class,()->mala.walkerSource(8));
        var diagnostic=new GeneralMolecularSamplingDiagnostics().evaluate(state,new totah.lab.prometheus.molecular.GeneralMolecularCoulombHamiltonian(hydrogen()),mala);
        assertEquals("DIRECT_WAVEFUNCTION_MCMC",diagnostic.samplingMode());
        assertEquals(1.0,diagnostic.effectiveSampleFraction(),0);assertEquals(mala.diagnostics().measurementAcceptance(),diagnostic.acceptanceRate().orElseThrow(),0);
    }

    @Test void invalidConfigurationsAreRejected(){
        assertThrows(IllegalArgumentException.class,()->new WavefunctionMcmcSampleSet.Configuration(WavefunctionMcmcSampleSet.Kernel.RANDOM_WALK_METROPOLIS,1,0,1,1,1,.5,1,1));
        assertThrows(IllegalArgumentException.class,()->new WavefunctionMcmcSampleSet.Configuration(WavefunctionMcmcSampleSet.Kernel.RANDOM_WALK_METROPOLIS,2,0,1,1,0,.5,1,1));
    }
    private static double meanRadius(WavefunctionMcmcSampleSet source){double[] sum={0};int[] n={0};source.forEach((w,c)->{var p=c.particles().getFirst();sum[0]+=Math.sqrt(p.xBohr()*p.xBohr()+p.yBohr()*p.yBohr()+p.zBohr()*p.zBohr());n[0]++;});return sum[0]/n[0];}
    private static Molecule hydrogen(){return new Molecule("H-mcmc",List.of(new NuclearCenter(0,"H",new NuclearCharge(1),new CartesianPosition(0,0,0,LengthUnit.BOHR))),new MolecularCharge(0),new ElectronCount(1),new SpinSector(1,0,2));}
}
