package totah.lab.mettl7.recognition;

import totah.lab.athena.interaction.Interaction;
import totah.lab.athena.interaction.InteractionProfile;
import totah.lab.athena.interaction.InteractionType;
import totah.lab.athena.design.feature.LigandFeature;
import totah.lab.athena.recognition.EvidenceQuality;
import totah.lab.athena.recognition.RecognitionConstraint;
import totah.lab.athena.recognition.RecognitionGraph;
import totah.lab.athena.recognition.RecognitionStateObservation;
import totah.lab.athena.recognition.StableLigandFeatureMap;
import totah.lab.athena.recognition.InteractionStableFeatureAssigner;
import totah.lab.athena.surface.differential.DifferentialSurfaceMap;
import totah.lab.athena.surface.differential.ExplicitResidueCorrespondence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** METTL7 admission boundary from canonical Java evidence to one observed recognition state. */
public final class Mettl7RecognitionStateAdapter {
    public static final String VERSION = "METTL7_RECOGNITION_STATE_ADAPTER_V1";

    public Result adapt(Input input) throws IOException {
        Objects.requireNonNull(input, "input");
        List<Rejection> rejected = validate(input);
        if (!rejected.isEmpty()) return new Result(Optional.empty(), rejected, VERSION);

        Map<Interaction, Integer> ordinal = new java.util.IdentityHashMap<>();
        for (int i = 0; i < input.interactionProfile().interactions().size(); i++) {
            ordinal.put(input.interactionProfile().interactions().get(i), i);
        }
        RecognitionGraph graph = RecognitionGraph.from(input.interactionProfile(),
                input.differentialSurface(),
                new RecognitionGraph.Provenance(input.paralogId(), input.ligandId(), input.poseId(),
                        input.familyId(), Optional.of(input.geometryFamilyProvenance())),
                interaction -> input.featureReceipt().stableFeatureIdByInteractionOrdinal()
                        .get(ordinal.get(interaction)),
                (interaction, profile) -> input.interactionQualityByOrdinal()
                        .get(ordinal.get(interaction)));
        RecognitionStateObservation observation = new RecognitionStateObservation(input.poseId(), graph,
                input.seedId(), input.runId(), input.familyId(), input.featureReceipt().receiptSha256(),
                input.overallQuality(), input.constraints());
        return new Result(Optional.of(new Accepted(observation, input.poseArtifact(),
                input.receptorArtifact(), input.ligandArtifact(), input.samArtifact(), input.featureReceipt(),
                input.interactionProfileProvenance(), input.geometryFamilyProvenance(),
                input.validityEvidence(), VERSION)), List.of(), VERSION);
    }

    private static List<Rejection> validate(Input input) throws IOException {
        List<Rejection> out = new ArrayList<>();
        requireIdentity(input.poseId(), "pose", out); requireIdentity(input.ligandId(), "ligand", out);
        requireIdentity(input.paralogId(), "paralog", out); requireIdentity(input.seedId(), "seed", out);
        requireIdentity(input.runId(), "run", out); requireIdentity(input.receptorSamProvenance(), "receptor/SAM", out);
        requireIdentity(input.interactionProfileProvenance(), "interaction profile", out);
        requireIdentity(input.geometryFamilyProvenance(), "geometry/family", out);
        verifySafely(input.poseArtifact(), RejectionCode.POSE_HASH_MISMATCH, out);
        verifySafely(input.receptorArtifact(), RejectionCode.RECEPTOR_HASH_MISMATCH, out);
        verifySafely(input.ligandArtifact(), RejectionCode.LIGAND_HASH_MISMATCH, out);
        input.samArtifact().ifPresent(artifact -> verifySafely(artifact, RejectionCode.SAM_HASH_MISMATCH, out));
        if (!input.poseArtifact().identity().equals(input.poseId()))
            out.add(new Rejection(RejectionCode.POSE_IDENTITY_MISMATCH, "pose artifact identity differs from pose id"));
        if (!input.receptorArtifact().identity().equals(input.paralogId()))
            out.add(new Rejection(RejectionCode.RECEPTOR_IDENTITY_MISMATCH, "receptor artifact identity differs from paralog"));
        if (input.featureMap().quality() == EvidenceQuality.UNAVAILABLE)
            out.add(new Rejection(RejectionCode.FEATURE_CORRESPONDENCE_UNAVAILABLE, "stable feature map unavailable"));
        if (!input.featureMap().ligandId().equals(input.ligandId()))
            out.add(new Rejection(RejectionCode.LIGAND_IDENTITY_MISMATCH, "feature-map ligand differs"));
        if (!input.ligandArtifact().identity().equals(input.ligandId())
                || !input.ligandArtifact().sha256().equalsIgnoreCase(input.featureReceipt().canonicalSdfSha256()))
            out.add(new Rejection(RejectionCode.LIGAND_IDENTITY_MISMATCH, "canonical SDF identity/hash differs"));
        if (!input.featureReceipt().ligandId().equals(input.ligandId())
                || !input.featureReceipt().poseId().equals(input.poseId()))
            out.add(new Rejection(RejectionCode.FEATURE_RECEIPT_IDENTITY_MISMATCH, "feature receipt identity differs"));
        if (!input.featureReceipt().completeBijection()
                || input.featureReceipt().poseSerialToSdfAtomIndex().size() != input.featureReceipt().poseAtomCount()
                || input.featureReceipt().poseSerialToSdfAtomIndex().values().stream().distinct().count()
                != input.featureReceipt().poseAtomCount()
                || input.featureReceipt().poseSerialToSdfAtomIndex().values().stream()
                .anyMatch(index -> index < 0 || index >= input.featureReceipt().sdfAtomCount()))
            out.add(new Rejection(RejectionCode.FEATURE_CORRESPONDENCE_UNAVAILABLE, "pose-to-SDF atom map is not bijective"));
        if (!mappingHash(input.featureReceipt().poseSerialToSdfAtomIndex())
                .equals(input.featureReceipt().mappingSha256()))
            out.add(new Rejection(RejectionCode.MAPPING_HASH_MISMATCH, "pose/SDF mapping hash differs"));
        if (!featureMapHash(input.featureMap()).equals(input.featureReceipt().featureMapSha256()))
            out.add(new Rejection(RejectionCode.FEATURE_MAP_HASH_MISMATCH, "stable feature-map hash differs"));
        if (!receiptHash(input.featureReceipt()).equals(input.featureReceipt().receiptSha256()))
            out.add(new Rejection(RejectionCode.RECEIPT_HASH_MISMATCH, "feature receipt hash differs"));
        int interactions = input.interactionProfile().interactions().size();
        if (input.featureReceipt().stableFeatureIdByInteractionOrdinal().size() != interactions)
            out.add(new Rejection(RejectionCode.INCOMPLETE_INTERACTION_FEATURE_MAP, "not every interaction has a stable feature"));
        if (input.interactionQualityByOrdinal().size() != interactions)
            out.add(new Rejection(RejectionCode.INCOMPLETE_EVIDENCE_QUALITY_MAP, "not every interaction has quality"));
        for (int i = 0; i < interactions; i++) {
            String stableId = input.featureReceipt().stableFeatureIdByInteractionOrdinal().get(i);
            Optional<StableLigandFeatureMap.Feature> stableFeature = stableId == null
                    ? Optional.empty() : input.featureMap().byStableId(stableId);
            if (stableFeature.isEmpty())
                out.add(new Rejection(RejectionCode.UNKNOWN_STABLE_FEATURE, "interaction " + i));
            else if (!InteractionStableFeatureAssigner.compatible(stableFeature.get().type(),
                    input.interactionProfile().interactions().get(i)))
                out.add(new Rejection(RejectionCode.INCOMPATIBLE_STABLE_FEATURE,
                        "interaction " + i + ": " + stableFeature.get().type() + " for "
                                + input.interactionProfile().interactions().get(i).type()));
            if (!input.interactionQualityByOrdinal().containsKey(i))
                out.add(new Rejection(RejectionCode.INCOMPLETE_EVIDENCE_QUALITY_MAP, "interaction " + i));
            var residue = input.interactionProfile().interactions().get(i).residue();
            if (!input.interactionProfile().cofactorResidues().contains(residue)
                    && input.residueCorrespondence().subjectOf(residue).isEmpty())
                out.add(new Rejection(RejectionCode.RESIDUE_CORRESPONDENCE_UNAVAILABLE, residue.toString()));
        }
        if (input.overallQuality() == EvidenceQuality.UNAVAILABLE)
            out.add(new Rejection(RejectionCode.EVIDENCE_UNAVAILABLE, "overall evidence unavailable"));
        return List.copyOf(out);
    }

    private static void verify(Artifact artifact, RejectionCode code, List<Rejection> out) throws IOException {
        if (!Files.isRegularFile(artifact.path())) { out.add(new Rejection(code, "missing: " + artifact.path())); return; }
        String actual = sha256(artifact.path());
        if (!actual.equalsIgnoreCase(artifact.sha256())) out.add(new Rejection(code, artifact.path().toString()));
    }
    private static void verifySafely(Artifact artifact, RejectionCode code, List<Rejection> out) {
        try {
            verify(artifact, code, out);
        } catch (IOException exception) {
            out.add(new Rejection(code, "unreadable: " + artifact.path() + ": " + exception.getMessage()));
        }
    }
    private static String sha256(Path path) throws IOException {
        try (InputStream stream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; int read;
            while ((read = stream.read(buffer)) >= 0) digest.update(buffer, 0, read);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static String mappingHash(Map<Integer, Integer> mapping) {
        return sha256(mapping.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining("\n")));
    }
    public static String featureMapHash(StableLigandFeatureMap map) {
        String features = map.features().stream().sorted(java.util.Comparator.comparing(StableLigandFeatureMap.Feature::stableId))
                .map(f -> f.stableId() + "|" + f.type() + "|" + String.join(",", f.canonicalAtomOrbits())
                        + "|" + String.join(",", f.sourceFeatureIds()) + "|" + f.quality() + "|"
                        + f.provenance().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(",")))
                .collect(java.util.stream.Collectors.joining("\n"));
        return sha256(String.join("\n", map.ligandId(), map.atomIdentityProvenance(),
                map.featurePerceptionProvenance(), map.quality().name(),
                String.join(",", map.limitations().stream().sorted().toList()), features));
    }
    public static String receiptHash(PoseFeatureReceipt receipt) {
        return sha256(String.join("\n", receipt.ligandId(), receipt.poseId(), receipt.canonicalSdfSha256(),
                receipt.canonicalIdCode(), Integer.toString(receipt.poseAtomCount()),
                Integer.toString(receipt.sdfAtomCount()), receipt.mappingSha256(), receipt.featureMapSha256(),
                receipt.stableFeatureIdByInteractionOrdinal().entrySet().stream().sorted(Map.Entry.comparingByKey())
                        .map(e -> e.getKey() + "=" + e.getValue()).collect(java.util.stream.Collectors.joining(",")),
                String.join(",", receipt.ambiguityFlags().stream().sorted().toList())));
    }
    private static void requireIdentity(String value, String label, List<Rejection> out) {
        if (value == null || value.isBlank()) out.add(new Rejection(RejectionCode.PROVENANCE_MISSING, label));
    }
    private static void require(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " required");
    }
    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("(?i)[0-9a-f]{64}"))
            throw new IllegalArgumentException(label + " must be 64 hexadecimal characters");
    }

    public record Input(Artifact poseArtifact, Artifact receptorArtifact, Artifact ligandArtifact,
            Optional<Artifact> samArtifact,
            String ligandId, String paralogId, String poseId, String seedId, String runId,
            Optional<String> familyId, String receptorSamProvenance,
            InteractionProfile interactionProfile, String interactionProfileProvenance,
            StableLigandFeatureMap featureMap, PoseFeatureReceipt featureReceipt,
            ExplicitResidueCorrespondence residueCorrespondence,
            Optional<DifferentialSurfaceMap> differentialSurface,
            String geometryFamilyProvenance, EvidenceQuality overallQuality,
            Map<Integer, EvidenceQuality> interactionQualityByOrdinal,
            List<RecognitionConstraint> constraints, List<String> validityEvidence) {
        public Input {
            Objects.requireNonNull(poseArtifact); Objects.requireNonNull(receptorArtifact);
            Objects.requireNonNull(ligandArtifact);
            samArtifact = Objects.requireNonNull(samArtifact); familyId = Objects.requireNonNull(familyId);
            Objects.requireNonNull(interactionProfile); Objects.requireNonNull(featureMap);
            Objects.requireNonNull(featureReceipt); Objects.requireNonNull(residueCorrespondence);
            differentialSurface = Objects.requireNonNull(differentialSurface); Objects.requireNonNull(overallQuality);
            interactionQualityByOrdinal = Map.copyOf(interactionQualityByOrdinal);
            constraints = List.copyOf(constraints); validityEvidence = List.copyOf(validityEvidence);
        }
    }
    public record Artifact(String identity, Path path, String sha256) {
        public Artifact {
            require(identity, "artifact identity");
            path = Objects.requireNonNull(path, "artifact path").toAbsolutePath().normalize();
            requireSha256(sha256, "artifact sha256");
        }
    }
    public record PoseFeatureReceipt(String ligandId, String poseId, String canonicalSdfSha256,
            String canonicalIdCode, Map<Integer, Integer> poseSerialToSdfAtomIndex,
            int poseAtomCount, int sdfAtomCount,
            Map<Integer, String> stableFeatureIdByInteractionOrdinal, boolean completeBijection,
            String mappingSha256, String featureMapSha256, String receiptSha256,
            List<String> ambiguityFlags) {
        public PoseFeatureReceipt {
            require(ligandId, "receipt ligandId"); require(poseId, "receipt poseId");
            requireSha256(canonicalSdfSha256, "canonical SDF sha256");
            require(canonicalIdCode, "canonical IDCode");
            if (poseAtomCount < 0 || sdfAtomCount < 0) throw new IllegalArgumentException("atom counts must be non-negative");
            poseSerialToSdfAtomIndex = Map.copyOf(Objects.requireNonNull(poseSerialToSdfAtomIndex));
            stableFeatureIdByInteractionOrdinal = Map.copyOf(Objects.requireNonNull(stableFeatureIdByInteractionOrdinal));
            requireSha256(mappingSha256, "mapping sha256"); requireSha256(featureMapSha256, "feature-map sha256");
            if (!"PENDING".equals(receiptSha256)) requireSha256(receiptSha256, "receipt sha256");
            ambiguityFlags = List.copyOf(Objects.requireNonNull(ambiguityFlags));
        }
        public static PoseFeatureReceipt create(String ligandId, String poseId, String canonicalSdfSha256,
                String canonicalIdCode, Map<Integer, Integer> mapping, int poseAtomCount, int sdfAtomCount,
                Map<Integer, String> interactionFeatures, StableLigandFeatureMap featureMap,
                List<String> ambiguityFlags) {
            boolean bijective = mapping.size() == poseAtomCount
                    && mapping.values().stream().distinct().count() == poseAtomCount
                    && mapping.values().stream().allMatch(index -> index >= 0 && index < sdfAtomCount);
            String mappingSha = mappingHash(mapping); String featureSha = featureMapHash(featureMap);
            PoseFeatureReceipt unhashed = new PoseFeatureReceipt(ligandId, poseId, canonicalSdfSha256,
                    canonicalIdCode, mapping, poseAtomCount, sdfAtomCount, interactionFeatures, bijective,
                    mappingSha, featureSha, "PENDING", ambiguityFlags);
            return new PoseFeatureReceipt(ligandId, poseId, canonicalSdfSha256, canonicalIdCode, mapping,
                    poseAtomCount, sdfAtomCount, interactionFeatures, bijective, mappingSha, featureSha,
                    receiptHash(unhashed), ambiguityFlags);
        }
    }
    public record Accepted(RecognitionStateObservation observation, Artifact poseArtifact,
            Artifact receptorArtifact, Artifact ligandArtifact, Optional<Artifact> samArtifact, PoseFeatureReceipt featureReceipt,
            String interactionProfileProvenance, String geometryFamilyProvenance,
            List<String> validityEvidence, String adapterVersion) {
        public Accepted { validityEvidence = List.copyOf(validityEvidence); }
    }
    public record Result(Optional<Accepted> accepted, List<Rejection> rejections, String adapterVersion) {
        public Result { accepted = Objects.requireNonNull(accepted); rejections = List.copyOf(rejections); }
        public boolean admitted() { return accepted.isPresent(); }
    }
    public record Rejection(RejectionCode code, String detail) { }
    public enum RejectionCode {
        PROVENANCE_MISSING, POSE_HASH_MISMATCH, RECEPTOR_HASH_MISMATCH, LIGAND_HASH_MISMATCH, SAM_HASH_MISMATCH,
        POSE_IDENTITY_MISMATCH, RECEPTOR_IDENTITY_MISMATCH, LIGAND_IDENTITY_MISMATCH,
        FEATURE_RECEIPT_IDENTITY_MISMATCH, FEATURE_CORRESPONDENCE_UNAVAILABLE,
        MAPPING_HASH_MISMATCH, FEATURE_MAP_HASH_MISMATCH, RECEIPT_HASH_MISMATCH,
        INCOMPLETE_INTERACTION_FEATURE_MAP, INCOMPLETE_EVIDENCE_QUALITY_MAP,
        UNKNOWN_STABLE_FEATURE, INCOMPATIBLE_STABLE_FEATURE, RESIDUE_CORRESPONDENCE_UNAVAILABLE,
        INTERACTION_PROVENANCE_MISSING, EVIDENCE_UNAVAILABLE
    }
}
