package totah.lab.math.charges;

import totah.lab.math.linear.SparseMatrix;
import totah.lab.math.linear.LinearSolver;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * QEq: Rappé & Goddard charge equilibration (1991).
 *
 * <p>This implementation is equivalent to Open Babel's QEq:
 * <ul>
 *   <li>Loads parameters from Open Babel's qeq.txt format (4 columns)</li>
 *   <li>Uses same Gaussian-type orbital Coulomb integral (Chen & Martínez 2009)</li>
 *   <li>Same unit conversions and constraint handling</li>
 * </ul>
 *
 * <p>Built-in fallback parameters cover 103 elements from Open Babel's qeq.txt.
 * Users can override with their own qeq.txt file.
 */
public class QEqModel implements ChargeModel {

    // Open Babel qeq.txt format: Element  Eneg(V)  Hardness(V/e)  Radius(A)
    // These are the actual parameters from Open Babel's data/qeq.txt
    // Sources: Rappé & Goddard 1991 (published) + UFF-derived (unpublished)
    private static final Map<String, double[]> DEFAULT_PARAMETERS = loadBuiltinParameters();

    private static final double COULOMB_THRESHOLD = 1e-10;
    private static final double ANGSTROM_TO_BOHR = 1.889726;
    // Open Babel qeq.h: const double eV = 3.67493245e-2 (eV -> Hartree)
    private static final double EV_TO_HARTREE = 3.67493245e-2;

    private final LinearSolver solver;
    private final double coulombThreshold;
    private final Map<String, double[]> parameters;
    // Fallback warnings are emitted once per element, not once per atom
    private final Set<String> warnedFallback = new HashSet<>();

    public QEqModel(LinearSolver solver) {
        this(solver, COULOMB_THRESHOLD, null);
    }

    public QEqModel(LinearSolver solver, Path paramFile) {
        this(solver, COULOMB_THRESHOLD, paramFile);
    }

    public QEqModel(LinearSolver solver, double coulombThreshold, Path paramFile) {
        this.solver = solver;
        this.coulombThreshold = coulombThreshold;
        this.parameters = paramFile != null ? loadParameters(paramFile) : DEFAULT_PARAMETERS;
    }

    @Override
    public double[] computeCharges(ChargeSystem sys, double totalFormalCharge) {
        int n = sys.size();
        double[] chi = new double[n];
        double[] eta = new double[n];
        double[] basis = new double[n];

        for (int i = 0; i < n; i++) {
            String elem = sys.getElement(i);
            double[] p = parameters.get(elem);
            if (p == null) {
                if (warnedFallback.add(elem)) {
                    System.err.println("QEqModel: No parameters for element '" + elem
                            + "', using C");
                }
                p = parameters.get("C");
            }
            // Open Babel converts chi/eta from eV to Hartree so the hardness
            // matrix shares one unit system with the Coulomb integrals (bohr)
            chi[i] = p[0] * EV_TO_HARTREE;
            eta[i] = p[1] * EV_TO_HARTREE;
            double rBohr = p[2] * ANGSTROM_TO_BOHR;
            basis[i] = 1.0 / (rBohr * rBohr);
        }

        SparseMatrix H = buildHardnessMatrix(sys, n, eta, basis);
        double[] V = new double[n + 1];
        for (int i = 0; i < n; i++) {
            V[i] = -chi[i];
        }
        V[n] = totalFormalCharge;

        return solver.solve(H, V);
    }


    /**
     * True when QEq parameters are available for the given element symbol.
     */
    public boolean hasParameters(String element) {
        return parameters.containsKey(element);
    }


    private SparseMatrix buildHardnessMatrix(ChargeSystem sys, int n, double[] eta, double[] basis) {
        SparseMatrix H = new SparseMatrix(n + 1);

        for (int i = 0; i < n; i++) {
            H.set(i, i, eta[i]);
        }

        double minBasis = Arrays.stream(basis).min().orElse(1.0);
        double coulMaxDist = 2.0 * Math.sqrt(-Math.log(coulombThreshold) / minBasis);

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double dx = sys.getX(i) - sys.getX(j);
                double dy = sys.getY(i) - sys.getY(j);
                double dz = sys.getZ(i) - sys.getZ(j);
                double r = Math.sqrt(dx * dx + dy * dy + dz * dz);
                double rBohr = r * ANGSTROM_TO_BOHR;

                if (rBohr < coulMaxDist) {
                    double coulomb = coulombIntegral(basis[i], basis[j], rBohr);
                    H.set(i, j, coulomb);
                    H.set(j, i, coulomb);
                } else {
                    // Open Babel qeq.cpp: beyond the screening cutoff the
                    // interaction is the unscreened Coulomb term 1/R
                    H.set(i, j, 1.0 / rBohr);
                    H.set(j, i, 1.0 / rBohr);
                }
            }
        }

        for (int i = 0; i < n; i++) {
            H.set(i, n, 1.0);
            H.set(n, i, 1.0);
        }
        H.set(n, n, 0.0);

        return H;
    }

    private double coulombIntegral(double a, double b, double r) {
        double p = Math.sqrt(a * b / (a + b));
        return erf(p * r) / r;
    }

    private double erf(double x) {
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;

        int sign = (x >= 0) ? 1 : -1;
        x = Math.abs(x);
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        return sign * y;
    }

    private Map<String, double[]> loadParameters(Path file) {
        Map<String, double[]> loaded = new LinkedHashMap<>();
        try (BufferedReader br = Files.newBufferedReader(file)) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length < 4) continue;
                String elem = parts[0];
                double xi = Double.parseDouble(parts[1]);
                double eta = Double.parseDouble(parts[2]);
                double radius = Double.parseDouble(parts[3]);
                loaded.put(elem, new double[]{xi, eta, radius});
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load QEq parameters from " + file, e);
        }
        System.out.println("QEqModel: Loaded " + loaded.size() + " parameters from " + file);
        return loaded;
    }

    private static Map<String, double[]> loadBuiltinParameters() {
        Map<String, double[]> p = new LinkedHashMap<>();
        // All 103 elements from Open Babel's qeq.txt
        String[] data = {
                "H 4.528 13.8904 0.8271", "He 9.66 29.8400 1.7640", "Li 3.006 4.7720 2.4076",
                "Be 4.877 8.8860 1.2930", "B 5.11 9.5000 1.2094", "C 5.343 10.1260 1.1346",
                "N 6.899 11.7600 0.9770", "O 8.741 13.3640 0.8597", "F 10.874 14.9480 0.7686",
                "Ne 11.04 21.1000 1.4394", "Na 2.843 4.5920 2.5020", "Mg 3.951 7.3860 1.5555",
                "Al 4.06 7.1800 1.6002", "Si 4.168 6.9740 1.6474", "P 5.463 8.0000 1.4362",
                "S 6.928 8.9720 1.2806", "Cl 8.564 9.8920 1.1615", "Ar 9.465 12.7100 1.2259",
                "K 2.421 3.8400 2.9920", "Ca 3.231 5.7600 1.9947", "Sc 3.395 6.1600 1.8651",
                "Ti 3.47 6.7600 1.6996", "V 3.65 6.8200 1.6846", "Cr 3.415 7.7300 1.4863",
                "Mn 3.325 8.2100 1.3994", "Fe 3.76 8.2800 1.3876", "Co 4.105 8.3500 1.3760",
                "Ni 4.465 8.4100 1.3661", "Cu 4.2 8.4400 1.3613", "Zn 5.106 8.5700 1.3406",
                "Ga 3.641 6.3200 1.8179", "Ge 4.051 6.8760 1.6709", "As 5.188 7.6180 1.5082",
                "Se 6.428 8.2620 1.3906", "Br 7.79 8.8500 1.2982", "Kr 8.505 11.4300 1.0268",
                "Rb 2.331 3.6920 3.1119", "Sr 3.024 4.8800 2.3544", "Y 3.83 5.6200 2.0444",
                "Zr 3.4 7.1000 1.6182", "Nb 3.55 6.7600 1.6996", "Mo 3.465 7.5100 1.5299",
                "Tc 3.29 7.9800 1.4398", "Ru 3.575 8.0300 1.4308", "Rh 3.975 8.0100 1.4344",
                "Pd 4.32 8.0000 1.4362", "Ag 4.436 6.2680 1.8330", "Cd 5.034 7.9140 1.4518",
                "In 3.506 5.7920 1.9836", "Sn 3.987 6.2480 1.8389", "Sb 4.899 6.6840 1.7189",
                "Te 5.816 7.0520 1.6292", "I 6.822 7.5240 1.5270", "Xe 7.595 9.9500 1.1547",
                "Cs 2.183 3.4220 3.3575", "Ba 2.814 4.7920 2.3976", "La 2.8355 5.4830 2.0954",
                "Ce 2.774 5.3840 2.1340", "Pr 2.858 5.1280 2.2405", "Nd 2.8685 5.2410 2.1922",
                "Pm 2.881 5.3460 2.1491", "Sm 2.9115 5.4390 2.1124", "Eu 2.8785 5.5750 2.0609",
                "Gd 3.1665 5.9490 1.9313", "Tb 3.018 5.6680 2.0270", "Dy 3.0555 5.7430 2.0006",
                "Ho 3.127 5.7820 1.9871", "Er 3.1865 5.8290 1.9711", "Tm 3.2514 5.8658 1.9587",
                "Yb 3.2889 5.9300 1.9375", "Lu 2.9629 4.9258 2.3325", "Hf 3.7 6.8000 1.6896",
                "Ta 5.1 5.7000 2.0157", "W 4.63 6.6200 1.7355", "Re 3.96 7.8400 1.4655",
                "Os 5.14 7.2600 1.5825", "Ir 5 8.0000 1.4362", "Pt 4.79 8.8600 1.2968",
                "Au 4.894 5.1720 2.2214", "Hg 6.27 8.3200 1.3809", "Tl 3.2 5.8000 1.9809",
                "Pb 3.9 7.0600 1.6274", "Bi 4.69 7.4800 1.5360", "Po 4.21 8.4200 1.3645",
                "At 4.75 9.5000 1.2094", "Rn 5.37 10.7400 1.0698", "Fr 2 4.0000 2.8723",
                "Ra 2.843 4.8680 2.3602", "Ac 2.835 5.6700 2.0263", "Th 3.175 5.8100 1.9775",
                "Pa 2.985 5.8100 1.9775", "U 3.341 5.7060 2.0135", "Np 3.549 5.4340 2.1143",
                "Pu 3.243 5.6380 2.0378", "Am 2.9895 6.0070 1.9126", "Cm 2.8315 6.3790 1.8011",
                "Bk 3.1935 6.0710 1.8925", "Cf 3.197 6.2020 1.8525", "Es 3.333 6.1780 1.8597",
                "Fm 3.4 6.2000 1.8531", "Md 3.47 6.2200 1.8471", "No 3.475 6.3500 1.8093",
                "Lr 3.5 6.4000 1.7952"
        };
        for (String line : data) {
            String[] parts = line.split("\\s+");
            p.put(parts[0], new double[]{
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3])
            });
        }
        return p;
    }
}