package totah.lab.hephaestus.receptor.alphafold;


import java.util.List;
import java.util.Objects;

public record AlphaFoldConfidenceReport(
        double cutoff,
        int incomingResidueCount,
        int retainedResidueCount,
        List<String> droppedResidues) {

    public AlphaFoldConfidenceReport {
        if (!Double.isFinite(cutoff)
                || cutoff < 0.0
                || cutoff > 100.0) {

            throw new IllegalArgumentException(
                    "cutoff must be between 0 and 100.");
        }

        if (incomingResidueCount < 0) {
            throw new IllegalArgumentException(
                    "incomingResidueCount must not be negative.");
        }

        if (retainedResidueCount < 0) {
            throw new IllegalArgumentException(
                    "retainedResidueCount must not be negative.");
        }

        if (retainedResidueCount > incomingResidueCount) {
            throw new IllegalArgumentException(
                    "retainedResidueCount cannot exceed "
                            + "incomingResidueCount.");
        }

        droppedResidues =
                droppedResidues == null
                        ? List.of()
                        : List.copyOf(droppedResidues);

        if (droppedResidues.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    "droppedResidues must not contain null elements.");
        }
    }

    public int droppedResidueCount() {
        return droppedResidues.size();
    }
}
