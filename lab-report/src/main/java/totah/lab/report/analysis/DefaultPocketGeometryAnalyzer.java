package totah.lab.report.analysis;

import totah.lab.pocket.Pocket;
import totah.lab.pocket.PocketBox;
import totah.lab.pocket.Sphere;
import totah.lab.pocket.geometry.PocketGeometry;
import totah.lab.protein.Atom;
import totah.lab.protein.Point3D;
import totah.lab.protein.Residue;
import totah.lab.protein.Structure;
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
        Pocket resolved = PocketAnalysisSupport.resolvedCopy(
                pocket,
                structure
        );
        Point3D centroid = PocketGeometry.calculateHeavyAtomCentroid(resolved);
        PocketBox box = PocketGeometry.calculateHeavyAtomBox(resolved);
        double boundingBoxVolume = PocketGeometry.boxVolume(box);
        double maximumCentroidDistance =
                PocketGeometry.maximumHeavyAtomDistance(resolved, centroid);
        double radiusOfGyration =
                PocketGeometry.heavyAtomRadiusOfGyration(resolved);
        int heavyAtomCount = heavyAtomCount(resolved.getResidues());
        int sphereCount = sphereCount(resolved);

        Map<String, Object> values = new LinkedHashMap<>();
        values.put("basis", "RESIDUE_HEAVY_ATOMS");
        values.put("centroid", point(centroid));
        values.put("boundingBox", box(box));
        values.put("boundingBoxVolumeAngstrom3", boundingBoxVolume);
        values.put("maximumCentroidDistanceAngstrom",
                maximumCentroidDistance);
        values.put("radiusOfGyrationAngstrom", radiusOfGyration);
        values.put("heavyAtomCount", heavyAtomCount);
        values.put("pointCount", sphereCount);
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
        evidence.add(new ReportEvidence(
                "G-001",
                EvidenceCategory.GEOMETRY,
                "The residue-heavy-atom pocket bounding box has a volume of "
                        + decimal(boundingBoxVolume) + " cubic angstroms.",
                Map.of(
                        "boundingBoxVolumeAngstrom3",
                        boundingBoxVolume
                )
        ));
        evidence.add(new ReportEvidence(
                "G-002",
                EvidenceCategory.GEOMETRY,
                "The maximum heavy-atom distance from the pocket centroid is "
                        + decimal(maximumCentroidDistance) + " angstroms.",
                Map.of(
                        "maximumCentroidDistanceAngstrom",
                        maximumCentroidDistance,
                        "radiusOfGyrationAngstrom",
                        radiusOfGyration
                )
        ));
        numericAttribute(pocket, "volume").ifPresent(volume ->
                evidence.add(new ReportEvidence(
                        "G-003",
                        EvidenceCategory.GEOMETRY,
                        "The pocket source reports an estimated cavity volume "
                                + "of " + decimal(volume)
                                + " cubic angstroms.",
                        Map.of("estimatedVolumeAngstrom3", volume)
                )));
        return new PocketAnalysisResult(values, evidence);
    }

    private int heavyAtomCount(List<Residue> residues) {
        return Math.toIntExact(residues.stream()
                .flatMap(residue -> residue.getAtoms().stream())
                .filter(Atom::isHeavyAtom)
                .count());
    }

    private int sphereCount(Pocket pocket) {
        List<Sphere> spheres = pocket.getSpheres();
        return spheres == null ? 0 : spheres.size();
    }

    private Map<String, Double> point(Point3D point) {
        return Map.of("x", point.x(), "y", point.y(), "z", point.z());
    }

    private Map<String, Object> box(PocketBox box) {
        return Map.of(
                "min", point(box.getMin()),
                "max", point(box.getMax()),
                "sizeAngstrom", Map.of(
                        "x", box.getSizeX(),
                        "y", box.getSizeY(),
                        "z", box.getSizeZ()
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
