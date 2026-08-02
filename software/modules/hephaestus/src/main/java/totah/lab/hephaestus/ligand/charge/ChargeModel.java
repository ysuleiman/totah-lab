package totah.lab.hephaestus.ligand.charge;


/**
 * Pluggable partial charge model.
 */
public interface ChargeModel {
    /**
     * Assign partial charges to atoms.
     *
     * <p>Implementations that solve a constrained system (e.g. QEq) may return
     * an array longer than {@code system.size()}, with auxiliary unknowns such
     * as the Lagrange multiplier appended; only the first {@code system.size()}
     * entries are atom charges. Callers must bound their reads to
     * {@code system.size()}.
     *
     * @param system  Geometry + topology + element info
     * @param totalFormalCharge  Target total charge (usually 0)
     * @return  Array of partial charges, same order as atoms (see note above)
     */
    double[] computeCharges(ChargeSystem system, double totalFormalCharge);

    /** Return true if this model has parameters for the given element. */
    default boolean hasParameters(String element) { return true; }
}