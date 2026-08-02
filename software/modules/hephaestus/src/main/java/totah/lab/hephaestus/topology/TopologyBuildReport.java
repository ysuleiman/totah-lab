package totah.lab.hephaestus.topology;

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
