package totah.lab.athena.pocket.component;

public record ComponentPocketGeometry(
        ComponentPocketRelationshipClass relationshipClass,
        double minimumPocketAtomDistance,
        double minimumAlphaSphereCenterDistance,
        double minimumAlphaSphereSurfaceDistance,
        double componentCentroidPocketCentroidDistance,
        int heavyAtomsInsideSphereCloud,
        int heavyAtomsNearSphereCloud,
        double heavyAtomInsideFraction,
        double heavyAtomNearFraction,
        int contactingPocketResidues) {}
