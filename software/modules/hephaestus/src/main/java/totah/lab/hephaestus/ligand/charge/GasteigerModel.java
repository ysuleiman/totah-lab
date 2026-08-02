package totah.lab.hephaestus.ligand.charge;

import java.util.Arrays;
import java.util.Map;

/**
 * Gasteiger-Marsili iterative partial charge model
 * (Gasteiger &amp; Marsili, Tetrahedron 36, 3219, 1980).
 * Fast, approximate, no matrix solve required.
 *
 * <p>Per iteration k the electronegativity of each atom is evaluated as
 * &chi;(q) = a + b&middot;q + c&middot;q&sup2;, charge flows pairwise across each
 * bond from the less to the more electronegative atom, and the transfer is
 * damped by (1/2)^k - the scheme used by Open Babel's OBGastChrg. Pairwise
 * transfers conserve the total charge by construction.
 */
public class GasteigerModel implements ChargeModel {

    // a, b, c from Gasteiger-Marsili 1980 Table I.
    private static final Map<String, AtomParameters> PARAMETERS = Map.ofEntries(
            Map.entry("H",  new AtomParameters(7.17, 6.24, -0.56)),
            Map.entry("C:SP3", new AtomParameters(7.98, 9.18, 1.88)),
            Map.entry("C:SP2", new AtomParameters(8.79, 9.32, 1.51)),
            Map.entry("C:SP",  new AtomParameters(10.39, 9.45, 0.73)),
            Map.entry("N:SP3", new AtomParameters(11.54, 10.82, 1.36)),
            Map.entry("N:SP2", new AtomParameters(12.87, 11.15, 0.85)),
            Map.entry("N:SP",  new AtomParameters(15.68, 11.70, -0.27)),
            Map.entry("O:SP3", new AtomParameters(14.18, 12.92, 1.39)),
            Map.entry("O:SP2", new AtomParameters(17.07, 13.79, 0.47)),
            Map.entry("S",  new AtomParameters(10.14, 9.13, 1.38)),
            Map.entry("P",  new AtomParameters(8.90, 8.24, 0.96)),
            Map.entry("F",  new AtomParameters(14.66, 13.85, 2.31)),
            Map.entry("Cl", new AtomParameters(11.00, 9.69, 1.35)),
            Map.entry("Br", new AtomParameters(10.08, 8.47, 1.16)),
            Map.entry("I",  new AtomParameters(9.90, 7.96, 0.96))
    );

    // Open Babel molchrg.h: fixed denominator when hydrogen receives charge
    private static final double HYDROGEN_DENOM = 20.02;

    private final int maxIterations;

    public GasteigerModel() {
        this(6);
    }

    public GasteigerModel(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    @Override
    public double[] computeCharges(ChargeSystem sys, double totalFormalCharge) {
        int n = sys.size();
        double[] q = initializeCharges(sys);
        AtomParameters[] params = new AtomParameters[n];
        for (int i = 0; i < n; i++) {
            params[i] = getParams(sys, i);
        }

        double damping = 1.0;
        for (int iter = 0; iter < maxIterations; iter++) {
            damping *= 0.5; // (1/2)^k damping per iteration

            double[] chi = new double[n];
            for (int i = 0; i < n; i++) {
                chi[i] = params[i].chi(q[i]);
            }

            // Pairwise bond transfers; each bond is visited once (j > i), so
            // the total charge is conserved exactly at every step
            for (int i = 0; i < n; i++) {
                for (int j : sys.getNeighbors(i)) {
                    if (j <= i) continue;
                    int src = chi[i] >= chi[j] ? i : j;
                    int dst = chi[i] >= chi[j] ? j : i;
                    // Denominator is chi(+1) = a+b+c of the receiving atom
                    // (fixed 20.02 for hydrogen, as in Open Babel)
                    double denom = "H".equals(sys.getElement(dst))
                            ? HYDROGEN_DENOM
                            : params[dst].denom();
                    double transfer = (chi[src] - chi[dst]) / denom;
                    q[src] -= damping * transfer;
                    q[dst] += damping * transfer;
                }
            }
        }

        // Normalize to target total charge
        double sum = Arrays.stream(q).sum();
        double correction = (sum - totalFormalCharge) / n;
        for (int i = 0; i < n; i++) {
            q[i] -= correction;
        }

        return q;
    }

    /** Gasteiger-Marsili seeds the iteration with per-atom formal charges. */
    private double[] initializeCharges(ChargeSystem sys) {
        double[] charges = new double[sys.size()];
        for (int index = 0; index < charges.length; index++) {
            charges[index] = sys.getFormalCharge(index);
        }
        return charges;
    }

    private AtomParameters getParams(ChargeSystem system, int atomIndex) {
        String element = system.getElement(atomIndex);
        String key = switch (element) {
            case "C", "N" -> element + ":" + hybridization(system, atomIndex);
            case "O" -> element + ":" + (isSp2(system, atomIndex) ? "SP2" : "SP3");
            default -> element;
        };
        AtomParameters parameters = PARAMETERS.get(key);
        if (parameters == null) {
            throw new IllegalArgumentException(
                    "No Gasteiger parameters for atom " + atomIndex + " (" + key + ")");
        }
        return parameters;
    }

    @Override
    public boolean hasParameters(String element) {
        return PARAMETERS.containsKey(element)
                || PARAMETERS.containsKey(element + ":SP3");
    }

    private String hybridization(ChargeSystem system, int atomIndex) {
        double maximumOrder = maximumBondOrder(system, atomIndex);
        if (maximumOrder >= 2.5) {
            return "SP";
        }
        return isSp2(system, atomIndex) ? "SP2" : "SP3";
    }

    private boolean isSp2(ChargeSystem system, int atomIndex) {
        return system.isAromatic(atomIndex)
                || maximumBondOrder(system, atomIndex) >= 1.5;
    }

    private double maximumBondOrder(ChargeSystem system, int atomIndex) {
        double maximum = 0.0;
        for (int neighbor : system.getNeighbors(atomIndex)) {
            maximum = Math.max(
                    maximum,
                    system.getBondOrder(atomIndex, neighbor));
        }
        return maximum;
    }

    private record AtomParameters(double a, double b, double c) {
        /** Electronegativity chi(q) = a + b*q + c*q^2 */
        double chi(double q) {
            return a + b * q + c * q * q;
        }

        /** chi(+1) = a + b + c, the transfer denominator */
        double denom() {
            return a + b + c;
        }
    }
}
