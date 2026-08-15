package totah.lab.prometheus.planning;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import totah.lab.prometheus.comparability.ComparabilityDecision;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceBundle;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QuantumEvidence;

/** Pure matcher from strategy requirements to authoritative evidence; it never executes calculations. */
public final class StrategyEvidenceMatcher {
    private final ProtocolComparability comparability;
    private final CostModel costModel;

    public StrategyEvidenceMatcher(ProtocolComparability comparability, CostModel costModel) {
        this.comparability = Objects.requireNonNull(comparability, "comparability");
        this.costModel = Objects.requireNonNull(costModel, "costModel");
    }

    public MissingEvidencePlan match(
            EvidenceRequirementSet requirementSet,
            EvidenceBundle evidence,
            Set<String> availableDependencies) {
        Objects.requireNonNull(requirementSet, "requirementSet");
        Objects.requireNonNull(evidence, "evidence");
        availableDependencies = Set.copyOf(Objects.requireNonNull(availableDependencies, "availableDependencies"));

        List<StrategyRequirementResolution> resolutions = new ArrayList<>();
        List<CalculationSpecification> calculations = new ArrayList<>();
        Set<String> calculationKeys = new HashSet<>();
        int index = 0;
        for (StrategyEvidenceRequirement requirement : requirementSet.requirements()) {
            index++;
            StrategyRequirementResolution resolution = resolve(requirement, evidence, availableDependencies);
            resolutions.add(resolution);
            if (resolution.decision() == EvidenceReuseDecision.GENERATE_NEW) {
                CalculationSpecification specification = specify(requirementSet.strategyId(), index, requirement);
                if (calculationKeys.add(specification.checksum())) calculations.add(specification);
            }
        }
        return new MissingEvidencePlan(requirementSet.strategyId(), resolutions, calculations);
    }

    private StrategyRequirementResolution resolve(
            StrategyEvidenceRequirement requirement,
            EvidenceBundle bundle,
            Set<String> availableDependencies) {
        ScientificEvidenceRequirement scientific = requirement.scientific();
        EvidenceRequirement requested = scientific.requirement();
        if (!requirement.scientificallyRequired()) {
            return result(requirement, EvidenceReuseDecision.INCOMPATIBLE_EXISTING, List.of(),
                    "optional requirement is not included in the minimal plan", "");
        }
        if (requested.geometry() == null) {
            return result(requirement, EvidenceReuseDecision.INSUFFICIENT_METADATA, List.of(),
                    "required geometry metadata is unavailable", "");
        }
        if (missingRequiredProtocolMetadata(requirement)) {
            return result(requirement, EvidenceReuseDecision.INSUFFICIENT_METADATA, List.of(),
                    "scientifically required protocol metadata is unresolved", "");
        }

        List<QuantumEvidence> sameSubject = bundle.quantum().stream()
                .filter(e -> sameSubject(e.identity(), scientific))
                .sorted(Comparator.comparing(e -> e.identity().evidenceHash()))
                .toList();
        List<QuantumEvidence> accepted = sameSubject.stream().filter(StrategyEvidenceMatcher::accepted).toList();
        List<QuantumEvidence> compatible = accepted.stream()
                .filter(e -> protocolCompatible(e.identity(), scientific, requirement.exactProtocolRequired()))
                .toList();
        if (!compatible.isEmpty()) {
            QuantumEvidence chosen = compatible.get(0);
            EvidenceReuseDecision decision = requested.role() == DatasetRole.HOLDOUT
                    ? EvidenceReuseDecision.RESERVE_AS_HOLDOUT : EvidenceReuseDecision.REUSE_EXISTING;
            return result(requirement, decision, List.of(chosen.identity().evidenceHash()),
                    requested.role() == DatasetRole.HOLDOUT
                            ? "compatible accepted evidence is reserved and cannot enter development"
                            : "compatible accepted evidence satisfies the scientific requirement",
                    "");
        }

        if (requirement.derivation().isPresent()) {
            DerivationRule derivation = requirement.derivation().orElseThrow();
            List<QuantumEvidence> sources = bundle.quantum().stream()
                    .filter(StrategyEvidenceMatcher::accepted)
                    .filter(e -> e.identity().calculationType() == derivation.sourceType())
                    .filter(e -> sameMoleculeAndGeometry(e.identity(), scientific))
                    .filter(e -> protocolCompatible(e.identity(), scientific, requirement.exactProtocolRequired()))
                    .sorted(Comparator.comparing(e -> e.identity().evidenceHash()))
                    .toList();
            if (!sources.isEmpty()) {
                return result(requirement, EvidenceReuseDecision.DERIVE_FROM_EXISTING,
                        List.of(sources.get(0).identity().evidenceHash()),
                        "derive " + derivation.derivedValue() + " from authoritative "
                                + derivation.sourceType() + " using " + derivation.method()
                                + "; no new scientific calculation is required", "");
            }
        }

        if (!accepted.isEmpty() && requirement.exactProtocolRequired()) {
            ComparabilityDecision comparison = compareFirst(accepted.get(0), scientific);
            return result(requirement, EvidenceReuseDecision.INCOMPATIBLE_EXISTING, List.of(),
                    "accepted evidence exists but its protocol is " + comparison.verdict()
                            + ": " + comparison.reason(), "");
        }
        List<String> missingDependencies = requirement.externalDependencies().stream()
                .filter(dependency -> !availableDependencies.contains(dependency)).toList();
        if (!missingDependencies.isEmpty()) {
            return result(requirement, EvidenceReuseDecision.BLOCKED_BY_INFRASTRUCTURE, List.of(),
                    "the scientific evidence requirement remains feasible",
                    "unavailable execution dependencies: " + missingDependencies);
        }
        return result(requirement, EvidenceReuseDecision.GENERATE_NEW, List.of(),
                "no compatible authoritative evidence or derivable source exists", "");
    }

    private boolean protocolCompatible(EvidenceIdentity identity, ScientificEvidenceRequirement requested,
            boolean exact) {
        if (exact) return identity.protocol().protocolKey().equals(requested.requirement().protocol().protocolKey());
        EvidenceIdentity target = new EvidenceIdentity(requested.requirement().molecule(), identity.atomMapHash(),
                requested.requirement().geometry(), requested.formalCharge(), requested.multiplicity(),
                identity.calculationType(), requested.requirement().protocol(), requested.constraints(),
                requested.requestedOutputs());
        return comparability.compare(identity, target).verdict()
                == totah.lab.prometheus.comparability.ComparabilityVerdict.COMPARABLE;
    }

    private ComparabilityDecision compareFirst(QuantumEvidence evidence, ScientificEvidenceRequirement requested) {
        EvidenceIdentity target = new EvidenceIdentity(requested.requirement().molecule(),
                evidence.identity().atomMapHash(), requested.requirement().geometry(), requested.formalCharge(),
                requested.multiplicity(), evidence.identity().calculationType(), requested.requirement().protocol(),
                requested.constraints(), requested.requestedOutputs());
        return comparability.compare(evidence.identity(), target);
    }

    private static boolean sameSubject(EvidenceIdentity identity, ScientificEvidenceRequirement requested) {
        return identity.calculationType() == requested.requirement().calculationType()
                && sameMoleculeAndGeometry(identity, requested)
                && identity.formalCharge() == requested.formalCharge()
                && identity.multiplicity() == requested.multiplicity()
                && identity.constraints().equals(requested.constraints())
                && identity.requestedOutputs().equals(requested.requestedOutputs());
    }

    private static boolean sameMoleculeAndGeometry(EvidenceIdentity identity,
            ScientificEvidenceRequirement requested) {
        return identity.molecule().equals(requested.requirement().molecule())
                && identity.geometry().sha256().equals(requested.requirement().geometry().sha256());
    }

    private static boolean accepted(QuantumEvidence evidence) {
        return evidence.acceptance() == EvidenceAcceptanceState.ACCEPTED
                && evidence.convergence() == ConvergenceStatus.CONVERGED;
    }

    private static boolean missingRequiredProtocolMetadata(StrategyEvidenceRequirement requirement) {
        var p = requirement.scientific().requirement().protocol();
        for (String field : requirement.requiredMetadata()) {
            String value = switch (field) {
                case "method" -> p.method();
                case "basis" -> p.basis();
                case "software" -> p.software();
                case "softwareVersion" -> p.softwareVersion();
                case "dispersion" -> p.dispersion();
                case "environment" -> p.environment();
                default -> null;
            };
            if (value == null || value.isBlank() || value.equalsIgnoreCase("unknown")) return true;
        }
        return false;
    }

    private CalculationSpecification specify(String strategyId, int index,
            StrategyEvidenceRequirement strategyRequirement) {
        ScientificEvidenceRequirement scientific = strategyRequirement.scientific();
        EvidenceRequirement r = scientific.requirement();
        return new CalculationSpecification(strategyId + "-" + index, r.purpose(), r.molecule(), r.geometry(),
                scientific.formalCharge(), scientific.multiplicity(), r.protocol(), scientific.constraints(),
                r.calculationType(), scientific.requestedOutputs(), scientific.acceptanceGates(), r.role(),
                costModel.estimate(r));
    }

    private static StrategyRequirementResolution result(StrategyEvidenceRequirement requirement,
            EvidenceReuseDecision decision, List<String> hashes, String scientific, String infrastructure) {
        return new StrategyRequirementResolution(requirement, decision, hashes, scientific, infrastructure);
    }
}
