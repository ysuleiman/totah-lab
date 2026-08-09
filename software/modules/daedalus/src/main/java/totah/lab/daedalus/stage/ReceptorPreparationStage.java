package totah.lab.daedalus.stage;

import totah.lab.gaia.molecule.Protein;
import totah.lab.hephaestus.client.HephaestusClient;
import totah.lab.hephaestus.model.PreparedProtein;
import totah.lab.hephaestus.receptor.ReceptorPreparationOptions;
import totah.lab.hephaestus.receptor.ReceptorPreparationResult;
import totah.lab.hephaestus.validation.ValidationException;
import totah.lab.hephaestus.validation.ValidationReport;
import totah.lab.hermes.file.pdbqt.PdbqtWriteResult;
import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.Stage;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Daedalus adapter for the complete Hephaestus receptor workflow. */
public final class ReceptorPreparationStage implements Stage {
    private final HephaestusClient hephaestus;

    public ReceptorPreparationStage(HephaestusClient hephaestus) {
        this.hephaestus = Objects.requireNonNull(hephaestus, "hephaestus");
    }

    @Override
    public void run(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context");
        Protein protein = context.require(ContextKeys.TARGET_PROTEIN);
        ReceptorPreparationOptions options = preparationOptions(context);

        ReceptorPreparationResult preparation =
                hephaestus.prepareReceptor(protein, options);
        context.put(ContextKeys.RECEPTOR_PREPARATION_RESULT, preparation);
        if (preparation.hasErrors()) {
            throw new IllegalStateException(
                    "Hephaestus receptor preparation reported errors: "
                            + preparation.issues());
        }

        PreparedProtein preparedProtein = preparation.preparedProtein();
        ValidationReport validation =
                hephaestus.validatePreparedProtein(preparedProtein);
        if (validation.hasErrors()) {
            throw new ValidationException(validation);
        }

        Path output = outputPath(context);
        PdbqtWriteResult writeResult =
                hephaestus.writePreparedReceptor(preparedProtein, output);
        context.put(ContextKeys.PREPARED_PROTEIN, preparedProtein);
        context.put(ContextKeys.PDBQT_WRITE_RESULT, writeResult);
        context.put(ContextKeys.RECEPTOR_PDBQT,
                writeResult.rigidOutput().toString());
        context.put(ContextKeys.OUTPUT_PDBQT_PATH,
                writeResult.rigidOutput().toString());
    }

    private ReceptorPreparationOptions preparationOptions(
            PipelineContext context) {
        Object configured = context.get(
                ContextKeys.RECEPTOR_PREPARATION_OPTIONS);
        if (configured == null) {
            return ReceptorPreparationOptions.defaults();
        }
        if (configured instanceof ReceptorPreparationOptions options) {
            return options;
        }
        throw new IllegalArgumentException(
                ContextKeys.RECEPTOR_PREPARATION_OPTIONS
                        + " must be ReceptorPreparationOptions");
    }

    private Path outputPath(PipelineContext context) {
        Object configured = context.get(ContextKeys.OUTPUT_PDBQT_PATH);
        if (configured instanceof Path path) return path;
        if (configured != null && !configured.toString().isBlank()) {
            return Path.of(configured.toString());
        }
        return context.getRunDirectory().resolve("prepared_receptor.pdbqt");
    }
}
