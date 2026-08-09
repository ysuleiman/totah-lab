package totah.lab.web.poseanalysis;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Best-effort parsed docking pose label. Recognizes optional tokens used
 * by the import pipelines: {@code s<N>} seed, {@code m<N>} mode,
 * {@code rank<N>} and {@code conf<X>} confidence. Fields that do not
 * occur in the label are {@code null}; unknown labels parse to an
 * all-null record rather than failing.
 */
public record PoseLabel(
        Integer seed,
        Integer mode,
        Integer rank,
        Double confidence
) {

    private static final Pattern SEED =
            Pattern.compile("\\bs(\\d+)\\b");
    private static final Pattern MODE =
            Pattern.compile("\\bm(\\d+)\\b");
    private static final Pattern RANK =
            Pattern.compile("\\brank(\\d+)\\b");
    private static final Pattern CONFIDENCE =
            Pattern.compile("\\bconf(-?\\d+(?:\\.\\d+)?)");

    public static PoseLabel parse(String label) {
        Objects.requireNonNull(label, "label");
        String trimmed = label.trim();
        return new PoseLabel(
                integer(SEED.matcher(trimmed)),
                integer(MODE.matcher(trimmed)),
                integer(RANK.matcher(trimmed)),
                decimal(CONFIDENCE.matcher(trimmed))
        );
    }

    private static Integer integer(Matcher matcher) {
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static Double decimal(Matcher matcher) {
        return matcher.find() ? Double.valueOf(matcher.group(1)) : null;
    }
}
