package totah.lab.prometheus.comparison;

/** Exact disposition of one model/metric comparison cell. */
public enum ComparisonCellState {
    EVALUATED,
    UNEVALUATED,
    MISSING,
    INCOMPATIBLE_PROTOCOL,
    INCOMPATIBLE_VALIDATION_DEFINITION,
    INCOMPATIBLE_UNIT
}
