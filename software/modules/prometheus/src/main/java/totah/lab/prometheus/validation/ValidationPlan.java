package totah.lab.prometheus.validation;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import totah.lab.prometheus.identity.CanonicalHashing;

/**
 * A preregistered validation plan: the gates a frozen candidate must pass on
 * the holdout, fixed <em>before</em> any holdout value is observed.
 *
 * <p>The constructor is private; the only way to obtain a plan is
 * {@link #preregister(String, List, String)}, which stamps {@code preregisteredAt}
 * at creation. {@link #preregistered()} is therefore always true — an
 * unpreregistered plan cannot exist, and {@link FrozenCandidate#freeze} refuses
 * any plan for which it is not.
 */
public final class ValidationPlan {

    private final String planId;
    private final List<ValidationGate> gates;
    private final String holdoutDatasetId;
    private final Instant preregisteredAt;
    private final boolean preregistered;

    private ValidationPlan(
            String planId,
            List<ValidationGate> gates,
            String holdoutDatasetId,
            Instant preregisteredAt) {

        Objects.requireNonNull(planId, "planId");
        if (planId.isBlank()) {
            throw new IllegalArgumentException("planId must be non-blank");
        }
        this.planId = planId;
        this.gates = List.copyOf(Objects.requireNonNull(gates, "gates"));
        if (this.gates.isEmpty()) {
            throw new IllegalArgumentException("a validation plan needs at least one gate");
        }
        Objects.requireNonNull(holdoutDatasetId, "holdoutDatasetId");
        if (holdoutDatasetId.isBlank()) {
            throw new IllegalArgumentException("holdoutDatasetId must be non-blank");
        }
        this.holdoutDatasetId = holdoutDatasetId;
        this.preregisteredAt = Objects.requireNonNull(preregisteredAt, "preregisteredAt");
        this.preregistered = true;
    }

    /**
     * Preregisters a validation plan. The gates and the holdout dataset id are
     * fixed now, before holdout evaluation; {@code preregisteredAt} is stamped
     * at this moment.
     */
    public static ValidationPlan preregister(
            String planId,
            List<ValidationGate> gates,
            String holdoutDatasetId) {

        return new ValidationPlan(planId, gates, holdoutDatasetId, Instant.now());
    }

    /** SHA-256 over the canonical serialization of the gates and the holdout dataset id. */
    public String planChecksum() {
        StringBuilder sb = new StringBuilder();
        sb.append("holdoutDatasetId=").append(holdoutDatasetId);
        for (ValidationGate gate : gates) {
            sb.append('\n').append("gate=").append(gate.gateId())
                    .append('|').append(gate.description())
                    .append('|').append(gate.metric())
                    .append('|').append(CanonicalHashing.format(gate.threshold()))
                    .append('|').append(gate.comparison().name());
        }
        return CanonicalHashing.sha256Hex(sb.toString());
    }

    /** Always true: plans can only be created via {@link #preregister}. */
    public boolean preregistered() {
        return preregistered;
    }

    public String planId() {
        return planId;
    }

    /** The preregistered gates, in plan order; unmodifiable. */
    public List<ValidationGate> gates() {
        return gates;
    }

    public String holdoutDatasetId() {
        return holdoutDatasetId;
    }

    public Instant preregisteredAt() {
        return preregisteredAt;
    }
}
