package totah.lab.pipeline.stage;

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
