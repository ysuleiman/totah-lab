package totah.lab.web.service;

import org.springframework.stereotype.Service;
import totah.lab.io.StructureIO;
import totah.lab.protein.Structure;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class StructureArtifactService {

    private final ConcurrentMap<Long, CachedStructure> cache =
            new ConcurrentHashMap<>();

    public Structure load(
            long artifactId,
            String storageLocation
    ) throws IOException {
        Path path = Path.of(storageLocation).toAbsolutePath().normalize();
        CachedStructure cached = cache.get(artifactId);
        if (cached != null && cached.path().equals(path)) {
            return cached.structure();
        }

        Structure loaded = StructureIO.load(path);
        cache.put(artifactId, new CachedStructure(path, loaded));
        return loaded;
    }

    private record CachedStructure(
            Path path,
            Structure structure
    ) {
    }
}
