package totah.lab.pipeline.stage;

public record ResidueState(
        String residueKey,
        String originalName,
        String preparedName,
        String amberTemplateName,
        boolean nTerminus,
        boolean cTerminus,
        boolean disulfide,
        String note) {
}
