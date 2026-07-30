package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.selection.LigandPreparationOrchestrator;
import totah.lab.ligand.selection.LigandSelection;
import totah.lab.ligand.selection.LigandSelectionPolicy;
import totah.lab.ligand.selection.SelectedLigandPreparation;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.StructureCleanupResult;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapts the CCD-backed ligand preparation workflow to the pipeline context.
 *
 * <p>The chemistry remains in {@code totah.lab.ligand}; this stage only
 * selects a cleanup-extracted ligand, delegates preparation, writes its PDBQT,
 * and publishes the result. A structure without an extracted bound ligand is
 * a valid receptor-preparation input and leaves the ligand outputs absent.</p>
 */
public final class LigandPreparationStage implements Stage {

    private static final String OUTPUT_FILE_NAME = "prepared_ligand.pdbqt";

    private final LigandPreparationOrchestrator orchestrator;
    private final boolean ligandRequired;
    private final LigandSelectionPolicy selectionPolicy;

    public LigandPreparationStage() {
        this(null, false, new LigandSelectionPolicy());
    }

    public LigandPreparationStage(boolean ligandRequired) {
        this(null, ligandRequired, new LigandSelectionPolicy());
    }

    public LigandPreparationStage(
            boolean ligandRequired,
            LigandSelectionPolicy selectionPolicy) {
        this(null, ligandRequired, selectionPolicy);
    }

    LigandPreparationStage(LigandPreparationOrchestrator orchestrator) {
        this(orchestrator, false, new LigandSelectionPolicy());
    }

    LigandPreparationStage(
            LigandPreparationOrchestrator orchestrator,
            boolean ligandRequired,
            LigandSelectionPolicy selectionPolicy) {
        this.orchestrator = orchestrator;
        this.ligandRequired = ligandRequired;
        this.selectionPolicy = Objects.requireNonNull(
                selectionPolicy, "selectionPolicy is null");
    }

    @Override
    public void run(PipelineContext context) throws IOException {
        Objects.requireNonNull(context, "context is null");
        StructureCleanupResult cleanupResult =
                context.require(ContextKeys.STRUCTURE_CLEANUP_RESULT);
        LigandSelection selection = configuredSelection(context);

        LigandPreparationOrchestrator activeOrchestrator =
                orchestrator != null ? orchestrator : orchestrator(context);
        Optional<SelectedLigandPreparation> prepared = selection == null
                ? activeOrchestrator.prepareOnly(cleanupResult)
                : Optional.of(activeOrchestrator.prepare(cleanupResult, selection));
        if (prepared.isEmpty()) {
            if (ligandRequired) {
                throw new IllegalStateException(
                        "No eligible ligand was found in the configured ligand input");
            }
            return;
        }

        Path runDirectory = Objects.requireNonNull(
                context.getRunDirectory(),
                "Missing runDirectory execution path inside context.");
        Files.createDirectories(runDirectory);

        SelectedLigandPreparation result = prepared.orElseThrow();
        String pdbqt = result.preparation().pdbqt();
        Path outputPath = runDirectory.resolve(OUTPUT_FILE_NAME);
        Files.writeString(outputPath, pdbqt, StandardCharsets.UTF_8);

        context.put(ContextKeys.LIGAND_PREPARATION_RESULT, result);
        context.put(ContextKeys.LIGAND_PDBQT, pdbqt);
        context.put(ContextKeys.LIGAND_PDBQT_PATH, outputPath);
    }

    private LigandSelection configuredSelection(PipelineContext context) {
        Object configured = context.get(ContextKeys.LIGAND_SELECTION);
        if (configured == null) {
            return null;
        }
        if (configured instanceof LigandSelection selection) {
            return selection;
        }
        throw new IllegalArgumentException(
                ContextKeys.LIGAND_SELECTION + " must be a LigandSelection");
    }

    private LigandPreparationOrchestrator orchestrator(
            PipelineContext context) throws IOException {
        return new LigandPreparationOrchestrator(
                PipelineLigandPreparerFactory.create(context), selectionPolicy);
    }
}
