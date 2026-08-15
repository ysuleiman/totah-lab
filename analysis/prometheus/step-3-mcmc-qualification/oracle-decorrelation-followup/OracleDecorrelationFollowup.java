import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.validation.H2o13ReferenceReader;
import totah.lab.prometheus.variational.*;
import totah.lab.prometheus.variational.WavefunctionMcmcSampleSet.Kernel;
import totah.lab.prometheus.variational.force.GeneralAnalyticDifferentialSwctForceEstimator;

/** Executes the locked spacing-only oracle follow-up and, conditionally, the already locked MALA path. */
public final class OracleDecorrelationFollowup {
    private static final double ANGSTROM_TO_BOHR=1.8897261254578281;
    private static final Path REFERENCE=Path.of("analysis/prometheus/step-3-multi-nuclear-validation/reference/H2O13_STEP3_SELECTED_RECORDS.xyz");
    private record Geometry(String id,double oh1,double oh2,double angle,List<Double> parameters){}
    private static final List<Geometry> GEOMETRIES=List.of(
            new Geometry("EQ",.95,.95,105,List.of(8.299999999999972,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)),
            new Geometry("COMPRESSED",.85,.95,100,List.of(8.299999999999947,7.600000000000001,7.600000000000001,8.0,7.600000000000001,.10000000000000003)),
            new Geometry("STRETCHED",.95,1.10,115,List.of(8.099999999999907,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)));

    public static void main(String[] ignored)throws Exception{
        var reader=new H2o13ReferenceReader();var eq=GEOMETRIES.getFirst();var reference=reader.read(REFERENCE,eq.oh1,eq.oh2,eq.angle);var state=state(eq,reference);
        var oracle=generate(state,Kernel.RANDOM_WALK_METROPOLIS,.20,.50,8);reportSampler("ORACLE_EQ_SPACING8",oracle,state);
        if(!healthy(oracle,state)){System.out.println("DECISION,RANDOM_WALK_DECORRELATION_INSUFFICIENT");return;}
        System.out.println("STAGE,RANDOM_WALK_ORACLE_QUALIFIED");
        var mala=generate(state,Kernel.METROPOLIS_ADJUSTED_LANGEVIN,.03,.55,2);reportSampler("MALA_EQ",mala,state);
        if(!healthy(mala,state)){System.out.println("DECISION,MALA_QUALIFICATION_FAILED");return;}
        for(var geometry:GEOMETRIES){reference=reader.read(REFERENCE,geometry.oh1,geometry.oh2,geometry.angle);state=state(geometry,reference);var samples=geometry.id.equals("EQ")?mala:generate(state,Kernel.METROPOLIS_ADJUSTED_LANGEVIN,.03,.55,2);var statistics=samples.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));if(!healthy(samples,state)){System.out.println("STEP3,"+geometry.id+",SAMPLER_HEALTH_FAIL");continue;}var force=new GeneralAnalyticDifferentialSwctForceEstimator().evaluate(state,samples);double forceSquared=0,maxForce=0;int components=0;for(int atom=0;atom<3;atom++){var predicted=force.forces().get(atom);var expected=reference.atoms().get(atom);double[] p={predicted.fx(),predicted.fy(),predicted.fz()},q={expected.fxHartreePerBohr(),expected.fyHartreePerBohr(),expected.fzHartreePerBohr()};for(int axis=0;axis<3;axis++){double error=p[axis]-q[axis];forceSquared+=error*error;maxForce=Math.max(maxForce,Math.abs(error));components++;}}System.out.println("STEP3,"+geometry.id+","+statistics.energyMeanHartree()+","+reference.absoluteEnergyHartree()+","+Math.abs(statistics.energyMeanHartree()-reference.absoluteEnergyHartree())+","+Math.sqrt(forceSquared/components)+","+maxForce+","+statistics.normalizedEss()+","+statistics.betweenWalkerRhat()+","+force.wallTimeNanos()+","+force.peakHeapBytes());}
        System.out.println("DECISION,DIRECT_WAVEFUNCTION_MCMC_QUALIFIED_STEP3_RERUN_COMPLETE");
    }

    private static WavefunctionMcmcSampleSet generate(GeneralSlaterJastrowState state,Kernel kernel,double step,double target,int spacing){return WavefunctionMcmcSampleSet.generate(state,new WavefunctionMcmcSampleSet.Configuration(kernel,8,200,128,spacing,step,target,20,20260815L));}
    private static void reportSampler(String id,WavefunctionMcmcSampleSet samples,GeneralSlaterJastrowState state){var d=samples.diagnostics();var s=samples.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));System.out.println("SAMPLER,"+id+","+d.kernel()+","+d.warmupAcceptance()+","+d.measurementAcceptance()+","+d.frozenStepSizeBohr()+","+s.integratedAutocorrelationTime()+","+s.autocorrelationAdjustedEss()+","+s.normalizedEss()+","+s.betweenWalkerRhat()+","+s.maximumRetainedStickingFraction()+","+s.topOnePercentVarianceFraction()+","+s.topFivePercentVarianceFraction()+","+s.energyMeanHartree()+","+s.energyVarianceHartree2()+","+d.stateEvaluations()+","+d.elapsedNanos()+","+d.peakHeapBytes()+","+d.replayHash()+","+s.localEnergyReplayHash());}
    private static boolean healthy(WavefunctionMcmcSampleSet samples,GeneralSlaterJastrowState state){var d=samples.diagnostics();var s=samples.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));return d.measurementAcceptance()>=.30&&d.measurementAcceptance()<=.75&&s.normalizedEss()>=.20&&s.maximumRetainedStickingFraction()<=.25&&s.betweenWalkerRhat()<=1.20&&s.topOnePercentVarianceFraction()<=.50&&s.topFivePercentVarianceFraction()<=.80&&s.samples()==1024;}
    private static GeneralSlaterJastrowState state(Geometry geometry,H2o13ReferenceReader.Reference reference){List<NuclearCenter> nuclei=new ArrayList<>();for(int i=0;i<reference.atoms().size();i++){var atom=reference.atoms().get(i);nuclei.add(new NuclearCenter(i,atom.element(),new NuclearCharge(atom.element().equals("O")?8:1),new CartesianPosition(atom.xAngstrom()*ANGSTROM_TO_BOHR,atom.yAngstrom()*ANGSTROM_TO_BOHR,atom.zAngstrom()*ANGSTROM_TO_BOHR,LengthUnit.BOHR)));}return new GeneralSlaterJastrowState(new Molecule("step3-water-"+geometry.id,nuclei,new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1)),new ParameterVector(geometry.parameters));}
}
