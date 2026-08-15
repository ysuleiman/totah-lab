package totah.lab.prometheus.planning;

/**
 * Estimates the cost of fulfilling one {@link EvidenceRequirement}. Estimates
 * are coarse pre-authorization numbers: they exist so a human can authorize or
 * reject a plan before any expensive calculation launches.
 */
public interface CostModel {

    CostEstimate estimate(EvidenceRequirement requirement);
}
