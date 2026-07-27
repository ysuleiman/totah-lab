package totah.lab.pipeline;

public interface Stage {
    public void run(PipelineContext context) throws Exception;
}
