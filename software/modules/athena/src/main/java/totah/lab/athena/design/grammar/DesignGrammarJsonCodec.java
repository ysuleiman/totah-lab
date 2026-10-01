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
