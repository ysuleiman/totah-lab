package totah.lab.athena.relaxation;

import java.util.Objects;
import java.util.Optional;

/** Fully explicit bounded local-minimization policy. No scientific defaults. */
public record LocalRelaxationProtocol(
        String protocolId,
        double receptorMobileRadiusAngstroms,
        BackbonePolicy backbonePolicy,
        Optional<HarmonicForceConstant> backboneRestraintForceConstant,
        SideChainPolicy sideChainPolicy,
        Optional<HarmonicForceConstant> localSideChainRestraintForceConstant,
        Restraint ligandRestraint,
        Restraint samRestraint,
        SystemAtomMapping systemAtomMapping,
        AtomSelectionRules atomSelectionRules,
        String minimizerType,
        double forceToleranceKilojoulesPerMoleNanometre,
        int maximumIterations,
        Integer cpuThreads,
        boolean deterministicForces) {
    public LocalRelaxationProtocol {
        protocolId = require(protocolId, "protocolId");
        if (!Double.isFinite(receptorMobileRadiusAngstroms)
                || receptorMobileRadiusAngstroms <= 0.0) {
            throw new IllegalArgumentException("mobile radius must be positive");
        }
        Objects.requireNonNull(backbonePolicy, "backbonePolicy");
        backboneRestraintForceConstant = Objects.requireNonNull(
                backboneRestraintForceConstant, "backboneRestraintForceConstant");
        Objects.requireNonNull(sideChainPolicy, "sideChainPolicy");
        localSideChainRestraintForceConstant = Objects.requireNonNull(
                localSideChainRestraintForceConstant, "localSideChainRestraintForceConstant");
        if (backbonePolicy == BackbonePolicy.HARMONICALLY_RESTRAINED
                && backboneRestraintForceConstant.isEmpty()) {
            throw new IllegalArgumentException("restrained backbone requires an explicit force constant");
        }
        if (backbonePolicy == BackbonePolicy.FIXED
                && backboneRestraintForceConstant.isPresent()) {
            throw new IllegalArgumentException("fixed backbone must not declare a restraint force constant");
        }
        if (sideChainPolicy == SideChainPolicy.WITHIN_RADIUS_RESTRAINED
                && localSideChainRestraintForceConstant.isEmpty()) {
            throw new IllegalArgumentException("restrained local side chains require an explicit force constant");
        }
        if (sideChainPolicy == SideChainPolicy.WITHIN_RADIUS_MOBILE
                && localSideChainRestraintForceConstant.isPresent()) {
            throw new IllegalArgumentException("mobile local side chains must not declare a restraint force constant");
        }
        Objects.requireNonNull(ligandRestraint, "ligandRestraint");
        Objects.requireNonNull(samRestraint, "samRestraint");
        Objects.requireNonNull(systemAtomMapping, "systemAtomMapping");
        Objects.requireNonNull(atomSelectionRules, "atomSelectionRules");
        minimizerType = require(minimizerType, "minimizerType");
        if (!Double.isFinite(forceToleranceKilojoulesPerMoleNanometre)
                || forceToleranceKilojoulesPerMoleNanometre <= 0.0
                || maximumIterations <= 0 || cpuThreads == null || cpuThreads <= 0) {
            throw new IllegalArgumentException("invalid minimization settings");
        }
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public enum BackbonePolicy { FIXED, HARMONICALLY_RESTRAINED }
    public enum SideChainPolicy { WITHIN_RADIUS_MOBILE, WITHIN_RADIUS_RESTRAINED }

    public record Restraint(Policy policy,
            Optional<HarmonicForceConstant> forceConstant,
            double maximumDisplacementAngstroms) {
        public Restraint {
            Objects.requireNonNull(policy, "policy");
            forceConstant = Objects.requireNonNull(forceConstant, "forceConstant");
            if (!Double.isFinite(maximumDisplacementAngstroms)
                    || maximumDisplacementAngstroms < 0.0) {
                throw new IllegalArgumentException("invalid restraint");
            }
            if (policy == Policy.NONE && forceConstant.isPresent()) {
                throw new IllegalArgumentException("NONE restraint must not have a force constant");
            }
            if (policy != Policy.NONE && forceConstant.isEmpty()) {
                throw new IllegalArgumentException("active restraint requires an explicit force constant");
            }
        }
    }

    public enum Policy { NONE, POSITIONAL, POSITIONAL_AND_ORIENTATIONAL }
}
