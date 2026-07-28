package totah.lab.pipeline;

import org.biojava.nbio.structure.chem.ChemCompProvider;
import totah.lab.io.ChemCompProviders;
import totah.lab.ligand.LigandPreparer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Creates a ligand preparer from pipeline CCD configuration.
 */
public final class PipelineLigandPreparerFactory {

    private PipelineLigandPreparerFactory() {
    }

    public static LigandPreparer create(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context is null");
        Object configured = context.get(ContextKeys.CHEM_COMP_PROVIDER);
        ChemCompProvider provider;
        if (configured instanceof ChemCompProvider chemCompProvider) {
            provider = chemCompProvider;
        } else if (configured != null) {
            throw new IllegalArgumentException(
                    ContextKeys.CHEM_COMP_PROVIDER + " must be a ChemCompProvider");
        } else {
            boolean onlineLookup = parseBoolean(
                    context.get(ContextKeys.CCD_ONLINE_LOOKUP), false);
            provider = ChemCompProviders.create(
                    onlineLookup,
                    onlineLookup ? ccdCacheDirectory(context) : null);
            context.put(ContextKeys.CHEM_COMP_PROVIDER, provider);
        }
        return new LigandPreparer(provider);
    }

    private static Path ccdCacheDirectory(PipelineContext context) {
        Object configured = context.get(ContextKeys.CCD_CACHE_DIRECTORY);
        if (configured instanceof Path path) {
            return path;
        }
        if (configured != null && !configured.toString().isBlank()) {
            return Path.of(configured.toString());
        }
        return context.getWorkingDirectory().resolve(".ccd-cache");
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return Boolean.parseBoolean(value.toString());
    }
}
