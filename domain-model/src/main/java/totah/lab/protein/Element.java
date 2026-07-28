package totah.lab.protein;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public enum Element {

    H ("H",   1,   1.008,   1.20, 0.31),
    HE("He",  2,   4.0026,  1.40, 0.28),

    LI("Li",  3,   6.94,    1.82, 1.28),
    BE("Be",  4,   9.0122,  1.53, 0.96),
    B ("B",   5,  10.81,    1.92, 0.84),
    C ("C",   6,  12.011,   1.70, 0.76),
    N ("N",   7,  14.007,   1.55, 0.71),
    O ("O",   8,  15.999,   1.52, 0.66),
    F ("F",   9,  18.998,   1.47, 0.57),
    NE("Ne", 10,  20.180,   1.54, 0.58),

    NA("Na", 11,  22.990,   2.27, 1.66),
    MG("Mg", 12,  24.305,   1.73, 1.41),
    AL("Al", 13,  26.982,   1.84, 1.21),
    SI("Si", 14,  28.085,   2.10, 1.11),
    P ("P",  15,  30.974,   1.80, 1.07),
    S ("S",  16,  32.06,    1.80, 1.05),
    CL("Cl", 17,  35.45,    1.75, 1.02),
    AR("Ar", 18,  39.948,   1.88, 1.06),

    K ("K",  19,  39.0983,  2.75, 2.03),
    CA("Ca", 20,  40.078,   2.31, 1.76),

    FE("Fe", 26,  55.845,   2.00, 1.24),
    CU("Cu", 29,  63.546,   1.40, 1.32),
    ZN("Zn", 30,  65.38,    1.39, 1.22),

    SE("Se", 34,  78.971,   1.90, 1.20),
    BR("Br", 35,  79.904,   1.85, 1.20),

    I ("I",  53, 126.90447, 1.98, 1.39),

    UNKNOWN("?", 0, 0.0, 0.0, 0.0);

    private final String symbol;
    private final int atomicNumber;
    private final double atomicMass;
    private final double vanDerWaalsRadius;
    private final double covalentRadius;

    Element(String symbol,
            int atomicNumber,
            double atomicMass,
            double vanDerWaalsRadius,
            double covalentRadius) {

        this.symbol = symbol;
        this.atomicNumber = atomicNumber;
        this.atomicMass = atomicMass;
        this.vanDerWaalsRadius = vanDerWaalsRadius;
        this.covalentRadius = covalentRadius;
    }

    public String getSymbol() {
        return symbol;
    }

    public int getAtomicNumber() {
        return atomicNumber;
    }

    public double getAtomicMass() {
        return atomicMass;
    }

    public double getVanDerWaalsRadius() {
        return vanDerWaalsRadius;
    }

    public double getCovalentRadius() {
        return covalentRadius;
    }

    public boolean isHydrogen() {
        return this == H;
    }

    public boolean isHeavy() {
        return this != H && this != UNKNOWN;
    }

    public boolean isHalogen() {
        return this == F || this == CL || this == BR || this == I;
    }

    public boolean isMetal() {
        return switch (this) {
            case LI, BE, NA, MG, AL, K, CA, FE, CU, ZN -> true;
            default -> false;
        };
    }

    public boolean isUnknown() {
        return this == UNKNOWN;
    }

    @Override
    public String toString() {
        return symbol;
    }

    private static final Map<String, Element> LOOKUP = new HashMap<>();

    static {
        for (Element element : values()) {
            LOOKUP.put(element.symbol.toUpperCase(Locale.ROOT), element);
        }
    }

    public static Element fromSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return UNKNOWN;
        }

        return LOOKUP.getOrDefault(
                symbol.trim().toUpperCase(Locale.ROOT),
                UNKNOWN);
    }

    public double getVanDerWaalsRadiusOrDefault(double defaultRadius) {
        return this == UNKNOWN
                ? defaultRadius
                : vanDerWaalsRadius;
    }
}