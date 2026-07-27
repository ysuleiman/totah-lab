package totah.lab.protein;

public record ResidueClassificationEvidence(
        boolean available,
        boolean standard,
        boolean polymeric,
        boolean water,
        String parentComponentId,
        String residueType,
        String polymerType
) {}
