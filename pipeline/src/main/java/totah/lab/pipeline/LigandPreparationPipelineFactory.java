package totah.lab.pipeline;

import totah.lab.pipeline.stage.LigandAtomTypingStage;
import totah.lab.pipeline.stage.LigandChargeAssignmentStage;
import totah.lab.pipeline.stage.LigandGraphBuilderStage;
import totah.lab.pipeline.stage.LigandHydrogenationStage;
import totah.lab.pipeline.stage.LigandInputStage;
import totah.lab.pipeline.stage.LigandPdbqtExporterStage;
import totah.lab.pipeline.stage.LigandSelectionStage;
import totah.lab.pipeline.stage.LigandTorsionTreeStage;
import totah.lab.ligand.selection.LigandSelectionPolicy;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Creates the standalone PDB/mmCIF ligand-preparation workflow.
 */
public final class LigandPreparationPipelineFactory {

    private final PipelineProperties properties;

    public LigandPreparationPipelineFactory(Path workspace) {
        this(new PipelineProperties(workspace));
    }

    public LigandPreparationPipelineFactory(PipelineProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties is null");
    }

    public Pipeline create(Map<String, Object> config, Path ligandPath) throws Exception {
        Objects.requireNonNull(ligandPath, "ligandPath is null");
        Path runDirectory =
                PipelineRunDirectoryFactory.create(properties.workspace());
        PipelineContext context = new PipelineContext(
                properties.workspace(), runDirectory)
                .with(ContextKeys.LIGAND_PATH, ligandPath)
                .with(ContextKeys.RUN_DIRECTORY, runDirectory)
                .withAll(config);

        return Pipeline.builder()
                .context(context)
                .stage(new LigandInputStage())
                .stage(new LigandSelectionStage(
                        true, new LigandSelectionPolicy(Set.of())))
                .stage(new LigandGraphBuilderStage())
                .stage(new LigandHydrogenationStage())
                .stage(new LigandChargeAssignmentStage())
                .stage(new LigandAtomTypingStage())
                .stage(new LigandTorsionTreeStage())
                .stage(new LigandPdbqtExporterStage())
                .build();
    }
}
