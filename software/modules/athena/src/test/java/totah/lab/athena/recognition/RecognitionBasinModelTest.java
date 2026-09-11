package totah.lab.athena.recognition;

import org.junit.jupiter.api.Test;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.interaction.PerceptionSummary;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecognitionBasinModelTest {
    private static final InteractionThresholds T = InteractionThresholds.athenaDefaults();
    private static final ResidueId A10 = new ResidueId("A", 10, null);
    private static final ResidueId B20 = new ResidueId("B", 20, null);

    @Test void stableFeatureIdentityUsesCanonicalOrbitsNotSourceAtomNames() {
        LigandFeature first = feature("arbitrary-A", Set.of("C1", "C2"));
        LigandFeature renamed = feature("arbitrary-B", Set.of("X7", "X9"));
        StableLigandFeatureMapper mapper = new StableLigandFeatureMapper();
        var a = mapper.map("lig", List.of(first), Map.of("C1", "orbit-1", "C2", "orbit-2"),
                "canonical graph ranks v1", "backend v1", EvidenceQuality.ADEQUATE, Map.of(), List.of());
        var b = mapper.map("lig", List.of(renamed), Map.of("X7", "orbit-2", "X9", "orbit-1"),
                "canonical graph ranks v1", "backend v1", EvidenceQuality.ADEQUATE, Map.of(), List.of());
        assertThat(a.features().getFirst().stableId()).isEqualTo(b.features().getFirst().stableId());
        assertThat(a.features().getFirst().canonicalAtomOrbits()).containsExactly("orbit-1", "orbit-2");
    }

    @Test void incompleteAtomIdentityIsRejectedRatherThanGuessed() {
        assertThatThrownBy(() -> new StableLigandFeatureMapper().map("DCMB",
                List.of(feature("ring", Set.of("C1", "C2"))), Map.of("C1", "orbit-1"),
                "incomplete topology", "existing perception", EvidenceQuality.DEGRADED,
                Map.of(), List.of("pi topology unavailable"))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("C2");
    }

    @Test void oneToOneAssignmentDoesNotReuseTargetEdge() {
        RecognitionGraph source = graph("s", edge("s1", "f", A10, InteractionType.PI_CATION, 3),
                edge("s2", "f", A10, InteractionType.PI_CATION, 3.2));
        RecognitionGraph target = graph("t", edge("t1", "g", B20, InteractionType.PI_CATION, 3));
        var result = new RecognitionEdgeAssigner().assign(source, target,
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 10));
        assertThat(result.matches()).hasSize(1);
        assertThat(result.unmatchedSource()).hasSize(1);
    }

    @Test void equallyPlausibleTargetsRemainExplicitlyAmbiguous() {
        RecognitionGraph source = graph("s", edge("s1", "f", A10, InteractionType.PI_CATION, 3));
        RecognitionGraph target = graph("t", edge("t1", "g", B20, InteractionType.PI_CATION, 3),
                edge("t2", "g", B20, InteractionType.PI_CATION, 3));
        var result = new RecognitionEdgeAssigner().assign(source, target,
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 1));
        assertThat(result.matches()).isEmpty();
        assertThat(result.ambiguities()).singleElement().extracting(RecognitionEdgeAssignment.Ambiguity::status)
                .isEqualTo("AMBIGUOUS_ASSIGNMENT");
    }

    @Test void targetCompetitionUsesBestGeometryRatherThanSourceIterationOrder() {
        RecognitionGraph source = graph("s", edge("a-first-but-worse", "f", A10,
                InteractionType.PI_CATION, 4), edge("z-second-but-exact", "f", A10,
                InteractionType.PI_CATION, 3));
        RecognitionGraph target = graph("t", edge("target", "g", B20, InteractionType.PI_CATION, 3));
        var result = new RecognitionEdgeAssigner().assign(source, target,
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 2));
        assertThat(result.matches()).singleElement().satisfies(match ->
                assertThat(match.source().id()).isEqualTo("z-second-but-exact"));
    }

    @Test void targetCompetitionLoserIsReconsideredForSecondChoice() {
        RecognitionGraph source = graph("s", edge("s1", "f", A10, InteractionType.PI_CATION, 3.1),
                edge("s2", "f", A10, InteractionType.PI_CATION, 3.0));
        RecognitionGraph target = graph("t", edge("t1", "g", B20, InteractionType.PI_CATION, 3.0),
                edge("t2", "g", B20, InteractionType.PI_CATION, 3.3));
        var result = new RecognitionEdgeAssigner().assign(source, target,
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 2));
        assertThat(result.matches()).hasSize(2);
        assertThat(result.matches()).extracting(match -> match.target().id())
                .containsExactlyInAnyOrder("t1", "t2");
    }

    @Test void equalSourceCompetitionRetainsBothSourcesInAuditEvidence() {
        RecognitionGraph source = graph("s", edge("s1", "f", A10, InteractionType.PI_CATION, 3),
                edge("s2", "f", A10, InteractionType.PI_CATION, 3));
        RecognitionGraph target = graph("t", edge("t1", "g", B20, InteractionType.PI_CATION, 3));
        var result = new RecognitionEdgeAssigner().assign(source, target,
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 1));
        assertThat(result.matches()).isEmpty();
        assertThat(result.ambiguities()).hasSize(2);
        assertThat(result.ambiguities()).extracting(RecognitionEdgeAssignment.Ambiguity::status)
                .allSatisfy(status -> assertThat(status).contains("s1", "s2"));
        assertThat(result.matches()).extracting(match -> match.source().id())
                .doesNotContain("s1", "s2");
    }

    @Test void topologyComponentsRemainSeparateAndScalarNeedsExplicitWeights() {
        var assignment = new RecognitionEdgeAssigner().assign(
                graph("s", edge("s1", "f", A10, InteractionType.PI_CATION, 3)),
                graph("t", edge("t1", "g", B20, InteractionType.HYDROGEN_BOND, 3.1)),
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 1));
        RecognitionTopologyDistance distance = RecognitionTopologyDistance.from(assignment);
        assertThat(distance.substitutedEdges()).isEqualTo(1);
        assertThat(distance.reroutedEdges()).isZero();
        assertThat(distance.typedJaccard()).isZero();
        assertThat(distance.scalarDistance(new RecognitionTopologyDistance.ScalarPolicy(1, 2, 3, 4, 5, "test")))
                .isEqualTo(3);
    }

    @Test void noMatchedEdgesReportsGeometryUnavailableRatherThanZero() {
        var assignment = new RecognitionEdgeAssigner().assign(
                graph("s", edge("s1", "f", A10, InteractionType.PI_CATION, 3)), graph("t"),
                new ExplicitResidueCorrespondence(Map.of(A10, B20)), policy(Map.of("f", "g"), 1));
        var geometry = RecognitionTopologyDistance.from(assignment).matchedGeometryDeviation();
        assertThat(geometry.distanceAvailable()).isFalse();
        assertThat(geometry.meanDistanceAngstroms()).isNaN();
    }

    @Test void basinMembershipRequiresBothGeometryAndTopologyAndRetainsSingletons() {
        var a = observation("p1", "seed1", "fam1");
        var b = observation("p2", "seed2", "fam2");
        RecognitionTopologyDistance same = topology(1.0);
        RecognitionTopologyDistance different = topology(0.0);
        RecognitionBasinPolicy policy = basinPolicy(2, .2, 2, 2);
        var geomFails = new RecognitionBasinBuilder().build(List.of(a, b), (x, y) -> OptionalDouble.of(3),
                (x, y) -> Optional.of(same), policy);
        assertThat(geomFails.nonRecurrentBasins()).hasSize(2);
        var topoFails = new RecognitionBasinBuilder().build(List.of(a, b), (x, y) -> OptionalDouble.of(1),
                (x, y) -> Optional.of(different), policy);
        assertThat(topoFails.nonRecurrentBasins()).hasSize(2);
        var bothPass = new RecognitionBasinBuilder().build(List.of(a, b), (x, y) -> OptionalDouble.of(1),
                (x, y) -> Optional.of(same), policy);
        assertThat(bothPass.recurrentBasins()).singleElement().satisfies(basin -> {
            assertThat(basin.poseIds()).containsExactly("p1", "p2");
            assertThat(basin.geometryDispersion().maximum()).isEqualTo(1);
        });
    }

    @Test void missingPairEvidenceIsReportedAndCannotCreateRecurrence() {
        var result = new RecognitionBasinBuilder().build(List.of(observation("p1", "s1", "f1"),
                        observation("p2", "s2", "f2")), (x, y) -> OptionalDouble.empty(),
                (x, y) -> Optional.of(topology(1)), basinPolicy(2, .2, 2, 2));
        assertThat(result.evidenceInsufficiencies()).hasSize(1);
        assertThat(result.ambiguousMemberships()).isEmpty();
        assertThat(result.incompletePairEvidenceStates()).hasSize(2);
        assertThat(result.recurrentBasins()).isEmpty();
    }

    @Test void directionalDistancesRequireAnExplicitSymmetryRule() {
        var observations = List.of(observation("p1", "s1", "f1"), observation("p2", "s2", "f2"));
        RecognitionBasinPolicy strict = new RecognitionBasinPolicy(2, .2, 2, 2,
                new RecognitionTopologyDistance.ScalarPolicy(1, 0, 0, 0, 0, "test"),
                RecognitionBasinPolicy.PairSymmetryPolicy.REQUIRE_EQUAL, "strict symmetry");
        assertThatThrownBy(() -> new RecognitionBasinBuilder().build(observations,
                (a, b) -> OptionalDouble.of(a.poseId().equals("p1") ? 1 : 1.5),
                (a, b) -> Optional.of(topology(1)), strict))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("directional");
    }

    @Test void definingRequiresPersistentEdgeAndAdequateAlternativeEvidence() {
        RecognitionBasin basin = new RecognitionBasinBuilder().build(
                List.of(observation("p1", "s1", "f1"), observation("p2", "s2", "f2")),
                (x, y) -> OptionalDouble.of(0), (x, y) -> Optional.of(topology(1)),
                basinPolicy(1, .2, 2, 2)).recurrentBasins().getFirst();
        RecognitionEdge.Key key = basin.persistentEdges().iterator().next();
        InteractionRoleClassifier classifier = new InteractionRoleClassifier();
        assertThat(classifier.classify(basin, new InteractionRoleClassifier.MaterialContributionEvidence(
                Set.of(key), true, "alternative basin comparison")).roles().get(key))
                .isEqualTo(InteractionRole.DEFINING);
        assertThat(classifier.classify(basin, new InteractionRoleClassifier.MaterialContributionEvidence(
                Set.of(key), false, "inadequate alternatives")).roles().get(key))
                .isEqualTo(InteractionRole.UNRESOLVED);
    }

    @Test void semanticAdmissionComponentsControlMembershipAndRetainReasons() {
        var observations=List.of(observation("p1","s1",""),observation("p2","s2",""));
        var recurrence=new RecognitionRecurrencePolicy(2,2,2,"frozen fixture");
        for(int rejectedComponent=0;rejectedComponent<3;rejectedComponent++){
            int component=rejectedComponent;
            var result=new RecognitionBasinBuilder().buildWithAdmissions(observations,(x,y)->OptionalDouble.of(1),
                    (x,y)->Optional.of(topology(1)),basinPolicy(2,.4,1,1),(a,b,g,t,p)->{
                        boolean reroute=component==0,geometry=component!=1,ambiguity=component==2;
                        return new RecognitionPairAdmission(true,true,reroute,geometry,ambiguity,
                                EvidenceQuality.ADEQUATE,false,List.of("fixture rejection"),"fixture");},recurrence);
            assertThat(result.basins()).hasSize(2);assertThat(result.pairAdmissions()).singleElement()
                    .satisfies(p->assertThat(p.admission().reasons()).contains("fixture rejection"));
        }
        var admitted=new RecognitionBasinBuilder().buildWithAdmissions(observations,(x,y)->OptionalDouble.of(1),
                (x,y)->Optional.of(topology(1)),basinPolicy(2,.4,1,1),(a,b,g,t,p)->new RecognitionPairAdmission(
                        true,true,false,true,false,EvidenceQuality.ADEQUATE,true,List.of("all gates pass"),"fixture"),recurrence);
        assertThat(admitted.basins()).singleElement().satisfies(b->assertThat(b.recurrent()).isTrue());
    }

    @Test void recurrenceUsesMembersSeedsAndRunsButNotFamilies() {
        RecognitionRecurrencePolicy policy=new RecognitionRecurrencePolicy(2,2,2,"frozen");
        RecognitionBasin twoIndependent=basinWith(List.of(observation("p1","s1",""),observation("p2","s2","")));
        assertThat(policy.recurrent(twoIndependent)).isTrue();
        assertThat(policy.recurrent(basinWith(List.of(observation("p1","s1",""),observation("p2","s1",""))))).isFalse();
        var oneRun=List.of(observationWithRun("p1","s1","run"),observationWithRun("p2","s2","run"));
        assertThat(policy.recurrent(basinWith(oneRun))).isFalse();
        assertThat(twoIndependent.families()).isEmpty();
    }

    @Test void fixedFrameGeometryIsDeterministicOrderInvariantAndUnavailableOnMappingFailure() {
        var a=observation("p1","s1","");var b=observation("p2","s2","");
        Map<String,FixedFrameHeavyAtomGeometryDistance.PointAngstrom> reordered=new java.util.LinkedHashMap<>();
        reordered.put("b",point(2));reordered.put("a",point(1));
        var distance=new FixedFrameHeavyAtomGeometryDistance(Map.of(
                "p1",new FixedFrameHeavyAtomGeometryDistance.PoseGeometry(Map.of("a",point(0),"b",point(1)),"h1","fixture"),
                "p2",new FixedFrameHeavyAtomGeometryDistance.PoseGeometry(reordered,"h2","fixture")));
        assertThat(distance.distanceAngstroms(a,a).orElseThrow()).isZero();
        assertThat(distance.distanceAngstroms(a,b).orElseThrow()).isEqualTo(1);
        assertThat(distance.distanceAngstroms(a,b)).isEqualTo(distance.distanceAngstroms(a,b));
        var missing=new FixedFrameHeavyAtomGeometryDistance(Map.of("p1",new FixedFrameHeavyAtomGeometryDistance.PoseGeometry(Map.of("a",point(0)),"h","fixture")));
        assertThat(missing.distanceAngstroms(a,b)).isEmpty();
    }

    private static FixedFrameHeavyAtomGeometryDistance.PointAngstrom point(double x){return new FixedFrameHeavyAtomGeometryDistance.PointAngstrom(x,0,0);}
    private static RecognitionStateObservation observationWithRun(String pose,String seed,String run){
        var base=observation(pose,seed,"");return new RecognitionStateObservation(pose,base.graph(),seed,run,Optional.empty(),base.ligandFeatureMapProvenance(),base.quality(),base.constraints());
    }
    private static RecognitionBasin basinWith(List<RecognitionStateObservation> observations){
        return new RecognitionBasinBuilder().build(observations,(x,y)->OptionalDouble.of(0),(x,y)->Optional.of(topology(1)),basinPolicy(1,.4,1,1))
                .nonRecurrentBasins().stream().findFirst().orElseGet(()->new RecognitionBasinBuilder().build(observations,(x,y)->OptionalDouble.of(0),(x,y)->Optional.of(topology(1)),basinPolicy(1,.4,1,1)).recurrentBasins().getFirst());
    }

    private static LigandFeature feature(String id, Set<String> atoms) {
        return new LigandFeature(id, LigandFeature.Type.AROMATIC_RING, atoms,
                new Point3D(0, 0, 0), Map.of());
    }
    private static RecognitionEdgeAssignmentPolicy policy(Map<String, String> features, double tolerance) {
        EnumMap<InteractionType, RecognitionEdgeAssignmentPolicy.GeometryTolerance> tolerances = new EnumMap<>(InteractionType.class);
        for (InteractionType type : InteractionType.values()) tolerances.put(type,
                new RecognitionEdgeAssignmentPolicy.GeometryTolerance(tolerance, 180, 180));
        return new RecognitionEdgeAssignmentPolicy(features, tolerances, "fixture policy");
    }
    private static RecognitionBasinPolicy basinPolicy(double geom, double topo, int seeds, int families) {
        return new RecognitionBasinPolicy(geom, topo, seeds, families,
                new RecognitionTopologyDistance.ScalarPolicy(1, 0, 0, 0, 0, "typed Jaccard only"),
                RecognitionBasinPolicy.PairSymmetryPolicy.MAXIMUM, "fixture");
    }
    private static RecognitionTopologyDistance topology(double jaccard) {
        return new RecognitionTopologyDistance(1, 0, 0, 0, 0, 0, 0, 0, jaccard,
                new RecognitionTopologyDistance.GeometryDeviationSummary(0, 0, 0, 0, 0, 0), 0, 0);
    }
    private static RecognitionStateObservation observation(String pose, String seed, String family) {
        RecognitionGraph graph = graph(pose, edge(pose + "-e", "f", A10,
                InteractionType.HYDROPHOBIC_CONTACT, 3));
        return new RecognitionStateObservation(pose, graph, seed, "run-" + seed, family.isBlank()?Optional.empty():Optional.of(family),
                "stable-map-v1", EvidenceQuality.ADEQUATE, List.of());
    }
    private static RecognitionGraph graph(String pose, RecognitionEdge... edges) {
        var provenance = new RecognitionGraph.Provenance("protein", "ligand", pose,
                Optional.of("family"), Optional.of("fixture"));
        return RecognitionGraph.of(provenance, profile(), List.of(edges));
    }
    private static RecognitionEdge edge(String id, String feature, ResidueId residue, InteractionType type, double distance) {
        return new RecognitionEdge(id, RecognitionNode.ligand(feature), RecognitionNode.environment(residue, false),
                interaction(residue, type, distance), Optional.empty(), EvidenceQuality.ADEQUATE,
                InteractionRole.UNRESOLVED, id, Optional.of("family"), Optional.of("fixture"));
    }
    private static Interaction interaction(ResidueId residue, InteractionType type, double distance) {
        return new Interaction(type, residue, List.of(atom(1, "P")), List.of(atom(2, "L")),
                distance, null, null, null, null, T);
    }
    private static Atom atom(int serial, String name) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(serial, 0, 0))
                .element(Element.C).occupancy(1).bFactor(0).charge(0).build();
    }
    private static InteractionProfile profile() {
        return new InteractionProfile(List.of(), List.of(), Set.of(), T,
                List.of(new PerceptionSummary(PerceptionSummary.RECEPTOR,
                        PerceptionProvenance.BOND_GRAPH, 1, 0, 0, 0, 0)));
    }
}
