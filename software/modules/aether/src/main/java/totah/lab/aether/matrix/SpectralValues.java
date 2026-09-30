package totah.lab.aether.matrix;

import java.util.Arrays;
import java.util.List;
import totah.lab.aether.provenance.ContentHash;

/** Private immutable vector storage and domain-specific canonical value hashing. */
record SpectralValues(List<Double> values, String resultHash) {
    static SpectralValues capture(double[] values, String domain) {
        List<Double> copy = Arrays.stream(values).boxed().toList();
        var canonical = ContentHash.accumulator().line(domain).line(Integer.toString(copy.size()));
        for (double value : copy) canonical.line(ContentHash.number(value));
        return new SpectralValues(copy, canonical.finish());
    }
}
