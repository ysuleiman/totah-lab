package totah.lab.athena.design.grammar;

import totah.lab.athena.pocket.compare.KabschRigidPointAligner;
import totah.lab.gaia.geometry.Point3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Cheap, target-independent 3-D alignment of corresponding pharmacophore features. */
public final class FeatureTemplateAlignmentEvaluator {
    private static final int MINIMUM_CORRESPONDENCES = 3;
    private final KabschRigidPointAligner aligner = new KabschRigidPointAligner();

    public AlignmentEvidence evaluate(
            String templateId,
            Map<String, Point3D> conformerFeatures,
            Map<String, Point3D> templateFeatures,
            double maximumRmsd) {
        Objects.requireNonNull(templateId, "templateId");
        Objects.requireNonNull(conformerFeatures, "conformerFeatures");
        Objects.requireNonNull(templateFeatures, "templateFeatures");
        if (!Double.isFinite(maximumRmsd) || maximumRmsd < 0) {
            throw new IllegalArgumentException("maximumRmsd must be finite and non-negative");
        }
        List<String> ids = conformerFeatures.keySet().stream()
                .filter(templateFeatures::containsKey).sorted().toList();
        if (ids.size() < MINIMUM_CORRESPONDENCES) {
            return new AlignmentEvidence(templateId, false, false, null, ids,
                    "at least three configured feature correspondences are required");
        }
        List<Point3D> source = new ArrayList<>(), target = new ArrayList<>();
        for (String id : ids) { source.add(conformerFeatures.get(id)); target.add(templateFeatures.get(id)); }
        var transform = aligner.align(source, target);
        double sum = 0;
        for (int i = 0; i < source.size(); i++) sum += transform.apply(source.get(i)).distanceSquared(target.get(i));
        double rmsd = Math.sqrt(sum / source.size());
        return new AlignmentEvidence(templateId, true, rmsd <= maximumRmsd, rmsd, ids,
                "Kabsch feature RMSD evaluated against configured maximum");
    }

    public record AlignmentEvidence(String templateId, boolean evaluated, boolean passed,
                                    Double rmsd, List<String> correspondingFeatureIds, String reason) {
        public AlignmentEvidence { correspondingFeatureIds = List.copyOf(correspondingFeatureIds); }
    }
}
