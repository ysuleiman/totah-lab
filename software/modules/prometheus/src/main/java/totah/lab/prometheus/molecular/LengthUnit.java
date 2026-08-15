package totah.lab.prometheus.molecular;

public enum LengthUnit { BOHR, ANGSTROM;
    private static final double BOHR_PER_ANGSTROM=1.8897261254578281;
    public double toBohr(double value){return this==BOHR?value:value*BOHR_PER_ANGSTROM;}
}
