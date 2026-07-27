package totah.lab.pipeline.report;

import java.util.List;

public record HydrogenOptimizationReport(
        int inputResidues,
        int outputResidues,
        int optimizedResidues,
        int movedHydrogens,
        List<String> optimizedResidueLabels) {

    public HydrogenOptimizationReport {
        optimizedResidueLabels = List.copyOf(optimizedResidueLabels);
    }
}
