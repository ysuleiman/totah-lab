package totah.lab.aether.matrix;

import java.util.ArrayList;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.OneShotRhfReceipt;
import totah.lab.aether.provenance.ScientificStatus;

/** Occupy the lowest-energy orbitals once; this operation does not perform SCF. */
public final class OccupiedDensityCalculator {
    public static final String IMPLEMENTATION = "aether-occupied-density-1";
    public static final String PROTOCOL = "binary64;dimensionless;" + DensityMatrix.CONVENTION
            + ";singlet;ascending-energy-columns;first-Ne/2;ordered-sum;CTSC=I;trace(PS)=Ne;PSP=2P;tolerance=1e-10";
    private static final String REASON = "Occupied-orbital density construction only; no self-consistency assertion";
    private OccupiedDensityCalculator() {}

    /** Count electrons from the current H/C/N/O system; never infer occupations from a density trace. */
    public static Occupation occupation(QuantumSystem system, int orbitalCount) {
        long electrons = system.nuclei().stream().mapToLong(n -> (long) n.charge()).sum() - system.molecularCharge();
        if (system.multiplicity() != 1 || electrons % 2 != 0) {
            throw new IllegalArgumentException("Closed-shell occupation requires even electrons and singlet multiplicity");
        }
        if (orbitalCount < 1 || electrons < 0 || electrons / 2 > orbitalCount) {
            throw new IllegalArgumentException("Impossible occupation in the supplied orbital space");
        }
        return new Occupation(electrons, (int) (electrons / 2), orbitalCount, IntegralMatrixData.systemHash(system));
    }

    /** C and energies must be evidence from the same validated orbital solve. */
    public static Result build(QuantumSystem system, OverlapMatrix overlap,
                               MolecularOrbitalCoefficients coefficients, OrbitalEnergies energies) {
        var source = coefficients.receipt();
        requireEqual(IntegralMatrixData.systemHash(system), source.systemHash(), "C/system");
        requireEqual(source.receiptHash(), energies.receipt().receiptHash(), "C/energies");
        requireEqual(source.overlapReceipt().receiptHash(), overlap.receipt().receiptHash(), "C/S");
        requireEqual(IntegralMatrixData.protocol(OneShotRhfCalculator.PROTOCOL, overlap.functions()), source.protocol(), "orbital protocol");
        requireEqual(OneShotRhfCalculator.IMPLEMENTATION, source.implementation(), "orbital implementation");
        var construction = construct(system, overlap, coefficients.size(), energies.size(), coefficients::get, energies::get);
        var occupation = construction.occupation();
        var density = construction.density();
        double trace = construction.trace(), idempotency = construction.idempotency(), orthonormality = construction.orthonormality();
        String protocol = IntegralMatrixData.protocol(PROTOCOL, overlap.functions());
        String sources = "\n" + source.receiptHash() + "\n" + occupation.receiptHash() + "\n" + density.densityHash();
        var identity = IntegralMatrixData.identity(source.basisGeometryHash(), IMPLEMENTATION, protocol, REASON, sources,
                "aether-occupied-density-v1\n" + density.densityHash() + "\n" + ContentHash.number(trace) + "\n"
                        + ContentHash.number(idempotency) + "\n" + ContentHash.number(orthonormality));
        var receipt = new Receipt(source, occupation, density.densityHash(), protocol, trace, idempotency,
                orthonormality, identity.calculationHash(), identity.resultHash(), identity.receiptHash());
        return new Result(density, receipt);
    }

    /** Shared occupied-density mathematics for validated physical or DIIS update orbitals. */
    static Construction construct(QuantumSystem system, OverlapMatrix overlap, int n, int energyCount,
                                  IntegralMatrixData.Entry coefficients, java.util.function.IntToDoubleFunction energies) {
        var occupation = occupation(system, n);
        if (overlap.size() != n || energyCount != n) throw new IllegalArgumentException("Orbital dimensions mismatch");
        double orthonormality = validateOrbitals(n, coefficients, overlap, energies);
        var density = formDensity(system,overlap,occupation,coefficients);
        double trace = 0, idempotency = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            trace += density.get(i,j) * overlap.get(j,i);
            double psp = 0;
            for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) {
                psp += density.get(i,a) * overlap.get(a,b) * density.get(b,j);
            }
            idempotency = Math.max(idempotency, Math.abs(psp - 2 * density.get(i,j)));
        }
        checkError(Math.abs(trace - occupation.electrons()), "electron count");
        checkError(idempotency, "doubled-density idempotency");
        return new Construction(density, occupation, trace, idempotency, orthonormality);
    }
    static DensityMatrix formDensity(QuantumSystem system,OverlapMatrix overlap,Occupation occupation,IntegralMatrixData.Entry coefficients) {
        int n=overlap.size();var entries = new ArrayList<Double>(n * n);
        for (int mu = 0; mu < n; mu++) for (int nu = 0; nu < n; nu++) {
            double value = 0;
            for (int i = 0; i < occupation.occupiedOrbitals(); i++) {
                value += 2 * (coefficients.get(mu, i) * coefficients.get(nu, i));
            }
            entries.add(value);
        }
        return DensityMatrix.fromRowMajor(system, overlap.functions(), entries);
    }
    record Construction(DensityMatrix density, Occupation occupation, double trace, double idempotency, double orthonormality) {}

    // Package-private for fault injection: production C is immutable and has no unchecked public constructor.
    static double validateOrbitals(MolecularOrbitalCoefficients c, OverlapMatrix s, OrbitalEnergies energies) {
        return validateOrbitals(c.size(), c::get, s, energies::get);
    }
    private static double validateOrbitals(int n, IntegralMatrixData.Entry c, OverlapMatrix s,
                                           java.util.function.IntToDoubleFunction energies) {
        for (int i = 0; i < n; i++) {
            if (!Double.isFinite(energies.applyAsDouble(i)) || (i > 0 && energies.applyAsDouble(i) < energies.applyAsDouble(i-1))) {
                throw new IllegalArgumentException("Orbital energies must be finite and ascending");
            }
            for (int j = 0; j < n; j++) if (!Double.isFinite(c.get(i,j))) {
                throw new IllegalArgumentException("Nonfinite MO coefficients");
            }
        }
        double error = 0;
        for (int i = 0; i < n; i++) for (int j = 0; j < n; j++) {
            double value = 0;
            for (int a = 0; a < n; a++) for (int b = 0; b < n; b++) value += c.get(a,i) * s.get(a,b) * c.get(b,j);
            error = Math.max(error, Math.abs(value - (i == j ? 1 : 0)));
        }
        checkError(error, "MO orthonormality");
        return error;
    }

    private static void checkError(double error, String label) {
        if (!Double.isFinite(error) || error > 1e-10) throw new IllegalArgumentException("Failed " + label);
    }
    static void requireEqual(String expected, String actual, String label) {
        if (!expected.equals(actual)) throw new IllegalArgumentException("Incompatible " + label + " provenance");
    }

    /** Immutable occupation evidence; the selected columns are [0, occupiedOrbitals). */
    public static final class Occupation {
        private final long electrons;
        private final int occupiedOrbitals, orbitalCount;
        private final String systemHash, receiptHash;
        private Occupation(long electrons, int occupiedOrbitals, int orbitalCount, String systemHash) {
            this.electrons = electrons; this.occupiedOrbitals = occupiedOrbitals; this.orbitalCount = orbitalCount;
            this.systemHash = systemHash;
            receiptHash = ContentHash.sha256("aether-occupation-v1\n" + PROTOCOL + "\n" + systemHash + "\n"
                    + electrons + "\n" + occupiedOrbitals + "\n" + orbitalCount + "\nSCREENING_ONLY");
        }
        public long electrons() { return electrons; }
        public int occupiedOrbitals() { return occupiedOrbitals; }
        public int orbitalCount() { return orbitalCount; }
        public String systemHash() { return systemHash; }
        public String receiptHash() { return receiptHash; }
        public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
        @Override public String toString() { return "Occupation[" + electrons + "," + occupiedOrbitals + "," + orbitalCount + "," + systemHash + "," + receiptHash + "]"; }
    }
    public record Receipt(OneShotRhfReceipt orbitalSource, Occupation occupation, String densityHash,
                          String protocol, double tracePS, double maxIdempotencyError, double maxOrthonormalityError,
                          String calculationHash, String resultHash, String receiptHash) {
        public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    }
    public static final class Result {
        private final DensityMatrix density;
        private final Receipt receipt;
        private Result(DensityMatrix density, Receipt receipt) { this.density = density; this.receipt = receipt; }
        public DensityMatrix density() { return density; }
        public Receipt receipt() { return receipt; }
    }
}
