package totah.lab.hephaestus.amber;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * AMBER force-field Lennard-Jones parameters (R* and ε) per atom type.
 *
 * <p>Singleton that auto-loads from classpath on first use.
 * Parses parm10.dat or equivalent AMBER parameter files for the
 * nonbonded (MOD4) section.
 *
 * <p>Format expected (3 columns per line after the MOD4 header):
 * <pre>
 *   atom_type  Rmin/2(Å)  epsilon(kcal/mol)
 * </pre>
 *
 * <p>Combining rules: Lorentz-Berthelot
 * <pre>
 *   Rmin_ij = Rmin_i + Rmin_j      (arithmetic)
 *   eps_ij  = sqrt(eps_i * eps_j)  (geometric)
 * </pre>
 */
public class AmberParameterSet {

    public static final String DEFAULT_RESOURCE = "/amber/lib/parm10.dat";
    private static final AmberParameterSet INSTANCE = new AmberParameterSet();

    private final Map<String, double[]> ljParams = new HashMap<>();
    private volatile boolean loaded = false;
    private boolean warnedUnloaded = false;

    private AmberParameterSet() {}

    /**
     * Returns the singleton instance, auto-loading from classpath
     * on first call if not already loaded.
     */
    public static AmberParameterSet getInstance() {
        if (!INSTANCE.loaded) {
            synchronized (INSTANCE) {
                if (!INSTANCE.loaded) {
                    try {
                        INSTANCE.loadFromResource(DEFAULT_RESOURCE);
                    } catch (IOException e) {
                        System.err.println("[AmberParameterSet] Failed to auto-load "
                                + DEFAULT_RESOURCE + ": " + e.getMessage());
                    }
                }
            }
        }
        return INSTANCE;
    }

    /**
     * For testing: create a fresh empty instance (not the singleton).
     */
    public static AmberParameterSet createEmpty() {
        return new AmberParameterSet();
    }

    /**
     * Load from a classpath resource (e.g. "/amber/lib/parm10.dat").
     */
    public void loadFromResource(String resourcePath) throws IOException {
        try (InputStream is = getClass().getResourceAsStream(resourcePath)) {
            if (is == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }
            parse(new BufferedReader(new InputStreamReader(is)));
        }
    }

    /**
     * Load from a file on disk.
     */
    public void loadFromFile(Path path) throws IOException {
        try (BufferedReader br = Files.newBufferedReader(path)) {
            parse(br);
        }
    }

    private void parse(BufferedReader br) throws IOException {
        boolean inLJ = false;
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;

            // Section markers in parm10.dat style
            String upper = line.toUpperCase();
            if (upper.startsWith("MOD4") || upper.startsWith("NONBON") || upper.startsWith("LJEDIT")) {
                inLJ = true;
                continue;
            }
            if (upper.startsWith("BONDS") || upper.startsWith("ANGLES") ||
                    upper.startsWith("DIHED") || upper.startsWith("IMPROPER") ||
                    upper.startsWith("HBOND")) {
                inLJ = false;
                continue;
            }

            if (!inLJ) continue;

            String[] parts = line.split("\s+");
            if (parts.length < 3) continue;

            // AMBER atom types are 1-2 uppercase chars (e.g. CT, O, N3, HW)
            String atomType = parts[0];
            if (!atomType.matches("[A-Z][A-Z0-9]?")) continue;

            try {
                double radius = Double.parseDouble(parts[1]); // Rmin/2 in Å
                double epsilon = Double.parseDouble(parts[2]); // well depth in kcal/mol
                ljParams.put(atomType, new double[]{radius, epsilon});
            } catch (NumberFormatException e) {
                // skip malformed lines
            }
        }
        loaded = true;
        System.out.println("[AmberParameterSet] Loaded " + ljParams.size() + " LJ atom types");
    }

    /**
     * Returns {Rmin/2, epsilon} for the given AMBER atom type, or null.
     * Logs once if the parameter set was never loaded — an unloaded set
     * otherwise makes every LJ term silently evaluate to 0.
     */
    public double[] getLJ(String amberAtomType) {
        if (!loaded && !warnedUnloaded) {
            warnedUnloaded = true;
            System.err.println("[AmberParameterSet] Warning: LJ parameters not loaded; "
                    + "all LJ energies evaluate to 0.0");
        }
        return ljParams.get(amberAtomType);
    }

    public boolean hasLJ(String amberAtomType) {
        return ljParams.containsKey(amberAtomType);
    }

    /**
     * Lennard-Jones energy (kcal/mol) between two atom types at distance r.
     *
     * <p>Uses standard 12-6 form with Lorentz-Berthelot combining rules:
     * <pre>
     *   E = 4·ε·[ (σ/r)^12 - (σ/r)^6 ]
     *   σ = (σ_i + σ_j)/2
     *   ε = sqrt(ε_i · ε_j)
     * </pre>
     *
     * <p>where σ = Rmin / 2^(1/6)  (zero-crossing distance).
     */
    public double ljEnergy(String typeI, String typeJ, double r) {
        double[] pI = getLJ(typeI);
        double[] pJ = getLJ(typeJ);
        if (pI == null || pJ == null) return 0.0;

        // Stored values are already Rmin/2; AMBER combines them additively:
        // Rmin_ij = Rmin/2_i + Rmin/2_j
        double rminIJ = pI[0] + pJ[0];

        double epsI = pI[1];
        double epsJ = pJ[1];
        double epsIJ = Math.sqrt(epsI * epsJ);

        // Convert Rmin to sigma (zero-crossing distance)
        // Rmin = sigma * 2^(1/6)  =>  sigma = Rmin / 2^(1/6)
        double sigma = rminIJ / 1.1224620483093729;  // divide by 2^(1/6)

        double sr = sigma / r;
        double sr6 = sr * sr * sr * sr * sr * sr;
        double sr12 = sr6 * sr6;

        return 4.0 * epsIJ * (sr12 - sr6);
    }

    /**
     * Add a single LJ parameter (for testing).
     */
    public void addParameter(String atomType, double rmin2, double epsilon) {
        ljParams.put(atomType, new double[]{rmin2, epsilon});
        loaded = true;
    }
}