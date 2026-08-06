package totah.lab.daedalus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.daedalus.stage.DockingInputAssemblyStage;
import totah.lab.daedalus.stage.LigandPreparationStage;
import totah.lab.daedalus.stage.ReceptorPreparationStage;
import totah.lab.daedalus.stage.TargetLoadStage;
import totah.lab.daedalus.stage.VinaDockingStage;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class PipelineFactoryTest {
    @TempDir
    Path workspace;

    @Test
    void dockingPipelineAssemblesPreparationStagesWithoutVina()
            throws Exception {
        Pipeline pipeline = new PipelineFactory(workspace)
                .createDockingPipeline(
                        Map.of(),
                        workspace.resolve("target.pdb"));

        List<Class<?>> stages = stageTypes(pipeline);
        assertEquals(List.of(
                TargetLoadStage.class,
                ReceptorPreparationStage.class,
                LigandPreparationStage.class,
                DockingInputAssemblyStage.class), stages);
    }

    @Test
    void vinaStageIsOnlyWiredWhenAnExecutableIsConfigured()
            throws Exception {
        Pipeline without = new PipelineFactory(workspace)
                .createDockingPipeline(
                        Map.of(),
                        workspace.resolve("target.pdb"),
                        new DockingProperties(null));
        assertEquals(4, without.getStages().size());

        Pipeline with = new PipelineFactory(workspace)
                .createDockingPipeline(
                        Map.of(),
                        workspace.resolve("target.pdb"),
                        new DockingProperties(
                                workspace.resolve("vina")));
        List<Class<?>> stages = stageTypes(with);
        assertEquals(5, stages.size());
        assertInstanceOf(VinaDockingStage.class,
                with.getStages().get(4));
    }

    private static List<Class<?>> stageTypes(Pipeline pipeline) {
        return pipeline.getStages().stream()
                .map(Object::getClass)
                .toList();
    }
}
