package totah.lab.daedalus;

import totah.lab.hephaestus.client.HephaestusClients;
import totah.lab.daedalus.docking.VinaDockingRunner;
import totah.lab.daedalus.stage.*;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Objects;


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

    /**
     * The docking pipeline without docking execution: target load,
     * receptor preparation, ligand preparation and docking-input
     * assembly. The ligand SDF comes from the config map
     * ({@link ContextKeys#LIGAND_PATH}).
     */
    public Pipeline createDockingPipeline(Map<String, Object> config,  Path targetPdb) throws Exception {
        return createDockingPipeline(
                config, targetPdb, new DockingProperties(null));
    }

    /**
     * The full docking pipeline. Vina execution is opt-in: the
     * {@link VinaDockingStage} is only wired when
     * {@code dockingProperties.vinaExecutable()} is configured, and
     * additionally requires {@link ContextKeys#VINA_DOCKING_OPTIONS}
     * (the search box) in the config map at run time.
     */
    public Pipeline createDockingPipeline(
            Map<String, Object> config,
            Path targetPdb,
            DockingProperties dockingProperties) throws Exception {
        Objects.requireNonNull(
                dockingProperties, "dockingProperties");
        Path runDirectory = PipelineRunDirectoryFactory.create(properties.workspace());

        PipelineContext context = new PipelineContext(properties.workspace(), runDirectory)
                .with(ContextKeys.TARGET_PDB_PATH, targetPdb)
                .with(ContextKeys.RUN_DIRECTORY, runDirectory)
                .withAll(config);

        Pipeline.PipelineBuilder builder = Pipeline.builder().context(context)
                .stage(new TargetLoadStage())
                .stage(new ReceptorPreparationStage(
                        HephaestusClients.createDefault()))
                .stage(new LigandPreparationStage(
                        HephaestusClients.createDefault()))
                .stage(new DockingInputAssemblyStage());

        if (dockingProperties.vinaExecutable() != null) {
            builder.stage(new VinaDockingStage(
                    VinaDockingRunner.fromProperties(dockingProperties)));
        }

        return builder.build();
    }
}
