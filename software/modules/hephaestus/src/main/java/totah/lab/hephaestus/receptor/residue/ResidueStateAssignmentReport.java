package totah.lab.hephaestus.receptor.residue;


import java.util.List;
import java.util.Objects;

public record ResidueStateAssignmentReport(
        int incomingResidueCount,
        int preparedResidueCount,
        List<String> convertedResidues,
        List<String> disulfideResidues,
        List<String> assignedTemplates) {

    public ResidueStateAssignmentReport {

        if (incomingResidueCount < 0) {
            throw new IllegalArgumentException(
                    "incomingResidueCount must not be negative.");
        }

        if (preparedResidueCount < 0) {
            throw new IllegalArgumentException(
                    "preparedResidueCount must not be negative.");
        }

        if (preparedResidueCount > incomingResidueCount) {
            throw new IllegalArgumentException(
                    "preparedResidueCount cannot exceed incomingResidueCount.");
        }

        convertedResidues = convertedResidues == null
                ? List.of()
                : List.copyOf(convertedResidues);

        disulfideResidues = disulfideResidues == null
                ? List.of()
                : List.copyOf(disulfideResidues);

        assignedTemplates = assignedTemplates == null
                ? List.of()
                : List.copyOf(assignedTemplates);

        requireNoNulls(convertedResidues, "convertedResidues");
        requireNoNulls(disulfideResidues, "disulfideResidues");
        requireNoNulls(assignedTemplates, "assignedTemplates");
    }

    public int convertedResidueCount() {
        return convertedResidues.size();
    }

    public int disulfideResidueCount() {
        return disulfideResidues.size();
    }

    public int assignedTemplateCount() {
        return assignedTemplates.size();
    }

    private static void requireNoNulls(
            List<String> values,
            String name) {

        if (values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(
                    name + " must not contain null elements.");
        }
    }
}
