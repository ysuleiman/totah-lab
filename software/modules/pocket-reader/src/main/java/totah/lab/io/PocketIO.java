package totah.lab.io;

import totah.lab.gaia.pocket.Pocket;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class PocketIO {

    private static final AdapterRegistry<Pocket> REGISTRY =
            new AdapterRegistry<>();

    static {
        REGISTRY.register(new P2RankAdapter());
        REGISTRY.register(new FPocketAdapter());
    }

    private PocketIO() {
    }

    public static List<Pocket> load(Path folder) throws IOException {
        return REGISTRY.load(folder);
    }
}
