import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import totah.lab.prometheus.molecular.*;
import totah.lab.prometheus.neural.GeneralSlaterJastrowState;
import totah.lab.prometheus.validation.H2o13ReferenceReader;
import totah.lab.prometheus.variational.*;

public final class Step3SamplingVarianceDiagnosis {
    private static final double ANGSTROM_TO_BOHR=1.8897261254578281;
    private static final int[] SKIPS={1009,2017,3019,4027};
    private static final List<Choice> CHOICES=List.of(
            new Choice("EQ",.95,.95,105,List.of(8.299999999999972,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)),
            new Choice("COMPRESSED",.85,.95,100,List.of(8.299999999999947,7.600000000000001,7.600000000000001,8.0,7.600000000000001,.10000000000000003)),
            new Choice("STRETCHED",.95,1.10,115,List.of(8.099999999999907,7.600000000000001,7.600000000000001,8.399999999999999,7.600000000000001,.10000000000000003)));

    public static void main(String[] args)throws Exception{
        if(args.length!=1)throw new IllegalArgumentException("selected H2O-13 reference file required");
        var reader=new H2o13ReferenceReader();
        System.out.println("geometry,skip,sampling_mode,acceptance_rate,acceptance_status,n,finite,raw_weight_min,raw_weight_max,effective_weight_min,effective_weight_max,max_normalized_weight,ess,ess_fraction,energy_mean,energy_variance,energy_min,energy_q01,energy_q50,energy_q99,energy_max,top1_variance_fraction,top5_variance_fraction,lag1,lag2,replay_hash");
        for(Choice choice:CHOICES){var ref=reader.read(Path.of(args[0]),choice.oh1,choice.oh2,choice.angle);Molecule molecule=molecule(ref);var state=GeneralSlaterJastrowState.cuspInitialized(molecule).withParameters(new ParameterVector(choice.parameters));var hamiltonian=new GeneralMolecularCoulombHamiltonian(molecule);for(int skip:SKIPS){var result=new GeneralMolecularSamplingDiagnostics().evaluate(state,hamiltonian,new GeneralMolecularImportanceBatches(molecule,128,4.0,skip,64));System.out.println(choice.id+","+skip+","+result.csv());}}
    }

    private static Molecule molecule(H2o13ReferenceReader.Reference reference){List<NuclearCenter> nuclei=new ArrayList<>();for(int i=0;i<reference.atoms().size();i++){var atom=reference.atoms().get(i);nuclei.add(new NuclearCenter(i,atom.element(),new NuclearCharge("O".equals(atom.element())?8:1),new CartesianPosition(atom.xAngstrom()*ANGSTROM_TO_BOHR,atom.yAngstrom()*ANGSTROM_TO_BOHR,atom.zAngstrom()*ANGSTROM_TO_BOHR,LengthUnit.BOHR)));}return new Molecule("prometheus-step3-water",nuclei,new MolecularCharge(0),new ElectronCount(10),new SpinSector(5,5,1));}
    private record Choice(String id,double oh1,double oh2,double angle,List<Double> parameters){Choice{parameters=List.copyOf(parameters);}}
}
