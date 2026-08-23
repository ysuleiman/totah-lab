package totah.lab.prometheus.potential.delta.training;

import java.util.Set;

/** Frozen grouped split identity; target values are deliberately absent. */
public record CrossValidationFold(String id,Set<String> trainingIds,Set<String> validationIds){public CrossValidationFold{trainingIds=Set.copyOf(trainingIds);validationIds=Set.copyOf(validationIds);if(!java.util.Collections.disjoint(trainingIds,validationIds))throw new IllegalArgumentException("training/validation overlap");}}
