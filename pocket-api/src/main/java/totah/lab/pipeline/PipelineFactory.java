package totah.lab.pipeline;

import totah.lab.math.charges.QEqModel;
import totah.lab.math.linear.HybridSolver;
import totah.lab.pipeline.stage.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;


public class PipelineFactory {

    private final PipelineProperties properties;
    private final String QEQ_RESOURCE = "/qeq.txt";

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
        return createDockingPipeline(config, targetPdb, loadQeqFromResources());
    }

    public Pipeline createDockingPipeline(Map<String, Object> config, Path targetPdb, Path qeqFile) throws Exception {
        QEqModel qeqModel = qeqFile != null
                ? new QEqModel(new HybridSolver(2000), qeqFile)
                : new QEqModel(new HybridSolver(2000));
        Path runDirectory = createRunDirectory();

        PipelineContext context = new PipelineContext(properties.workspace(), runDirectory)
                .with(ContextKeys.TARGET_PDB_PATH, targetPdb)
                .with(ContextKeys.QEQ_FILE, qeqFile)
                .with(ContextKeys.RUN_DIRECTORY, runDirectory)
                .withAll(config);

        return Pipeline.builder().context(context)
                .stage(new TargetLoadStage())
                .stage(new StructureCleanupStage())
                .stage(new AlphaFoldFilterStage())
                .stage(new ResidueStateAssignmentStage())
                .stage(new ReceptorHydrogenationStage())
                .stage(new HydrogenOptimizationStage())
                .stage(new TopologyBuilderStage())
                .stage(new ChargeAssignmentStage(qeqModel))
                .stage(new AD4AtomTypingStage())
                .stage(new PdbqtExporterStage())
                .build();
    }

    private Path createRunDirectory() throws IOException {
        LocalDateTime now = LocalDateTime.now();
        String runId = now
                .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS"));
        Path runDirectory = this.properties.workspace().resolve(runId);
        Files.createDirectories(runDirectory);
        return runDirectory;
    }

    private Path loadQeqFromResources() {
        try (InputStream is = PipelineFactory.class.getResourceAsStream(QEQ_RESOURCE)) {
            if (is == null) {
                return null; // use built-in parameters
            }
            Path temp = Files.createTempFile("qeq", ".txt");
            Files.copy(is, temp, StandardCopyOption.REPLACE_EXISTING);
            temp.toFile().deleteOnExit();
            return temp;
        } catch (Exception e) {
            System.err.println("Warning: Could not load " + QEQ_RESOURCE
                    + ", using built-in parameters (" + e + ")");
            return null;
        }
    }
}
