package totah.lab.mnemosyne;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.*;

import static totah.lab.mnemosyne.ScientificReference.Kind.*;

/** Bounded, versioned JSON exchange. No catalog, storage engine, domain identity migration or truth inference. */
public final class EvidenceExchange {
    public static final String SCHEMA = "mnemosyne-exchange/1";
    public static final String RECORD_SCHEMA = "mnemosyne-record/1";
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Comparator<ScientificReference> REFERENCES = Comparator
            .comparing((ScientificReference r) -> r.kind().name()).thenComparing(ScientificReference::namespace)
            .thenComparing(ScientificReference::id).thenComparing(ScientificReference::version);
    private final ObjectMapper json;

    public EvidenceExchange() {
        json = JsonMapper.builder(JsonFactory.builder().enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
                .addModule(new Jdk8Module()).addModule(new JavaTimeModule())
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                        DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                .addMixIn(Observation.Uncertainty.class, UncertaintyTags.class).build();
        for (var shape : List.of(CoercionInputShape.Integer, CoercionInputShape.Float, CoercionInputShape.Boolean))
            json.coercionConfigFor(LogicalType.Textual).setCoercion(shape, CoercionAction.Fail);
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "kind")
    @JsonSubTypes({@JsonSubTypes.Type(value = Observation.Unknown.class, name = "UNKNOWN"),
            @JsonSubTypes.Type(value = Observation.Interval.class, name = "REPORTED_INTERVAL"),
            @JsonSubTypes.Type(value = Observation.ReportedError.class, name = "REPORTED_ERROR")})
    private interface UncertaintyTags { }

    public enum RecordType {
        REFERENCE(ScientificReference.class), OBSERVATION(Observation.class), REVIEW(Review.class),
        ASSESSMENT(Assessment.class), REVIEW_CHANGE(EvidenceHistory.ReviewChange.class);
        private final Class<?> javaType;
        RecordType(Class<?> javaType) { this.javaType = javaType; }
        static RecordType of(Object value) {
            return Arrays.stream(values()).filter(t -> t.javaType.isInstance(value)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("unsupported exchange record"));
        }
    }
    public record RecordDigest(ScientificReference reference, RecordType type, String sha256) {
        public RecordDigest { Objects.requireNonNull(reference); Objects.requireNonNull(type); digestText(sha256); }
    }
    /** Membership and integrity only; actual records occur once in the exchange body. */
    public record Manifest(String schema, ScientificReference reference, ScientificReference creationActivity,
                           Instant createdAt, Optional<ScientificReference> parent, List<RecordDigest> records) {
        public Manifest {
            if (!SCHEMA.equals(schema)) throw new IllegalArgumentException("unsupported manifest schema");
            reference.require(SNAPSHOT); creationActivity.require(ACTIVITY); Objects.requireNonNull(createdAt);
            parent = Objects.requireNonNull(parent); parent.ifPresent(p -> p.require(SNAPSHOT));
            if (parent.filter(reference::equals).isPresent()) throw new IllegalArgumentException("self-parent snapshot");
            records = List.copyOf(records);
            if (records.stream().map(RecordDigest::reference).distinct().count() != records.size())
                throw new IllegalArgumentException("duplicate manifest identity");
        }
    }
    public record Snapshot(Manifest manifest, EvidenceHistory history) {
        public Snapshot { Objects.requireNonNull(manifest); Objects.requireNonNull(history); }
        @Override public boolean equals(Object other) {
            return other instanceof Snapshot s && manifest.equals(s.manifest)
                    && history.observations().equals(s.history.observations()) && history.reviews().equals(s.history.reviews())
                    && history.assessments().equals(s.history.assessments()) && history.changes().equals(s.history.changes());
        }
        @Override public int hashCode() {
            return Objects.hash(manifest, history.observations(), history.reviews(), history.assessments(), history.changes());
        }
    }

    public Snapshot snapshot(ScientificReference id, ScientificReference activity, Instant createdAt,
                             Optional<ScientificReference> parent, EvidenceHistory history) throws IOException {
        var records = records(history);
        var entries = new ArrayList<RecordDigest>();
        for (var record : records) entries.add(new RecordDigest(reference(record), RecordType.of(record), sha256(encodeRecord(record))));
        var snapshot = new Snapshot(new Manifest(SCHEMA, id, activity, createdAt, parent, entries), history);
        verify(snapshot);
        return snapshot;
    }

    /** Canonical UTF-8 JSON, sorted object keys; list order and scalar spelling are preserved. */
    public byte[] encodeRecord(Object record) throws IOException {
        var wrapper = json.createObjectNode(); wrapper.put("schema", RECORD_SCHEMA);
        wrapper.put("type", RecordType.of(record).name()); wrapper.set("data", json.valueToTree(record));
        byte[] bytes = canonical(wrapper);
        if (bytes.length > MAX_BYTES) throw new IOException("bounded record exceeds byte limit");
        return bytes;
    }
    public Object decodeRecord(byte[] bytes) throws IOException { return decodeRecord(bytes, Optional.empty()); }
    public Object decodeRecord(byte[] bytes, Optional<String> expectedContentDigest) throws IOException {
        var tree = parse(bytes);
        var record = readRecord(tree);
        if (expectedContentDigest.isPresent() && !sha256(encodeRecord(record)).equals(expectedContentDigest.orElseThrow()))
            throw new IOException("record content digest mismatch");
        return record;
    }
    public String contentDigest(Object record) throws IOException { return sha256(encodeRecord(record)); }

    public byte[] encode(Snapshot snapshot) throws IOException {
        verify(snapshot);
        var root = json.createObjectNode(); root.put("schema", SCHEMA);
        var manifest = json.valueToTree(snapshot.manifest()); root.set("manifest", manifest);
        root.put("manifestSha256", sha256(canonical(manifest)));
        var records = root.putArray("records");
        for (var record : records(snapshot.history())) records.add(json.readTree(encodeRecord(record)));
        byte[] bytes = canonical(root);
        if (bytes.length > MAX_BYTES) throw new IOException("bounded exchange exceeds byte limit");
        return bytes;
    }
    public Snapshot decode(byte[] bytes) throws IOException {
        try {
            var root = parse(bytes); fields(root, "schema", "manifest", "manifestSha256", "records");
            if (!SCHEMA.equals(text(root, "schema"))) throw new IOException("unsupported exchange schema");
            if (!sha256(canonical(root.get("manifest"))).equals(text(root, "manifestSha256")))
                throw new IOException("manifest digest mismatch");
            text(root.get("manifest"), "createdAt");
            var manifest = json.treeToValue(root.get("manifest"), Manifest.class);
            if (!root.get("records").isArray()) throw new IOException("records must be an array");
            var records = new ArrayList<Object>();
            for (var node : root.get("records")) records.add(readRecord(node));
            var snapshot = new Snapshot(manifest, restore(records));
            verify(snapshot);
            if (records.size() != manifest.records().size()) throw new IOException("duplicate or unlisted record");
            return snapshot;
        } catch (IllegalArgumentException | NullPointerException error) {
            throw new IOException("invalid exchange", error);
        }
    }
    public void write(Path path, Snapshot snapshot) throws IOException {
        Files.write(path, encode(snapshot), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }
    public Snapshot read(Path path) throws IOException {
        if (Files.size(path) > MAX_BYTES) throw new IOException("bounded exchange exceeds byte limit");
        try (var input = Files.newInputStream(path)) { return decode(input.readNBytes(MAX_BYTES + 1)); }
    }

    private void verify(Snapshot snapshot) throws IOException {
        var records = records(snapshot.history());
        var actual = new ArrayList<RecordDigest>();
        for (var record : records) actual.add(new RecordDigest(reference(record), RecordType.of(record), contentDigest(record)));
        if (!snapshot.manifest().records().equals(actual)) throw new IOException("manifest membership/content mismatch");
        // Validate a self-contained known-time view, including every scoped relationship, without repairing it.
        restore(records);
        for (var review : snapshot.history().reviews().values())
            if (review.recordedAt().isAfter(snapshot.manifest().createdAt())) throw new IOException("snapshot predates contained review");
        for (var assessment : snapshot.history().assessments().values())
            if (assessment.recordedAt().isAfter(snapshot.manifest().createdAt())) throw new IOException("snapshot predates contained assessment");
        for (var change : snapshot.history().changes().values())
            if (change.recordedAt().isAfter(snapshot.manifest().createdAt())) throw new IOException("snapshot predates contained change");
    }
    private EvidenceHistory restore(List<Object> records) throws IOException {
        try {
            var ids = new HashSet<ScientificReference>();
            for (var record : records) if (!ids.add(reference(record))) throw new IllegalArgumentException("duplicate record identity");
            var history = new EvidenceHistory();
            for (var r : records) if (r instanceof Observation o) history = history.append(o);
            for (var r : records) if (r instanceof Review review) history = history.append(review);
            // All changes first: admissibility checks use their explicit known/effective times, not file order.
            for (var r : records) if (r instanceof EvidenceHistory.ReviewChange change) history = history.append(change);
            for (var r : records) if (r instanceof Assessment assessment) history = history.append(assessment);
            return history;
        } catch (IllegalArgumentException | NullPointerException error) { throw new IOException("inconsistent self-contained history", error); }
    }
    private Object readRecord(JsonNode wrapper) throws IOException {
        fields(wrapper, "schema", "type", "data");
        if (!RECORD_SCHEMA.equals(text(wrapper, "schema"))) throw new IOException("unsupported record schema");
        if (!wrapper.get("data").isObject()) throw new IOException("record data object required");
        try {
            var type = RecordType.valueOf(text(wrapper, "type"));
            var data = wrapper.get("data");
            if (type == RecordType.REVIEW) { text(data, "reviewedAt"); text(data, "recordedAt"); }
            if (type == RecordType.ASSESSMENT) text(data, "recordedAt");
            if (type == RecordType.REVIEW_CHANGE) { text(data, "effectiveAt"); text(data, "recordedAt"); }
            return json.treeToValue(data, type.javaType);
        }
        catch (IllegalArgumentException error) { throw new IOException("unsupported or invalid record", error); }
    }
    private static List<Object> records(EvidenceHistory history) {
        var result = new ArrayList<Object>(); result.addAll(history.observations().values()); result.addAll(history.reviews().values());
        result.addAll(history.assessments().values()); result.addAll(history.changes().values());
        result.sort(Comparator.comparing(EvidenceExchange::reference, REFERENCES)); return List.copyOf(result);
    }
    private static ScientificReference reference(Object record) {
        return switch (record) {
            case Observation o -> o.reference(); case Review r -> r.reference(); case Assessment a -> a.reference();
            case EvidenceHistory.ReviewChange c -> c.reference();
            default -> throw new IllegalArgumentException("only history records may be contained in snapshots");
        };
    }
    private JsonNode parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_BYTES) throw new IOException("bounded exchange exceeds byte limit");
        return json.readTree(bytes);
    }
    private byte[] canonical(JsonNode node) throws IOException { return json.writeValueAsBytes(sorted(node)); }
    private JsonNode sorted(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = json.createObjectNode(); var names = new TreeSet<String>(); node.fieldNames().forEachRemaining(names::add);
            for (var name : names) sorted.set(name, sorted(node.get(name))); return sorted;
        }
        if (node.isArray()) { ArrayNode sorted = json.createArrayNode(); node.forEach(n -> sorted.add(sorted(n))); return sorted; }
        return node;
    }
    private static String text(JsonNode node, String field) throws IOException {
        if (node.get(field) == null || !node.get(field).isTextual()) throw new IOException("text field required: " + field);
        return node.get(field).textValue();
    }
    private static void fields(JsonNode node, String... required) throws IOException {
        if (node == null || !node.isObject()) throw new IOException("object required");
        var names = new HashSet<String>(); node.fieldNames().forEachRemaining(names::add);
        if (!names.equals(Set.of(required))) throw new IOException("unexpected or missing exchange fields");
    }
    /** JDK SHA-256 over supplied bytes. No dependency on a scientific domain is justified for this primitive. */
    public static String sha256(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static void digestText(String value) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SHA-256 required");
    }
}
