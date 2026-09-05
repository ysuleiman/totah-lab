package totah.lab.mettl7.campaign.v2;

import totah.lab.daedalus.docking.VinaPoseOutputOptions;

import java.util.List;

/**
 * Pre-measurement controls for the clean v2 campaign. Values reproduce the
 * rigor of the existing controlled METTL7 campaigns and apply uniformly.
 */
public final class Mettl7MechanisticMatrixV2Protocol {

    public static final String CAMPAIGN_ID =
            "METTL7_MECHANISTIC_MATRIX_V2_CLEAN_REBUILD";
    public static final String DOCKING_ENGINE = "AutoDock Vina";
    public static final int EXHAUSTIVENESS = 32;
    public static final int MAXIMUM_MODES = 9;
    public static final double ENERGY_RANGE_KCAL_PER_MOL = 3.0;
    public static final List<Integer> SEEDS = List.of(1, 7, 42);
    public static final String PRIMARY_COFACTOR_STATE = "SAM";

    private Mettl7MechanisticMatrixV2Protocol() { }

    /** Lower bound before explicit chemical-species enumeration. */
    public static int nominalSeededRunCount() {
        return Mettl7MechanisticMatrixV2Panel.nominalDockingCellCount()
                * SEEDS.size();
    }

    public static VinaPoseOutputOptions poseOutputOptions() {
        return new VinaPoseOutputOptions(
                MAXIMUM_MODES, ENERGY_RANGE_KCAL_PER_MOL);
    }
}
