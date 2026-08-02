package totah.lab.hephaestus.charge;

import java.util.List;

public record ChargeAssignmentReport(
        int residueCount,
        int atomCount,
        int amberAssignedAtoms,
        int fixedIonAtoms,
        String source,
        double totalCharge,
        List<String> assignedTemplates) {

    public ChargeAssignmentReport {
        assignedTemplates = List.copyOf(assignedTemplates);
    }
}
