package totah.lab.prometheus.comparability;

import java.util.Objects;

import totah.lab.prometheus.evidence.CalculationType;

/**
 * The kind of energy target a calculation speaks to. Only evidence aimed at the
 * same target can ever be energetically comparable.
 */
public enum EnergyTarget {
    CONFORMATIONAL,
    INTERACTION,
    ELECTROSTATIC_POTENTIAL,
    FORCE_CONSTANT,
    CLASSICAL;

    /** Maps a calculation type to the energy target its results speak to. */
    public static EnergyTarget of(CalculationType type) {
        Objects.requireNonNull(type, "type");
        return switch (type) {
            case OPTIMIZATION, SINGLE_POINT, TORSION_SCAN, CONSTRAINED_SCAN -> CONFORMATIONAL;
            case INTERACTION_ENERGY, COUNTERPOISE_INTERACTION -> INTERACTION;
            case ESP, RESP -> ELECTROSTATIC_POTENTIAL;
            case HESSIAN, FORCE_EVALUATION -> FORCE_CONSTANT;
            case CLASSICAL_FIXED_GEOMETRY_ENERGY, ENERGY_DECOMPOSITION -> CLASSICAL;
        };
    }
}
