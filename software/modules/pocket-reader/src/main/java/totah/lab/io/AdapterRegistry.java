package totah.lab.io;


import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public final class AdapterRegistry<O> {

    private final List<Adapter<Path, O>> adapters = new ArrayList<>();

    public void register(Adapter<Path, O> adapter) {
        adapters.add(Objects.requireNonNull(adapter));
    }

    public List<O> load(Path input) throws IOException {
        List<O> results = new ArrayList<>();
        for (Adapter<Path, O> adapter : adapters) {
            if (adapter.supports(input)) {
                results.addAll(adapter.parse(input));
            }
        }
        if (results.isEmpty()) {
            throw new IOException(
                    "No adapter supports: " + input);
        }
        return results;
    }
}
