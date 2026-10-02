package totah.lab.mnemosyne;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Read-only discovery over a freshly validated, explicitly selected Phase 7 evidence view. */
public final class EvidenceDiscovery {
    public static final String METHOD = "mnemosyne-evidence-discovery/1";
    private final EvidenceQueries queries;

    public EvidenceDiscovery(EvidenceQueries queries) { this.queries = Objects.requireNonNull(queries); }

    /** Exactly one identity or exact label; role is an optional explicit constraint. */
    public record ParticipantConstraint(Optional<ScientificReference> entity, Optional<String> label,
                                        Optional<DiscoveryDescription.Term> role) {
        public ParticipantConstraint {
            entity = Objects.requireNonNull(entity); label = Objects.requireNonNull(label); role = Objects.requireNonNull(role);
            if (entity.isPresent() == label.isPresent()) throw new IllegalArgumentException("choose identity or label");
            entity.ifPresent(e -> e.require(ScientificReference.Kind.SUBJECT)); label.ifPresent(ScientificReference::text);
        }
    }
    public record Query(List<ParticipantConstraint> participants, Optional<DiscoveryDescription.Term> relationship,
                        Optional<DiscoveryDescription.Modality> modality, Optional<ScientificReference> endpoint) {
        public Query {
            participants = List.copyOf(participants);
            if (participants.isEmpty()) throw new IllegalArgumentException("participant constraints required");
            relationship = Objects.requireNonNull(relationship); modality = Objects.requireNonNull(modality);
            endpoint = Objects.requireNonNull(endpoint); endpoint.ifPresent(e -> e.require(ScientificReference.Kind.ENDPOINT));
        }
    }
    public record Candidate(ScientificReference entity, List<ScientificReference> descriptions) {
        public Candidate { Objects.requireNonNull(entity); descriptions = List.copyOf(descriptions); }
    }
    /** All supporting description identities are retained, including withdrawn descriptions. */
    public record NameResolution(String label, Optional<DiscoveryDescription.Term> role, List<Candidate> candidates) {
        public NameResolution { ScientificReference.text(label); role = Objects.requireNonNull(role); candidates = List.copyOf(candidates); }
    }
    public record Match(Observation observation, List<EvidenceBranchView.DescriptionState> descriptions) {
        public Match { Objects.requireNonNull(observation); descriptions = List.copyOf(descriptions); }
    }
    public enum Status { MATCHES, NO_MATCH_IN_SELECTED_SNAPSHOT, AMBIGUOUS_NAME }
    public enum Limitation {
        SELECTED_BRANCH_ONLY, PARTIAL_DISCOVERY_COVERAGE, UNKNOWN_MODALITY_PRESENT,
        SOURCE_AVAILABILITY_NOT_CHECKED, ASSAY_DETAILS_NOT_MODELED, EXPERIMENT_EVENT_TIME_NOT_MODELED
    }
    /** The full immutable branch view retains reviews, changes, assessments, exact pins and first inclusion. */
    public record Result(String method, Query query, EvidenceBranchView evidence, Status status, List<Match> matches,
                         List<NameResolution> names, List<ScientificReference> observationsWithoutDescription,
                         List<ScientificReference> descriptionsWithUnknownModality, List<Limitation> limitations) {
        public Result {
            if (!METHOD.equals(method)) throw new IllegalArgumentException("unsupported discovery method");
            Objects.requireNonNull(query); Objects.requireNonNull(evidence); Objects.requireNonNull(status);
            matches = List.copyOf(matches); names = List.copyOf(names);
            observationsWithoutDescription = List.copyOf(observationsWithoutDescription);
            descriptionsWithUnknownModality = List.copyOf(descriptionsWithUnknownModality); limitations = List.copyOf(limitations);
        }
    }

    /** No persistent index, name inference, branch preference, source scan, or scientific resolution. */
    public Result find(ScientificReference snapshot, Query query) throws IOException {
        Objects.requireNonNull(query);
        var view = queries.evidence(snapshot);
        var names = new ArrayList<NameResolution>();
        boolean ambiguous = false;
        boolean unresolved = false;
        var identities = new ArrayList<ScientificReference>();
        for (var constraint : query.participants()) {
            if (constraint.entity().isPresent()) { identities.add(constraint.entity().orElseThrow()); continue; }
            var label = constraint.label().orElseThrow();
            var candidates = new TreeMap<ScientificReference, List<ScientificReference>>(DiscoveryDescription.REFERENCES);
            for (var state : view.descriptions()) for (var participant : state.record().participants()) {
                if (participant.labels().contains(label) && constraint.role().map(participant.role()::equals).orElse(true))
                    candidates.computeIfAbsent(participant.entity(), ignored -> new ArrayList<>()).add(state.record().reference());
            }
            names.add(new NameResolution(label, constraint.role(), candidates.entrySet().stream()
                    .map(e -> new Candidate(e.getKey(), e.getValue().stream().distinct().sorted(DiscoveryDescription.REFERENCES).toList())).toList()));
            ambiguous |= candidates.size() > 1; unresolved |= candidates.isEmpty();
            // Placeholder never reaches matching when resolution is absent or ambiguous.
            identities.add(candidates.size() == 1 ? candidates.firstKey() : null);
        }
        var matches = new ArrayList<Match>();
        if (!ambiguous && !unresolved) for (var observation : view.observations()) {
            if (query.endpoint().isPresent() && !query.endpoint().orElseThrow().equals(observation.endpoint())) continue;
            var descriptions = view.descriptions().stream().filter(s -> s.record().observation().equals(observation.reference()))
                    .filter(s -> matches(s.record(), query, identities)).toList();
            if (!descriptions.isEmpty()) matches.add(new Match(observation, descriptions));
        }
        var described = view.descriptions().stream().map(s -> s.record().observation()).collect(java.util.stream.Collectors.toSet());
        var uncovered = view.observations().stream().map(Observation::reference).filter(r -> !described.contains(r)).toList();
        var unknown = view.descriptions().stream().map(EvidenceBranchView.DescriptionState::record)
                .filter(d -> d.modality() == DiscoveryDescription.Modality.UNKNOWN).map(DiscoveryDescription::reference).toList();
        var limitations = new ArrayList<>(List.of(Limitation.SELECTED_BRANCH_ONLY, Limitation.SOURCE_AVAILABILITY_NOT_CHECKED,
                Limitation.ASSAY_DETAILS_NOT_MODELED, Limitation.EXPERIMENT_EVENT_TIME_NOT_MODELED));
        if (!uncovered.isEmpty()) limitations.add(Limitation.PARTIAL_DISCOVERY_COVERAGE);
        if (!unknown.isEmpty()) limitations.add(Limitation.UNKNOWN_MODALITY_PRESENT);
        limitations.sort(java.util.Comparator.naturalOrder());
        return new Result(METHOD, query, view, ambiguous ? Status.AMBIGUOUS_NAME
                : matches.isEmpty() ? Status.NO_MATCH_IN_SELECTED_SNAPSHOT : Status.MATCHES,
                matches, names, uncovered, unknown, limitations);
    }
    private static boolean matches(DiscoveryDescription description, Query query, List<ScientificReference> identities) {
        if (query.relationship().isPresent() && !query.relationship().orElseThrow().equals(description.relationship())) return false;
        if (query.modality().isPresent() && query.modality().orElseThrow() != description.modality()) return false;
        for (int i = 0; i < identities.size(); i++) {
            var entity = identities.get(i); var constraint = query.participants().get(i);
            if (description.participants().stream().noneMatch(p -> p.entity().equals(entity)
                    && constraint.role().map(p.role()::equals).orElse(true)
                    && constraint.label().map(p.labels()::contains).orElse(true))) return false;
        }
        return true;
    }
}
