package totah.lab.prometheus.neural.ferminet.runtime;

/** Locked architecture dimensions for the reference-aligned Prometheus FermiNet-v1 ansatz. */
public record FermiNetV1Configuration(
        int spatialDimensions,
        int interactionLayers,
        int oneElectronWidth,
        int twoElectronWidth,
        int determinants,
        boolean fullDeterminants,
        boolean isotropicNuclearEnvelope,
        boolean jastrowEnabled,
        boolean biasOrbitals,
        boolean useLastLayer,
        boolean separateSpinChannels) {

    public static final String REPRESENTATION_ID =
            "prometheus-ferminet-v1-deepmind-c4312c315dda-full-det";

    public FermiNetV1Configuration {
        if (spatialDimensions != 3) {
            throw new IllegalArgumentException(
                    "Prometheus FermiNet-v1 is locked to three dimensions");
        }

        if (interactionLayers < 1
                || oneElectronWidth < 1
                || twoElectronWidth < 1
                || determinants < 1) {
            throw new IllegalArgumentException("invalid FermiNet dimensions");
        }

        if (!fullDeterminants) {
            throw new IllegalArgumentException(
                    "Reference-aligned Prometheus FermiNet-v1 requires full determinants");
        }

        if (!isotropicNuclearEnvelope) {
            throw new IllegalArgumentException(
                    "Reference-aligned Prometheus FermiNet-v1 requires isotropic nuclear envelopes");
        }

        if (jastrowEnabled) {
            throw new IllegalArgumentException(
                    "Reference-aligned Prometheus FermiNet-v1 has no Jastrow");
        }

        if (biasOrbitals) {
            throw new IllegalArgumentException(
                    "Reference-aligned Prometheus FermiNet-v1 disables orbital bias");
        }

        if (useLastLayer) {
            throw new IllegalArgumentException(
                    "Reference-aligned Prometheus FermiNet-v1 uses useLastLayer=false");
        }

        if (separateSpinChannels) {
            throw new IllegalArgumentException(
                    "Reference-aligned Prometheus FermiNet-v1 uses shared two-electron stream parameters");
        }
    }

    public static FermiNetV1Configuration locked() {
        return new FermiNetV1Configuration(
                3,
                4,
                256,
                32,
                16,
                true,
                true,
                false,
                false,
                false,
                false);
    }

    /** Small configuration for algebra/parity tests, never production. */
    static FermiNetV1Configuration testFixture() {
        return new FermiNetV1Configuration(
                3,
                2,
                8,
                4,
                2,
                true,
                true,
                false,
                false,
                false,
                false);
    }
}