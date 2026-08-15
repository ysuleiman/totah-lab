import java.nio.file.Path;
import java.util.List;

import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.validation.H2o13ReferenceReader;
import totah.lab.prometheus.variational.*;
import totah.lab.prometheus.variational.WavefunctionMcmcSampleSet.Kernel;
import totah.lab.prometheus.variational.force.GeneralAnalyticDifferentialSwctForceEstimator;

/** Locked Step-3 direct-|Psi|^2 MCMC qualification runner. */
public final class Step3DirectMcmcQualification {
    private static final double A=1.8897261254578281;
    private static final Path REFERENCE=Path.of("analysis/prometheus/step-3-multi-nuclear-validation/reference/H2O13_STEP3_SELECTED_RECORDS.xyz");
    private record Geometry(String id,double oh1,double oh2,double angle,List<Double> parameters){}
    private static final List<Geometry> GEOMETRIES=List.of(
            new Geometry("EQ",.95,.95,105,List.of(8.299999999999972,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)),
            new Geometry("COMPRESSED",.85,.95,100,List.of(8.299999999999947,7.600000000000001,7.600000000000001,8.0,7.600000000000001,.10000000000000003)),
            new Geometry("STRETCHED",.95,1.10,115,List.of(8.099999999999907,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)));

    public static void main(String[] args)throws Exception{
        var reader=new H2o13ReferenceReader();Geometry eq=GEOMETRIES.getFirst();var ref=reader.read(REFERENCE,eq.oh1,eq.oh2,eq.angle);var state=state(eq,ref);
        var rw=run(state,Kernel.RANDOM_WALK_METROPOLIS,.20,.50);print("ORACLE_EQ",rw,state);
        if(!healthy(rw,state)) {System.out.println("DECISION,RANDOM_WALK_METROPOLIS_QUALIFICATION_FAILED");return;}
        var mala=run(state,Kernel.METROPOLIS_ADJUSTED_LANGEVIN,.03,.55);print("MALA_EQ",mala,state);
        if(!healthy(mala,state)){System.out.println("DECISION,MALA_QUALIFICATION_FAILED");return;}
        for(Geometry geometry:GEOMETRIES){ref=reader.read(REFERENCE,geometry.oh1,geometry.oh2,geometry.angle);state=state(geometry,ref);var samples=geometry.id.equals("EQ")?mala:run(state,Kernel.METROPOLIS_ADJUSTED_LANGEVIN,.03,.55);var stats=samples.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));var force=new GeneralAnalyticDifferentialSwctForceEstimator().evaluate(state,samples);double maxForce=0,sumForce=0;int count=0;for(int i=0;i<3;i++){var predicted=force.forces().get(i);var atom=ref.atoms().get(i);double[] p={predicted.fx(),predicted.fy(),predicted.fz()},q={atom.fxHartreePerBohr(),atom.fyHartreePerBohr(),atom.fzHartreePerBohr()};for(int axis=0;axis<3;axis++){double error=Math.abs(p[axis]-q[axis]);maxForce=Math.max(maxForce,error);sumForce+=error*error;count++;}}System.out.println("STEP3,"+geometry.id+","+stats.energyMeanHartree()+","+ref.absoluteEnergyHartree()+","+Math.abs(stats.energyMeanHartree()-ref.absoluteEnergyHartree())+","+Math.sqrt(sumForce/count)+","+maxForce+","+stats.normalizedEss()+","+stats.betweenWalkerRhat()+","+stats.topOnePercentVarianceFraction()+","+stats.topFivePercentVarianceFraction()+","+force.wallTimeNanos()+","+force.peakHeapBytes());}
        System.out.println("DECISION,DIRECT_WAVEFUNCTION_MCMC_QUALIFIED_STEP3_RERUN_COMPLETE");
    }
    private static WavefunctionMcmcSampleSet run(GeneralSlaterJastrowState state,Kernel kernel,double step,double target){return WavefunctionMcmcSampleSet.generate(state,new WavefunctionMcmcSampleSet.Configuration(kernel,8,200,128,2,step,target,20,20260815L));}
    private static void print(String id,WavefunctionMcmcSampleSet set,GeneralSlaterJastrowState state){var d=set.diagnostics();var s=set.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));System.out.println("SAMPLER,"+id+","+d.kernel()+","+d.warmupAcceptance()+","+d.measurementAcceptance()+","+d.frozenStepSizeBohr()+","+s.integratedAutocorrelationTime()+","+s.autocorrelationAdjustedEss()+","+s.normalizedEss()+","+s.betweenWalkerRhat()+","+s.maximumRetainedStickingFraction()+","+s.topOnePercentVarianceFraction()+","+s.topFivePercentVarianceFraction()+","+s.energyMeanHartree()+","+s.energyVarianceHartree2()+","+d.stateEvaluations()+","+d.elapsedNanos()+","+d.peakHeapBytes()+","+d.replayHash()+","+s.localEnergyReplayHash());}
    private static boolean healthy(WavefunctionMcmcSampleSet set,GeneralSlaterJastrowState state){var d=set.diagnostics();var s=set.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));return d.measurementAcceptance()>=.30&&d.measurementAcceptance()<=.75&&s.normalizedEss()>=.20&&s.maximumRetainedStickingFraction()<=.25&&s.betweenWalkerRhat()<=1.20&&s.topOnePercentVarianceFraction()<=.50&&s.topFivePercentVarianceFraction()<=.80;}
    private static GeneralSlaterJastrowState state(Geometry g,H2o13ReferenceReader.Reference ref){List<NuclearCenter> nuclei=new java.util.ArrayList<>();for(int i=0;i<ref.atoms().size();i++){var atom=ref.atoms().get(i);nuclei.add(new NuclearCenter(i,atom.element(),new NuclearCharge(atom.element().equals("O")?8:1),new CartesianPosition(atom.xAngstrom()*A,atom.yAngstrom()*A,atom.zAngstrom()*A,LengthUnit.BOHR)));}Molecule molecule=new Molecule("step3-water-"+g.id,nuclei,new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1));return new GeneralSlaterJastrowState(molecule,new ParameterVector(g.parameters));}
}
