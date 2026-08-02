package totah.lab.daedalus;

import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.daedalus.stage.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;


public class PipelineFactory {

    private final PipelineProperties properties;
    public PipelineFactory(){
        this(Paths.get(System.getProperty("user.dir")).toAbsolutePath());
    }

    public PipelineFactory(Path workSpacePath){
        this(new PipelineProperties(workSpacePath));

    }

    public PipelineFactory(PipelineProperties properties){
        this.properties = properties;
    }
    public Pipeline createDockingPipeline(Map<String, Object> config,  Path targetPdb) throws Exception {
        Path runDirectory = PipelineRunDirectoryFactory.create(properties.workspace());

        PipelineContext context = new PipelineContext(properties.workspace(), runDirectory)
                .with(ContextKeys.TARGET_PDB_PATH, targetPdb)
                .with(ContextKeys.RUN_DIRECTORY, runDirectory)
                .withAll(config);

        return Pipeline.builder().context(context)
                .stage(new TargetLoadStage())
                .stage(new ReceptorPreparationStage(
                        HephaestusClients.createDefault()))
                .build();
    }
}
