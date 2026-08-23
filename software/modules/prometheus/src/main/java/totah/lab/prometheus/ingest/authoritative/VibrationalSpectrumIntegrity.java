package totah.lab.prometheus.ingest.authoritative;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Integrity checks and sign-preserving classification for vibrational spectra.
 *
 * <p>A projected spectrum has already had translational and rotational modes removed. An
 * exact zero in such a spectrum therefore cannot be silently interpreted as one of those
 * rigid-body modes. It is ambiguous evidence (including the historical imaginary-to-zero
 * conversion defect) and must fail closed. Negative signed frequencies are preserved and
 * classified as negative vibrational curvature; no magnitude or acceptance threshold is
 * applied by this integrity layer.
 */
public final class VibrationalSpectrumIntegrity {

    public enum Classification {
        POSITIVE_VIBRATIONAL_CURVATURE,
        SADDLE_POINT
    }

    public record Assessment(Classification classification, int negativeVibrationalModes) {
        public Assessment {
            Objects.requireNonNull(classification, "classification");
            if (negativeVibrationalModes < 0) {
                throw new IllegalArgumentException("negativeVibrationalModes must be nonnegative");
            }
        }
    }

    private VibrationalSpectrumIntegrity() {
    }

    public static Assessment assessProjected(
            List<Double> signedFrequenciesCmInverse, String projectionIdentity, Path source) throws IOException {
        Objects.requireNonNull(signedFrequenciesCmInverse, "signedFrequenciesCmInverse");
        Objects.requireNonNull(projectionIdentity, "projectionIdentity");
        Objects.requireNonNull(source, "source");
        if (!projectionIdentity.contains("exclude_trans=True")
                || !projectionIdentity.contains("exclude_rot=True")) {
            throw new IOException("frequency projection does not establish removal of translation and rotation: "
                    + projectionIdentity + " in " + source);
        }
        if (signedFrequenciesCmInverse.isEmpty()) {
            throw new IOException("projected vibrational spectrum is empty: " + source);
        }
        int negative = 0;
        for (int mode = 0; mode < signedFrequenciesCmInverse.size(); mode++) {
            Double frequency = signedFrequenciesCmInverse.get(mode);
            if (frequency == null || !Double.isFinite(frequency)) {
                throw new IOException("nonfinite projected frequency at mode " + mode + " in " + source);
            }
            if (frequency == 0.0d) {
                throw new IOException("suspicious exact zero at projected vibrational mode " + mode
                        + " in " + source
                        + "; translation/rotation were declared excluded, so signed mode identity is unproven");
            }
            if (frequency < 0.0d) {
                negative++;
            }
        }
        return new Assessment(
                negative == 0 ? Classification.POSITIVE_VIBRATIONAL_CURVATURE : Classification.SADDLE_POINT,
                negative);
    }
}
