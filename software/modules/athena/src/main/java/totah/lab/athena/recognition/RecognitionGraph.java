package totah.lab.athena.recognition;

import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.surface.differential.DifferentialSurfaceMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable, unweighted graph view over an existing Athena interaction profile. */
public final class RecognitionGraph {
    @FunctionalInterface
    public interface LigandFeatureResolver {
        String featureId(Interaction interaction);
    }

    @FunctionalInterface
    public interface EvidenceQualityResolver {
        EvidenceQuality quality(Interaction interaction, InteractionProfile profile);
    }

    public record Provenance(String proteinId, String ligandId, String poseId,
                             Optional<String> familyId,
                             Optional<String> representativeProvenance) {
        public Provenance {
            if (proteinId == null || proteinId.isBlank()) throw new IllegalArgumentException("proteinId required");
            if (ligandId == null || ligandId.isBlank()) throw new IllegalArgumentException("ligandId required");
            if (poseId == null || poseId.isBlank()) throw new IllegalArgumentException("poseId required");
            familyId = Objects.requireNonNull(familyId, "familyId");
            representativeProvenance = Objects.requireNonNull(representativeProvenance,
                    "representativeProvenance");
        }
    }

    private final Provenance provenance;
    private final InteractionProfile sourceProfile;
    private final List<RecognitionEdge> edges;
    private final Set<RecognitionNode> nodes;

    private RecognitionGraph(Provenance provenance, InteractionProfile sourceProfile,
            List<RecognitionEdge> edges) {
        this.provenance = Objects.requireNonNull(provenance, "provenance");
        this.sourceProfile = Objects.requireNonNull(sourceProfile, "sourceProfile");
        this.edges = List.copyOf(edges);
        LinkedHashSet<RecognitionNode> collected = new LinkedHashSet<>();
        this.edges.forEach(edge -> {
            collected.add(edge.ligandFeature());
            collected.add(edge.environment());
        });
        this.nodes = Set.copyOf(collected);
    }

    public static RecognitionGraph from(InteractionProfile profile,
            Optional<DifferentialSurfaceMap> surface,
            Provenance provenance,
            LigandFeatureResolver featureResolver) {
        return from(profile, surface, provenance, featureResolver,
                (interaction, source) -> source.anyPerceptionDegraded()
                        ? EvidenceQuality.DEGRADED : EvidenceQuality.ADEQUATE);
    }

    public static RecognitionGraph from(InteractionProfile profile,
            Optional<DifferentialSurfaceMap> surface,
            Provenance provenance,
            LigandFeatureResolver featureResolver,
            EvidenceQualityResolver qualityResolver) {
        Objects.requireNonNull(profile, "profile");
        surface = Objects.requireNonNull(surface, "surface");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(featureResolver, "featureResolver");
        Objects.requireNonNull(qualityResolver, "qualityResolver");
        List<RecognitionEdge> edges = new ArrayList<>();
        for (int index = 0; index < profile.interactions().size(); index++) {
            Interaction interaction = profile.interactions().get(index);
            String feature = featureResolver.featureId(interaction);
            if (feature == null || feature.isBlank()) {
                throw new IllegalArgumentException("feature resolver returned blank id");
            }
            EvidenceQuality quality = Objects.requireNonNull(
                    qualityResolver.quality(interaction, profile), "edge quality");
            boolean sam = profile.cofactorResidues().contains(interaction.residue());
            edges.add(new RecognitionEdge(provenance.poseId() + ":" + index,
                    RecognitionNode.ligand(feature),
                    RecognitionNode.environment(interaction.residue(), sam), interaction,
                    surface.flatMap(map -> map.score(interaction.residue())), quality,
                    InteractionRole.UNRESOLVED, provenance.poseId(), provenance.familyId(),
                    provenance.representativeProvenance()));
        }
        return new RecognitionGraph(provenance, profile, edges);
    }

    /** Test/campaign assembly without recomputing any scientific evidence. */
    public static RecognitionGraph of(Provenance provenance, InteractionProfile sourceProfile,
            List<RecognitionEdge> edges) {
        return new RecognitionGraph(provenance, sourceProfile,
                List.copyOf(Objects.requireNonNull(edges, "edges")));
    }

    public Provenance provenance() { return provenance; }
    public InteractionProfile sourceProfile() { return sourceProfile; }
    public List<RecognitionEdge> edges() { return edges; }
    public Set<RecognitionNode> nodes() { return nodes; }

    public EvidenceQuality quality() {
        if (edges.stream().anyMatch(edge -> edge.quality() == EvidenceQuality.UNAVAILABLE)) {
            return EvidenceQuality.UNAVAILABLE;
        }
        if (sourceProfile.anyPerceptionDegraded()
                || edges.stream().anyMatch(edge -> edge.quality() == EvidenceQuality.DEGRADED)) {
            return EvidenceQuality.DEGRADED;
        }
        return EvidenceQuality.ADEQUATE;
    }

    /** Stable evidence rendering; deliberately contains no score. */
    public List<String> canonicalLines() {
        return edges.stream().sorted(Comparator.comparing(RecognitionEdge::id)).map(edge ->
                String.join("\t", edge.id(), edge.ligandFeature().id(),
                        edge.environmentResidue().toString(), edge.interactionType().name(),
                        Double.toString(edge.interaction().distanceAngstroms()),
                        edge.quality().name(), edge.role().name(), edge.poseId(),
                        edge.familyId().orElse(""))).toList();
    }
}
