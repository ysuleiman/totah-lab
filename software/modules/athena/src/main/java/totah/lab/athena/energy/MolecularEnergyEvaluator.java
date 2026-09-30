package totah.lab.athena.energy;

/** Adapter boundary for validated external molecular-mechanics engines. */
public interface MolecularEnergyEvaluator {
    String evaluatorId();

    EnergyEvaluation evaluate(MolecularState state) throws EnergyEvaluationException;
}
