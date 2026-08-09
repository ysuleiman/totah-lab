package totah.lab.hermes.file.pocket.reader;


import totah.lab.gaia.pocket.Pocket;
import totah.lab.hermes.file.pocket.AdapterRegistry;
import totah.lab.hermes.file.pocket.FPocketAdapter;
import totah.lab.hermes.file.pocket.P2RankAdapter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Reads every supported pocket-result format found under a path.
 *
 * <p>A directory may contain both fpocket and P2Rank outputs. Every matching
 * adapter contributes pockets to the returned list. Callers can inspect each
 * pocket's {@code PocketSource} without knowing which adapters were used.</p>
 */
public final class AutoDetectingPocketReader
        implements PocketReader {

    private final AdapterRegistry<Pocket> registry;

    public AutoDetectingPocketReader() {
        this(List.of(
                new FPocketAdapter(),
                new P2RankAdapter()));
    }

    AutoDetectingPocketReader(
            List<? extends
                    totah.lab.hermes.file.pocket.Adapter<Path, Pocket>>
                    adapters) {

        this.registry = new AdapterRegistry<>(
                Objects.requireNonNull(adapters, "adapters"));
    }

    @Override
    public List<Pocket> read(Path path) throws IOException {
        Objects.requireNonNull(path, "path");
        return registry.read(path);
    }

    @Override
    public boolean supports(Path path) {
        return registry.supports(path);
    }
}
