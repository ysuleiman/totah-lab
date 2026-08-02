package totah.lab.daedalus.stage;

import totah.lab.gaia.molecule.Protein;
import totah.lab.gaia.structure.Structure;
import totah.lab.hephaestus.factory.ProteinFactory;
import totah.lab.hermes.file.reader.BioJavaStructureReader;
import totah.lab.hermes.file.reader.StructureReader;
import totah.lab.hermes.structure.StructureReaderOptions;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/** Loads one immutable Gaia protein through Hermes. */
public final class TargetLoadStage implements Stage {
    private final StructureReader structureReader;
    private final ProteinFactory proteinFactory;

    public TargetLoadStage() {
        this(null, new ProteinFactory());
    }

    public TargetLoadStage(
            StructureReader structureReader,
            ProteinFactory proteinFactory) {
        this.structureReader = structureReader;
        this.proteinFactory = Objects.requireNonNull(
                proteinFactory, "proteinFactory");
    }

    @Override
    public void run(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        Path targetPath = context.require(ContextKeys.TARGET_PDB_PATH);
        validateTargetPath(targetPath);

        StructureReader reader = structureReader != null
                ? structureReader
                : configuredReader(context);
        if (!reader.supports(targetPath)) {
            throw new IllegalArgumentException(
                    "Unsupported target structure format: " + targetPath);
        }
        Structure structure = reader.read(targetPath);
        if (structure.isEmpty()) {
            throw new IOException("No residues loaded from " + targetPath);
        }
        String targetId = targetPath.getFileName().toString();
        Protein protein = proteinFactory.create(targetId, structure);
        context.put(ContextKeys.TARGET_PROTEIN, protein);
    }

    private StructureReader configuredReader(PipelineContext context) {
        boolean online = parseBoolean(
                context.get(ContextKeys.CCD_ONLINE_LOOKUP), false);
        Path cache = online ? ccdCacheDirectory(context) : null;
        return new BioJavaStructureReader(
                new StructureReaderOptions(online, cache));
    }

    private Path ccdCacheDirectory(PipelineContext context) {
        Object configured = context.get(ContextKeys.CCD_CACHE_DIRECTORY);
        if (configured instanceof Path path) return path;
        if (configured != null && !configured.toString().isBlank()) {
            return Path.of(configured.toString());
        }
        return context.getWorkingDirectory().resolve(".ccd-cache");
    }

    private boolean parseBoolean(Object value, boolean defaultValue) {
        if (value == null) return defaultValue;
        if (value instanceof Boolean flag) return flag;
        return Boolean.parseBoolean(value.toString());
    }

    private void validateTargetPath(Path targetPath) throws IOException {
        Objects.requireNonNull(targetPath, "targetPath");
        if (!Files.exists(targetPath)) {
            throw new IOException(
                    "Target structure file does not exist: " + targetPath);
        }
        if (!Files.isRegularFile(targetPath)) {
            throw new IOException(
                    "Target structure path is not a regular file: " + targetPath);
        }
        if (!Files.isReadable(targetPath)) {
            throw new IOException(
                    "Target structure file is not readable: " + targetPath);
        }
    }
}
