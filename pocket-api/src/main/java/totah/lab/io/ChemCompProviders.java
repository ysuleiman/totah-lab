package totah.lab.io;

import org.biojava.nbio.structure.chem.ChemCompProvider;
import org.biojava.nbio.structure.chem.DownloadChemCompProvider;
import org.biojava.nbio.structure.chem.ReducedChemCompProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates CCD providers shared by structure loading and ligand preparation.
 */
public final class ChemCompProviders {

    private ChemCompProviders() {
    }

    public static ChemCompProvider create(
            boolean onlineLookup,
            Path cacheDirectory) throws IOException {
        if (!onlineLookup) {
            return new ReducedChemCompProvider();
        }
        Objects.requireNonNull(
                cacheDirectory,
                "cacheDirectory is required when online CCD lookup is enabled");
        Files.createDirectories(cacheDirectory);
        return new OnlineFallbackChemCompProvider(
                new ReducedChemCompProvider(),
                new DownloadChemCompProvider(cacheDirectory.toString()));
    }
}
