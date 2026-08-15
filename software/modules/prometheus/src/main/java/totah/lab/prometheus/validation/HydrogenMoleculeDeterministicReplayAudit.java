package totah.lab.prometheus.validation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

import totah.lab.prometheus.neural.HydrogenMoleculeCorrelatedState;
import totah.lab.prometheus.variational.ConvergedFiniteDifferenceAdam;
import totah.lab.prometheus.variational.HydrogenMoleculeHamiltonian;
import totah.lab.prometheus.variational.HydrogenMoleculeImportancePointSet;
import totah.lab.prometheus.variational.HydrogenMoleculeRayleighFunctional;
import totah.lab.prometheus.variational.ParameterVector;

/** Independent exact replay audit for the locked H2 optimizer path at R=1.4 bohr. */
public final class HydrogenMoleculeDeterministicReplayAudit {
    private HydrogenMoleculeDeterministicReplayAudit() { }
    public static ReplayResult run() {
        double radius=1.4;var initial=new HydrogenMoleculeCorrelatedState(radius,
                new ParameterVector(List.of(1.0,0.0,0.0,0.0,0.0)));
        var hamiltonian=new HydrogenMoleculeHamiltonian(radius);
        var points=HydrogenMoleculeImportancePointSet.create(2500,radius,1.15,43);
        var functional=new HydrogenMoleculeRayleighFunctional();
        var optimizer=new ConvergedFiniteDifferenceAdam(120,18,8,0.012,2e-4,2e-7);
        var first=optimizer.optimize(initial,hamiltonian,functional,points);
        var second=optimizer.optimize(initial,hamiltonian,functional,points);
        double maximum=0;
        for(int i=0;i<first.parameters().values().size();i++) maximum=Math.max(maximum,
                Math.abs(first.parameters().values().get(i)-second.parameters().values().get(i)));
        return new ReplayResult(first.objective(),second.objective(),Math.abs(first.objective()-second.objective()),
                maximum,first.iterations(),second.iterations(),first.objectiveEvaluations(),second.objectiveEvaluations(),
                maximum<=1e-14&&first.objective()==second.objective());
    }
    public static void main(String[] args)throws IOException{ReplayResult result=run();String json=result.toJson();
        if(args.length==0)System.out.println(json);else Files.writeString(Path.of(args[0]),json,StandardCharsets.UTF_8);
        if(!result.passed)throw new IllegalStateException("H2 deterministic replay failed");}
    public record ReplayResult(double firstEnergy,double secondEnergy,double energyDifference,
            double maximumParameterDifference,int firstIterations,int secondIterations,long firstObjectiveEvaluations,
            long secondObjectiveEvaluations,boolean passed){String toJson(){return String.format(Locale.ROOT,
                    "{\"R_bohr\":1.4,\"first_energy\":%.16g,\"second_energy\":%.16g,\"energy_difference\":%.16g,\"maximum_parameter_difference\":%.16g,\"first_iterations\":%d,\"second_iterations\":%d,\"first_objective_evaluations\":%d,\"second_objective_evaluations\":%d,\"passed\":%s}%n",
                    firstEnergy,secondEnergy,energyDifference,maximumParameterDifference,firstIterations,secondIterations,
                    firstObjectiveEvaluations,secondObjectiveEvaluations,passed);}}
}
