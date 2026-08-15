package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import totah.lab.prometheus.variational.ParameterVector;

/** Executes the four locked training arms after an independently persisted passing preflight. */
public final class ExactFiniteObjectiveTraining {
    private static final double[] RADII={.8,1,1.2,1.4,1.6,2,3,4,6};
    private static final double[] REFERENCES={-1.0200566663601389,-1.1245397195465791,-1.1649352434400281,
            -1.1744757142200755,-1.1685833733709263,-1.1381329571315035,-1.0573262688692439,-1.0163902529471283,-1.0008357076542279};
    private static final double[] FROZEN={.8576772116910546,.11919655001255025,-.06709570692540537,.04370894911240642,
            -.32732397143757097,.21519667708138937,-.06386208428749664,.04232059707741613,.017563345336565027,
            -.12118637444956007,.11444052280585346,.26554487072063354,.19811737981250818,.07860098998305089,
            -.2778578205251936,-.16701609069702947,.07580798604963333,-.15755013283163458,.22812063643399538,-.1453261891402233};
    private ExactFiniteObjectiveTraining() { }

    public static void main(String[] args)throws IOException {
        if(args.length!=2)throw new IllegalArgumentException("passing preflight directory and output directory required");
        String preflight=Files.readString(Path.of(args[0]).resolve("EXACT_FINITE_OBJECTIVE_PREFLIGHT_DECISION.json"));
        if(!preflight.contains("PREFLIGHT_PASSES_TRAINING_AUTHORIZED"))throw new IllegalStateException("training not authorized by preflight");
        Path output=Path.of(args[1]);Files.createDirectories(output);var optimizer=optimizer();ParameterVector baseline=vector(FROZEN);
        runAndPersist(optimizer,baseline,"primary",output);runAndPersist(optimizer,baseline,"replay",output);
        runAndPersist(optimizer,perturbed(baseline,1),"perturb_plus",output);runAndPersist(optimizer,perturbed(baseline,-1),"perturb_minus",output);
        write(output.resolve("TRAINING_COMPLETE"),"all four locked arms completed and synchronously persisted\n");
    }
    private static ExactFiniteObjectiveOptimizer optimizer(){return new ExactFiniteObjectiveOptimizer(
            new ExactFiniteObjectiveOptimizer.Configuration(120,18,8,.05,1e-3,1.3333333333333333e-5,.10,2500),RADII,REFERENCES);}
    private static void runAndPersist(ExactFiniteObjectiveOptimizer optimizer,ParameterVector seed,String name,Path output)throws IOException{
        Path path=output.resolve(name+".json");if(Files.exists(path))return;var result=optimizer.optimize(seed);
        String json=String.format(Locale.ROOT,"{\"arm\":\"%s\",\"loss\":%.17g,\"energy_loss\":%.17g,\"force_loss\":%.17g,\"diagnostic_force\":%.17g,\"mean_energy\":%.17g,\"iterations\":%d,\"objective_evaluations\":%d,\"state_evaluations\":%d,\"local_energy_evaluations\":%d,\"sample_count\":%d,\"wall_time_ns\":%d,\"peak_observed_heap_bytes\":%d,\"converged\":%s,\"parameters\":%s,\"loss_history\":%s}%n",name,result.loss(),result.energyLoss(),result.forceLoss(),result.diagnosticForce(),result.meanEnergy(),result.iterations(),result.objectiveEvaluations(),result.stateEvaluations(),result.localEnergyEvaluations(),result.sampleCount(),result.wallTimeNanos(),result.peakObservedHeapBytes(),result.converged(),result.parameters().values(),result.history());
        write(path,json);
    }
    private static ParameterVector perturbed(ParameterVector source,double sign){List<Double> values=new ArrayList<>(source.values());for(int i=0;i<values.size();i++)values.set(i,values.get(i)+sign*(i%4==0?.02:.005));return new ParameterVector(values);}
    private static ParameterVector vector(double[] source){List<Double> values=new ArrayList<>();for(double value:source)values.add(value);return new ParameterVector(values);}
    private static void write(Path path,String text)throws IOException{try(FileChannel channel=FileChannel.open(path,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){ByteBuffer bytes=StandardCharsets.UTF_8.encode(text);while(bytes.hasRemaining())channel.write(bytes);channel.force(true);}}
}
