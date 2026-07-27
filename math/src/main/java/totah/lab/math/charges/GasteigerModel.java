package totah.lab.math.charges;

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

    // a, b, c from Gasteiger-Marsili 1980 (Table I; sp3 values where the paper
    // distinguishes hybridization - ChargeSystem carries no hybridization info)
    private static final Map<String, AtomParameters> PARAMETERS = Map.ofEntries(
            Map.entry("H",  new AtomParameters(7.17, 6.24, -0.56)),
            Map.entry("C",  new AtomParameters(7.98, 9.18, 1.88)),
            Map.entry("N",  new AtomParameters(11.54, 10.82, 1.36)),
            Map.entry("O",  new AtomParameters(14.18, 12.92, 1.39)),
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
            params[i] = getParams(sys.getElement(i));
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

    /**
     * Gasteiger-Marsili seeds the iteration with formal charges. ChargeSystem
     * does not carry per-atom formal charges, so start neutral; the final
     * normalization enforces the requested total charge.
     */
    private double[] initializeCharges(ChargeSystem sys) {
        return new double[sys.size()];
    }

    private AtomParameters getParams(String element) {
        return PARAMETERS.getOrDefault(element, PARAMETERS.get("C"));
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