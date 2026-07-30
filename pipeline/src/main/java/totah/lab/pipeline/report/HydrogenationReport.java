package totah.lab.pipeline.report;

import java.util.List;

public record HydrogenationReport(
        int inputResidues,
        int outputResidues,
        int strippedHydrogens,
        int outputHydrogens,
        List<String> assignedTemplates,
        List<String> disulfideResidues) {

    public HydrogenationReport {
        assignedTemplates = List.copyOf(assignedTemplates);
        disulfideResidues = List.copyOf(disulfideResidues);
    }
}
