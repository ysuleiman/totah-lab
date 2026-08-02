package totah.lab.hephaestus.receptor.hydrogen;

public final class HydrogenGeometry {

    private HydrogenGeometry() {
    }

    // Bond lengths in Å
    public static final double C_H_SP3 = 1.09;
    public static final double C_H_SP2 = 1.08;
    public static final double N_H_SP3 = 1.01;
    public static final double N_H_SP2 = 1.00;
    public static final double O_H = 0.96;
    public static final double S_H = 1.34;
    public static final double C_OXT = 1.25;

    // Angles in radians
    public static final double TETRAHEDRAL_ANGLE =
            Math.toRadians(109.5);

    public static final double TRIGONAL_ANGLE =
            Math.toRadians(120.0);

    public static final double PLANAR_N_H_ANGLE =
            Math.toRadians(119.8);

    public static final double O_H_ANGLE =
            Math.toRadians(108.5);

    public static final double[] METHYL_DIHEDRALS = {
            Math.toRadians(60.0),
            Math.toRadians(180.0),
            Math.toRadians(-60.0)
    };

    public static final double[] METHYLENE_DIHEDRALS = {
            Math.toRadians(120.0),
            Math.toRadians(-120.0)
    };

    public static final double[] PLANAR_NH2_DIHEDRALS = {
            0.0,
            Math.PI
    };
}
