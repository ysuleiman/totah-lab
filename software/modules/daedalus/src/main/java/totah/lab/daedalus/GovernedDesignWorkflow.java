package totah.lab.daedalus;

import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.athena.design.backend.CanonicalIdentityService;
import totah.lab.athena.design.backend.MolecularBackendException;
import totah.lab.athena.design.generation.MolecularDesignGraphGenerator.AuthorizedEdit;
import totah.lab.athena.design.generation.MolecularDesignTree.*;
import totah.lab.athena.design.grammar.DesignGrammarJsonCodec;
import totah.lab.athena.design.reasoning.DesignKnowledge;
import totah.lab.athena.design.reasoning.HypothesisDirectedPlanner;
import totah.lab.hermes.pubchem.PubChemMolecularWeightImporter;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

import static totah.lab.athena.design.reasoning.DesignKnowledge.*;
import static totah.lab.athena.design.reasoning.ReviewedEvidenceAdapters.*;

/** Factory-owned host protocol. No docking, preparation, chemistry search or candidate execution. */
public final class GovernedDesignWorkflow implements AutoCloseable {
    private final Path registry, runDirectory;
    private final CanonicalIdentityService identity;
    private final DesignGrammarJsonCodec codec = new DesignGrammarJsonCodec();
    private final PubChemMolecularWeightImporter importer = new PubChemMolecularWeightImporter();
    private final ObjectMapper json = new ObjectMapper();
    private final Journal journal;
    private int imported;

    GovernedDesignWorkflow(Path workspace, Path runDirectory, CanonicalIdentityService identity) throws IOException {
        this.registry = workspace.toAbsolutePath().normalize().resolve("design-evidence-registry");
        this.runDirectory = runDirectory.toAbsolutePath().normalize();
        this.identity = Objects.requireNonNull(identity);
        Files.createDirectories(registry);
        journal = new Journal(this.runDirectory.resolve("reasoning.jsonl"));
    }
    public Path runDirectory() { return runDirectory; }
    public Path registryDirectory() { return registry; }

    /** Review callback cannot run before the importer has verified its raw-to-projection mapping. */
    public SourceDecision reviewPubChem(Path rawProperty, Path rawStructure, Instant importedAt,
                                        Reference evidence, Function<SourceReceipt, SourceReview> reviewer) throws IOException {
        try {
            var path = runDirectory.resolve("source-" + imported++ + ".json");
            importer.importRecord(rawProperty, path, importedAt);
            var verified = importer.readVerified(path);
            var receipt = json.treeToValue(verified, SourceReceipt.class);
            if (!receipt.errors().isEmpty()) throw new IOException("source parse errors: " + receipt.errors());
            var structure = importer.structure(rawStructure, receipt.identifier());
            var review = Objects.requireNonNull(reviewer.apply(receipt));
            if (review.subject() == null) throw new IOException("review lacks molecular subject");
            var graph = identity.decodeStructure("SMILES", structure.path("smiles").asText());
            var association = new SourceAssociation(structure.path("rawBase64").asText(), structure.path("sha256").asText(),
                    structure.path("uri").asText(), structure.path("parser").asText(), graph,
                    identity.associate(graph, review.subject().graph()));
            var decision = codec.registerSourceReview(registry, receipt, review, evidence, association);
            journal.sourceReview(decision); // Durability precedes release to callers.
            return decision;
        } catch (MolecularBackendException | RuntimeException error) {
            journal.protocolFailure("reviewPubChem", error.toString()); throw new IOException("subject certification failed", error);
        } catch (IOException error) {
            journal.protocolFailure("reviewPubChem", error.toString()); throw error;
        }
    }

    /** Domain owners supply a reviewed interpretation; this host does not implement or strengthen domain science. */
    public void registerInterpretation(Interpretation interpretation) throws IOException {
        codec.registerInterpretation(registry, interpretation); journal.interpretation(interpretation);
    }

    public Path saveKnowledge(String filename, DesignKnowledge knowledge) throws IOException {
        var path = output(filename);
        verifyAndJournal(knowledge); codec.writeKnowledge(path, knowledge, registry); return path;
    }
    public List<AuthorizedEdit> plan(Path snapshot, List<Reference> hypotheses, Set<EvidenceKind> kinds, DesignState parent) throws IOException {
        var knowledge = load(snapshot);
        return new HypothesisDirectedPlanner(knowledge, hypotheses, kinds, journal::planningDecision, identity).editsFor(parent);
    }
    public Path evaluate(Path snapshot, String output, Reference nextSnapshot, Reference evaluation, Reference hypothesis,
                         String runId, Attempt attempt, Reference evaluator, Map<String, Reference> observations) throws IOException {
        var knowledge = load(snapshot);
        var result = knowledge.evaluate(evaluation, hypothesis, runId, attempt, evaluator, observations);
        return saveKnowledge(output, knowledge.withEvaluation(nextSnapshot, result));
    }
    public Path revise(Path snapshot, String output, Reference nextSnapshot, Reference previous, String version,
                       Reference observation, String claim, String consequence, Prediction prediction, String limitations) throws IOException {
        return saveKnowledge(output, load(snapshot).revise(nextSnapshot, previous, version, observation, claim, consequence, prediction, limitations));
    }
    public DesignKnowledge load(Path snapshot) throws IOException {
        try {
            var knowledge = codec.readKnowledge(snapshot, registry); verifyAndJournal(knowledge); return knowledge;
        } catch (IOException error) { journal.protocolFailure("loadKnowledge", error.toString()); throw error; }
    }
    private void verifyAndJournal(DesignKnowledge knowledge) throws IOException {
        if (!QUALIFIED_SCHEMA.equals(knowledge.schema())) throw new IOException("production reasoning requires qualified knowledge");
        for (var evidence : knowledge.evidence()) {
            if (codec.isSourceReview(registry, evidence.reference())) {
                var review = codec.readSourceReview(registry, evidence.reference());
                if (!evidence.equals(review.evidence()) || review.association() == null || !review.association().proof().proven())
                    throw new IOException("approved, structurally certified source review required");
                importer.verifyReceipt(json.valueToTree(review.receipt()));
                var stored = review.association();
                var source = importer.verifyStructure(Base64.getDecoder().decode(stored.rawBase64()), review.receipt().identifier());
                try {
                    var graph = identity.decodeStructure("SMILES", source.path("smiles").asText());
                    var proof = identity.associate(graph, evidence.qualification().subject().graph());
                    if (!source.path("uri").asText().equals(stored.uri()) || !source.path("parser").asText().equals(stored.parser())
                            || !graph.equals(stored.graph()) || !proof.proven() || !proof.equals(stored.proof()))
                        throw new IOException("stored molecular association cannot be reproduced by this backend");
                } catch (MolecularBackendException error) { throw new IOException("subject certification unavailable", error); }
                journal.sourceReview(review);
            } else {
                var interpretation = codec.readInterpretation(registry, evidence.reference());
                if (!evidence.equals(interpretation.evidence())) throw new IOException("interpreted evidence differs from immutable reservation");
                journal.interpretation(interpretation);
            }
        }
        journal.knowledge(knowledge); // Every source/review and complete knowledge precedes planning or evaluation.
    }
    private Path output(String name) throws IOException {
        if (name == null || Path.of(name).isAbsolute() || Path.of(name).getNameCount() != 1 || name.equals(".") || name.equals(".."))
            throw new IOException("one output filename required");
        return runDirectory.resolve(name);
    }
    @Override public void close() throws IOException { journal.close(); }
}
