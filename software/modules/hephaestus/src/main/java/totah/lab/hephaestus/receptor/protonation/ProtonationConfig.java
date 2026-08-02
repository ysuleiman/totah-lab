package totah.lab.hephaestus.receptor.protonation;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Set;

public record ProtonationConfig(
        double ph,
        double voxelGridSize,
        double clashCutoff,
        HistidineState histidineState,
        boolean detectDisulfides,
        double disulfideCutoff,
        double metalCoordinationCutoff,
        NTerminusState nTerminusState,
        CTerminusState cTerminusState,
        Path amberParameterPath) {

    public static final double PKA_ASP = 3.9;
    public static final double PKA_GLU = 4.3;
    public static final double PKA_CYS = 8.3;
    public static final double PKA_TYR = 10.1;
    public static final double PKA_LYS = 10.5;

    public static final double DEFAULT_PH = 7.4;
    public static final double DEFAULT_VOXEL_GRID_SIZE = 0.5;
    public static final double DEFAULT_CLASH_CUTOFF = 1.0;
    public static final double DEFAULT_DISULFIDE_CUTOFF = 2.2;
    public static final double DEFAULT_METAL_COORDINATION_CUTOFF = 4.0;

    public static final Set<String> METAL_ELEMENTS = Set.of(
            "LI", "NA", "K", "RB", "CS",
            "BE", "MG", "CA", "SR", "BA",
            "SC", "TI", "V", "CR", "MN", "FE", "CO", "NI", "CU", "ZN",
            "Y", "ZR", "NB", "MO", "TC", "RU", "RH", "PD", "AG", "CD",
            "LU", "HF", "TA", "W", "RE", "OS", "IR", "PT", "AU", "HG",
            "AL", "GA", "IN", "SN", "TL", "PB", "BI"
    );

    public ProtonationConfig {
        validateRange(ph, 0.0, 14.0, "ph");
        validatePositive(voxelGridSize, "voxelGridSize");
        validatePositive(clashCutoff, "clashCutoff");
        validatePositive(disulfideCutoff, "disulfideCutoff");
        validatePositive(
                metalCoordinationCutoff,
                "metalCoordinationCutoff");

        Objects.requireNonNull(
                histidineState,
                "histidineState");

        Objects.requireNonNull(
                nTerminusState,
                "nTerminusState");

        Objects.requireNonNull(
                cTerminusState,
                "cTerminusState");
    }

    public static ProtonationConfig defaults() {
        return new ProtonationConfig(
                DEFAULT_PH,
                DEFAULT_VOXEL_GRID_SIZE,
                DEFAULT_CLASH_CUTOFF,
                HistidineState.HIE,
                true,
                DEFAULT_DISULFIDE_CUTOFF,
                DEFAULT_METAL_COORDINATION_CUTOFF,
                NTerminusState.NH3,
                CTerminusState.COO,
                null
        );
    }

    public ProtonationConfig withPh(double ph) {
        return new ProtonationConfig(
                ph,
                voxelGridSize,
                clashCutoff,
                histidineState,
                detectDisulfides,
                disulfideCutoff,
                metalCoordinationCutoff,
                nTerminusState,
                cTerminusState,
                amberParameterPath
        );
    }

    public ProtonationConfig withHistidineState(
            HistidineState histidineState) {

        return new ProtonationConfig(
                ph,
                voxelGridSize,
                clashCutoff,
                histidineState,
                detectDisulfides,
                disulfideCutoff,
                metalCoordinationCutoff,
                nTerminusState,
                cTerminusState,
                amberParameterPath
        );
    }

    private static void validatePositive(
            double value,
            String fieldName) {

        if (!Double.isFinite(value) || value <= 0.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be finite and positive.");
        }
    }

    private static void validateRange(
            double value,
            double minimum,
            double maximum,
            String fieldName) {

        if (!Double.isFinite(value)
                || value < minimum
                || value > maximum) {

            throw new IllegalArgumentException(
                    fieldName
                            + " must be between "
                            + minimum
                            + " and "
                            + maximum
                            + ".");
        }
    }
}
