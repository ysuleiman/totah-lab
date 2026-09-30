package totah.lab.athena.energy.openmm;

import java.nio.file.Path;
import java.util.Map;

/** A manifest whose declared artifacts have been resolved and hash-verified. */
public record VerifiedOpenMmSystem(OpenMmSystemManifest manifest,
        Path manifestPath, String manifestSha256, Map<String, String> verifiedHashes) {
    public VerifiedOpenMmSystem {
        manifestPath = manifestPath.toAbsolutePath().normalize();
        verifiedHashes = Map.copyOf(verifiedHashes);
    }
}
