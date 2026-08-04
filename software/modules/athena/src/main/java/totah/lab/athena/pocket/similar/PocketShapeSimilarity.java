package totah.lab.athena.pocket.similar;

import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.structure.Structure;

import java.util.Objects;

public final class PocketShapeSimilarity {

    private PocketShapeSimilarity() {
    }

    public static double compare(
            Structure queryStructure,
            Pocket queryPocket,
            Structure candidateStructure,
            Pocket candidatePocket) {

        Objects.requireNonNull(
                queryStructure,
                "queryStructure");

        Objects.requireNonNull(
                queryPocket,
                "queryPocket");

        Objects.requireNonNull(
                candidateStructure,
                "candidateStructure");

        Objects.requireNonNull(
                candidatePocket,
                "candidatePocket");

        PocketShapeDescriptor queryDescriptor =
                PocketShapeDescriptorFactory.describe(
                        queryStructure,
                        queryPocket);

        PocketShapeDescriptor candidateDescriptor =
                PocketShapeDescriptorFactory.describe(
                        candidateStructure,
                        candidatePocket);

        return PocketShapeDistance.calculate(
                queryDescriptor,
                candidateDescriptor);
    }
}
