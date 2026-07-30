package totah.lab.pipeline.report;

import java.util.List;

public record AlphaFoldConfidenceReport(
        double cutoff,
        int inputResidues,
        int outputResidues,
        List<String> droppedResidues) {

    public AlphaFoldConfidenceReport {
        droppedResidues = List.copyOf(droppedResidues);
    }
}
