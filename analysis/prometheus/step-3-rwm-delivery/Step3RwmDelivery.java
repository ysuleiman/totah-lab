import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.validation.H2o13ReferenceReader;
import totah.lab.prometheus.variational.*;
import totah.lab.prometheus.variational.WavefunctionMcmcSampleSet.Kernel;
import totah.lab.prometheus.variational.force.GeneralAnalyticDifferentialSwctForceEstimator;

public final class Step3RwmDelivery {
    private static final double A=1.8897261254578281;
    private static final Path REFERENCE=Path.of("analysis/prometheus/step-3-multi-nuclear-validation/reference/H2O13_STEP3_SELECTED_RECORDS.xyz");
    private record Geometry(String id,double oh1,double oh2,double angle,List<Double> parameters){}
    private static final List<Geometry> GEOMETRIES=List.of(
        new Geometry("EQ",.95,.95,105,List.of(8.299999999999972,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)),
        new Geometry("COMPRESSED",.85,.95,100,List.of(8.299999999999947,7.600000000000001,7.600000000000001,8.0,7.600000000000001,.10000000000000003)),
        new Geometry("STRETCHED",.95,1.10,115,List.of(8.099999999999907,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)));

    public static void main(String[] ignored)throws Exception{
        var reader=new H2o13ReferenceReader();double energySquared=0,forceSquared=0,maxForce=0;boolean all=true;int forceCount=0;
        for(var geometry:GEOMETRIES){var reference=reader.read(REFERENCE,geometry.oh1,geometry.oh2,geometry.angle);var state=state(geometry,reference);long started=System.nanoTime();var samples=WavefunctionMcmcSampleSet.generate(state,new WavefunctionMcmcSampleSet.Configuration(Kernel.RANDOM_WALK_METROPOLIS,8,200,128,8,.20,.50,20,20260815L));long samplingNanos=System.nanoTime()-started;var statistics=samples.statisticalDiagnostics(state,new GeneralMolecularCoulombHamiltonian(state.molecule()));var force=new GeneralAnalyticDifferentialSwctForceEstimator().evaluate(state,samples);double[][] walkerForce=new double[8][9];for(int walker=0;walker<8;walker++){var wf=new GeneralAnalyticDifferentialSwctForceEstimator().evaluate(state,samples.walkerSource(walker));for(int atom=0;atom<3;atom++){var v=wf.forces().get(atom);walkerForce[walker][3*atom]=v.fx();walkerForce[walker][3*atom+1]=v.fy();walkerForce[walker][3*atom+2]=v.fz();}}
            double energyError=statistics.energyMeanHartree()-reference.absoluteEnergyHartree(),energySe=Math.sqrt(statistics.energyVarianceHartree2()/statistics.autocorrelationAdjustedEss()),geometryForceSquared=0,geometryMaxForce=0,maxForceSe=0;double[] total={0,0,0};
            for(int atom=0;atom<3;atom++){var predicted=force.forces().get(atom);var expected=reference.atoms().get(atom);double[] p={predicted.fx(),predicted.fy(),predicted.fz()},q={expected.fxHartreePerBohr(),expected.fyHartreePerBohr(),expected.fzHartreePerBohr()};for(int axis=0;axis<3;axis++){int component=3*atom+axis;double error=p[axis]-q[axis];geometryForceSquared+=error*error;forceSquared+=error*error;geometryMaxForce=Math.max(geometryMaxForce,Math.abs(error));maxForce=Math.max(maxForce,Math.abs(error));forceCount++;total[axis]+=p[axis];double mean=0;for(int w=0;w<8;w++)mean+=walkerForce[w][component]/8;double variance=0;for(int w=0;w<8;w++)variance+=(walkerForce[w][component]-mean)*(walkerForce[w][component]-mean)/7;double forceSe=Math.sqrt(variance/8);maxForceSe=Math.max(maxForceSe,forceSe);System.out.println("FORCE,"+geometry.id+","+atom+","+axis+","+p[axis]+","+q[axis]+","+error+","+forceSe);}}
            double forceRmse=Math.sqrt(geometryForceSquared/9),translation=Math.sqrt(total[0]*total[0]+total[1]*total[1]+total[2]*total[2]);energySquared+=energyError*energyError;long observableNanos=force.wallTimeNanos();double samplingPercent=100.0*samplingNanos/(samplingNanos+observableNanos);boolean pass=Math.abs(energyError)<=.015&&energySe<=.005&&forceRmse<=.010&&geometryMaxForce<=.025&&maxForceSe<=.010&&translation<=.005&&statistics.normalizedEss()>=.20;all&=pass;
            System.out.println("RESULT,"+geometry.id+","+statistics.energyMeanHartree()+","+reference.absoluteEnergyHartree()+","+energyError+","+energySe+","+forceRmse+","+geometryMaxForce+","+maxForceSe+","+translation+","+samples.diagnostics().measurementAcceptance()+","+statistics.integratedAutocorrelationTime()+","+statistics.autocorrelationAdjustedEss()+","+statistics.normalizedEss()+","+samplingNanos+","+observableNanos+","+samplingPercent+","+samples.diagnostics().peakHeapBytes()+","+(pass?"PASS":"FAIL"));
        }
        double energyRmse=Math.sqrt(energySquared/3),globalForceRmse=Math.sqrt(forceSquared/forceCount);boolean aggregate=energyRmse<=.010&&globalForceRmse<=.010&&maxForce<=.025;all&=aggregate;System.out.println("AGGREGATE,"+energyRmse+","+globalForceRmse+","+maxForce+","+(aggregate?"PASS":"FAIL"));System.out.println("DECISION,"+(all?"STEP_3_MULTI_NUCLEAR_VALIDATION_PASSED":"STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED"));
    }
    private static GeneralSlaterJastrowState state(Geometry geometry,H2o13ReferenceReader.Reference reference){List<NuclearCenter> nuclei=new ArrayList<>();for(int i=0;i<3;i++){var atom=reference.atoms().get(i);nuclei.add(new NuclearCenter(i,atom.element(),new NuclearCharge(atom.element().equals("O")?8:1),new CartesianPosition(atom.xAngstrom()*A,atom.yAngstrom()*A,atom.zAngstrom()*A,LengthUnit.BOHR)));}return new GeneralSlaterJastrowState(new Molecule("step3-water-"+geometry.id,nuclei,new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1)),new ParameterVector(geometry.parameters));}
}
