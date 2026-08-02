package totah.lab.hephaestus.receptor.residue;

public record AssignedTemplate(
        String chainId,
        int residueNumber,
        Character insertionCode,
        String residueName,
        String templateName) {
}
