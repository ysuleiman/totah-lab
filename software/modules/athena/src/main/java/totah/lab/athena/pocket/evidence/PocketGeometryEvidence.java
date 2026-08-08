package totah.lab.athena.pocket.evidence;

import totah.lab.gaia.geometry.Point3D;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Source-reported fpocket output plus separately versioned derived geometry. */
public record PocketGeometryEvidence(
        String fpocketId,
        EvidenceChannel<Integer> reportedRank,
        EvidenceChannel<Double> reportedScore,
        EvidenceChannel<Map<String, Double>> reportedDescriptors,
        EvidenceChannel<Double> reportedVolume,
        EvidenceChannel<List<Point3D>> alphaSpheres,
        EvidenceChannel<Point3D> centroid,
        EvidenceChannel<Map<String, Double>> shapeRepresentation
) {
    public PocketGeometryEvidence {
        Objects.requireNonNull(fpocketId, "fpocketId");
        if (fpocketId.isBlank()) {
            throw new IllegalArgumentException("fpocketId must not be blank");
        }
        fpocketId = fpocketId.trim();
        Objects.requireNonNull(reportedRank, "reportedRank");
        Objects.requireNonNull(reportedScore, "reportedScore");
        Objects.requireNonNull(reportedDescriptors, "reportedDescriptors");
        Objects.requireNonNull(reportedVolume, "reportedVolume");
        Objects.requireNonNull(alphaSpheres, "alphaSpheres");
        Objects.requireNonNull(centroid, "centroid");
        Objects.requireNonNull(shapeRepresentation, "shapeRepresentation");
        EvidenceChannel.requireOrigin(reportedRank,
                EvidenceOrigin.SOURCE_REPORTED, "reportedRank");
        EvidenceChannel.requireOrigin(reportedScore,
                EvidenceOrigin.SOURCE_REPORTED, "reportedScore");
        EvidenceChannel.requireOrigin(reportedDescriptors,
                EvidenceOrigin.SOURCE_REPORTED, "reportedDescriptors");
        EvidenceChannel.requireOrigin(reportedVolume,
                EvidenceOrigin.SOURCE_REPORTED, "reportedVolume");
        EvidenceChannel.requireOrigin(alphaSpheres,
                EvidenceOrigin.SOURCE_OBSERVED, "alphaSpheres");
        EvidenceChannel.requireOrigin(centroid, EvidenceOrigin.DERIVED, "centroid");
        EvidenceChannel.requireOrigin(shapeRepresentation,
                EvidenceOrigin.DERIVED, "shapeRepresentation");
    }
}
