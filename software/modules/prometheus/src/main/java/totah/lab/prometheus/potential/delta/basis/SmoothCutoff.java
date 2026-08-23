package totah.lab.prometheus.potential.delta.basis;

/** Locked C2 quintic switch from 4.0 to 4.5 angstrom. */
public final class SmoothCutoff {
    public static final double SWITCH_START=4.0, CUTOFF=4.5;
    private SmoothCutoff(){}
    public static double value(double r){if(r<=SWITCH_START)return 1; if(r>=CUTOFF)return 0; double u=(r-SWITCH_START)/(CUTOFF-SWITCH_START);return 1-10*u*u*u+15*u*u*u*u-6*u*u*u*u*u;}
    public static double derivative(double r){if(r<=SWITCH_START||r>=CUTOFF)return 0; double u=(r-SWITCH_START)/(CUTOFF-SWITCH_START);return (-30*u*u+60*u*u*u-30*u*u*u*u)/(CUTOFF-SWITCH_START);}
}
