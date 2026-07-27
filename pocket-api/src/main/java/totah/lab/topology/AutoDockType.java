package totah.lab.topology;


public enum AutoDockType {
    C("C", false),   // Aliphatic Carbon
    A("A", true),    // Aromatic Carbon
    N("N", false),   // Nitrogen (Non-acceptor)
    NA("NA", true),  // Nitrogen (Acceptor)
    O("O", false),   // Oxygen (Non-acceptor)
    OA("OA", true),  // Oxygen (Acceptor)
    S("S", false),   // Sulfur
    SA("SA", true),  // Sulfur (Acceptor)
    HD("HD", false), // Hydrogen donor (bound to N or O)
    H("H", false),   // Non-polar Hydrogen (bound to C)
    F("F", false),   // Fluorine
    Cl("Cl", false), // Chlorine
    Br("Br", false), // Bromine
    I("I", false),   // Iodine
    Mg("Mg", false), // Magnesium
    Mn("Mn", false), // Manganese
    Fe("Fe", false), // Iron
    Zn("Zn", false), // Zinc
    Ca("Ca", false); // Calcium

    private final String symbol;
    private final boolean isAcceptorOrAromatic;

    AutoDockType(String symbol, boolean isAcceptorOrAromatic) {
        this.symbol = symbol;
        this.isAcceptorOrAromatic = isAcceptorOrAromatic;
    }

    public String getSymbol() { return symbol; }
}