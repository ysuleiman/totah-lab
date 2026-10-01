package totah.lab.athena.design.grammar;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;

/** Target-independent JSON persistence for grammars and evidence vectors. */
public final class DesignGrammarJsonCodec {
    private final ObjectMapper mapper;

    public DesignGrammarJsonCodec() {
        this(new ObjectMapper());
    }

    public DesignGrammarJsonCodec(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    public DesignGrammar readGrammar(Path path) throws IOException {
        return mapper.readValue(path.toFile(), DesignGrammar.class);
    }

    public totah.lab.athena.design.reasoning.DesignKnowledge readKnowledge(Path path) throws IOException {
        try (var input = java.nio.file.Files.newInputStream(path)) {
            return mapper.readValue(input, totah.lab.athena.design.reasoning.DesignKnowledge.class);
        }
    }

    /** A new immutable snapshot must never overwrite an earlier scientific input. */
    public void writeKnowledge(Path path, totah.lab.athena.design.reasoning.DesignKnowledge knowledge) throws IOException {
        try (var output = java.nio.file.Files.newOutputStream(path, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            mapper.writerWithDefaultPrettyPrinter().writeValue(output, knowledge);
        }
    }

    /** Source receipts cross the external-resource boundary as versioned JSON, not module dependencies. */
    public totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceReceipt readSourceReceipt(Path path) throws IOException {
        try (var input = java.nio.file.Files.newInputStream(path)) {
            return mapper.readValue(input, totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceReceipt.class);
        }
    }

    /** One local registry, shared by independently loaded snapshots. Even a rejected reservation is immutable. */
    public totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceDecision registerSourceReview(
            Path registry, totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceReceipt receipt,
            totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceReview review,
            totah.lab.athena.design.generation.MolecularDesignTree.Reference evidenceReference) throws IOException {
        var decision = totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.reviewSource(receipt, review, evidenceReference);
        java.nio.file.Files.createDirectories(registry);
        var path = reservationPath(registry, evidenceReference);
        byte[] bytes = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(decision);
        try (var channel = java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.CREATE_NEW,
                java.nio.file.StandardOpenOption.WRITE)) {
            var buffer = java.nio.ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
        } catch (java.nio.file.FileAlreadyExistsException exists) {
            var previous = readSourceReview(registry, evidenceReference);
            if (!previous.equals(decision)) throw new IOException("evidence ID/version already reserved for different immutable content: " + evidenceReference);
        }
        return decision;
    }

    public totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceDecision readSourceReview(
            Path registry, totah.lab.athena.design.generation.MolecularDesignTree.Reference reference) throws IOException {
        try (var input = java.nio.file.Files.newInputStream(reservationPath(registry, reference))) {
            var decision = mapper.readValue(input, totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.SourceDecision.class);
            if (!reference.equals(decision.evidenceReference())) throw new IOException("registry key/content mismatch");
            return decision;
        }
    }

    /** Governed one-source snapshots require every evidence item to match an approved immutable reservation. */
    public totah.lab.athena.design.reasoning.DesignKnowledge readKnowledge(Path path, Path registry) throws IOException {
        var knowledge = readKnowledge(path); validateRegisteredEvidence(knowledge, registry); return knowledge;
    }
    public void writeKnowledge(Path path, totah.lab.athena.design.reasoning.DesignKnowledge knowledge, Path registry) throws IOException {
        validateRegisteredEvidence(knowledge, registry); writeKnowledge(path, knowledge);
    }
    private void validateRegisteredEvidence(totah.lab.athena.design.reasoning.DesignKnowledge knowledge, Path registry) throws IOException {
        for (var evidence : knowledge.evidence()) {
            var registered = readSourceReview(registry, evidence.reference()).evidence();
            if (!evidence.equals(registered)) throw new IOException("knowledge evidence differs from approved immutable reservation: " + evidence.reference());
        }
    }
    private Path reservationPath(Path directory, totah.lab.athena.design.generation.MolecularDesignTree.Reference reference) throws IOException {
        return directory.resolve(totah.lab.aether.provenance.ContentHash.sha256(
                mapper.writeValueAsBytes(java.util.List.of(reference.id(), reference.version()))) + ".json");
    }

    public PredockCandidateEvidence[] readCandidates(Path path) throws IOException {
        return mapper.readValue(path.toFile(), PredockCandidateEvidence[].class);
    }

    public void writeGrammar(Path path, DesignGrammar grammar) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), grammar);
    }

    public void writeEvidence(Path path, Iterable<PredockEvidenceVector> evidence) throws IOException {
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), evidence);
    }
}
