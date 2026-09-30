package totah.lab.aether.matrix;

import java.util.Optional;
import totah.lab.aether.provenance.ContentHash;
import totah.lab.aether.provenance.OneShotRhfReceipt;
import totah.lab.aether.provenance.ScientificStatus;
import static totah.lab.aether.matrix.OccupiedDensityCalculator.requireEqual;

/** Energy of one supplied state. A new orbital density requires its own fresh Fock assembly. */
public final class RhfEnergyCalculator {
    public static final String IMPLEMENTATION = "aether-rhf-supplied-state-energy-1";
    public static final String PROTOCOL = "hartree;binary64;row-major-ordered-sum;Eelec=0.5*sum(P*(Hcore+F));"
            + "Etotal=Eelec+Enuc;exact-input-density-hash;" + DensityMatrix.CONVENTION;
    public static final String EVALUATION = "SUPPLIED_STATE_NO_SCF_PERFORMED";
    private RhfEnergyCalculator() {}

    /** External symmetric densities may be non-idempotent and non-self-consistent. */
    public static Result evaluate(DensityMatrix density, CoreHamiltonianMatrix core, FockMatrix fock) {
        return evaluate(density, core, fock, Optional.empty());
    }
    /** Retains the complete occupied-MO lineage, in addition to the Fock input-density lineage. */
    public static Result evaluate(OccupiedDensityCalculator.Result density, CoreHamiltonianMatrix core, FockMatrix fock) {
        return evaluate(density.density(), core, fock, Optional.of(density.receipt()));
    }
    private static Result evaluate(DensityMatrix density, CoreHamiltonianMatrix core, FockMatrix fock,
                                    Optional<OccupiedDensityCalculator.Receipt> build) {
        OccupiedDensityCalculator.occupation(density.system(), density.size());
        var source = fock.receipt();
        requireEqual(density.systemHash(), source.systemHash(), "density/Fock system");
        requireEqual(density.basisGeometryHash(), source.basisGeometryHash(), "density/Fock basis/geometry/order");
        requireEqual(density.densityHash(), source.densityHash(), "Fock input density");
        requireEqual(core.receipt().receiptHash(), source.coreReceipt().receiptHash(), "Hcore/Fock");
        requireEqual(OneShotRhfCalculator.IMPLEMENTATION, source.implementation(), "Fock implementation");
        requireEqual(IntegralMatrixData.protocol(OneShotRhfCalculator.PROTOCOL, density.functions()), source.protocol(), "Fock numerical protocol");
        if (density.size() != core.size() || density.size() != fock.size()) throw new IllegalArgumentException("Energy dimensions mismatch");
        double electronic = MeanFieldArithmetic.rhfEnergy(density,core::get,fock::get);
        var nuclear = NuclearRepulsion.calculate(density.system());
        double total = electronic + nuclear.hartree();
        if (!Double.isFinite(electronic) || !Double.isFinite(total)) throw new ArithmeticException("Nonfinite RHF state energy");
        String protocol = IntegralMatrixData.protocol(PROTOCOL, density.functions());
        String sources = "\n" + source.receiptHash() + "\n" + nuclear.receipt().receiptHash() + "\n"
                + build.map(OccupiedDensityCalculator.Receipt::receiptHash).orElse("EXTERNAL_DENSITY");
        var identity = IntegralMatrixData.identity(density.basisGeometryHash(), IMPLEMENTATION, protocol, EVALUATION, sources,
                "aether-RHF-state-energy-v1\n" + ContentHash.number(electronic) + "\n" + ContentHash.number(nuclear.hartree())
                        + "\n" + ContentHash.number(total));
        var receipt = new Receipt(source, build, nuclear.receipt(), protocol, EVALUATION,
                identity.calculationHash(), identity.resultHash(), identity.receiptHash());
        return new Result(electronic, nuclear.hartree(), total, receipt);
    }
    public record Receipt(OneShotRhfReceipt fockSource, Optional<OccupiedDensityCalculator.Receipt> densityConstruction,
                          NuclearRepulsion.Receipt nuclearSource, String protocol, String evaluation,
                          String calculationHash, String resultHash, String receiptHash) {
        public ScientificStatus status() { return ScientificStatus.SCREENING_ONLY; }
    }
    public static final class Result {
        private final double electronicHartree, nuclearHartree, totalHartree;
        private final Receipt receipt;
        private Result(double electronic, double nuclear, double total, Receipt receipt) {
            electronicHartree = electronic; nuclearHartree = nuclear; totalHartree = total; this.receipt = receipt;
        }
        public double electronicHartree() { return electronicHartree; }
        public double nuclearHartree() { return nuclearHartree; }
        public double totalHartree() { return totalHartree; }
        public Receipt receipt() { return receipt; }
    }
}
