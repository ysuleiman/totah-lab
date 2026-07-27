package totah.lab.protein.hydrogenation;

import lombok.ToString;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;

import java.nio.file.Path;
import java.util.Set;

/**
 * Immutable protonation configuration.
 * Can be built directly or parsed from a PipelineContext.
 */
@ToString
public final class ProtonationConfig {

    public enum HisState { HIE, HID, HIP, AUTO }

    // pKa constants
    public static final double PKA_ASP = 3.9;
    public static final double PKA_GLU = 4.3;
    public static final double PKA_CYS = 8.3;
    public static final double PKA_TYR = 10.1;
    public static final double PKA_LYS = 10.5;

    // Defaults
    public static final double DEFAULT_PH = 7.4;
    public static final double DEFAULT_VOXEL = 0.5;
    public static final double DEFAULT_CLASH = 1.0;
    public static final double DEFAULT_SS_CUTOFF = 2.2;
    public static final double DEFAULT_METAL_CUTOFF = 4.0;

    // Metal elements that suppress protonation on nearby O/N/S atoms
    // (ChimeraX rule: no H on electronegative atom X within ~4 Å of metal M)
    public static final Set<String> METAL_ELEMENTS = Set.of(
            "LI", "NA", "K", "RB", "CS",           // alkali
            "BE", "MG", "CA", "SR", "BA",         // alkaline earth
            "SC", "TI", "V", "CR", "MN", "FE", "CO", "NI", "CU", "ZN",
            "Y", "ZR", "NB", "MO", "TC", "RU", "RH", "PD", "AG", "CD",
            "LU", "HF", "TA", "W", "RE", "OS", "IR", "PT", "AU", "HG",
            "AL", "GA", "IN", "SN", "TL", "PB", "BI");

    private final double ph;
    private final double voxelGridSize;
    private final double clashCutoff;
    private final HisState hisState;
    private final boolean detectDisulfides;
    private final double disulfideCutoff;
    private final double metalCutoff;
    private final String nCap;
    private final String cCap;
    private final Object amberParmPath;

    private ProtonationConfig(Builder b) {
        this.ph = b.ph;
        this.voxelGridSize = b.voxelGridSize;
        this.clashCutoff = b.clashCutoff;
        this.hisState = b.hisState;
        this.detectDisulfides = b.detectDisulfides;
        this.disulfideCutoff = b.disulfideCutoff;
        this.metalCutoff = b.metalCutoff;
        this.nCap = b.nCap;
        this.cCap = b.cCap;
        this.amberParmPath = b.amberParmPath;
    }

    public double ph() { return ph; }
    public double voxelGridSize() { return voxelGridSize; }
    public double clashCutoff() { return clashCutoff; }
    public HisState hisState() { return hisState; }
    public boolean detectDisulfides() { return detectDisulfides; }
    public double disulfideCutoff() { return disulfideCutoff; }
    public double metalCutoff() { return metalCutoff; }
    public String nCap() { return nCap; }
    public String cCap() { return cCap; }
    public Object amberParmPath() { return amberParmPath; }

    public static Builder builder() { return new Builder(); }

    public static ProtonationConfig fromContext(PipelineContext ctx) {
        Builder b = builder();
        b.ph = parseDouble(ctx, ContextKeys.PH, DEFAULT_PH);
        b.voxelGridSize = parseDouble(ctx, ContextKeys.VOXEL_GRID_SIZE, DEFAULT_VOXEL);
        b.clashCutoff = parseDouble(ctx, ContextKeys.HYDROGEN_CLASH_CUTOFF, DEFAULT_CLASH);
        b.hisState = parseHisState(ctx);
        b.detectDisulfides = parseBoolean(ctx, ContextKeys.DETECT_DISULFIDES, true);
        b.disulfideCutoff = parseDouble(ctx, ContextKeys.DISULFIDE_CUTOFF, DEFAULT_SS_CUTOFF);
        b.metalCutoff = parseDouble(ctx, ContextKeys.METAL_COORDINATION_CUTOFF, DEFAULT_METAL_CUTOFF);
        b.nCap = parseString(ctx, ContextKeys.CAP_N_TERMINUS, "NH3+");
        b.cCap = parseString(ctx, ContextKeys.CAP_C_TERMINUS, "COO-");
        b.amberParmPath = ctx.get(ContextKeys.AMBER_PARM_PATH);
        return b.build();
    }

    // -------------------- parsing helpers --------------------

    private static HisState parseHisState(PipelineContext ctx) {
        Object val = ctx.get(ContextKeys.HIS_PROTONATION_STATE);
        if (val == null) return HisState.HIE;
        String str = val.toString().trim().toUpperCase();
        if ("AUTO".equals(str)) {
            System.err.println("[ReceptorHydrogenation] hisProtonationState=AUTO is no longer supported. " +
                    "Use PROPKA or PDB2PQR, then pass HIE/HID/HIP explicitly. Defaulting to HIE.");
            return HisState.HIE;
        }
        try {
            return HisState.valueOf(str);
        } catch (IllegalArgumentException e) {
            System.err.println("[ReceptorHydrogenation] Invalid hisProtonationState '" + str +
                    "', using default HIE.");
            return HisState.HIE;
        }
    }

    private static boolean parseBoolean(PipelineContext ctx, String key, boolean def) {
        Object v = ctx.get(key);
        return v == null ? def : (v instanceof Boolean ? (Boolean) v : Boolean.parseBoolean(v.toString()));
    }

    private static String parseString(PipelineContext ctx, String key, String def) {
        Object v = ctx.get(key);
        return v != null ? v.toString() : def;
    }

    private static double parseDouble(PipelineContext ctx, String key, double def) {
        Object v = ctx.get(key);
        if (v == null) return def;
        if (v instanceof Number) return ((Number) v).doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            System.err.println("[ReceptorHydrogenation] Invalid number for '" + key + "': " + v);
            return def;
        }
    }

    public static final class Builder {
        private double ph = DEFAULT_PH;
        private double voxelGridSize = DEFAULT_VOXEL;
        private double clashCutoff = DEFAULT_CLASH;
        private HisState hisState = HisState.HIE;
        private boolean detectDisulfides = true;
        private double disulfideCutoff = DEFAULT_SS_CUTOFF;
        private double metalCutoff = DEFAULT_METAL_CUTOFF;
        private String nCap = "NH3+";
        private String cCap = "COO-";
        private Object amberParmPath;

        private Builder() {}

        public ProtonationConfig build() { return new ProtonationConfig(this); }
    }
}
