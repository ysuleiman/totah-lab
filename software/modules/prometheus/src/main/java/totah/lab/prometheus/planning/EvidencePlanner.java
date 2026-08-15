package totah.lab.prometheus.planning;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.comparability.ComparabilityDecision;
import totah.lab.prometheus.comparability.ProtocolComparability;
import totah.lab.prometheus.evidence.ConvergenceStatus;
import totah.lab.prometheus.evidence.EvidenceAcceptanceState;
import totah.lab.prometheus.evidence.EvidenceIdentity;
import totah.lab.prometheus.evidence.QuantumEvidence;
import totah.lab.prometheus.evidence.EvidenceBundle;

/**
 * Decides, for each {@link EvidenceRequirement}, whether existing evidence can
 * be reused or a new calculation must be specified.
 *
 * <p>Core principle: scientifically equivalent evidence is never calculated
 * twice. A requirement is REUSE_EXISTING only when an exact-identity match
 * (same calculation type, molecule, electronic state, geometry, protocol key,
 * constraints and requested outputs) exists in ACCEPTED + CONVERGED state.
 *
 * <p>Protocols are never silently substituted: evidence for the same geometry
 * and calculation type under a DIFFERENT protocol yields INCOMPATIBLE_EXISTING
 * with the {@link ProtocolComparability} verdict as the reason, and no new
 * calculation is auto-generated — a human/strategy decides whether the
 * different protocol is acceptable.
 *
 * <p>Failed or unaccepted attempts never count as reusable, but they never
 * block regeneration either: the requirement is GENERATE_NEW and the failed
 * hashes are named in the reason.
 *
 * <p>The planner emits a pure value ({@link EvidenceGenerationPlan}); it has no
 * execution capability and launches nothing.
 *
 * <p>The original {@link #plan(String, List, EvidenceBundle)} API retains its
 * neutral-singlet defaults. Molecules or states needing explicit charge,
 * multiplicity, constraints, outputs, or gates use {@link #planScientific}.
 */
public final class EvidencePlanner {

    /** Constraints assumed for requirements in the current wave. */
    static final List<String> DEFAULT_CONSTRAINTS = List.of();

    /** Requested outputs assumed for requirements in the current wave. */
    static final List<String> DEFAULT_REQUESTED_OUTPUTS = List.of("energy");

    /** Acceptance gates attached to every newly specified calculation. */
    static final List<String> DEFAULT_ACCEPTANCE_GATES =
            List.of("convergence=CONVERGED", "acceptance=ACCEPTED");

    private final ProtocolComparability comparability;
    private final CostModel costModel;

    public EvidencePlanner(ProtocolComparability comparability, CostModel costModel) {
        this.comparability = Objects.requireNonNull(comparability, "comparability");
        this.costModel = Objects.requireNonNull(costModel, "costModel");
    }

    /**
     * Resolves every requirement against the existing evidence bundle. The
     * returned plan is a pure value; no calculation is launched.
     */
    public EvidenceGenerationPlan plan(
            String planId,
            List<EvidenceRequirement> requirements,
            EvidenceBundle existing) {

        Objects.requireNonNull(planId, "planId");
        if (planId.isBlank()) {
            throw new IllegalArgumentException("planId must be non-blank");
        }
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(existing, "existing");

        return planScientific(planId,
                requirements.stream().map(ScientificEvidenceRequirement::neutralSinglet).toList(),
                existing);
    }

    /** Resolves requirements with an explicit electronic state and calculation contract. */
    public EvidenceGenerationPlan planScientific(
            String planId,
            List<ScientificEvidenceRequirement> requirements,
            EvidenceBundle existing) {

        Objects.requireNonNull(planId, "planId");
        if (planId.isBlank()) {
            throw new IllegalArgumentException("planId must be non-blank");
        }
        Objects.requireNonNull(requirements, "requirements");
        Objects.requireNonNull(existing, "existing");

        List<RequirementResolution> resolutions = new ArrayList<>();
        List<CalculationSpecification> newCalculations = new ArrayList<>();

        int index = 0;
        for (ScientificEvidenceRequirement scientific : requirements) {
            index++;
            Objects.requireNonNull(scientific, "requirement at index " + index);
            EvidenceRequirement requirement = scientific.requirement();

            if (!requirement.required()) {
                resolutions.add(new RequirementResolution(
                        requirement,
                        PlanDecision.NOT_REQUIRED,
                        List.of(),
                        "requirement is flagged not required; no evidence is planned"));
                continue;
            }
            if (requirement.geometry() == null) {
                resolutions.add(new RequirementResolution(
                        requirement,
                        PlanDecision.BLOCKED,
                        List.of(),
                        "required calculation cannot be specified without a geometry"));
                continue;
            }

            resolutions.add(resolve(planId, index, scientific, existing, newCalculations));
        }

        return EvidenceGenerationPlan.of(resolutions, newCalculations);
    }

    private RequirementResolution resolve(
            String planId,
            int index,
            ScientificEvidenceRequirement scientific,
            EvidenceBundle existing,
            List<CalculationSpecification> newCalculations) {

        EvidenceRequirement requirement = scientific.requirement();

        List<QuantumEvidence> candidates = new ArrayList<>();
        for (QuantumEvidence evidence : existing.quantum()) {
            EvidenceIdentity identity = evidence.identity();
            if (identity.calculationType() == requirement.calculationType()
                    && identity.formalCharge() == scientific.formalCharge()
                    && identity.multiplicity() == scientific.multiplicity()
                    && identity.molecule().equals(requirement.molecule())
                    && identity.geometry().sha256().equals(requirement.geometry().sha256())) {
                candidates.add(evidence);
            }
        }

        List<QuantumEvidence> exactMatches = new ArrayList<>();
        List<QuantumEvidence> sameProtocolDifferentContract = new ArrayList<>();
        List<QuantumEvidence> differentProtocol = new ArrayList<>();
        for (QuantumEvidence candidate : candidates) {
            EvidenceIdentity identity = candidate.identity();
            if (identity.protocol().protocolKey().equals(requirement.protocol().protocolKey())
                    && identity.constraints().equals(scientific.constraints())
                    && identity.requestedOutputs().equals(scientific.requestedOutputs())) {
                exactMatches.add(candidate);
            } else if (identity.protocol().protocolKey().equals(requirement.protocol().protocolKey())) {
                sameProtocolDifferentContract.add(candidate);
            } else {
                differentProtocol.add(candidate);
            }
        }

        List<String> reusableHashes = exactMatches.stream()
                .filter(e -> e.acceptance() == EvidenceAcceptanceState.ACCEPTED
                        && e.convergence() == ConvergenceStatus.CONVERGED)
                .map(e -> e.identity().evidenceHash())
                .toList();
        if (!reusableHashes.isEmpty()) {
            return new RequirementResolution(
                    requirement,
                    PlanDecision.REUSE_EXISTING,
                    reusableHashes,
                    "accepted, converged evidence already satisfies this requirement;"
                            + " equivalent evidence is never calculated twice");
        }

        if (!exactMatches.isEmpty()) {
            List<String> failedHashes = exactMatches.stream()
                    .map(e -> e.identity().evidenceHash())
                    .toList();
            CalculationSpecification spec = specify(planId, index, scientific);
            newCalculations.add(spec);
            return new RequirementResolution(
                    requirement,
                    PlanDecision.GENERATE_NEW,
                    List.of(),
                    "existing attempts failed or were not accepted (hashes: " + failedHashes
                            + "); failed evidence never blocks regeneration but is never reusable");
        }

        if (!sameProtocolDifferentContract.isEmpty()) {
            CalculationSpecification spec = specify(planId, index, scientific);
            newCalculations.add(spec);
            return new RequirementResolution(
                    requirement,
                    PlanDecision.GENERATE_NEW,
                    List.of(),
                    "existing evidence uses the same protocol but different constraints or"
                            + " requested outputs; it is not interchangeable");
        }

        if (!differentProtocol.isEmpty()) {
            QuantumEvidence candidate = differentProtocol.get(0);
            EvidenceIdentity requirementIdentity = new EvidenceIdentity(
                    requirement.molecule(),
                    candidate.identity().atomMapHash(),
                    requirement.geometry(),
                    scientific.formalCharge(),
                    scientific.multiplicity(),
                    requirement.calculationType(),
                    requirement.protocol(),
                    scientific.constraints(),
                    scientific.requestedOutputs());
            ComparabilityDecision decision =
                    comparability.compare(candidate.identity(), requirementIdentity);
            return new RequirementResolution(
                    requirement,
                    PlanDecision.INCOMPATIBLE_EXISTING,
                    List.of(),
                    "existing evidence for this geometry and calculation type only under a"
                            + " different protocol: " + decision.verdict() + " (" + decision.reason()
                            + "); protocols are never silently substituted — a human/strategy"
                            + " must decide whether it is acceptable");
        }

        CalculationSpecification spec = specify(planId, index, scientific);
        newCalculations.add(spec);
        return new RequirementResolution(
                requirement,
                PlanDecision.GENERATE_NEW,
                List.of(),
                "no existing evidence for this calculation; new calculation specified with a"
                        + " coarse pre-authorization cost estimate");
    }

    private CalculationSpecification specify(
            String planId, int index, ScientificEvidenceRequirement scientific) {
        EvidenceRequirement requirement = scientific.requirement();
        return new CalculationSpecification(
                planId + "-" + index,
                requirement.purpose(),
                requirement.molecule(),
                requirement.geometry(),
                scientific.formalCharge(),
                scientific.multiplicity(),
                requirement.protocol(),
                scientific.constraints(),
                requirement.calculationType(),
                scientific.requestedOutputs(),
                scientific.acceptanceGates(),
                requirement.role(),
                costModel.estimate(requirement));
    }
}
