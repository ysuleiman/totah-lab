package totah.lab.athena.recognition;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.interaction.PerceptionSummary;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.athena.surface.differential.DifferentialResidueScore;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DifferentialRecognitionComparatorTest {
    private static final InteractionThresholds THRESHOLDS = InteractionThresholds.athenaDefaults();
    private static final ResidueId A10 = new ResidueId("A", 10, null);
    private static final ResidueId A11 = new ResidueId("A", 11, null);
    private static final ResidueId B20 = new ResidueId("B", 20, null);
    private static final ResidueId B30 = new ResidueId("B", 30, null);
    private final DifferentialRecognitionComparator comparator = new DifferentialRecognitionComparator();

    @Test
    void exactMappedEdgeIsPreservedAndMetricsAreTransparent() {
        RecognitionGraph a = graph("A", edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        RecognitionGraph b = graph("B", edge("b", "ring", B20, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));

        DifferentialRecognition result = compare(a, b, RecognitionComparisonPolicy.evidenceOnly());

        assertThat(result.edges()).singleElement().extracting(DifferentialRecognition.EdgeComparison::state)
                .isEqualTo(CrossParalogEdgeState.PRESERVED);
        assertThat(result.metrics().typedIntersection()).isEqualTo(1);
        assertThat(result.metrics().typedUnion()).isEqualTo(1);
        assertThat(result.metrics().typedJaccard()).isEqualTo(1.0);
    }

    @Test
    void definingAbsentEdgeIsLostButUnresolvedAbsentEdgeIsUnobserved() {
        RecognitionEdge edge = edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED);
        RecognitionGraph source = graph("A", edge);
        RecognitionGraph empty = graph("B");
        RecognitionComparisonPolicy defining = new RecognitionComparisonPolicy(Set.of(),
                Map.of(edge.key(), InteractionRole.DEFINING));

        assertThat(compare(source, empty, defining).edges().getFirst().state())
                .isEqualTo(CrossParalogEdgeState.LOST);
        assertThat(compare(source, empty, RecognitionComparisonPolicy.evidenceOnly())
                .edges().getFirst().state()).isEqualTo(CrossParalogEdgeState.UNOBSERVED);
    }

    @Test
    void differentTypeAtMappedEnvironmentIsSubstituted() {
        RecognitionGraph a = graph("A", edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        RecognitionGraph b = graph("B", edge("b", "ring", B20, InteractionType.PI_STACK_T_SHAPED,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        assertThat(compare(a, b, RecognitionComparisonPolicy.evidenceOnly()).edges().getFirst().state())
                .isEqualTo(CrossParalogEdgeState.SUBSTITUTED);
    }

    @Test
    void sameFeatureAtDifferentEnvironmentIsRerouted() {
        RecognitionGraph a = graph("A", edge("a", "amine", A10, InteractionType.SALT_BRIDGE,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        RecognitionGraph b = graph("B", edge("b", "amine", B30, InteractionType.HYDROGEN_BOND,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        assertThat(compare(a, b, RecognitionComparisonPolicy.evidenceOnly()).edges().getFirst().state())
                .isEqualTo(CrossParalogEdgeState.REROUTED);
    }

    @Test
    void explicitConstraintViolationIsIncompatible() {
        RecognitionEdge edge = edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED);
        RecognitionComparisonPolicy policy = new RecognitionComparisonPolicy(
                Set.of(new RecognitionEdge.Key("ring", B20, InteractionType.PI_CATION)), Map.of());
        assertThat(compare(graph("A", edge), graph("B"), policy).edges().getFirst().state())
                .isEqualTo(CrossParalogEdgeState.INCOMPATIBLE);
    }

    @Test
    void explicitIncompatibilityTakesPrecedenceOverObservedExactEdge() {
        RecognitionEdge edge = edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED);
        RecognitionComparisonPolicy policy = new RecognitionComparisonPolicy(
                Set.of(new RecognitionEdge.Key("ring", B20, InteractionType.PI_CATION)), Map.of());
        var result = compare(graph("A", edge), graph("B", edge("b", "ring", B20,
                InteractionType.PI_CATION, EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED)), policy);
        assertThat(result.edges().getFirst().state()).isEqualTo(CrossParalogEdgeState.INCOMPATIBLE);
    }

    @Test
    void degradedSourceOrTargetPropagatesUnavailable() {
        RecognitionGraph degradedSource = graph("A", edge("a", "ring", A10,
                InteractionType.PI_CATION, EvidenceQuality.DEGRADED, InteractionRole.UNRESOLVED));
        RecognitionGraph target = graph("B", edge("b", "ring", B20, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        assertThat(compare(degradedSource, target, RecognitionComparisonPolicy.evidenceOnly())
                .edges().getFirst().state()).isEqualTo(CrossParalogEdgeState.UNAVAILABLE);
        assertThat(compare(degradedSource, target, RecognitionComparisonPolicy.evidenceOnly())
                .metrics().typedUnion()).isEqualTo(1);

        RecognitionGraph degradedTarget = graph("B", edge("b", "other", B30,
                InteractionType.HYDROPHOBIC_CONTACT, EvidenceQuality.DEGRADED,
                InteractionRole.UNRESOLVED));
        assertThat(compare(graph("A", edge("a2", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED)), degradedTarget,
                RecognitionComparisonPolicy.evidenceOnly()).edges().getFirst().state())
                .isEqualTo(CrossParalogEdgeState.UNAVAILABLE);
    }

    @Test
    void missingResidueCorrespondenceIsUnavailable() {
        RecognitionGraph source = graph("A", edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        DifferentialRecognition result = comparator.compare(source, graph("B"),
                new ExplicitResidueCorrespondence(Map.of()), RecognitionComparisonPolicy.evidenceOnly());
        assertThat(result.edges().getFirst().state()).isEqualTo(CrossParalogEdgeState.UNAVAILABLE);
    }

    @Test
    void surfaceAnnotationRemainsSeparateAndMarksChangedEdgeIntersection() {
        RecognitionEdge sourceEdge = edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED,
                Optional.of(new DifferentialResidueScore(A10, Optional.of(B20), .4, .8, .6, .5, .5)));
        DifferentialRecognition result = compare(graph("A", sourceEdge), graph("B"),
                RecognitionComparisonPolicy.evidenceOnly());
        assertThat(result.edges().getFirst().differentialEnvironment()).isTrue();
        assertThat(result.metrics().changedDifferentialEnvironmentCount()).isEqualTo(1);
        assertThat(sourceEdge.differentialSurface().orElseThrow().rus()).isEqualTo(.5);
    }

    @Test
    void comparisonIsDirectionalWhileTypedJaccardIsSymmetricForMappedGraphs() {
        RecognitionGraph a = graph("A", edge("a", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        RecognitionGraph b = graph("B", edge("b", "ring", B20, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED), edge("b2", "tail", B30,
                InteractionType.HYDROPHOBIC_CONTACT, EvidenceQuality.ADEQUATE,
                InteractionRole.UNRESOLVED));
        DifferentialRecognition forward = compare(a, b, RecognitionComparisonPolicy.evidenceOnly());
        DifferentialRecognition reverse = comparator.compare(b, a,
                new ExplicitResidueCorrespondence(Map.of(B20, A10, B30, A11)),
                RecognitionComparisonPolicy.evidenceOnly());
        assertThat(forward.metrics().typedJaccard()).isEqualTo(reverse.metrics().typedJaccard());
        assertThat(forward.edges()).hasSize(1);
        assertThat(reverse.edges()).hasSize(2);
    }

    @Test
    void graphCreationDoesNotMutateProfileAndRenderingIsDeterministic() {
        Interaction interaction = interaction(A10, InteractionType.HYDROPHOBIC_CONTACT);
        InteractionProfile profile = profile(List.of(interaction), false);
        List<Interaction> before = profile.interactions();
        RecognitionGraph.Provenance provenance = provenance("A");
        RecognitionGraph first = RecognitionGraph.from(profile, Optional.empty(), provenance,
                ignored -> "carbon-face");
        RecognitionGraph second = RecognitionGraph.from(profile, Optional.empty(), provenance,
                ignored -> "carbon-face");
        assertThat(profile.interactions()).containsExactlyElementsOf(before);
        assertThat(first.canonicalLines()).isEqualTo(second.canonicalLines());
        assertThatThrownBy(() -> first.edges().add(first.edges().getFirst()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void samNodeExistsOnlyForExistingCofactorInteraction() {
        Interaction interaction = interaction(A10, InteractionType.HYDROGEN_BOND);
        InteractionProfile profile = new InteractionProfile(List.of(interaction), List.of(interaction),
                Set.of(A10), THRESHOLDS, List.of(summary(false)));
        RecognitionGraph graph = RecognitionGraph.from(profile, Optional.empty(), provenance("A"),
                ignored -> "donor");
        assertThat(graph.edges().getFirst().environment().kind())
                .isEqualTo(RecognitionNode.Kind.SAM_FEATURE);
    }

    @Test
    void substitutedConstraintRequiresSubstituteAndUnavailableStatesAgree() {
        assertThatThrownBy(() -> new RecognitionConstraint("c", RecognitionConstraint.Type.CATION_PI_GEOMETRY,
                "ring", Optional.of(A10), List.of(THRESHOLDS.provenance()), List.of(),
                RecognitionConstraint.Evaluation.SUBSTITUTED, Optional.of(InteractionType.PI_CATION),
                Optional.empty(), EvidenceQuality.ADEQUATE, THRESHOLDS.provenance()))
                .isInstanceOf(IllegalArgumentException.class);
        RecognitionConstraint unavailable = new RecognitionConstraint("u",
                RecognitionConstraint.Type.AROMATIC_FACE_AVAILABILITY, "ring", Optional.of(A10),
                List.of(), List.of(), RecognitionConstraint.Evaluation.UNAVAILABLE, Optional.empty(),
                Optional.empty(), EvidenceQuality.UNAVAILABLE, "incomplete topology");
        assertThat(unavailable.evaluation()).isEqualTo(RecognitionConstraint.Evaluation.UNAVAILABLE);
    }

    @Test
    void exposesBackgroundRerouteGroupsAndOnlyCanonicalDefiningFraction() {
        RecognitionEdge defining = edge("d", "ring", A10, InteractionType.PI_CATION,
                EvidenceQuality.ADEQUATE, InteractionRole.DEFINING);
        RecognitionEdge background = edge("s", "tail", A11, InteractionType.HYDROPHOBIC_CONTACT,
                EvidenceQuality.ADEQUATE, InteractionRole.SUPPORTING,
                Optional.of(new DifferentialResidueScore(A11, Optional.of(B30), .2, .8, 0, .1, .9)));
        RecognitionGraph source = graph("A", defining, background);
        RecognitionGraph target = graph("B",
                edge("tb", "tail", B30, InteractionType.HYDROPHOBIC_CONTACT,
                        EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED));
        DifferentialRecognition result = comparator.compare(source, target,
                new ExplicitResidueCorrespondence(Map.of(A10, B20, A11, B30)),
                RecognitionComparisonPolicy.evidenceOnly());
        assertThat(result.definingEdgePreservationFraction()).hasValue(0.0);
        assertThat(result.preservedBackgroundInteractions()).hasSize(1);
        assertThat(result.reroutedSubgraphs()).isEmpty();
    }

    private DifferentialRecognition compare(RecognitionGraph a, RecognitionGraph b,
            RecognitionComparisonPolicy policy) {
        return comparator.compare(a, b, new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy);
    }

    private static RecognitionGraph graph(String id, RecognitionEdge... edges) {
        return RecognitionGraph.of(provenance(id), profile(List.of(), false), List.of(edges));
    }

    private static RecognitionGraph.Provenance provenance(String id) {
        return new RecognitionGraph.Provenance(id, "ligand", id + "-pose",
                Optional.of(id + "-family"), Optional.of("fixture"));
    }

    private static RecognitionEdge edge(String id, String feature, ResidueId residue,
            InteractionType type, EvidenceQuality quality, InteractionRole role) {
        return edge(id, feature, residue, type, quality, role, Optional.empty());
    }

    private static RecognitionEdge edge(String id, String feature, ResidueId residue,
            InteractionType type, EvidenceQuality quality, InteractionRole role,
            Optional<DifferentialResidueScore> surface) {
        return new RecognitionEdge(id, RecognitionNode.ligand(feature),
                RecognitionNode.environment(residue, false), interaction(residue, type), surface,
                quality, role, "pose", Optional.of("family"), Optional.of("fixture"));
    }

    private static Interaction interaction(ResidueId residue, InteractionType type) {
        return new Interaction(type, residue, List.of(atom(1, "P", 0)),
                List.of(atom(2, "L", 3)), 3.0, null, null, null, null, THRESHOLDS);
    }

    private static Atom atom(int serial, String name, double x) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(x, 0, 0))
                .element(Element.C).occupancy(1).bFactor(0).charge(0).build();
    }

    private static InteractionProfile profile(List<Interaction> interactions, boolean degraded) {
        return new InteractionProfile(interactions, interactions, Set.of(), THRESHOLDS,
                List.of(summary(degraded)));
    }

    private static PerceptionSummary summary(boolean degraded) {
        return new PerceptionSummary(PerceptionSummary.RECEPTOR,
                degraded ? PerceptionProvenance.AD4_FALLBACK : PerceptionProvenance.BOND_GRAPH,
                1, 0, 0, 0, 0);
    }
}
