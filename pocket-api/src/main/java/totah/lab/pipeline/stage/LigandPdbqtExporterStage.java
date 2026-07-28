package totah.lab.pipeline.stage;

import totah.lab.ligand.LigandPreparationResult;
import totah.lab.ligand.LigandPreparer;
import totah.lab.ligand.ccd.CcdLigandGraphResult;
import totah.lab.ligand.charge.LigandChargeAssignmentResult;
import totah.lab.ligand.hydrogen.LigandHydrogenationResult;
import totah.lab.ligand.selection.SelectedLigandPreparation;
import totah.lab.ligand.torsion.LigandTorsionTreeResult;
import totah.lab.ligand.typing.LigandAd4TypingResult;
import totah.lab.pipeline.ContextKeys;
import totah.lab.pipeline.PipelineContext;
import totah.lab.pipeline.PipelineLigandPreparerFactory;
import totah.lab.pipeline.Stage;
import totah.lab.pipeline.cleanup.ClassifiedResidue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Assembles the preparation result and writes the ligand PDBQT artifact.
 */
public final class LigandPdbqtExporterStage implements Stage {

    static final String OUTPUT_FILE_NAME = "prepared_ligand.pdbqt";

    private final LigandPreparer preparer;

    public LigandPdbqtExporterStage() {
        this(null);
    }

    LigandPdbqtExporterStage(LigandPreparer preparer) {
        this.preparer = preparer;
    }

    @Override
    public void run(PipelineContext context) throws Exception {
        Objects.requireNonNull(context, "context is null");
        ClassifiedResidue selected = context.require(ContextKeys.SELECTED_LIGAND);
        CcdLigandGraphResult graph = context.require(ContextKeys.LIGAND_GRAPH_RESULT);
        LigandHydrogenationResult hydrogenated =
                context.require(ContextKeys.LIGAND_HYDROGENATION_RESULT);
        LigandChargeAssignmentResult charged =
                context.require(ContextKeys.LIGAND_CHARGE_ASSIGNMENT_RESULT);
        LigandAd4TypingResult typed =
                context.require(ContextKeys.LIGAND_AD4_TYPING_RESULT);
        LigandTorsionTreeResult torsion =
                context.require(ContextKeys.LIGAND_TORSION_TREE_RESULT);
        LigandPreparer active = preparer != null
                ? preparer
                : PipelineLigandPreparerFactory.create(context);
        LigandPreparationResult preparation = active.assemble(
                selected.residue(), graph, hydrogenated, charged, typed, torsion);

        Path runDirectory = Objects.requireNonNull(
                context.getRunDirectory(),
                "Missing runDirectory execution path inside context.");
        Files.createDirectories(runDirectory);
        Path outputPath = runDirectory.resolve(OUTPUT_FILE_NAME);
        Files.writeString(outputPath, preparation.pdbqt(), StandardCharsets.UTF_8);

        context.put(
                ContextKeys.LIGAND_PREPARATION_RESULT,
                new SelectedLigandPreparation(selected, preparation));
        context.put(ContextKeys.LIGAND_PDBQT, preparation.pdbqt());
        context.put(ContextKeys.LIGAND_PDBQT_PATH, outputPath);
    }
}
