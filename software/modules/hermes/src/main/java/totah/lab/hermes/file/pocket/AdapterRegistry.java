package totah.lab.hermes.file.pocket;


import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Aggregates results from every adapter that supports the supplied path.
 *
 * <p>This registry intentionally allows multiple adapters to match because one
 * result directory may contain outputs from several pocket-detection tools.</p>
 */
public final class AdapterRegistry<O> {

    private final List<Adapter<Path, O>> adapters;

    public AdapterRegistry() {
        this.adapters = new ArrayList<>();
    }

    public AdapterRegistry(
            List<? extends Adapter<Path, O>> adapters) {

        Objects.requireNonNull(adapters, "adapters");

        if (adapters.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException(
                    "adapters must not contain null");
        }

        this.adapters = new ArrayList<>(adapters);
    }

    public void register(Adapter<Path, O> adapter) {
        adapters.add(
                Objects.requireNonNull(adapter, "adapter"));
    }

    public boolean supports(Path input) {
        if (input == null) {
            return false;
        }

        return adapters.stream()
                .anyMatch(adapter -> adapter.supports(input));
    }

    /**
     * Runs every matching adapter and combines all returned results.
     *
     * @throws IOException when no adapter supports the input, or when a
     *                     matching adapter cannot parse its results
     */
    public List<O> read(Path input) throws IOException {
        Objects.requireNonNull(input, "input");

        List<O> results = new ArrayList<>();
        List<String> matchedAdapters = new ArrayList<>();

        for (Adapter<Path, O> adapter : adapters) {
            if (!adapter.supports(input)) {
                continue;
            }

            matchedAdapters.add(
                    adapter.getClass().getSimpleName());

            List<O> parsed = adapter.parse(input);

            if (parsed == null) {
                throw new IOException(
                        "Adapter %s returned null for %s"
                                .formatted(
                                        adapter.getClass().getSimpleName(),
                                        input));
            }

            results.addAll(parsed);
        }

        if (matchedAdapters.isEmpty()) {
            throw new IOException(
                    "No pocket adapter supports input: " + input);
        }

        return List.copyOf(results);
    }
}