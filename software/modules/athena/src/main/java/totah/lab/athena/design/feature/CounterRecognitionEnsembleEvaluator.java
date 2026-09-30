package totah.lab.athena.design.feature;

import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.geometry.RigidTransform;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Target-neutral evaluator for a configurable ensemble of observed
 * counter-recognition placements. It retains every compatible atom/feature
 * assignment and never converts the evidence vector into a scalar score.
 */
public final class CounterRecognitionEnsembleEvaluator {
    private final double contactDistance;
    private final InvariantFrameAligner aligner;

    public CounterRecognitionEnsembleEvaluator(double contactDistance) {
        if (!Double.isFinite(contactDistance) || contactDistance <= 0) {
            throw new IllegalArgumentException("contactDistance must be finite and positive");
        }
        this.contactDistance = contactDistance;
        this.aligner = new InvariantFrameAligner();
    }

    public Result evaluate(Candidate candidate, List<Template> templates) {
        Objects.requireNonNull(candidate); Objects.requireNonNull(templates);
        List<TemplateMatch> matches = new ArrayList<>();
        for (Template template : templates) matches.add(match(candidate, template));
        matches.sort(Comparator.comparing((TemplateMatch m) -> !m.aligned())
                .thenComparing(TemplateMatch::alignmentRmsd,
                        Comparator.nullsLast(Double::compareTo))
                .thenComparing(TemplateMatch::templateId));
        return new Result(matches);
    }

    private TemplateMatch match(Candidate candidate, Template template) {
        Map<String, Point3D> source = new LinkedHashMap<>();
        Map<String, Point3D> target = new LinkedHashMap<>();
        candidate.parentToCandidateAtomIds().forEach((parent, child) -> {
            Point3D candidatePoint = candidate.atomPoints().get(child);
            Point3D templatePoint = template.parentAtomPoints().get(parent);
            if (candidatePoint != null && templatePoint != null) {
                source.put(parent, candidatePoint); target.put(parent, templatePoint);
            }
        });
        var alignment = aligner.align(source, target);
        if (!alignment.aligned()) return new TemplateMatch(template.id(), false, null,
                alignment.reason(), List.of(), 0, 0.0, template.observedFamilyId());
        RigidTransform transform = alignment.transform();
        Map<String, Point3D> transformedAtoms = new LinkedHashMap<>();
        candidate.atomPoints().forEach((id, point) -> transformedAtoms.put(id, transform.apply(point)));
        List<LigandFeature> transformedFeatures = candidate.features().stream()
                .map(feature -> new LigandFeature(feature.id(), feature.type(), feature.atomIds(),
                        transform.apply(feature.point()), feature.evidence())).toList();
        List<ResidueMatch> residues = new ArrayList<>(); Set<String> allContactAtoms = new LinkedHashSet<>();
        template.residueAtomPoints().forEach((residue, receptorAtoms) -> {
            Set<String> contactAtoms = new LinkedHashSet<>();
            transformedAtoms.forEach((id, point) -> {
                if (receptorAtoms.stream().anyMatch(atom -> atom.distance(point) <= contactDistance)) {
                    contactAtoms.add(id); allContactAtoms.add(id);
                }
            });
            List<FeatureAssignment> assignments = new ArrayList<>();
            for (LigandFeature feature : transformedFeatures) {
                Set<String> overlap = new LinkedHashSet<>(feature.atomIds()); overlap.retainAll(contactAtoms);
                if (!overlap.isEmpty()) assignments.add(new FeatureAssignment(feature.id(), feature.type(), overlap,
                        compensationClass(feature.type(), overlap, candidate.parentToCandidateAtomIds())));
            }
            residues.add(new ResidueMatch(residue, contactAtoms, assignments));
        });
        long heavy = candidate.atomElements().values().stream().filter(e -> !e.equalsIgnoreCase("H")).count();
        double contactBurialProxy = heavy == 0 ? 0.0 : (double) allContactAtoms.stream()
                .filter(id -> !"H".equalsIgnoreCase(candidate.atomElements().getOrDefault(id, ""))).count() / heavy;
        return new TemplateMatch(template.id(), true, alignment.rmsd(), alignment.reason(), residues,
                allContactAtoms.size(), contactBurialProxy, template.observedFamilyId());
    }

    private static String compensationClass(LigandFeature.Type type, Set<String> atoms,
                                            Map<String, String> parentToCandidate) {
        if (type == LigandFeature.Type.AROMATIC_RING || type == LigandFeature.Type.PI_FEATURE) {
            return "AROMATIC_GROUP";
        }
        if (type == LigandFeature.Type.HYDROPHOBE) return "HYDROPHOBIC_GROUP";
        if (atoms.stream().anyMatch(parentToCandidate::containsValue)) return "SAME_LINEAGE_ATOM";
        return "ALTERNATIVE_ATOM";
    }

    public record Candidate(Map<String, Point3D> atomPoints, Map<String, String> atomElements,
                            List<LigandFeature> features, Map<String, String> parentToCandidateAtomIds) {
        public Candidate { atomPoints=Map.copyOf(atomPoints);atomElements=Map.copyOf(atomElements);
            features=List.copyOf(features);parentToCandidateAtomIds=Map.copyOf(parentToCandidateAtomIds); }
    }
    public record Template(String id, String observedFamilyId, Map<String, Point3D> parentAtomPoints,
                           Map<String, List<Point3D>> residueAtomPoints, Double observedBurial) {
        public Template { parentAtomPoints=Map.copyOf(parentAtomPoints);Map<String,List<Point3D>> copy=new LinkedHashMap<>();
            residueAtomPoints.forEach((k,v)->copy.put(k,List.copyOf(v)));residueAtomPoints=Map.copyOf(copy); }
    }
    public record FeatureAssignment(String featureId, LigandFeature.Type type, Set<String> contactingAtomIds,
                                    String compensationClass) {
        public FeatureAssignment { contactingAtomIds=Set.copyOf(contactingAtomIds); }
    }
    public record ResidueMatch(String residueId, Set<String> contactingAtomIds,
                               List<FeatureAssignment> compatibleAssignments) {
        public ResidueMatch { contactingAtomIds=Set.copyOf(contactingAtomIds);compatibleAssignments=List.copyOf(compatibleAssignments); }
        public boolean supported() { return !contactingAtomIds.isEmpty(); }
    }
    public record TemplateMatch(String templateId, boolean aligned, Double alignmentRmsd, String alignmentEvidence,
                                List<ResidueMatch> residueMatches, int contactingAtomCount,
                                double contactBurialProxy, String observedFamilyId) {
        public TemplateMatch { residueMatches=List.copyOf(residueMatches); }
    }
    public record Result(List<TemplateMatch> matches) {
        public Result { matches=List.copyOf(matches); }
        public List<TemplateMatch> alignedMatches() { return matches.stream().filter(TemplateMatch::aligned).toList(); }
    }
}
