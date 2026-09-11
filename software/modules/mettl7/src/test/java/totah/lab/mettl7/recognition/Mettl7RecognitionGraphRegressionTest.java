package totah.lab.mettl7.recognition;

import org.junit.jupiter.api.Test;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.interaction.PerceptionSummary;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.athena.recognition.CrossParalogEdgeState;
import totah.lab.athena.recognition.DifferentialRecognition;
import totah.lab.athena.recognition.DifferentialRecognitionComparator;
import totah.lab.athena.recognition.EvidenceQuality;
import totah.lab.athena.recognition.InteractionRole;
import totah.lab.athena.recognition.RecognitionComparisonPolicy;
import totah.lab.athena.recognition.RecognitionEdge;
import totah.lab.athena.recognition.RecognitionGraph;
import totah.lab.athena.recognition.RecognitionNode;
import totah.lab.athena.surface.differential.DifferentialResidueScore;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Frozen evidence-shape regressions; no detector or docking calculation is reproduced here. */
class Mettl7RecognitionGraphRegressionTest {
    private static final InteractionThresholds THRESHOLDS = InteractionThresholds.athenaDefaults();
    private static final String SURFDIFF_OVERLAY =
            "research/mettl7-surfdiff-compatible-v1/LIGAND_INTERACTION_DIFFERENTIAL_OVERLAY.csv";
    private final DifferentialRecognitionComparator comparator = new DifferentialRecognitionComparator();

    @Test
    void netarsudilRepresentsK196SubstitutionAndConservedR206Background() {
        ResidueId r151 = residue(151), r196 = residue(196), r206 = residue(206);
        RecognitionGraph b = graph("METTL7B", "NETARSUDIL-B-FAMILY-5",
                edge("b-q151", "aryl-cap", r151, InteractionType.HYDROPHOBIC_CONTACT,
                        3.882917202310654, adequate(), .444444444444, .383679979971),
                edge("b-k196-h", "isoquinoline-ring", r196, InteractionType.HYDROPHOBIC_CONTACT,
                        3.6953842831294295, adequate(), .666666666667, .479637216824),
                edge("b-k196-pi", "isoquinoline-ring", r196, InteractionType.PI_CATION,
                        4.4989087967096095, adequate(), .666666666667, .479637216824),
                edge("b-r206", "distal-carbon", r206, InteractionType.HYDROPHOBIC_CONTACT,
                        3.7642946218382, adequate(), 0, .172881206033));
        RecognitionGraph a = graph("METTL7A", "NETARSUDIL-A-CONTROL",
                edge("a-h196", "isoquinoline-ring", r196, InteractionType.PI_STACK_T_SHAPED,
                        4.456375165485459, adequate(), .666666666667, .490304698184),
                edge("a-k151", "aryl-cap", r151, InteractionType.SALT_BRIDGE,
                        5.140637120046503, adequate(), .444444444444, .382445124703),
                edge("a-r206", "distal-carbon", r206, InteractionType.HYDROPHOBIC_CONTACT,
                        3.8, adequate(), 0, .171512801523));

        DifferentialRecognition result = comparator.compare(b, a,
                identityCorrespondence(r151, r196, r206), RecognitionComparisonPolicy.evidenceOnly());

        assertThat(state(result, "b-k196-pi")).isEqualTo(CrossParalogEdgeState.SUBSTITUTED);
        assertThat(state(result, "b-q151")).isEqualTo(CrossParalogEdgeState.SUBSTITUTED);
        assertThat(state(result, "b-r206")).isEqualTo(CrossParalogEdgeState.PRESERVED);
        assertThat(result.edges().stream().filter(row -> row.differentialEnvironment())).hasSize(4);
    }

    @Test
    void dcmbKeepsHydrophobicDifferentialsAndDoesNotUpgradeDegradedPiEvidence() {
        ResidueId r39 = residue(39), r43 = residue(43), r195 = residue(195), r199 = residue(199);
        RecognitionGraph a = graph("METTL7A", "DCMB-A-FAMILY",
                edge("a-f39", "dcmb-aryl", r39, InteractionType.HYDROPHOBIC_CONTACT,
                        3.3278883695220323, adequate(), .555555555556, .393125428315),
                edge("a-f43", "dcmb-aryl", r43, InteractionType.HYDROPHOBIC_CONTACT,
                        3.3911182521404357, adequate(), .555555555556, .550142106897),
                edge("a-f199", "dcmb-aryl", r199, InteractionType.HYDROPHOBIC_CONTACT,
                        3.3687169664428622, adequate(), .833333333333, .419754277892),
                edge("a-w195", "dcmb-aryl", r195, InteractionType.HYDROPHOBIC_CONTACT,
                        3.4040439186355984, adequate(), 0, .311640385148),
                edge("a-pi-degraded", "dcmb-aryl", r43, InteractionType.PI_STACK_PARALLEL,
                        4.0, EvidenceQuality.DEGRADED, .555555555556, .550142106897));
        RecognitionGraph b = graph("METTL7B", "DCMB-B-FAMILY",
                edge("b-w195", "dcmb-aryl", r195, InteractionType.HYDROPHOBIC_CONTACT,
                        3.5, adequate(), 0, .3));

        DifferentialRecognition result = comparator.compare(a, b,
                identityCorrespondence(r39, r43, r195, r199),
                RecognitionComparisonPolicy.evidenceOnly());

        assertThat(state(result, "a-f39")).isEqualTo(CrossParalogEdgeState.REROUTED);
        assertThat(state(result, "a-f43")).isEqualTo(CrossParalogEdgeState.REROUTED);
        assertThat(state(result, "a-f199")).isEqualTo(CrossParalogEdgeState.REROUTED);
        assertThat(state(result, "a-w195")).isEqualTo(CrossParalogEdgeState.PRESERVED);
        assertThat(state(result, "a-pi-degraded")).isEqualTo(CrossParalogEdgeState.UNAVAILABLE);
    }

    private static CrossParalogEdgeState state(DifferentialRecognition result, String id) {
        return result.edges().stream().filter(row -> row.sourceEdge().id().equals(id))
                .findFirst().orElseThrow().state();
    }

    private static ExplicitResidueCorrespondence identityCorrespondence(ResidueId... residues) {
        Map<ResidueId, ResidueId> map = new LinkedHashMap<>();
        for (ResidueId residue : residues) map.put(residue, residue);
        return new ExplicitResidueCorrespondence(map);
    }

    private static RecognitionGraph graph(String protein, String pose, RecognitionEdge... edges) {
        return RecognitionGraph.of(new RecognitionGraph.Provenance(protein, "ligand", pose,
                        Optional.of(pose + "-family"), Optional.of(SURFDIFF_OVERLAY)),
                profile(), List.of(edges));
    }

    private static RecognitionEdge edge(String id, String feature, ResidueId residue,
            InteractionType type, double distance, EvidenceQuality quality, double rup, double rus) {
        Interaction interaction = new Interaction(type, residue, List.of(atom(1, "P", 0)),
                List.of(atom(2, "L", distance)), distance, null, null, null, null, THRESHOLDS);
        DifferentialResidueScore surface = new DifferentialResidueScore(residue,
                Optional.of(residue), .2, .8, rup, rus, 1.0 - rus);
        return new RecognitionEdge(id, RecognitionNode.ligand(feature),
                RecognitionNode.environment(residue, false), interaction, Optional.of(surface),
                quality, InteractionRole.UNRESOLVED, "frozen-pose", Optional.of("frozen-family"),
                Optional.of(SURFDIFF_OVERLAY));
    }

    private static ResidueId residue(int number) { return new ResidueId("A", number, null); }
    private static EvidenceQuality adequate() { return EvidenceQuality.ADEQUATE; }

    private static InteractionProfile profile() {
        return new InteractionProfile(List.of(), List.of(), Set.of(), THRESHOLDS,
                List.of(new PerceptionSummary(PerceptionSummary.RECEPTOR,
                        PerceptionProvenance.BOND_GRAPH, 0, 0, 0, 0, 0)));
    }

    private static Atom atom(int serial, String name, double x) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(x, 0, 0))
                .element(Element.C).occupancy(1).charge(0).bFactor(0).build();
    }
}
