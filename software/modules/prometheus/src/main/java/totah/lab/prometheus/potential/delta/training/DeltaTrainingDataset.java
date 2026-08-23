package totah.lab.prometheus.potential.delta.training;

import java.util.List;

/** Training-only data boundary; deliberately has no holdout accessor. */
public record DeltaTrainingDataset(List<TrainingTarget> targets){public DeltaTrainingDataset{targets=List.copyOf(targets);}public record TrainingTarget(String id,String source,double residualEnergy,double[][] residualForces){public TrainingTarget{residualForces=copy(residualForces);} @Override public double[][] residualForces(){return copy(residualForces);} private static double[][] copy(double[][] values){return java.util.Arrays.stream(values).map(double[]::clone).toArray(double[][]::new);}}}
