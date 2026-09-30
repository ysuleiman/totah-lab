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
