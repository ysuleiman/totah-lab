package totah.lab.prometheus.neural;

/** Parameter-update engines available to iterative FermiNet optimization. */
public enum FermiNetOptimizerType {
    EXACT_SR,
    /** Experimental: implemented for comparison, not the production default. */
    KFAC
}
