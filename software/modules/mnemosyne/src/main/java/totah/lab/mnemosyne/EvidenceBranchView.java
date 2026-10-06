package totah.lab.mnemosyne;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Read-only view of one explicitly selected snapshot's ancestry, not a selection of scientific truth. */
public final class EvidenceBranchView {
    public static final String METHOD = "mnemosyne-branch-evidence-view/1";
    private static final Comparator<ScientificReference> REFERENCES = Comparator
            .comparing((ScientificReference r) -> r.kind().name()).thenComparing(ScientificReference::namespace)
            .thenComparing(ScientificReference::id).thenComparing(ScientificReference::version);

    /** Admissibility is evaluated by EvidenceHistory at the selected snapshot's timestamp. */
    public record ReviewState(Review record, boolean admissible, Optional<EvidenceHistory.ReviewChange> change) {
        public ReviewState { Objects.requireNonNull(record); change = Objects.requireNonNull(change); }
    }
    /** First inclusion on this path, not a claim about when or where a scientific observation originated. */
    public record RecordProvenance(EvidenceExchange.RecordDigest record, EvidenceAdmission.Pin firstIncludedIn) {
        public RecordProvenance { Objects.requireNonNull(record); Objects.requireNonNull(firstIncludedIn); }
    }
    /** Withdrawal annotates historical metadata, without preferring a replacement or hiding a disagreement. */
    public record DescriptionState(DiscoveryDescription record, Optional<DiscoveryDescription.Withdrawal> withdrawal) {
        public DescriptionState { Objects.requireNonNull(record); withdrawal = Objects.requireNonNull(withdrawal); }
    }
    private final List<EvidenceEnvelope> envelopes;
    private final List<EvidenceInterpretation> interpretations;
    private final List<DescriptionState> descriptions;
    private final List<DiscoveryDescription.Withdrawal> withdrawals;
    private final EvidenceAdmission.Pin selected;
    private final Instant asOf;
    private final List<EvidenceAdmission.Pin> path;
    private final List<Observation> observations;
    private final List<ReviewState> reviews;
    private final List<Assessment> assessments;
    private final List<EvidenceHistory.ReviewChange> changes;
    private final List<RecordProvenance> provenance;

    private EvidenceBranchView(EvidenceAdmission.Pin selected, EvidenceExchange.Snapshot snapshot,
                               List<EvidenceAdmission.Pin> path, List<RecordProvenance> provenance) {
        this.selected = selected;
        asOf = snapshot.manifest().createdAt();
        this.path = List.copyOf(path);
        this.provenance = List.copyOf(provenance);
        var history = snapshot.history();
        envelopes = history.envelopes().values().stream().sorted(Comparator.comparing(EvidenceEnvelope::reference, REFERENCES)).toList();
        interpretations = history.interpretations().values().stream().sorted(Comparator.comparing(EvidenceInterpretation::reference, REFERENCES)).toList();
        withdrawals = history.withdrawals().values().stream()
                .sorted(Comparator.comparing(DiscoveryDescription.Withdrawal::reference, REFERENCES)).toList();
        var withdrawalByDescription = new HashMap<ScientificReference, DiscoveryDescription.Withdrawal>();
        withdrawals.forEach(w -> withdrawalByDescription.put(w.description(), w));
        descriptions = history.descriptions().values().stream().sorted(Comparator.comparing(DiscoveryDescription::reference, REFERENCES))
                .map(d -> new DescriptionState(d, Optional.ofNullable(withdrawalByDescription.get(d.reference())))).toList();
        observations = history.observations().values().stream().sorted(Comparator.comparing(Observation::reference, REFERENCES)).toList();
        changes = history.changes().values().stream().sorted(Comparator.comparing(EvidenceHistory.ReviewChange::reference, REFERENCES)).toList();
        var changeByReview = new HashMap<ScientificReference, EvidenceHistory.ReviewChange>();
        changes.forEach(c -> changeByReview.put(c.review(), c));
        reviews = history.reviews().values().stream().sorted(Comparator.comparing(Review::reference, REFERENCES))
                .map(r -> new ReviewState(r, history.admissible(r.reference(), asOf, asOf), Optional.ofNullable(changeByReview.get(r.reference()))))
                .toList();
        // Withdrawals never erase or reinterpret an earlier attributed assessment.
        assessments = history.assessments().values().stream().sorted(Comparator.comparing(Assessment::reference, REFERENCES)).toList();
    }

    /**
     * Validates the complete catalog through EvidenceLineage, then reads only the chosen
     * root-to-snapshot path to build state/provenance. Invalid lineage produces no partial view.
     */
    public static EvidenceBranchView load(EvidenceSnapshotCatalog catalog, ScientificReference selected) throws IOException {
        Objects.requireNonNull(catalog);
        Objects.requireNonNull(selected).require(ScientificReference.Kind.SNAPSHOT);
        var lineage = EvidenceLineage.load(catalog);
        var ancestors = lineage.ancestry(selected); // Also rejects an unknown explicit selection.
        var candidates = ancestors.isEmpty() ? lineage.roots() : lineage.children(ancestors.getFirst().pin().reference());
        var target = candidates.stream().filter(e -> e.pin().reference().equals(selected)).findFirst()
                .orElseThrow(() -> new IOException("SELECTED_SNAPSHOT_MISSING_FROM_LINEAGE"));
        var entries = new ArrayList<>(ancestors.reversed()); entries.add(target);
        var path = new ArrayList<EvidenceAdmission.Pin>();
        var firstInclusion = new HashMap<ScientificReference, RecordProvenance>();
        EvidenceExchange.Snapshot last = null;
        Optional<ScientificReference> expectedParent = Optional.empty();
        for (var entry : entries) {
            var snapshot = catalog.read(entry.pin())
                    .orElseThrow(() -> new IOException("BRANCH_SNAPSHOT_DISAPPEARED: " + entry.pin().reference()));
            if (!snapshot.manifest().parent().equals(expectedParent)) throw new IOException("BRANCH_LINEAGE_CHANGED");
            for (var record : snapshot.manifest().records()) {
                var prior = firstInclusion.putIfAbsent(record.reference(), new RecordProvenance(record, entry.pin()));
                if (prior != null && !prior.record().equals(record)) throw new IOException("BRANCH_RECORD_CHANGED: " + record.reference());
            }
            // The pinned full snapshot already contains all inherited records: do not
            // replay them in a new temporal order or merge records from other snapshots.
            if (firstInclusion.size() != snapshot.manifest().records().size()) throw new IOException("BRANCH_RECORD_MISSING");
            path.add(entry.pin()); last = snapshot;
            expectedParent = Optional.of(entry.pin().reference());
        }
        return new EvidenceBranchView(target.pin(), Objects.requireNonNull(last), path,
                firstInclusion.values().stream().sorted(Comparator.comparing(p -> p.record().reference(), REFERENCES)).toList());
    }
    public List<EvidenceEnvelope> envelopes() { return envelopes; }
    public List<EvidenceInterpretation> interpretations() { return interpretations; }
    public List<DescriptionState> descriptions() { return descriptions; }
    public List<DiscoveryDescription.Withdrawal> withdrawals() { return withdrawals; }
    public EvidenceAdmission.Pin selected() { return selected; }
    /** Both known-time and effective-time for review admissibility; no wall clock is consulted. */
    public Instant asOf() { return asOf; }
    /** Includes root and selected snapshot in chronological lineage order, not timestamp ranking. */
    public List<EvidenceAdmission.Pin> path() { return path; }
    public List<Observation> observations() { return observations; }
    public List<ReviewState> reviews() { return reviews; }
    public List<Assessment> assessments() { return assessments; }
    public List<EvidenceHistory.ReviewChange> changes() { return changes; }
    public List<RecordProvenance> provenance() { return provenance; }
}
