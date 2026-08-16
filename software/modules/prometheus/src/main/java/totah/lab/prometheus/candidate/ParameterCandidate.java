package totah.lab.prometheus.candidate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.identity.ForceFieldAtomMap;
import totah.lab.prometheus.identity.MoleculeIdentity;

/**
 * A versioned set of derived force-field parameters for one molecule, with
 * explicit lineage (parent candidate, generation) and evidentiary standing.
 *
 * <p>A candidate may only be <em>constructed</em> as {@link EvidenceClass#EVIDENCE},
 * {@link EvidenceClass#FAILED_CANDIDATE} or {@link EvidenceClass#VALIDATED_DIAGNOSTIC}.
 * {@link EvidenceClass#PRODUCTION_MODEL} can only be reached through
 * {@link #promoteToProduction(ModelDecision)} with an explicitly accepting
 * decision. A failed candidate is preserved evidence and can never be promoted.
 */
public final class ParameterCandidate {

    private final String candidateId;
    private final MoleculeIdentity molecule;
    private final ForceFieldAtomMap atomTyping;
    private final List<DerivedParameter> parameters;
    private final String parentCandidateId;
    private final int generation;
    private final EvidenceClass evidenceClass;
    private final Instant createdAt;

    /**
     * Creates a candidate. {@code parentCandidateId} may be null (root candidate);
     * {@code generation} is 0 for a root, parent generation + 1 for children.
     *
     * @throws IllegalArgumentException if {@code evidenceClass} is
     *         {@link EvidenceClass#PRODUCTION_MODEL} — production status requires
     *         an explicit accepting decision via {@link #promoteToProduction(ModelDecision)}
     */
    public ParameterCandidate(
            String candidateId,
            MoleculeIdentity molecule,
            ForceFieldAtomMap atomTyping,
            List<DerivedParameter> parameters,
            String parentCandidateId,
            int generation,
            EvidenceClass evidenceClass,
            Instant createdAt) {

        this(candidateId, molecule, atomTyping, parameters, parentCandidateId,
                generation, evidenceClass, createdAt, false);
    }

    private ParameterCandidate(
            String candidateId,
            MoleculeIdentity molecule,
            ForceFieldAtomMap atomTyping,
            List<DerivedParameter> parameters,
            String parentCandidateId,
            int generation,
            EvidenceClass evidenceClass,
            Instant createdAt,
            boolean productionApproved) {

        Objects.requireNonNull(candidateId, "candidateId");
        if (candidateId.isBlank()) {
            throw new IllegalArgumentException("candidateId must be non-blank");
        }
        this.candidateId = candidateId;
        this.molecule = Objects.requireNonNull(molecule, "molecule");
        this.atomTyping = Objects.requireNonNull(atomTyping, "atomTyping");
        this.parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        if (generation < 0) {
            throw new IllegalArgumentException("generation must be >= 0");
        }
        if (parentCandidateId == null && generation != 0) {
            throw new IllegalArgumentException("a root candidate must have generation 0");
        }
        if (parentCandidateId != null && (parentCandidateId.isBlank() || generation == 0)) {
            throw new IllegalArgumentException("a child candidate needs a non-blank parent and generation > 0");
        }
        this.parentCandidateId = parentCandidateId; // nullable: null marks a root candidate
        this.generation = generation;
        Objects.requireNonNull(evidenceClass, "evidenceClass");
        if (evidenceClass == EvidenceClass.PRODUCTION_MODEL && !productionApproved) {
            throw new IllegalArgumentException(
                    "a candidate cannot be constructed as PRODUCTION_MODEL; "
                            + "use promoteToProduction with an accepting ModelDecision");
        }
        this.evidenceClass = evidenceClass;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    /**
     * Promotes this candidate to {@link EvidenceClass#PRODUCTION_MODEL}. Production
     * status can only come from an explicit accepting decision: the decision state
     * must be {@link DecisionState#VALIDATED_FOR_PRODUCTION} or
     * {@link DecisionState#VALIDATED_WITH_LIMITATIONS}.
     *
     * @throws IllegalStateException if this candidate is a failed candidate —
     *         a failed candidate is never promoted
     * @throws IllegalArgumentException if the decision state is not accepting
     */
    public ParameterCandidate promoteToProduction(ModelDecision decision) {
        Objects.requireNonNull(decision, "decision");
        if (evidenceClass == EvidenceClass.FAILED_CANDIDATE) {
            throw new IllegalStateException(
                    "failed candidate never promoted: " + candidateId);
        }
        if (decision.state() != DecisionState.VALIDATED_FOR_PRODUCTION
                && decision.state() != DecisionState.VALIDATED_WITH_LIMITATIONS) {
            throw new IllegalArgumentException(
                    "production requires an accepting decision, got " + decision.state());
        }
        return new ParameterCandidate(
                candidateId, molecule, atomTyping, parameters, parentCandidateId,
                generation, EvidenceClass.PRODUCTION_MODEL, createdAt, true);
    }

    /**
     * Derives a child candidate from this one: the child links back to this
     * candidate via {@code parentCandidateId}, has {@code generation + 1}, and
     * starts as {@link EvidenceClass#EVIDENCE}.
     */
    public ParameterCandidate deriveChild(String newCandidateId, List<DerivedParameter> newParameters) {
        return new ParameterCandidate(
                newCandidateId, molecule, atomTyping, newParameters,
                candidateId, generation + 1, EvidenceClass.EVIDENCE, Instant.now());
    }

    /** Parameters of the given kind, in candidate order. */
    public List<DerivedParameter> parametersByKind(ParameterKind kind) {
        Objects.requireNonNull(kind, "kind");
        return parameters.stream().filter(p -> p.kind() == kind).toList();
    }

    /** Parameters whose canonical atom index tuple contains the given serial. */
    public List<DerivedParameter> parametersTouching(int canonicalIndex) {
        return parameters.stream()
                .filter(p -> p.canonicalAtomIndices().contains(canonicalIndex))
                .toList();
    }

    public String candidateId() {
        return candidateId;
    }

    public MoleculeIdentity molecule() {
        return molecule;
    }

    public ForceFieldAtomMap atomTyping() {
        return atomTyping;
    }

    public List<DerivedParameter> parameters() {
        return parameters;
    }

    /** Id of the parent candidate, or null for a root candidate. */
    public String parentCandidateId() {
        return parentCandidateId;
    }

    public int generation() {
        return generation;
    }

    public EvidenceClass evidenceClass() {
        return evidenceClass;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
