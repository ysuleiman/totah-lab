package totah.lab.prometheus.planning;

/**
 * How the planner resolved one requirement.
 *
 * <ul>
 *   <li>{@code REUSE_EXISTING} — accepted, converged evidence already satisfies
 *       the requirement; never calculate equivalent evidence twice.</li>
 *   <li>{@code GENERATE_NEW} — no usable evidence exists; a new
 *       {@link CalculationSpecification} was added to the plan.</li>
 *   <li>{@code NOT_REQUIRED} — the requirement is flagged optional.</li>
 *   <li>{@code INCOMPATIBLE_EXISTING} — evidence for the same geometry and
 *       calculation type exists only under a different protocol. Protocols are
 *       never silently substituted; a human/strategy decides.</li>
 *   <li>{@code BLOCKED} — the requirement cannot be planned (e.g. required but
 *       missing geometry).</li>
 * </ul>
 */
public enum PlanDecision {
    REUSE_EXISTING,
    GENERATE_NEW,
    NOT_REQUIRED,
    INCOMPATIBLE_EXISTING,
    BLOCKED
}
