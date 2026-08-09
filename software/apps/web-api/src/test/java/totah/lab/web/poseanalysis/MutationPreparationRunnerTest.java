package totah.lab.web.poseanalysis;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MutationPreparationRunnerTest {

    @Test
    void dryRunDoesNotPrepareOrWriteTheMutation() {
        RecordingPreparation service = new RecordingPreparation();
        MutationPreparationRunner runner = new MutationPreparationRunner(
                service,
                "42",
                "V6L",
                "target/mutant.pdbqt",
                true
        );

        runner.run();

        assertFalse(service.called);
    }

    private static final class RecordingPreparation
            implements MutationPreparationOperation {

        private boolean called;

        @Override
        public MutationPreparationService.MutationPreparationResult prepare(
                long runId,
                String mutationSpec,
                Path output
        ) {
            called = true;
            throw new AssertionError("dry-run invoked preparation");
        }

        @Override
        public String render(
                MutationPreparationService.MutationPreparationResult result
        ) {
            called = true;
            throw new AssertionError("dry-run rendered a result");
        }
    }
}
