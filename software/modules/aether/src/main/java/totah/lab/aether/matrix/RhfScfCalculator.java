package totah.lab.aether.matrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import totah.lab.aether.basis.ContractedGaussian;
import totah.lab.aether.basis.Sto3gHydrogen;
import totah.lab.aether.model.QuantumSystem;
import totah.lab.aether.provenance.ContentHash;
import static totah.lab.aether.matrix.RhfScfResult.Status;

/** Plain deterministic closed-shell Roothaan SCF, with no acceleration or density mixing. */
public final class RhfScfCalculator {
    public static final String IMPLEMENTATION = "aether-rhf-scf-1";
    public static final String INITIAL_GUESS = "CORE_HAMILTONIAN;F(zero-P)=Hcore;Lowdin;lowest-Ne/2-doubly-occupied";
    public static final double ENERGY_THRESHOLD = 1e-12;
    public static final double DENSITY_THRESHOLD = 1e-10;
    public static final int DEFAULT_MAX_ITERATIONS = 128;
    public static final String PROTOCOL = "Java21;binary64;bohr;hartree;STO-3G-H;s-only;singlet;plain-Roothaan;"
            + "no-DIIS;no-damping;no-mixing;no-level-shift;E[n]=E(P[n],F(P[n]));"
            + "deltaE=abs(E[n]-E[n-1]);densityResidual=maxAbs(P[n+1]-P[n]);"
            + "both-criteria-required;first-deltaE-unavailable;return-consistent-P[n]-F[n]-C[n];"
            + OneShotRhfCalculator.PROTOCOL + ";" + OccupiedDensityCalculator.PROTOCOL + ";" + RhfEnergyCalculator.PROTOCOL;
    private RhfScfCalculator() {}

    public static RhfScfResult solve(QuantumSystem system, List<ContractedGaussian> basis) throws IOException {
        return solve(system, basis, DEFAULT_MAX_ITERATIONS);
    }

    /** Explicit policy; legacy overloads remain byte-compatible plain SCF. */
    public static RhfScfRun solve(QuantumSystem system, List<ContractedGaussian> basis, ScfPolicy policy) throws IOException {
        return solve(system, basis, DEFAULT_MAX_ITERATIONS, policy);
    }
    public static RhfScfRun solve(QuantumSystem system, List<ContractedGaussian> basis, int maximumIterations, ScfPolicy policy) throws IOException {
        Objects.requireNonNull(policy);
        return policy == ScfPolicy.PLAIN ? RhfScfRun.plain(solve(system, basis, maximumIterations))
                : DiisRhfScf.solve(system, basis, maximumIterations);
    }

    /** Ghost-aware SCF uses the same frozen DIIS policy; native-basis entry points remain strict. */
    public static RhfScfRun solve(GhostBasis basis) throws IOException {
        return solve(basis, DEFAULT_MAX_ITERATIONS);
    }
    public static RhfScfRun solve(GhostBasis basis, int maximumIterations) throws IOException {
        Objects.requireNonNull(basis);
        return DiisRhfScf.solve(basis.system(), basis.functions(), maximumIterations, basis);
    }

    /** The cap is explicit and receipt-bound; convergence thresholds and guess policy are frozen. */
    public static RhfScfResult solve(QuantumSystem system, List<ContractedGaussian> basis, int maximumIterations) throws IOException {
        Objects.requireNonNull(system);
        var functions = List.copyOf(basis);
        if (maximumIterations < 1) throw new IllegalArgumentException("SCF iteration cap must be positive");
        long started = System.nanoTime();
        var iterations = new ArrayList<RhfScfResult.Iteration>();
        OccupiedDensityCalculator.Result initial = null;
        long jkTime = 0, eigenTime = 0;
        try {
            validateScope(system, functions);
        } catch (IllegalArgumentException unsupported) {
            return finish(system, functions, maximumIterations, iterations, initial, Status.UNSUPPORTED_SYSTEM,
                    unsupported.getMessage(), jkTime, eigenTime, started);
        }
        try {
            // Validate distinct nuclei before producing any SCF state.
            NuclearRepulsion.calculate(system);
            var overlap = OverlapMatrix.compute(functions);
            var core = new CoreHamiltonianCalculator(system, functions).calculate();
            var eri = new ElectronRepulsionCalculator(system, functions).calculate();
            var zero = DensityMatrix.fromRowMajor(system, functions, Collections.nCopies(Math.multiplyExact(functions.size(), functions.size()), 0.0));
            long timer = System.nanoTime();
            var zeroJk = JkCalculator.calculate(zero, eri);
            jkTime += System.nanoTime() - timer;
            // Reuse the validated one-shot solver: exact zero J/K makes F exactly Hcore.
            var guess = OneShotRhfCalculator.solve(zero, overlap, core, zeroJk);
            eigenTime += eigensolveTime(guess);
            initial = OccupiedDensityCalculator.build(system, overlap, guess.coefficients(), guess.energies());
            var density = initial;
            OptionalDouble previousEnergy = OptionalDouble.empty();
            for (int index = 0; index < maximumIterations; index++) {
                int iteration = index + 1;
                timer = System.nanoTime();
                var jk = JkCalculator.calculate(density.density(), eri);
                jkTime += System.nanoTime() - timer;
                var orbitals = OneShotRhfCalculator.solve(density.density(), overlap, core, jk);
                eigenTime += eigensolveTime(orbitals);
                var energy = RhfEnergyCalculator.evaluate(density, core, orbitals.fock());
                var next = OccupiedDensityCalculator.build(system, overlap, orbitals.coefficients(), orbitals.energies());
                var delta = previousEnergy.isPresent()
                        ? OptionalDouble.of(Math.abs(energy.totalHartree() - previousEnergy.getAsDouble())) : OptionalDouble.empty();
                double residual = densityResidual(density.density(), next.density());
                if ((delta.isPresent() && !Double.isFinite(delta.getAsDouble())) || !Double.isFinite(residual)) {
                    throw new ArithmeticException("Nonfinite SCF convergence metric");
                }
                boolean energyPassed = delta.isPresent() && delta.getAsDouble() <= ENERGY_THRESHOLD;
                boolean densityPassed = residual <= DENSITY_THRESHOLD;
                var energies = new ArrayList<Double>();
                for (int i = 0; i < orbitals.energies().size(); i++) energies.add(orbitals.energies().get(i));
                var receipt = new RhfScfResult.IterationReceipt(iteration, energy.electronicHartree(), energy.totalHartree(),
                        delta, residual, energies, density.density().densityHash(), orbitals.receipt().fockHash(),
                        next.density().densityHash(), orbitals.receipt().receiptHash(), energy.receipt().receiptHash(),
                        density.receipt().receiptHash(), next.receipt().receiptHash(), energyPassed, densityPassed);
                iterations.add(new RhfScfResult.Iteration(iteration, density, orbitals, next, energy, receipt));
                if (energyPassed && densityPassed) {
                    return finish(system, functions, maximumIterations, iterations, initial, Status.CONVERGED,
                            "Both total-energy and density criteria passed", jkTime, eigenTime, started);
                }
                previousEnergy = OptionalDouble.of(energy.totalHartree());
                density = next;
            }
            return finish(system, functions, maximumIterations, iterations, initial, Status.MAX_ITERATIONS,
                    "Iteration cap reached without both convergence criteria", jkTime, eigenTime, started);
        } catch (IllegalArgumentException | ArithmeticException numerical) {
            return finish(system, functions, maximumIterations, iterations, initial, Status.NUMERICAL_FAILURE,
                    numerical.getMessage(), jkTime, eigenTime, started);
        }
    }

    static void validateScope(QuantumSystem system, List<ContractedGaussian> functions) throws IOException {
        OccupiedDensityCalculator.occupation(system, functions.size());
        if(BasisScope.def2(functions)) {
            var expectedDef2=new ArrayList<>(totah.lab.aether.basis.Def2SvpBasis.load().forSystem(system).stream()
                    .map(f->IntegralMatrixData.basisGeometryHash(List.of(f))).toList());
            if(functions.size()!=expectedDef2.size())throw new IllegalArgumentException("Incomplete def2-SVP basis");
            for(var f:functions)if(!expectedDef2.remove(IntegralMatrixData.basisGeometryHash(List.of(f))))
                throw new IllegalArgumentException("Incompatible def2-SVP basis/system");
            return;
        }
        var expected = new ArrayList<String>();
        if (system.nuclei().stream().allMatch(n -> n.charge() == 1)) {
            var hydrogen = Sto3gHydrogen.load();
            for (var nucleus : system.nuclei()) expected.add(IntegralMatrixData.basisGeometryHash(List.of(hydrogen.atBohr(nucleus.centerBohr()))));
        } else {
            for (var function : totah.lab.aether.basis.Sto3gBasis.load().forSystem(system))
                expected.add(IntegralMatrixData.basisGeometryHash(List.of(function)));
        }
        String scope = system.nuclei().stream().anyMatch(n -> n.charge() >= 15) ? "H/C/N/O/P/S/Cl" : "H/C/N/O";
        if (functions.size() != expected.size()) throw new IllegalArgumentException("SCF requires the complete STO-3G " + scope + " basis");
        for (var function : functions) {
            if (!expected.remove(IntegralMatrixData.basisGeometryHash(List.of(function)))) {
                throw new IllegalArgumentException("SCF supports the bundled STO-3G " + scope + " basis at the system nuclei only");
            }
        }
    }

    static double densityResidual(DensityMatrix current, DensityMatrix next) {
        double maximum = 0;
        for (int i = 0; i < current.size(); i++) for (int j = 0; j < current.size(); j++) {
            maximum = Math.max(maximum, Math.abs(next.get(i,j) - current.get(i,j)));
        }
        return maximum;
    }
    private static long eigensolveTime(OneShotRhfResult result) {
        var p = result.performanceCounters();
        return p.overlapEigendecompositionNanos() + p.fockTransformNanos() + p.fockEigendecompositionNanos();
    }

    private static RhfScfResult finish(QuantumSystem system, List<ContractedGaussian> functions, int cap,
                                       List<RhfScfResult.Iteration> iterations, OccupiedDensityCalculator.Result initial,
                                       Status status, String reason, long jkTime, long eigenTime, long started) {
        String protocol = IntegralMatrixData.protocol(PROTOCOL, functions);
        if (system.nuclei().stream().anyMatch(n -> n.charge() >= 15)) {
            protocol = protocol.replace("STO-3G-H-C-N-O;", "STO-3G-H-C-N-O-P-S-Cl;")
                    + ";SPCl-basis-sha256=" + totah.lab.aether.basis.Sto3gBasis.SPCL_RESOURCE_SHA256;
        }
        String systemHash = IntegralMatrixData.systemHash(system);
        String basisHash = functions.isEmpty() ? ContentHash.sha256("aether-empty-basis-v1") : IntegralMatrixData.basisGeometryHash(functions);
        String initialHash = initial == null ? "UNAVAILABLE" : initial.receipt().receiptHash();
        var receipts = iterations.stream().map(RhfScfResult.Iteration::receipt).toList();
        String sources = "\n" + systemHash + "\n" + INITIAL_GUESS + "\n" + ContentHash.number(ENERGY_THRESHOLD)
                + "\n" + ContentHash.number(DENSITY_THRESHOLD) + "\n" + cap;
        StringBuilder canonical = new StringBuilder("aether-scf-trajectory-v1\n").append(initialHash).append('\n');
        for (var r : receipts) {
            canonical.append(r.iterationNumber()).append('\n').append(ContentHash.number(r.electronicEnergy())).append('\n')
                    .append(ContentHash.number(r.totalEnergy())).append('\n')
                    .append(r.deltaEnergy().isPresent() ? ContentHash.number(r.deltaEnergy().getAsDouble()) : "UNAVAILABLE").append('\n')
                    .append(ContentHash.number(r.densityResidual())).append('\n');
            for (double e : r.orbitalEnergies()) canonical.append(ContentHash.number(e)).append('\n');
            canonical.append(r.densityHash()).append('\n').append(r.fockHash()).append('\n').append(r.outputDensityHash()).append('\n')
                    .append(r.orbitalReceiptHash()).append('\n').append(r.energyReceiptHash()).append('\n')
                    .append(r.inputConstructionHash()).append('\n').append(r.outputConstructionHash()).append('\n')
                    .append(r.energyCriterionPassed()).append('\n').append(r.densityCriterionPassed()).append('\n');
        }
        canonical.append(status).append('\n');
        var identity = IntegralMatrixData.identity(basisHash, IMPLEMENTATION, protocol, reason, sources, canonical.toString());
        var receipt = new RhfScfResult.Receipt(IMPLEMENTATION, protocol, systemHash, basisHash, INITIAL_GUESS, initialHash,
                ENERGY_THRESHOLD, DENSITY_THRESHOLD, cap, receipts, status, reason,
                identity.calculationHash(), identity.resultHash(), identity.receiptHash());
        OptionalDouble delta = receipts.isEmpty() ? OptionalDouble.empty() : receipts.getLast().deltaEnergy();
        OptionalDouble residual = receipts.isEmpty() ? OptionalDouble.empty() : OptionalDouble.of(receipts.getLast().densityResidual());
        return new RhfScfResult(iterations, Optional.ofNullable(initial), receipt,
                new RhfScfResult.PerformanceCounters(functions.size(), iterations.size(), jkTime, eigenTime,
                        System.nanoTime() - started, delta, residual));
    }
}
