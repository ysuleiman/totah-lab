package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.Sphere;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.Structure;
import totah.lab.report.config.PocketReportConfiguration;
import totah.lab.report.evidence.EvidenceCategory;
import totah.lab.report.evidence.ReportEvidence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DefaultPocketGeometryAnalyzer
        implements PocketGeometryAnalyzer {

    @Override
    public PocketAnalysisResult analyze(
            Pocket pocket,
            Structure structure,
            PocketReportConfiguration configuration
    ) {
        List<PocketAnalysisSupport.ResolvedResidue> resolved =
                PocketAnalysisSupport.resolve(pocket, structure);
        List<Point3D> heavyAtomPositions =
                heavyAtomPositions(resolved);
        Point3D centroid = centroid(heavyAtomPositions);
        Bounds box = bounds(heavyAtomPositions);
        double boundingBoxVolume = box.volume();
        double maximumCentroidDistance = heavyAtomPositions.stream()
                .mapToDouble(position -> distance(position, centroid))
                .max()
                .orElseThrow();
        List<Double> centroidDistances = heavyAtomPositions.stream()
                .map(position -> distance(position, centroid))
                .sorted()
                .toList();
        double meanCentroidDistance = centroidDistances.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        double percentile95CentroidDistance =
                percentileNearestRank(centroidDistances, 0.95);
        double maximumPairwiseSpan =
                maximumPairwiseDistance(heavyAtomPositions);
        double radiusOfGyration = Math.sqrt(heavyAtomPositions.stream()
                .mapToDouble(position -> {
                    double distance = distance(position, centroid);
                    return distance * distance;
                })
                .average()
                .orElseThrow());
        int heavyAtomCount = heavyAtomPositions.size();
        int sphereCount = sphereCount(pocket);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("basis", "RESIDUE_HEAVY_ATOMS");
        values.put("centroid", point(centroid));
        values.put("boundingBox", box(box));
        values.put("boundingBoxVolumeAngstrom3", boundingBoxVolume);
        values.put("maximumCentroidDistanceAngstrom",
                maximumCentroidDistance);
        values.put("meanCentroidDistanceAngstrom", meanCentroidDistance);
        values.put(
                "percentile95CentroidDistanceAngstrom",
                percentile95CentroidDistance
        );
        values.put("maximumPairwiseSpanAngstrom", maximumPairwiseSpan);
        values.put("radiusOfGyrationAngstrom", radiusOfGyration);
        values.put("heavyAtomCount", heavyAtomCount);
        values.put("pointCount", sphereCount);
        copyOptionalMetric(
                pocket,
                values,
                "sourcePocketNumber",
                "sourcePocketNumber"
        );
        copyOptionalMetric(
                pocket,
                values,
                "internalPocketId",
                "internalPocketId"
        );
        copyOptionalMetric(
                pocket,
                values,
                "volume",
                "estimatedVolumeAngstrom3"
        );
        copyOptionalMetric(
                pocket,
                values,
                "druggability_score",
                "druggabilityScore"
        );

        List<ReportEvidence> evidence = new ArrayList<>();
        java.util.Optional<Double> cavityVolume =
                numericAttribute(pocket, "volume");
        cavityVolume.ifPresent(volume ->
                evidence.add(new ReportEvidence(
                        "G-001",
                        EvidenceCategory.GEOMETRY,
                        "The pocket source reports an estimated cavity volume "
                                + "of " + decimal(volume)
                                + " cubic angstroms.",
                        Map.of("estimatedVolumeAngstrom3", volume)
                )));
        if (cavityVolume.isEmpty()) {
            evidence.add(boundingBoxEvidence("G-001", boundingBoxVolume));
        }
        evidence.add(new ReportEvidence(
                "G-002",
                EvidenceCategory.GEOMETRY,
                "The maximum heavy-atom distance from the pocket centroid is "
                        + decimal(maximumCentroidDistance) + " angstroms.",
                Map.of(
                        "maximumCentroidDistanceAngstrom",
                        maximumCentroidDistance,
                        "meanCentroidDistanceAngstrom",
                        meanCentroidDistance,
                        "percentile95CentroidDistanceAngstrom",
                        percentile95CentroidDistance,
                        "maximumPairwiseSpanAngstrom",
                        maximumPairwiseSpan,
                        "radiusOfGyrationAngstrom",
                        radiusOfGyration
                )
        ));
        if (cavityVolume.isPresent()) {
            evidence.add(boundingBoxEvidence("G-003", boundingBoxVolume));
        }
        return new PocketAnalysisResult(values, evidence);
    }

    private ReportEvidence boundingBoxEvidence(
            String id,
            double boundingBoxVolume
    ) {
        return new ReportEvidence(
                id,
                EvidenceCategory.GEOMETRY,
                "The residue-heavy-atom pocket bounding box has a volume of "
                        + decimal(boundingBoxVolume) + " cubic angstroms.",
                Map.of(
                        "boundingBoxVolumeAngstrom3",
                        boundingBoxVolume
                )
        );
    }

    private List<Point3D> heavyAtomPositions(
            List<PocketAnalysisSupport.ResolvedResidue> residues) {
        return residues.stream()
                .flatMap(residue -> residue.residue().getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .map(Atom::getPosition)
                .toList();
    }

    private Point3D centroid(List<Point3D> positions) {
        if (positions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket residues contain no heavy atoms");
        }
        return new Point3D(
                positions.stream().mapToDouble(Point3D::x).average().orElseThrow(),
                positions.stream().mapToDouble(Point3D::y).average().orElseThrow(),
                positions.stream().mapToDouble(Point3D::z).average().orElseThrow());
    }

    private Bounds bounds(List<Point3D> positions) {
        if (positions.isEmpty()) {
            throw new IllegalArgumentException(
                    "Pocket residues contain no heavy atoms");
        }
        return new Bounds(
                new Point3D(
                        positions.stream().mapToDouble(Point3D::x).min().orElseThrow(),
                        positions.stream().mapToDouble(Point3D::y).min().orElseThrow(),
                        positions.stream().mapToDouble(Point3D::z).min().orElseThrow()),
                new Point3D(
                        positions.stream().mapToDouble(Point3D::x).max().orElseThrow(),
                        positions.stream().mapToDouble(Point3D::y).max().orElseThrow(),
                        positions.stream().mapToDouble(Point3D::z).max().orElseThrow()));
    }

    private double percentileNearestRank(
            List<Double> sorted,
            double percentile
    ) {
        int index = Math.max(
                0,
                (int) Math.ceil(percentile * sorted.size()) - 1
        );
        return sorted.get(index);
    }

    private double maximumPairwiseDistance(List<Point3D> positions) {
        double maximum = 0.0;
        for (int first = 0; first < positions.size(); first++) {
            for (int second = first + 1;
                 second < positions.size();
                 second++) {
                maximum = Math.max(
                        maximum,
                        distance(positions.get(first), positions.get(second))
                );
            }
        }
        return maximum;
    }

    private double distance(Point3D first, Point3D second) {
        double x = first.x() - second.x();
        double y = first.y() - second.y();
        double z = first.z() - second.z();
        return Math.sqrt(x * x + y * y + z * z);
    }

    private int sphereCount(Pocket pocket) {
        List<Sphere> spheres = pocket.getSpheres();
        return spheres == null ? 0 : spheres.size();
    }

    private Map<String, Double> point(Point3D point) {
        return Map.of("x", point.x(), "y", point.y(), "z", point.z());
    }

    private Map<String, Object> box(Bounds box) {
        return Map.of(
                "min", point(box.min()),
                "max", point(box.max()),
                "sizeAngstrom", Map.of(
                        "x", box.sizeX(),
                        "y", box.sizeY(),
                        "z", box.sizeZ()
                )
        );
    }

    private record Bounds(Point3D min, Point3D max) {
        private double sizeX() { return max.x() - min.x(); }
        private double sizeY() { return max.y() - min.y(); }
        private double sizeZ() { return max.z() - min.z(); }
        private double volume() { return sizeX() * sizeY() * sizeZ(); }
    }

    private void copyOptionalMetric(
            Pocket pocket,
            Map<String, Object> values,
            String attribute,
            String reportName
    ) {
        numericAttribute(pocket, attribute)
                .ifPresent(value -> values.put(reportName, value));
    }

    private java.util.Optional<Double> numericAttribute(
            Pocket pocket,
            String name
    ) {
        Object value = pocket.getAttributes().get(name);
        if (value instanceof Number number) {
            double result = number.doubleValue();
            if (Double.isFinite(result)) {
                return java.util.Optional.of(result);
            }
        }
        return java.util.Optional.empty();
    }

    private String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
