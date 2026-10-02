package totah.lab.mnemosyne;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Attributed discovery metadata bound to one exact observation; never an inferred relationship. */
public record DiscoveryDescription(ScientificReference reference, ScientificReference observation,
                                   String observationSha256, List<Participant> participants, Term relationship,
                                   Modality modality, ScientificReference agent, Observation.Provenance provenance,
                                   Instant recordedAt) {
    public enum Modality { EXPERIMENTAL, COMPUTATIONAL, INFERRED, SYNTHETIC, UNKNOWN }
    /** Opaque versioned vocabulary term. This layer assigns no scientific meaning to it. */
    public record Term(String namespace, String id, String version) {
        public Term { ScientificReference.text(namespace); ScientificReference.text(id); ScientificReference.text(version); }
    }
    /** Labels are exact, attributed spellings, not a global synonym or identity registry. */
    public record Participant(ScientificReference entity, Term role, List<String> labels) {
        public Participant {
            entity.require(SUBJECT); Objects.requireNonNull(role);
            labels = List.copyOf(labels); labels.forEach(ScientificReference::text);
            if (labels.stream().distinct().count() != labels.size()) throw new IllegalArgumentException("duplicate label");
            labels = labels.stream().sorted().toList();
        }
    }
    static final Comparator<ScientificReference> REFERENCES = Comparator
            .comparing((ScientificReference r) -> r.kind().name()).thenComparing(ScientificReference::namespace)
            .thenComparing(ScientificReference::id).thenComparing(ScientificReference::version);
    private static final Comparator<Term> TERMS = Comparator.comparing(Term::namespace).thenComparing(Term::id).thenComparing(Term::version);

    public DiscoveryDescription {
        reference.require(DISCOVERY_DESCRIPTION); observation.require(OBSERVATION);
        if (observationSha256 == null || !observationSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("observation SHA-256 required");
        participants = List.copyOf(participants);
        if (participants.isEmpty()) throw new IllegalArgumentException("participants required");
        if (participants.stream().map(p -> List.of(p.entity(), p.role())).distinct().count() != participants.size())
            throw new IllegalArgumentException("duplicate entity/role");
        participants = participants.stream().sorted(Comparator.comparing(Participant::entity, REFERENCES)
                .thenComparing(Participant::role, TERMS)).toList();
        Objects.requireNonNull(relationship); Objects.requireNonNull(modality); agent.require(AGENT);
        Objects.requireNonNull(provenance); Objects.requireNonNull(recordedAt);
    }

    /** Append-only attributed withdrawal; optional replacement is explicit and never deletes history. */
    public record Withdrawal(ScientificReference reference, ScientificReference description,
                             Optional<ScientificReference> replacement, ScientificReference agent,
                             Observation.Provenance provenance, String reason, Instant recordedAt) {
        public Withdrawal {
            reference.require(DISCOVERY_WITHDRAWAL); description.require(DISCOVERY_DESCRIPTION);
            replacement = Objects.requireNonNull(replacement); replacement.ifPresent(r -> r.require(DISCOVERY_DESCRIPTION));
            if (replacement.filter(description::equals).isPresent()) throw new IllegalArgumentException("self replacement");
            agent.require(AGENT); Objects.requireNonNull(provenance); ScientificReference.text(reason); Objects.requireNonNull(recordedAt);
        }
    }
}
