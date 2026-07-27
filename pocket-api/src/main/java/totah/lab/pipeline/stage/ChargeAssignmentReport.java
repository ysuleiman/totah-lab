package totah.lab.pipeline.stage;

import java.util.List;

public record ChargeAssignmentReport(
        int residueCount,
        int atomCount,
        String source,
        double totalCharge,
        List<String> assignedTemplates) {

    public ChargeAssignmentReport {
        assignedTemplates = List.copyOf(assignedTemplates);
    }
}
