package totah.lab.prometheus.potential;

/** Molecule-agnostic energy and force evaluator. */
@FunctionalInterface
public interface Potential {
    PotentialEvaluation evaluate(QuantumCoordinates coordinates);
}
