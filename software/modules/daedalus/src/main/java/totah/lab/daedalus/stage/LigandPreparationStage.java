package totah.lab.daedalus.stage;

import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.ligand.LigandPreparationOptions;
import totah.lab.hephaestus.ligand.LigandPreparationResult;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Daedalus adapter for the Hephaestus ligand workflow: prepares the SDF
 * ligand named by {@link ContextKeys#LIGAND_PATH} and writes the
 * validated ligand PDBQT into the run directory.
 */
public final class LigandPreparationStage implements Stage {
    private final HephaestusClient hephaestus;

    public LigandPreparationStage(HephaestusClient hephaestus) {
        this.hephaestus = Objects.requireNonNull(hephaestus, "hephaestus");
    }

    @Override
    public void run(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        Path ligandSdf = ligandPath(context);
        LigandPreparationOptions options = preparationOptions(context);

        LigandPreparationResult preparation =
                hephaestus.prepareLigand(ligandSdf, options);
        context.put(ContextKeys.LIGAND_PREPARATION_RESULT, preparation);
        if (preparation.hasErrors()) {
            throw new IllegalStateException(
                    "Hephaestus ligand preparation reported errors: "
                            + preparation.issues());
        }

        Path output = outputPath(context);
        Path written = hephaestus.writePreparedLigand(
                preparation.preparedLigand(), output);
        context.put(ContextKeys.LIGAND_PDBQT_PATH, written.toString());
    }

    private Path ligandPath(PipelineContext context) {
        Object configured = context.require(ContextKeys.LIGAND_PATH);
        if (configured instanceof Path path) return path;
        if (!configured.toString().isBlank()) {
            return Path.of(configured.toString());
        }
        throw new IllegalArgumentException(
                ContextKeys.LIGAND_PATH
                        + " must contain a Path or non-blank path string");
    }

    private LigandPreparationOptions preparationOptions(
            PipelineContext context) {
        Object configured = context.get(
                ContextKeys.LIGAND_PREPARATION_OPTIONS);
        if (configured == null) {
            return LigandPreparationOptions.defaults();
        }
        if (configured instanceof LigandPreparationOptions options) {
            return options;
        }
        throw new IllegalArgumentException(
                ContextKeys.LIGAND_PREPARATION_OPTIONS
                        + " must be LigandPreparationOptions");
    }

    private Path outputPath(PipelineContext context) {
        Object configured = context.get(ContextKeys.LIGAND_PDBQT_PATH);
        if (configured instanceof Path path) return path;
        if (configured != null && !configured.toString().isBlank()) {
            return Path.of(configured.toString());
        }
        return context.getRunDirectory().resolve("ligand.pdbqt");
    }
}
