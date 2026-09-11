package totah.lab.daedalus.docking;

import java.time.Duration;
import java.util.Objects;

/** Runtime resource controls for one Vina process. */
public record VinaExecutionOptions(int cpuThreads, Duration executionTimeout) {
    public static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofMinutes(30);

    public VinaExecutionOptions(int cpuThreads) {
        this(cpuThreads, DEFAULT_EXECUTION_TIMEOUT);
    }

    public VinaExecutionOptions {
        if (cpuThreads < 1) {
            throw new IllegalArgumentException("cpuThreads must be positive");
        }
        Objects.requireNonNull(executionTimeout, "executionTimeout");
        if (executionTimeout.isZero() || executionTimeout.isNegative()) {
            throw new IllegalArgumentException("executionTimeout must be positive");
        }
    }
}
