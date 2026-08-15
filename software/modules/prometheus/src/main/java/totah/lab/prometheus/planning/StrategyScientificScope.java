package totah.lab.prometheus.planning;

import java.util.Objects;
import java.util.Set;

/** Declares the model terms a strategy fits; capability claims are validated against this scope. */
public record StrategyScientificScope(
        Set<FunctionalFormRequirement> parameterizedTerms,
        boolean claimsBondedSolution,
        boolean claimsNonbondedSolution,
        boolean openMmCompatible,
        boolean amberCompatible) {
    public StrategyScientificScope {
        parameterizedTerms = Set.copyOf(Objects.requireNonNull(parameterizedTerms, "parameterizedTerms"));
        if (claimsNonbondedSolution && parameterizedTerms.stream().noneMatch(
                term -> term == FunctionalFormRequirement.FIXED_ATOM_CHARGES
                        || term == FunctionalFormRequirement.LENNARD_JONES
                        || term == FunctionalFormRequirement.POLARIZATION)) {
            throw new IllegalArgumentException(
                    "strategy cannot claim a nonbonded solution without parameterizing nonbonded terms");
        }
        if (claimsBondedSolution && parameterizedTerms.stream().noneMatch(
                term -> term == FunctionalFormRequirement.HARMONIC_BONDS
                        || term == FunctionalFormRequirement.HARMONIC_ANGLES
                        || term == FunctionalFormRequirement.FOURIER_TORSIONS
                        || term == FunctionalFormRequirement.COUPLED_COORDINATES)) {
            throw new IllegalArgumentException(
                    "strategy cannot claim a bonded solution without parameterizing bonded terms");
        }
    }
}
