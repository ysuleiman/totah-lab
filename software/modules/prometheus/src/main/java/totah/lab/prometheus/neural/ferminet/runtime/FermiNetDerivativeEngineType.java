package totah.lab.prometheus.neural.ferminet.runtime;

/** Extensible runtime derivative backend selection. */
public enum FermiNetDerivativeEngineType {
    /** Validated scalar {@link FermiNetSpatialJet} oracle. */
    REFERENCE_JET,

    /** Shared-primal, multi-direction forward derivative backend. */
    BATCHED_FORWARD
}
