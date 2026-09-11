package totah.lab.mettl7.recognition;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionThresholds;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.interaction.PerceptionSummary;
import totah.lab.athena.interaction.perception.PerceptionProvenance;
import totah.lab.athena.recognition.EvidenceQuality;
import totah.lab.athena.recognition.StableLigandFeatureMap;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;
import totah.lab.gaia.chemistry.Element;
import totah.lab.gaia.geometry.Point3D;
import totah.lab.gaia.structure.Atom;
import totah.lab.gaia.structure.ResidueId;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Mettl7RecognitionStateAdapterTest {
    private static final ResidueId A10 = new ResidueId("A", 10, null);
    @TempDir Path temporary;

    @Test void admitsCompleteHashVerifiedJavaEvidence() throws Exception {
        var result = new Mettl7RecognitionStateAdapter().adapt(input(EvidenceQuality.ADEQUATE, true, false));
        assertThat(result.admitted()).isTrue();
        assertThat(result.accepted().orElseThrow().observation().graph().edges()).hasSize(1);
        assertThat(result.accepted().orElseThrow().adapterVersion())
                .isEqualTo(Mettl7RecognitionStateAdapter.VERSION);
    }

    @Test void hashMismatchIsVisibleRejection() throws Exception {
        var base = input(EvidenceQuality.ADEQUATE, true, false);
        var bad = new Mettl7RecognitionStateAdapter.Input(
                new Mettl7RecognitionStateAdapter.Artifact("pose-1", base.poseArtifact().path(), "0".repeat(64)),
                base.receptorArtifact(), base.ligandArtifact(), base.samArtifact(), base.ligandId(), base.paralogId(), base.poseId(),
                base.seedId(), base.runId(), base.familyId(), base.receptorSamProvenance(),
                base.interactionProfile(), base.interactionProfileProvenance(), base.featureMap(),
                base.featureReceipt(), base.residueCorrespondence(), base.differentialSurface(),
                base.geometryFamilyProvenance(), base.overallQuality(), base.interactionQualityByOrdinal(),
                base.constraints(), base.validityEvidence());
        var result = new Mettl7RecognitionStateAdapter().adapt(bad);
        assertThat(result.admitted()).isFalse();
        assertThat(result.rejections()).extracting(Mettl7RecognitionStateAdapter.Rejection::code)
                .contains(Mettl7RecognitionStateAdapter.RejectionCode.POSE_HASH_MISMATCH);
    }

    @Test void incompleteFeatureBijectionIsRejected() throws Exception {
        var result = new Mettl7RecognitionStateAdapter().adapt(input(EvidenceQuality.ADEQUATE, false, false));
        assertThat(result.rejections()).extracting(Mettl7RecognitionStateAdapter.Rejection::code)
                .contains(Mettl7RecognitionStateAdapter.RejectionCode.FEATURE_CORRESPONDENCE_UNAVAILABLE);
    }

    @Test void dcmbPiRemainsDegradedRatherThanBeingPromoted() throws Exception {
        var result = new Mettl7RecognitionStateAdapter().adapt(input(EvidenceQuality.DEGRADED, true, true));
        assertThat(result.admitted()).isTrue();
        assertThat(result.accepted().orElseThrow().observation().graph().edges().getFirst().quality())
                .isEqualTo(EvidenceQuality.DEGRADED);
    }

    @Test void cofactorInteractionDoesNotRequireProteinResidueCorrespondence() throws Exception {
        var base = input(EvidenceQuality.ADEQUATE, true, false);
        var cofactorProfile = new InteractionProfile(base.interactionProfile().interactions(),
                base.interactionProfile().rawInteractions(), Set.of(A10), base.interactionProfile().thresholds(),
                base.interactionProfile().perception());
        var changed = replaceProfile(base, cofactorProfile,
                new ExplicitResidueCorrespondence(Map.of()));
        assertThat(new Mettl7RecognitionStateAdapter().adapt(changed).admitted()).isTrue();
    }

    @Test void chemicallyIncompatibleStableFeatureIsRejected() throws Exception {
        var base = input(EvidenceQuality.ADEQUATE, true, false);
        var feature = new StableLigandFeatureMap.Feature("stable-ring", LigandFeature.Type.HYDROPHOBE,
                List.of("orbit-1"), List.of("source-ring"), EvidenceQuality.ADEQUATE, Map.of("source", "fixture"));
        var map = new StableLigandFeatureMap(base.ligandId(), List.of(feature), "OCL orbits", "Athena perception",
                EvidenceQuality.ADEQUATE, List.of());
        var receipt = Mettl7RecognitionStateAdapter.PoseFeatureReceipt.create(base.ligandId(), base.poseId(),
                base.featureReceipt().canonicalSdfSha256(), "idcode", Map.of(1, 0), 1, 1,
                Map.of(0, "stable-ring"), map, List.of());
        var changed = new Mettl7RecognitionStateAdapter.Input(base.poseArtifact(), base.receptorArtifact(),
                base.ligandArtifact(), base.samArtifact(), base.ligandId(), base.paralogId(), base.poseId(),
                base.seedId(), base.runId(), base.familyId(), base.receptorSamProvenance(), base.interactionProfile(),
                base.interactionProfileProvenance(), map, receipt, base.residueCorrespondence(),
                base.differentialSurface(), base.geometryFamilyProvenance(), base.overallQuality(),
                base.interactionQualityByOrdinal(), base.constraints(), base.validityEvidence());
        assertThat(new Mettl7RecognitionStateAdapter().adapt(changed).rejections())
                .extracting(Mettl7RecognitionStateAdapter.Rejection::code)
                .contains(Mettl7RecognitionStateAdapter.RejectionCode.INCOMPATIBLE_STABLE_FEATURE);
    }

    @Test void malformedArtifactAndReceiptAreRejectedAtConstruction() throws Exception {
        assertThatThrownBy(() -> new Mettl7RecognitionStateAdapter.Artifact("pose", temporary, "00"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64 hexadecimal");
        assertThatThrownBy(() -> new Mettl7RecognitionStateAdapter.PoseFeatureReceipt("lig", "pose",
                "0".repeat(64), "idcode", Map.of(), -1, 0, Map.of(), false,
                "0".repeat(64), "0".repeat(64), "0".repeat(64), List.of()))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("non-negative");
    }

    private static Mettl7RecognitionStateAdapter.Input replaceProfile(Mettl7RecognitionStateAdapter.Input base,
            InteractionProfile profile, ExplicitResidueCorrespondence correspondence) {
        return new Mettl7RecognitionStateAdapter.Input(base.poseArtifact(), base.receptorArtifact(),
                base.ligandArtifact(), base.samArtifact(), base.ligandId(), base.paralogId(), base.poseId(),
                base.seedId(), base.runId(), base.familyId(), base.receptorSamProvenance(), profile,
                base.interactionProfileProvenance(), base.featureMap(), base.featureReceipt(), correspondence,
                base.differentialSurface(), base.geometryFamilyProvenance(), base.overallQuality(),
                base.interactionQualityByOrdinal(), base.constraints(), base.validityEvidence());
    }

    private Mettl7RecognitionStateAdapter.Input input(EvidenceQuality quality,
            boolean completeBijection, boolean dcmb) throws Exception {
        Path pose = write("pose.pdbqt", "POSE"); Path receptor = write("receptor.pdbqt", "RECEPTOR");
        Path sdf = write("ligand.sdf", "SDF");
        String ligand = dcmb ? "DCMB" : "NETARSUDIL";
        var feature = new StableLigandFeatureMap.Feature("stable-ring", LigandFeature.Type.AROMATIC_RING,
                List.of("orbit-1"), List.of("source-ring"), quality, Map.of("source", "fixture"));
        var stableMap = new StableLigandFeatureMap(ligand, List.of(feature), "OCL orbits", "Athena perception",
                quality, dcmb ? List.of("DCMB_PI_EVIDENCE=DEGRADED") : List.of());
        var receipt = Mettl7RecognitionStateAdapter.PoseFeatureReceipt.create(ligand, "pose-1", sha(sdf),
                "idcode", Map.of(1, 0), completeBijection ? 1 : 2, completeBijection ? 1 : 2,
                Map.of(0, "stable-ring"), stableMap, List.of());
        return new Mettl7RecognitionStateAdapter.Input(artifact("pose-1", pose), artifact("B", receptor),
                artifact(ligand, sdf),
                Optional.empty(), ligand, "B", "pose-1", "seed-1", "run-1", Optional.of("family-1"),
                "validated B+SAM receptor", profile(), "InteractionProfiler/athenaDefaults",
                stableMap, receipt, new ExplicitResidueCorrespondence(Map.of(A10, A10)), Optional.empty(),
                "canonical family evidence", quality, Map.of(0, quality), List.of(), List.of("SAM compatible"));
    }
    private Path write(String name, String text) throws Exception {
        Path path = temporary.resolve(name); Files.writeString(path, text, StandardCharsets.UTF_8); return path;
    }
    private static Mettl7RecognitionStateAdapter.Artifact artifact(String identity, Path path) throws Exception {
        return new Mettl7RecognitionStateAdapter.Artifact(identity, path, sha(path));
    }
    private static String sha(Path path) throws Exception { return HexFormat.of().formatHex(
            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private static InteractionProfile profile() {
        var thresholds = InteractionThresholds.athenaDefaults();
        Interaction interaction = new Interaction(InteractionType.PI_CATION, A10,
                List.of(atom(1, "P")), List.of(atom(2, "L")), 4.0, null, null, null, null, thresholds);
        return new InteractionProfile(List.of(interaction), List.of(interaction), Set.of(), thresholds,
                List.of(new PerceptionSummary(PerceptionSummary.RECEPTOR,
                        PerceptionProvenance.BOND_GRAPH, 1, 0, 0, 0, 0)));
    }
    private static Atom atom(int serial, String name) {
        return Atom.builder().pdbSerial(serial).name(name).position(new Point3D(serial, 0, 0))
                .element(Element.C).occupancy(1).bFactor(0).charge(0).build();
    }
}
