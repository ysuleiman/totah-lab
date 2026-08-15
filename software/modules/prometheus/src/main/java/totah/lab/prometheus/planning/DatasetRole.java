package totah.lab.prometheus.planning;

/**
 * Which dataset a piece of evidence belongs to. Development evidence may be used
 * for fitting; holdout evidence is reserved for validation and must never leak
 * into parameterization; EITHER marks a requirement that accepts both.
 */
public enum DatasetRole {
    DEVELOPMENT,
    HOLDOUT,
    EITHER
}
