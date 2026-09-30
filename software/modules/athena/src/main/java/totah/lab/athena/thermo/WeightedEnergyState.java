package totah.lab.athena.thermo;

/** Energy plus explicit configuration-space measure represented by the state. */
public record WeightedEnergyState(String stateId, MolarEnergy energy, double measure) {
    public WeightedEnergyState {
        if (stateId == null || stateId.isBlank()) {
            throw new IllegalArgumentException("stateId must not be blank");
        }
        java.util.Objects.requireNonNull(energy, "energy");
        if (!Double.isFinite(measure) || measure <= 0.0) {
            throw new IllegalArgumentException("measure must be finite and positive");
        }
    }
}
