package totah.lab.athena.design.backend.ocl;

import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.backend.MolecularGraph;
import totah.lab.athena.design.backend.ConformerMinimizer;

import java.util.Objects;

/** Calculates frozen-conformation strain with one explicitly identified MMFF94S implementation. */
public final class OclMmff94sStrainCalculator {
    public static final String DEFINITION_ID = "OCL_2026_7_2_MMFF94S_DOCKED_MINUS_MINIMIZED_REFERENCE_V1";
    public static final int REFERENCE_MAXIMUM_ITERATIONS = 2000;

    private final OclGraphMapper mapper = new OclGraphMapper();
    private final ConformerMinimizer minimizer;

    public OclMmff94sStrainCalculator() {
        this(new OclMolecularBackend());
    }

    OclMmff94sStrainCalculator(ConformerMinimizer minimizer) {
        this.minimizer = Objects.requireNonNull(minimizer, "minimizer");
    }

    public Result calculate(MolecularGraph docked, MolecularGraph reference) throws MolecularBackendException {
        Objects.requireNonNull(docked, "docked");
        Objects.requireNonNull(reference, "reference");
        requireSameAtomIdentity(docked, reference);
        try {
            double dockedEnergy = energy(docked);
            var minimized = minimizer.minimize(reference,
                    new ConformerMinimizer.Configuration("MMFF94S", REFERENCE_MAXIMUM_ITERATIONS,
                            0.0001, 0.000001));
            if (!minimized.converged()) {
                throw new MolecularBackendException(
                        "MMFF94S reference minimization did not converge; strain is unavailable");
            }
            double referenceEnergy = minimized.energy();
            return new Result(DEFINITION_ID, OclMolecularBackend.BACKEND, OclMolecularBackend.VERSION,
                    dockedEnergy, referenceEnergy, dockedEnergy - referenceEnergy, minimized.converged(),
                    REFERENCE_MAXIMUM_ITERATIONS);
        } catch (Exception exception) {
            throw new MolecularBackendException("MMFF94S strain calculation failed", exception);
        }
    }

    private double energy(MolecularGraph graph) throws MolecularBackendException {
        return OclMmff94Support.create(mapper, graph,
                OclMmff94Support.resolve("MMFF94S")).getTotalEnergy();
    }

    private static void requireSameAtomIdentity(MolecularGraph docked, MolecularGraph reference)
            throws MolecularBackendException {
        if (docked.atoms().size() != reference.atoms().size()
                || docked.bonds().size() != reference.bonds().size()) {
            throw new MolecularBackendException("docked and reference graphs differ in size");
        }
        for (int i = 0; i < docked.atoms().size(); i++) {
            var first = docked.atoms().get(i); var second = reference.atoms().get(i);
            if (!first.id().equals(second.id()) || !first.element().equals(second.element())
                    || first.formalCharge() != second.formalCharge()) {
                throw new MolecularBackendException("docked and reference atom identity/order differs at " + i);
            }
        }
        for (int i = 0; i < docked.bonds().size(); i++) {
            var first = docked.bonds().get(i); var second = reference.bonds().get(i);
            if (!first.firstAtomId().equals(second.firstAtomId())
                    || !first.secondAtomId().equals(second.secondAtomId()) || first.order() != second.order()) {
                throw new MolecularBackendException("docked and reference bond identity/order differs at " + i);
            }
        }
    }

    public record Result(String definitionId, String backend, String backendVersion,
                         double dockedEnergyKcalMol, double referenceEnergyKcalMol,
                         double strainKcalMol, boolean referenceConverged,
                         int referenceMaximumIterations) { }
}
