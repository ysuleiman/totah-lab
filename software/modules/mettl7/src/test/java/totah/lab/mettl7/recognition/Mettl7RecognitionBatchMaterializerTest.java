package totah.lab.mettl7.recognition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.interaction.PerceptionSummary;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.athena.recognition.EvidenceQuality;
import totah.lab.athena.recognition.InteractionRole;
import totah.lab.athena.recognition.RecognitionEdge;
import totah.lab.athena.recognition.RecognitionGraph;
import totah.lab.athena.recognition.RecognitionNode;
import totah.lab.athena.recognition.RecognitionStateObservation;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class Mettl7RecognitionBatchMaterializerTest {
    @TempDir Path temporary;

    @Test
    void accountsForEveryBoundedPoseAndAdmitsOnlyCanonicalMaterializations() throws Exception {
        Path root = Path.of("../../..").toAbsolutePath().normalize();
        Path output = System.getProperty("mettl7.materialization.output") == null
                ? temporary : Path.of(System.getProperty("mettl7.materialization.output"));
        var detailed = Mettl7RecognitionBatchMaterializer.runWithEvidence(root, output);
        var summary=detailed.summary();

        assertThat(summary.outcomes()).hasSize(168);
        assertThat(summary.outcomes()).extracting(Mettl7RecognitionBatchMaterializer.Outcome::poseId)
                .doesNotHaveDuplicates();
        Map<String, Long> arms = summary.outcomes().stream().collect(Collectors.groupingBy(
                Mettl7RecognitionBatchMaterializer.Outcome::arm, Collectors.counting()));
        assertThat(arms).containsEntry("NETARSUDIL_B", 60L)
                .containsEntry("NETARSUDIL_A", 1L)
                .containsEntry("DCMB_A_R", 27L).containsEntry("DCMB_A_S", 27L)
                .containsEntry("DCMB_B_R", 27L).containsEntry("DCMB_B_S", 26L);
        assertThat(summary.outcomes()).allSatisfy(outcome -> {
            assertThat(outcome.status()).isIn("ADMITTED_ADEQUATE", "ADMITTED_DEGRADED",
                    "PENDING_EXTERNAL_EVIDENCE", "UNMAPPABLE", "REJECTED");
            if (outcome.status().startsWith("ADMITTED_")) {
                assertThat(outcome.reason()).isEqualTo("ADMITTED");
                assertThat(outcome.mappedAtoms()).isPositive().isLessThanOrEqualTo(outcome.sdfAtoms());
                assertThat(outcome.featureReceipt()).matches("[0-9a-f]{64}");
                assertThat(outcome.recognitionObservation()).isEqualTo(outcome.poseId());
            }
            if (outcome.status().equals("PENDING_EXTERNAL_EVIDENCE")) {
                assertThat(outcome.reason()).startsWith("FEATURE_ASSIGNMENT_UNAVAILABLE:");
            }
        });
        assertThat(summary.outcomes().stream().filter(o -> o.arm().equals("NETARSUDIL_B")))
                .allSatisfy(o -> assertThat(o.status()).isEqualTo("ADMITTED_ADEQUATE"));
        assertThat(summary.outcomes().stream().filter(o -> o.arm().equals("NETARSUDIL_A")))
                .allSatisfy(o -> assertThat(o.status()).isEqualTo("ADMITTED_DEGRADED"));
        assertThat(summary.outcomes().stream().filter(o -> o.arm().startsWith("DCMB"))
                .filter(o -> o.status().startsWith("ADMITTED_")))
                .allSatisfy(o -> assertThat(o.status()).isEqualTo("ADMITTED_DEGRADED"));
        assertThat(Files.readString(summary.receipt())).contains("dcmb_pi_evidence=DEGRADED")
                .contains("scientific_definitions_added=false");
        assertThat(detailed.evidence()).hasSize(168);
        assertThat(summary.outcomes().stream().filter(o -> o.status().startsWith("ADMITTED_")).count())
                .isEqualTo(168);
        assertThat(summary.outcomes().stream().filter(o -> o.status().equals("PENDING_EXTERNAL_EVIDENCE")).count())
                .isZero();
        assertThat(detailed.evidence()).allSatisfy(evidence -> {
            assertThat(evidence.observation().graph().edges()).noneMatch(edge ->
                    edge.environment().kind() == totah.lab.athena.recognition.RecognitionNode.Kind.SAM_FEATURE);
            assertThat(evidence.observation().graph().edges()).noneMatch(edge -> edge.environmentResidue().chainId().equals("L"));
            assertThat(evidence.surfDiffSha256()).matches("[0-9a-f]{64}");
            assertThat(evidence.observation().graph().edges()).allSatisfy(edge ->
                    assertThat(edge.representativeProvenance()).hasValueSatisfying(value ->
                            assertThat(value).contains("SURFDIFF:").contains(evidence.surfDiffSha256())));
        });
        assertThat(detailed.evidence()).anySatisfy(evidence ->
                assertThat(evidence.cofactorEvidence().cofactorResidues()).isNotEmpty());
        assertThat(detailed.evidence().stream().filter(e->e.arm().startsWith("DCMB")))
                .allSatisfy(e->assertThat(e.observation().graph().edges())
                        .noneMatch(edge->edge.interactionType().name().startsWith("PI_")));
        assertThat(detailed.evidence()).anySatisfy(evidence -> assertThat(evidence.observation().graph().edges())
                .anyMatch(edge->edge.differentialSurface().isPresent()));
    }

    @Test void stableLigandIdentityIsParalogIndependentButStereochemistrySpecific() {
        assertThat(Mettl7RecognitionBatchMaterializer.stableLigandId("NETARSUDIL_A"))
                .isEqualTo(Mettl7RecognitionBatchMaterializer.stableLigandId("NETARSUDIL_B"));
        assertThat(Mettl7RecognitionBatchMaterializer.stableLigandId("DCMB_A_R"))
                .isEqualTo(Mettl7RecognitionBatchMaterializer.stableLigandId("DCMB_B_R"));
        assertThat(Mettl7RecognitionBatchMaterializer.stableLigandId("DCMB_A_S"))
                .isNotEqualTo(Mettl7RecognitionBatchMaterializer.stableLigandId("DCMB_B_R"));
    }

    @Test void matchedEdgeAccountingRetainsDuplicateTypedEdges() {
        RecognitionStateObservation twoCopies = observation("two", 2);
        RecognitionStateObservation oneCopy = observation("one", 1);

        assertThat(Mettl7RecognitionBatchMaterializer.typedJaccard(twoCopies, oneCopy)).isEqualTo(1.0);
        assertThat(Mettl7RecognitionBatchMaterializer.typedMultisetIntersection(twoCopies, oneCopy)).isEqualTo(1);
        assertThat(Mettl7RecognitionBatchMaterializer.typedMultisetIntersection(oneCopy, twoCopies)).isEqualTo(1);
    }

    @Test void diagnosticSerializationSuppressesNonScientificFloatingPointNoise() {
        assertThat(Mettl7RecognitionBatchMaterializer.decimal(11.083837346493740))
                .isEqualTo(Mettl7RecognitionBatchMaterializer.decimal(11.083837346493741));
        assertThat(Mettl7RecognitionBatchMaterializer.decimal(11.083837346493740))
                .isEqualTo("11.083837346494");
    }

    private static RecognitionStateObservation observation(String poseId, int copies) {
        InteractionThresholds thresholds = InteractionThresholds.athenaDefaults();
        ResidueId residue = new ResidueId("A", 10, null);
        Atom protein = atom(1, "P");
        Atom ligand = atom(2, "L");
        List<RecognitionEdge> edges = java.util.stream.IntStream.range(0, copies).mapToObj(index -> {
            Interaction interaction = new Interaction(InteractionType.HYDROPHOBIC_CONTACT, residue,
                    List.of(protein), List.of(ligand), 3.0, null, null, null, null, thresholds);
            return new RecognitionEdge(poseId + ":" + index, RecognitionNode.ligand("feature"),
                    RecognitionNode.environment(residue, false), interaction, Optional.empty(),
                    EvidenceQuality.ADEQUATE, InteractionRole.UNRESOLVED, poseId,
                    Optional.empty(), Optional.of("fixture"));
        }).toList();
        InteractionProfile profile = new InteractionProfile(List.of(), List.of(), Set.of(), thresholds,
                List.of(new PerceptionSummary(PerceptionSummary.RECEPTOR,
                        PerceptionProvenance.BOND_GRAPH, 1, 0, 0, 0, 0)));
        RecognitionGraph graph = RecognitionGraph.of(new RecognitionGraph.Provenance(
                "protein", "ligand", poseId, Optional.empty(), Optional.of("fixture")), profile, edges);
        return new RecognitionStateObservation(poseId, graph, "seed", "run", Optional.empty(),
                "feature-map", EvidenceQuality.ADEQUATE, List.of());
    }

    private static Atom atom(int serial, String name) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(serial, 0, 0))
                .element(Element.C).occupancy(1).bFactor(0).charge(0).build();
    }
}
