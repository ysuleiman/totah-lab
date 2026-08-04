package totah.lab.web.service;

import totah.lab.athena.pocket.compare.PocketComparison;
import totah.lab.gaia.geometry.Point3D;

import java.util.List;

/**
 * Pairwise comparison payload for the inspection UI: both pockets'
 * geometry, the aligned point clouds exactly as produced by Athena's
 * alignment path ({@code PocketAlignment}), the comparison metrics
 * exactly as produced by {@code PocketComparator}, the residue
 * correspondence computed with the same retained alignment transform,
 * and the transform itself. No alignment or metric is recalculated in
 * web-api.
 */
public record PocketComparisonDetails(
        PocketGeometryView query,
        PocketGeometryView candidate,
        List<Point3D> alignedQueryPoints,
        List<Point3D> alignedCandidatePoints,
        PocketComparison comparison,
        String aligner,
        ResidueCorrespondenceView residueCorrespondence,
        TransformView transform
) {
}
