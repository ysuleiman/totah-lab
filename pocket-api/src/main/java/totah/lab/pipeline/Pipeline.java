package totah.lab.pipeline;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Builder
@Getter
public class Pipeline {

    PipelineContext context;

    @Singular
    private final List<Stage> stages;

    public void run() throws Exception {
        for (Stage stage : stages) {
            stage.run(context);
        }
    }
}