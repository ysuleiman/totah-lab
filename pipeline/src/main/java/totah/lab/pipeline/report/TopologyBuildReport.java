package totah.lab.pipeline.report;

import java.util.List;

public record TopologyBuildReport(
        int residueCount,
        int atomCount,
        int bondCount,
        int templateBondCount,
        int peptideBondCount,
        int disulfideBondCount,
        List<String> assignedTemplates) {

    public TopologyBuildReport {
        assignedTemplates = List.copyOf(assignedTemplates);
    }
}
