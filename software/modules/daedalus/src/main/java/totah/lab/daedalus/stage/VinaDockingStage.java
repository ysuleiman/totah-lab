package totah.lab.daedalus.stage;

import totah.lab.daedalus.ContextKeys;
import totah.lab.daedalus.PipelineContext;
import totah.lab.daedalus.Stage;
import totah.lab.daedalus.docking.DockingInput;
import totah.lab.daedalus.docking.VinaDockingOptions;
import totah.lab.daedalus.docking.VinaDockingResult;
import totah.lab.daedalus.docking.VinaDockingRunner;

import java.util.Objects;

/**
 * Runs AutoDock Vina on the assembled docking input. Only wired into a
 * pipeline when a vina executable is configured — see
 * {@code PipelineFactory}. The search box comes from the pipeline
 * context ({@link ContextKeys#VINA_DOCKING_OPTIONS}); execution
 * failures surface as a clear stage error.
 */
public final class VinaDockingStage implements Stage {
    private final VinaDockingRunner runner;

    public VinaDockingStage(VinaDockingRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @Override
    public void run(PipelineContext context) {
        Objects.requireNonNull(context, "context");
        DockingInput input = context.require(ContextKeys.DOCKING_INPUT);

        Object configured = context.get(ContextKeys.VINA_DOCKING_OPTIONS);
        if (!(configured instanceof VinaDockingOptions options)) {
            throw new IllegalStateException(
                    "Vina docking requires "
                            + ContextKeys.VINA_DOCKING_OPTIONS
                            + " (search box) in the pipeline context");
        }

        final VinaDockingResult result;
        try {
            result = runner.run(input, options);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(
                    "Vina execution interrupted", exception);
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Vina execution failed: " + exception.getMessage(),
                    exception);
        }

        if (result.exitCode() != 0) {
            throw new IllegalStateException(
                    "Vina exited with code " + result.exitCode()
                            + ": " + tail(result.output()));
        }

        context.put(ContextKeys.DOCKING_RESULT, result);
    }

    private static String tail(String output) {
        if (output == null || output.isBlank()) {
            return "(no output)";
        }
        String trimmed = output.strip();
        return trimmed.length() <= 300
                ? trimmed
                : trimmed.substring(trimmed.length() - 300);
    }
}
