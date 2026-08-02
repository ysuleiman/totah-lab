package totah.lab.daedalus.stage;

import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Validates prepared receptor/ligand artifacts for a future docking executor.
 */
public final class DockingInputAssemblyStage implements Stage {

    @Override
    public void run(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context is null");
        Path receptor = requiredPath(
                context, ContextKeys.RECEPTOR_PDBQT);
        Path ligand = requiredPath(
                context, ContextKeys.LIGAND_PDBQT_PATH);
        Path flex = optionalPath(context.get(ContextKeys.FLEX_PDBQT_PATH));
        if (flex != null) {
            validateFile(flex, ContextKeys.FLEX_PDBQT_PATH);
        }

        context.put(ContextKeys.DOCKING_INPUT,
                new DockingInput(receptor, ligand, Optional.ofNullable(flex)));
    }

    private Path requiredPath(PipelineContext context, String key)
            throws IOException {
        Object value = context.require(key);
        Path path = path(value, key);
        validateFile(path, key);
        return path;
    }

    private Path optionalPath(Object value) {
        return value == null ? null : path(value, ContextKeys.FLEX_PDBQT_PATH);
    }

    private Path path(Object value, String key) {
        if (value instanceof Path path) {
            return path;
        }
        if (value instanceof String text && !text.isBlank()) {
            return Path.of(text);
        }
        throw new IllegalArgumentException(
                key + " must contain a Path or non-blank path string");
    }

    private void validateFile(Path path, String key) throws IOException {
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new IOException(
                    key + " does not reference a readable file: " + path);
        }
    }
}
