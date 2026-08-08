package totah.lab.daedalus.fpocket;

import java.nio.file.Path;
import java.time.Duration;

/** Command-line entry point for the production assembly-mmCIF batch runner. */
public final class FpocketBatchCli {
    private FpocketBatchCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 5) {
            throw new IllegalArgumentException("Usage: FpocketBatchCli "
                    + "<fpocket> <cohort-root> <output-root> [workers] [force]");
        }
        int workers = args.length >= 4 ? Integer.parseInt(args[3])
                : Math.max(1, Math.min(8,
                        Runtime.getRuntime().availableProcessors() - 1));
        boolean force = args.length == 5 && Boolean.parseBoolean(args[4]);
        FpocketBatchRunner.BatchSummary summary = new FpocketBatchRunner(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), workers,
                Duration.ofMinutes(10), force).run();
        System.out.println(summary);
    }
}
