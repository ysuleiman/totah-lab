package totah.lab.athena.energy.openmm;

/** Frozen numerical tolerances for controlled OpenMM backend validation only. */
public final class OpenMmValidationTolerances {
    public static final double DETERMINISTIC_ENERGY_KJ_PER_MOL = 1.0e-9;
    public static final double DIRECT_REFERENCE_ENERGY_KJ_PER_MOL = 1.0e-9;
    public static final double FORCE_GROUP_SUM_KJ_PER_MOL = 1.0e-8;

    private OpenMmValidationTolerances() {}
}
