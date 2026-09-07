package totah.lab.daedalus.docking;

/** Runtime resource controls for one Vina process. */
public record VinaExecutionOptions(int cpuThreads) {

    public VinaExecutionOptions {
        if (cpuThreads < 1) {
            throw new IllegalArgumentException("cpuThreads must be positive");
        }
    }
}
