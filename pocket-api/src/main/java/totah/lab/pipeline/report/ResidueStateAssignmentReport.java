package totah.lab.pipeline.report;

import java.util.List;

public record ResidueStateAssignmentReport(
        int inputResidues,
        int outputResidues,
        List<String> convertedResidues,
        List<String> disulfideResidues,
        List<String> assignedTemplates) {

    public ResidueStateAssignmentReport {
        convertedResidues = List.copyOf(convertedResidues);
        disulfideResidues = List.copyOf(disulfideResidues);
        assignedTemplates = List.copyOf(assignedTemplates);
    }
}
