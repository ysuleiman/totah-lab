package totah.lab.aether.matrix;

import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.ScientificStatus;

/** Basis-independent fixed-nuclei Coulomb energy in hartree, with distances in bohr. */
public final class NuclearRepulsion {
    public static final String IMPLEMENTATION = "aether-nuclear-repulsion-1";
    public static final String PROTOCOL = "bohr;hartree;binary64;StrictMath.hypot;ordered-A<B-sum;no-screening;distinct-nuclei";
    private final double hartree;
    private final Receipt receipt;
    private NuclearRepulsion(double hartree, Receipt receipt) { this.hartree = hartree; this.receipt = receipt; }

    public static NuclearRepulsion calculate(QuantumSystem system) {
        double energy = 0;
        for (int a = 0; a < system.nuclei().size(); a++) for (int b = a + 1; b < system.nuclei().size(); b++) {
            var na = system.nuclei().get(a); var nb = system.nuclei().get(b);
            var x = na.centerBohr(); var y = nb.centerBohr();
            double distance = StrictMath.hypot(StrictMath.hypot(x.x()-y.x(), x.y()-y.y()), x.z()-y.z());
            if (!(distance > 0) || !Double.isFinite(distance)) throw new IllegalArgumentException("Coincident nuclei or nonfinite separation");
            energy += na.charge() * nb.charge() / distance;
            if (!Double.isFinite(energy)) throw new ArithmeticException("Nonfinite nuclear repulsion");
        }
        String systemHash = IntegralMatrixData.systemHash(system);
        // Empty basis identity is intentional: this scalar depends on nuclei, not AO functions.
        var identity = IntegralMatrixData.identity("", IMPLEMENTATION, PROTOCOL, "Fixed-nuclei repulsion only",
                "\n" + systemHash, "aether-Enuc-hartree-v1\n" + ContentHash.number(energy));
        return new NuclearRepulsion(energy, new Receipt(systemHash, IntegralMatrixData.nuclearCentersHash(system.nuclei()),
                PROTOCOL, identity.calculationHash(), identity.resultHash(), identity.receiptHash()));
    }
    public double hartree() { return hartree; }
    public Receipt receipt() { return receipt; }
    public record Receipt(String systemHash, String nuclearCentersHash, String protocol,
                          String calculationHash, String resultHash, String receiptHash) {
        public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    }
}
