package totah.lab.mettl7.evidence;

import java.util.List;
import java.util.Objects;

/** Immutable, provenance-bearing compound evidence; no inference or scoring is performed here. */
public record Mettl7CompoundEvidence(
        String canonicalIdentity,
        String experimentalA,
        String experimentalB,
        String role,
        String productivity,
        String methylAcceptor,
        String cofactorStateResults,
        String computationalResults,
        String reactionCompetence,
        String mutationalEvidence,
        String currentClassification,
        List<String> supersededClassifications,
        String source,
        List<String> runKeys,
        String nextExperiment) {

    public Mettl7CompoundEvidence {
        Objects.requireNonNull(canonicalIdentity, "canonicalIdentity");
        Objects.requireNonNull(experimentalA, "experimentalA");
        Objects.requireNonNull(experimentalB, "experimentalB");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(productivity, "productivity");
        Objects.requireNonNull(methylAcceptor, "methylAcceptor");
        Objects.requireNonNull(cofactorStateResults, "cofactorStateResults");
        Objects.requireNonNull(computationalResults, "computationalResults");
        Objects.requireNonNull(reactionCompetence, "reactionCompetence");
        Objects.requireNonNull(mutationalEvidence, "mutationalEvidence");
        Objects.requireNonNull(currentClassification, "currentClassification");
        supersededClassifications = List.copyOf(supersededClassifications);
        Objects.requireNonNull(source, "source");
        runKeys = List.copyOf(runKeys);
        Objects.requireNonNull(nextExperiment, "nextExperiment");
    }
}
