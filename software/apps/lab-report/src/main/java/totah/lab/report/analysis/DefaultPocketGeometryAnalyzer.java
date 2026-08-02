package totah.lab.report.analysis;

import totah.lab.athena.pocket.geometry.PocketResidueGeometry;
import totah.lab.athena.pocket.geometry.PocketShapeStatistics;
import totah.lab.athena.pocket.geometry.ResidueAtomPocketGeometry;
import totah.lab.gaia.pocket.Pocket;
import totah.lab.gaia.pocket.PocketMetricType;
import totah.lab.gaia.geometry.BoundingBox;
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
        PocketResidueGeometry pocketGeometry =
                new ResidueAtomPocketGeometry()
                        .residueGeometry(structure, pocket);
        Point3D centroid = pocketGeometry.centroid();
        BoundingBox box = pocketGeometry.bounds();
        double boundingBoxVolume = box.volume();
        PocketShapeStatistics shape =
                PocketShapeStatistics.of(heavyAtomPositions);
        int sphereCount = sphereCount(pocket);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("basis", "RESIDUE_HEAVY_ATOMS");
        values.put("centroid", point(centroid));
        values.put("boundingBox", box(box));
        values.put("boundingBoxVolumeAngstrom3", boundingBoxVolume);
        values.put("maximumCentroidDistanceAngstrom",
                shape.maximumCentroidDistance());
        values.put("meanCentroidDistanceAngstrom",
                shape.meanCentroidDistance());
        values.put(
                "percentile95CentroidDistanceAngstrom",
                shape.percentile95CentroidDistance()
        );
        values.put("maximumPairwiseSpanAngstrom",
                shape.maximumPairwiseSpan());
        values.put("radiusOfGyrationAngstrom",
                shape.radiusOfGyration());
        values.put("heavyAtomCount", shape.heavyAtomCount());
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
                        + decimal(shape.maximumCentroidDistance())
                        + " angstroms.",
                Map.of(
                        "maximumCentroidDistanceAngstrom",
                        shape.maximumCentroidDistance(),
                        "meanCentroidDistanceAngstrom",
                        shape.meanCentroidDistance(),
                        "percentile95CentroidDistanceAngstrom",
                        shape.percentile95CentroidDistance(),
                        "maximumPairwiseSpanAngstrom",
                        shape.maximumPairwiseSpan(),
                        "radiusOfGyrationAngstrom",
                        shape.radiusOfGyration()
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

    private int sphereCount(Pocket pocket) {
        return pocket.alphaSphereSet()
                .map(sphereSet -> sphereSet.spheres().size())
                .orElse(0);
    }

    private Map<String, Double> point(Point3D point) {
        return Map.of("x", point.x(), "y", point.y(), "z", point.z());
    }

    private Map<String, Object> box(BoundingBox box) {
        return Map.of(
                "min", point(box.min()),
                "max", point(box.max()),
                "sizeAngstrom", Map.of(
                        "x", box.width(),
                        "y", box.height(),
                        "z", box.depth()
                )
        );
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
        return switch (name) {
            case "volume" -> metric(pocket, PocketMetricType.VOLUME);
            case "druggability_score" ->
                    metric(pocket, PocketMetricType.FPOCKET_DRUGGABILITY);
            default -> metadataNumber(pocket, name);
        };
    }

    private java.util.Optional<Double> metric(
            Pocket pocket,
            PocketMetricType type
    ) {
        java.util.OptionalDouble value = pocket.metric(type);
        return value.isPresent()
                ? java.util.Optional.of(value.getAsDouble())
                : java.util.Optional.<Double>empty();
    }

    private java.util.Optional<Double> metadataNumber(
            Pocket pocket,
            String name
    ) {
        String value = pocket.metadata().get(name);
        if (value == null) {
            return java.util.Optional.empty();
        }
        try {
            double result = Double.parseDouble(value);
            if (Double.isFinite(result)) {
                return java.util.Optional.of(result);
            }
        } catch (NumberFormatException ignored) {
            // Non-numeric metadata values are not metrics.
        }
        return java.util.Optional.empty();
    }

    private String decimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}
