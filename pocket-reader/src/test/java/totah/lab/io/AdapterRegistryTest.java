package totah.lab.io;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AdapterRegistryTest {

    @Test
    void returnsAndFlattensElementResultsFromEverySupportingAdapter()
            throws Exception {
        AdapterRegistry<String> registry = new AdapterRegistry<>();
        registry.register(adapter(List.of("first", "second")));
        registry.register(adapter(List.of("third")));

        List<String> results = registry.load(Path.of("input"));

        assertEquals(List.of("first", "second", "third"), results);
    }

    private Adapter<Path, String> adapter(List<String> results) {
        return new Adapter<>() {
            @Override
            public boolean supports(Path input) {
                return true;
            }

            @Override
            public List<String> parse(Path input) {
                return results;
            }
        };
    }
}
