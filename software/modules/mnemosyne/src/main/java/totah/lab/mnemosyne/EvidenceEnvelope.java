package totah.lab.mnemosyne;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

/** Opaque evidence: unknown payload formats are preserved, never parsed by admission. */
public record EvidenceEnvelope(ScientificReference reference, String evidenceType, String payloadFormat,
                               String payloadVersion, Optional<String> payloadBase64, Optional<String> artifactPath,
                               String payloadSha256, Observation.Provenance provenance, ScientificReference method,
                               ScientificReference context, List<EvidenceSubject> subjects,
                               List<String> qualifications, List<String> limitations, Instant recordedAt) {
    public EvidenceEnvelope {
        reference.require(ScientificReference.Kind.EVIDENCE_ENVELOPE);
        ScientificReference.text(evidenceType); ScientificReference.text(payloadFormat); ScientificReference.text(payloadVersion);
        payloadBase64 = Objects.requireNonNull(payloadBase64); artifactPath = Objects.requireNonNull(artifactPath);
        if (payloadBase64.isPresent() == artifactPath.isPresent()) throw new IllegalArgumentException("exactly one payload source required");
        if (payloadSha256 == null || !payloadSha256.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("payload SHA-256 required");
        if (payloadBase64.isPresent()) {
            byte[] bytes = Base64.getDecoder().decode(payloadBase64.orElseThrow());
            if (!Base64.getEncoder().encodeToString(bytes).equals(payloadBase64.orElseThrow())
                    || !EvidenceExchange.sha256(bytes).equals(payloadSha256)) throw new IllegalArgumentException("payload digest/encoding mismatch");
        }
        artifactPath.ifPresent(ScientificReference::text);
        Objects.requireNonNull(provenance); method.require(ScientificReference.Kind.METHOD);
        context.require(ScientificReference.Kind.CONTEXT); subjects = List.copyOf(subjects);
        if (subjects.isEmpty()) throw new IllegalArgumentException("subjects required");
        qualifications = List.copyOf(qualifications); limitations = List.copyOf(limitations); Objects.requireNonNull(recordedAt);
    }
    /** Returns fresh bytes; no mutable payload is retained or exposed. */
    public byte[] readPayload() throws IOException {
        if (payloadBase64.isPresent()) return Base64.getDecoder().decode(payloadBase64.orElseThrow());
        byte[] bytes = Files.readAllBytes(Path.of(artifactPath.orElseThrow()));
        if (!EvidenceExchange.sha256(bytes).equals(payloadSha256)) throw new IOException("external evidence digest mismatch");
        return bytes;
    }
    /** Streaming verification for large authoritative artifacts; does not copy or rewrite them. */
    public void verifyArtifact() throws IOException {
        if (artifactPath.isEmpty()) return;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(Path.of(artifactPath.orElseThrow()))) {
                byte[] buffer = new byte[65536]; int n;
                while ((n = input.read(buffer)) != -1) digest.update(buffer, 0, n);
            }
            if (!HexFormat.of().formatHex(digest.digest()).equals(payloadSha256)) throw new IOException("external evidence digest mismatch");
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
