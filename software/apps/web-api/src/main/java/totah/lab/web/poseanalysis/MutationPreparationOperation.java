package totah.lab.web.poseanalysis;

import java.nio.file.Path;

/** Boundary used by the gated mutation-preparation runner. */
public interface MutationPreparationOperation {

    MutationPreparationService.MutationPreparationResult prepare(
            long runId,
            String mutationSpec,
            Path output
    );

    String render(
            MutationPreparationService.MutationPreparationResult result
    );
}
