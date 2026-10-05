package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import totah.lab.athena.system.*;
import totah.lab.gaia.geometry.*;
import totah.lab.gaia.structure.*;
import totah.lab.gaia.chemistry.Element;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Explicit raw measurements over the authoritative state; no contact or favorability classifier. */
final class ContinuousGeometryRules {
    private ContinuousGeometryRules() { }
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT);
    private static final Comparator<AtomReference> ORDER = Comparator.naturalOrder();
    private static final String PLAN = "athena:continuous-geometry-plan";
    private static final String RADII = "athena:radius-assignments";
    private static final String REPORT = "athena:rule-measurements";

    static void validate(RuleManifest m) {
        if (!Set.of("athena-rule/2", "athena-rule/3").contains(m.schema())
                || !(m.implementationVersion().equals("1") && m.profile().equals("ATHENA_CONTINUOUS_GEOMETRY_V1")
                    || m.implementationVersion().equals("2") && m.profile().equals("ATHENA_CONTINUOUS_GEOMETRY_V2")
                    || m.implementationVersion().equals("3") && m.profile().equals("ATHENA_CONTINUOUS_GEOMETRY_V3"))
                || m.family() != RuleManifest.Family.ENVIRONMENT
                || !m.parameters().keySet().equals(Set.of("planeNormalRelativeGapTolerance")))
            throw new IllegalArgumentException("continuous geometry manifest contract");
        var p = m.parameters().get("planeNormalRelativeGapTolerance");
        if (!p.unit().equals("dimensionless") || Double.parseDouble(p.value()) != 1e-10)
            throw new IllegalArgumentException("unreviewed numerical conditioning policy");
    }

    static SystemGraphAnalyzer analyzer(RuleManifest m, RuleRequest r, boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method() { return methodReference(m, evaluate); }
            public Set<SystemGraphCertificate.Capability> requires() { return Set.of(); }
            public Set<SystemGraphCertificate.Capability> qualifies() { return Set.of(); }
            public Set<String> evidenceTypes() { return Set.of(PLAN, RADII, REPORT, "athena:system-state"); }
            public List<Finding> analyze(SystemStateView state, List<EvidenceEnvelope> inputs, Map<String,String> config) throws Exception {
                if (!state.binding().equals(r.state()) || !m.key().equals(r.manifestKey())
                        || !RuleRegistry.digest(m).equals(r.manifestSha256())) throw new IllegalArgumentException("geometry request binding");
                var planEnvelope = one(inputs, PLAN);
                var plan = JSON.readTree(planEnvelope.readPayload());
                var radiusEnvelopes = inputs.stream().filter(e -> e.evidenceType().equals(RADII)).toList();
                if (radiusEnvelopes.size() > 1) throw new IllegalArgumentException("ambiguous radius source");
                EvidenceEnvelope radiusEnvelope = radiusEnvelopes.isEmpty() ? null : radiusEnvelopes.getFirst();
                var report = build(state, m, r, planEnvelope, plan, radiusEnvelope);
                if (evaluate) {
                    var prior = one(inputs, REPORT);
                    if (!prior.method().equals(methodReference(m, false))
                            || !JSON.readTree(report.toString()).equals(JSON.readTree(prior.readPayload())))
                        throw new IllegalArgumentException("geometry report provenance/content mismatch");
                }
                var status = evaluate && (m.retired() || (!m.schema().equals("athena-rule/3")
                        && m.qualification() != SystemGraphCertificate.Status.QUALIFIED)) ? NOT_EVALUATED : SUPPORTED_PRESENT;
                return List.of(new Finding(evaluate ? "evaluate" : "collect", List.of(RuleAnalyzers.subjects(state, r)), status,
                        Map.of("payload", report.toString()), List.of("raw geometry; quantity-specific coverage; no scientific interaction assertion"), m.limitations()));
            }
        };
    }
    private static ScientificReference methodReference(RuleManifest m, boolean evaluate) {
        return new ScientificReference(ScientificReference.Kind.METHOD, "athena.geometry", m.key() + (evaluate ? "/evaluate" : "/collect"), RuleRegistry.digest(m));
    }
    private static EvidenceEnvelope one(List<EvidenceEnvelope> inputs, String type) {
        var found = inputs.stream().filter(e -> e.evidenceType().equals(type)).toList();
        if (found.size() != 1) throw new IllegalArgumentException("one explicit " + type + " required");
        return found.getFirst();
    }
    private static ObjectNode build(SystemStateView s, RuleManifest m, RuleRequest r, EvidenceEnvelope pe, JsonNode plan, EvidenceEnvelope re) throws Exception {
        fields(plan, "schema", "stateBinding", "coordinateUnit", "coordinateSourceReferences", "groups", "operations", "radiusAssignmentReference", "sourceReferences", "limitations");
        equal(plan, "schema", "athena-continuous-geometry-plan/1"); equal(plan, "coordinateUnit", "ANGSTROM");
        if (!plan.get("stateBinding").equals(node(s.binding()))) throw new IllegalArgumentException("plan state mismatch");
        references(plan.get("coordinateSourceReferences")); references(plan.get("sourceReferences")); strings(plan.get("limitations"));
        if (!plan.path("groups").isArray() || !plan.path("operations").isArray()) throw new IllegalArgumentException("plan arrays required");
        var groups = new TreeMap<String,List<AtomReference>>();
        var selected = new TreeSet<>(ORDER);
        for (var group : plan.get("groups")) {
            fields(group, "id", "atoms", "sourceReferences"); references(group.get("sourceReferences"));
            var atoms = atoms(group.get("atoms"));
            if (atoms.isEmpty() || !atoms.equals(atoms.stream().distinct().sorted(ORDER).toList())
                    || groups.put(text(group, "id"), atoms) != null) throw new IllegalArgumentException("canonical unique nonempty groups required");
            selected.addAll(atoms);
        }
        var ids = new HashSet<String>();
        for (var op : plan.get("operations")) {
            if (!ids.add(text(op, "id"))) throw new IllegalArgumentException("duplicate operation");
            String kind = text(op, "kind");
            switch (kind) {
                case "DISTANCE", "ANGLE", "DIHEDRAL", "VECTOR" -> {
                    fields(op, "id", "kind", "atoms"); var a = atoms(op.get("atoms"));
                    int size = kind.equals("ANGLE") ? 3 : kind.equals("DIHEDRAL") ? 4 : 2;
                    if (a.size() != size) throw new IllegalArgumentException("ordered tuple arity"); selected.addAll(a);
                }
                case "POINT_PAIR_GROUP" -> {
                    if (m.implementationVersion().equals("3")) {
                        fields(op, "id", "kind", "atoms", "groupId");
                        var tuple = atoms(op.get("atoms"));
                        if (tuple.size() != 2 || tuple.get(0).equals(tuple.get(1)))
                            throw new IllegalArgumentException("distinct ordered pair required");
                        selected.addAll(tuple); group(groups, op, "groupId");
                    }
                }
                case "PLANE" -> { fields(op, "id", "kind", "groupId"); group(groups, op, "groupId"); }
                case "POINT_PLANE" -> { fields(op, "id", "kind", "atom", "planeGroupId"); selected.add(atom(op.get("atom"))); group(groups, op, "planeGroupId"); }
                case "PLANE_PAIR", "PAIR_MATRIX", "GROUP_MINIMUM" -> {
                    if (kind.equals("PLANE_PAIR")) fields(op, "id", "kind", "firstGroupId", "secondGroupId");
                    else {
                        if (kind.equals("PAIR_MATRIX")) {
                            fields(op, "id", "kind", "firstGroupId", "secondGroupId", "hydrogenScope", "topologyPolicy");
                            if (!Set.of("RETAIN_ALL", "FLAG_BONDED", "FLAG_BONDED_AND_1_3").contains(text(op, "topologyPolicy"))) throw new IllegalArgumentException("topology policy");
                        } else fields(op, "id", "kind", "firstGroupId", "secondGroupId", "hydrogenScope");
                        if (!Set.of("ALL_EXPLICIT", "HEAVY_ONLY").contains(text(op, "hydrogenScope"))) throw new IllegalArgumentException("hydrogen scope");
                    }
                    group(groups, op, "firstGroupId"); group(groups, op, "secondGroupId");
                }
                default -> { /* Preserve unknown tagged content; never execute its interpretation. */ }
            }
        }
        if (!r.first().isEmpty() || !r.second().isEmpty() || !r.atoms().equals(List.copyOf(selected))
                || !s.atoms().keySet().containsAll(selected)) throw new IllegalArgumentException("exact existing atom selection required");
        var radii = radii(s, plan.get("radiusAssignmentReference"), re);
        var report = JSON.createObjectNode(); report.put("schema", "athena-continuous-geometry/1");
        report.set("stateBinding", node(s.binding())); report.set("planReference", pin(pe)); report.set("plan", plan);
        report.set("radiusAssignmentReference", plan.get("radiusAssignmentReference"));
        report.set("implementation", node(Map.of("id", "athena.geometry", "version", m.implementationVersion(), "sourceReferences", List.of(methodReference(m, false)))));
        var out = report.putArray("operations");
        for (var op : plan.get("operations")) out.add(measure(s, m, r, op, groups, radii, plan.get("coordinateSourceReferences")));
        report.set("sourceReferences", plan.get("sourceReferences")); report.set("limitations", plan.get("limitations"));
        return report;
    }
    private static Map<AtomReference,JsonNode> radii(SystemStateView s, JsonNode expected, EvidenceEnvelope envelope) throws Exception {
        if (expected.isNull()) { if (envelope != null) throw new IllegalArgumentException("unselected radii"); return Map.of(); }
        fields(expected, "reference", "payloadSha256");
        if (envelope == null || !expected.equals(pin(envelope))) throw new IllegalArgumentException("radius evidence pin mismatch");
        var root = JSON.readTree(envelope.readPayload());
        fields(root, "schema", "stateBinding", "unit", "model", "assignments", "sourceReferences", "limitations");
        equal(root, "schema", "athena-radius-assignments/1"); equal(root, "unit", "ANGSTROM");
        if (!root.get("stateBinding").equals(node(s.binding()))) throw new IllegalArgumentException("radius state mismatch");
        fields(root.get("model"), "id", "version", "sourceReferences"); text(root.get("model"), "id"); text(root.get("model"), "version");
        references(root.get("model").get("sourceReferences")); references(root.get("sourceReferences")); strings(root.get("limitations"));
        if (!root.path("assignments").isArray()) throw new IllegalArgumentException("radius array");
        var map = new TreeMap<AtomReference,JsonNode>(ORDER); AtomReference previous = null;
        for (var a : root.get("assignments")) {
            fields(a, "atom", "status", "radius", "sourceReferences", "reasons"); references(a.get("sourceReferences")); strings(a.get("reasons"));
            var ref = atom(a.get("atom"));
            if (!s.atoms().containsKey(ref) || previous != null && ORDER.compare(previous, ref) >= 0) throw new IllegalArgumentException("radius atom order/identity");
            previous = ref; var status = EvidenceInterpretation.Status.valueOf(text(a, "status"));
            if (status == SUPPORTED_PRESENT) { if (decimal(a.get("radius")) <= 0) throw new IllegalArgumentException("positive radius required"); }
            else if (!a.get("radius").isNull()) throw new IllegalArgumentException("unavailable radius must be null");
            map.put(ref, a);
        }
        return map;
    }
    private static ObjectNode measure(SystemStateView s, RuleManifest m, RuleRequest r, JsonNode op,
                                      Map<String,List<AtomReference>> groups, Map<AtomReference,JsonNode> radii, JsonNode sources) throws Exception {
        var result = JSON.createObjectNode(); result.put("id", text(op, "id")); String kind = text(op, "kind"); result.put("kind", kind);
        var subjects = result.putObject("subjects"); subjects.set("operation", op); subjects.set("groups", node(groups));
        var q = result.putObject("quantities"); var c = result.putObject("coverage"); var reasons = result.putArray("reasons");
        for (String n : List.of("requestedSubjects", "evaluatedSubjects", "requestedPairs", "evaluatedPairs", "omittedSelfPairs", "omittedHydrogenPairs")) c.put(n, 0);
        c.put("completeEnumeration", true); c.put("frameQualified", s.frameQualified()); c.put("finiteCoordinates", true); c.put("budgetExceeded", false);
        for (String n : List.of("radiusCoverage", "topologyCoverage", "normalUniquenessStatus")) c.put(n, NOT_EVALUATED.name());
        if (!(Set.of("DISTANCE", "ANGLE", "DIHEDRAL", "VECTOR", "PLANE", "POINT_PLANE", "PLANE_PAIR", "PAIR_MATRIX", "GROUP_MINIMUM").contains(kind)
                || kind.equals("POINT_PAIR_GROUP") && m.implementationVersion().equals("3"))) {
            c.put("completeEnumeration", false);
            quantity(q, "measurement", null, "NONE", UNSUPPORTED, sources, "unknown operation kind; original plan preserved");
            return result;
        }
        List<AtomReference> tuple = op.has("atoms") ? atoms(op.get("atoms")) : List.of();
        var required = new TreeSet<>(ORDER); required.addAll(tuple);
        for (String n : List.of("groupId", "planeGroupId", "firstGroupId", "secondGroupId")) if (op.has(n)) required.addAll(group(groups, op, n));
        if (op.has("atom")) required.add(atom(op.get("atom")));
        c.put("requestedSubjects", required.size());
        if (required.size() > r.maximumNodes()) {
            c.put("completeEnumeration", false); c.put("budgetExceeded", true); reasons.add("explicit subject scope exceeds node budget; no partial tuple interpretation");
            quantity(q, "measurement", null, "NONE", UNKNOWN_INCONCLUSIVE, sources, "node budget"); return result;
        }
        c.put("evaluatedSubjects", required.size());
        try {
            switch (kind) {
                case "DISTANCE" -> quantity(q, "distanceAngstrom", point(s, tuple.get(0)).distance(point(s, tuple.get(1))), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                case "ANGLE" -> quantity(q, "angleDegrees", Math.toDegrees(point(s, tuple.get(1)).vectorTo(point(s, tuple.get(0))).angle(point(s, tuple.get(1)).vectorTo(point(s, tuple.get(2))))) , "DEGREE", SUPPORTED_PRESENT, sources, "");
                case "DIHEDRAL" -> quantity(q, "torsionDegrees", Dihedral.measureDegrees(point(s, tuple.get(0)), point(s, tuple.get(1)), point(s, tuple.get(2)), point(s, tuple.get(3))), "DEGREE", SUPPORTED_PRESENT, sources, "");
                case "VECTOR" -> {
                    var v = point(s, tuple.get(0)).vectorTo(point(s, tuple.get(1)));
                    quantity(q, "displacementAngstrom", vector(v), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                    quantity(q, "lengthAngstrom", v.magnitude(), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                    if (v.magnitude() < 1e-12) quantity(q, "unitVector", null, "DIMENSIONLESS", UNKNOWN_INCONCLUSIVE, sources, "Gaia vector normalization undefined");
                    else quantity(q, "unitVector", vector(v.normalize()), "DIMENSIONLESS", SUPPORTED_PRESENT, sources, "");
                }
                case "PLANE" -> {
                    var selection = group(groups, op, "groupId");
                    if (!m.implementationVersion().equals("1")) {
                        // Resolve the entire requested selection before publishing any centroid.
                        var points = selection.stream().map(a -> point(s, a)).toList();
                        var centroid = Plane3D.centroidOf(points);
                        quantity(q, "centroidAngstrom", List.of(finite(centroid.x()), finite(centroid.y()), finite(centroid.z())),
                                "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                        // Centroid availability cannot establish a plane or unique normal.
                        c.put("normalUniquenessStatus", UNKNOWN_INCONCLUSIVE.name());
                    }
                    var fit = fit(s, selection); planeQuantities(q, fit, sources);
                    c.put("normalUniquenessStatus", unique(fit, m) ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
                }
                case "POINT_PAIR_GROUP" -> {
                    var points = group(groups, op, "groupId").stream().map(a -> point(s, a)).toList();
                    var p = point(s, tuple.get(0)); var second = point(s, tuple.get(1));
                    var centroid = Plane3D.centroidOf(points);
                    quantity(q, "centroidAngstrom", List.of(finite(centroid.x()), finite(centroid.y()), finite(centroid.z())), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                    quantity(q, "firstCentroidDistanceAngstrom", p.distance(centroid), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                    quantity(q, "secondCentroidDistanceAngstrom", second.distance(centroid), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                    try {
                        quantity(q, "firstSecondCentroidAngleDegrees", Math.toDegrees(second.vectorTo(p).angle(second.vectorTo(centroid))), "DEGREE", SUPPORTED_PRESENT, sources, "");
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        c.put("completeEnumeration", false);
                        quantity(q, "firstSecondCentroidAngleDegrees", null, "DEGREE", UNKNOWN_INCONCLUSIVE, sources, "undefined tuple angle: " + e.getMessage());
                    }
                    c.put("normalUniquenessStatus", UNKNOWN_INCONCLUSIVE.name());
                    try {
                        var fit = Plane3D.fitWithDiagnostics(points);
                        if (!unique(fit, m)) throw new IllegalArgumentException("nonunique plane normal");
                        c.put("normalUniquenessStatus", s.frameQualified() ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
                        var direction = centroid.vectorTo(second).normalize();
                        double cosine = Math.abs(fit.plane().normal().dot(direction));
                        quantity(q, "secondCentroidNormalAngleDegrees", Math.toDegrees(Math.acos(Math.min(1.0, Math.max(0.0, cosine)))), "DEGREE", SUPPORTED_PRESENT, sources, "");
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        c.put("completeEnumeration", false);
                        quantity(q, "secondCentroidNormalAngleDegrees", null, "DEGREE", UNKNOWN_INCONCLUSIVE, sources, "undefined normal angle: " + e.getMessage());
                    }
                }
                case "POINT_PLANE" -> {
                    var fit = fit(s, group(groups, op, "planeGroupId")); boolean unique = unique(fit, m);
                    c.put("normalUniquenessStatus", unique ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
                    var p = point(s, atom(op.get("atom")));
                    quantity(q, "perpendicularDistanceAngstrom", unique ? fit.plane().absoluteDistanceTo(p) : null, "ANGSTROM", unique ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, unique ? "" : "ambiguous plane normal");
                    quantity(q, "inPlaneOffsetAngstrom", unique ? fit.plane().centroid().distance(fit.plane().project(p)) : null, "ANGSTROM", unique ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, unique ? "" : "ambiguous plane normal");
                }
                case "PLANE_PAIR" -> {
                    var a = fit(s, group(groups, op, "firstGroupId")); var b = fit(s, group(groups, op, "secondGroupId")); boolean unique = unique(a, m) && unique(b, m);
                    c.put("normalUniquenessStatus", unique ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
                    quantity(q, "centroidDistanceAngstrom", a.plane().centroid().distance(b.plane().centroid()), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
                    quantity(q, "unsignedNormalAngleDegrees", unique ? a.plane().angleToDegrees(b.plane()) : null, "DEGREE", unique ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, unique ? "" : "ambiguous normal");
                    quantity(q, "secondOntoFirstOffsetAngstrom", unique ? a.plane().centroid().distance(a.plane().project(b.plane().centroid())) : null, "ANGSTROM", unique ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, unique ? "" : "ambiguous normal");
                    quantity(q, "firstOntoSecondOffsetAngstrom", unique ? b.plane().centroid().distance(b.plane().project(a.plane().centroid())) : null, "ANGSTROM", unique ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, unique ? "" : "ambiguous normal");
                }
                case "PAIR_MATRIX", "GROUP_MINIMUM" -> pairs(s, r, op, groups, radii, q, c, sources);
                default -> { c.put("completeEnumeration", false); quantity(q, "measurement", null, "NONE", UNSUPPORTED, sources, "unknown operation kind; original plan preserved"); }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            c.put("completeEnumeration", false); reasons.add(e.getMessage());
            quantity(q, "measurement", null, "NONE", UNKNOWN_INCONCLUSIVE, sources, "undefined geometry: " + e.getMessage());
        }
        if (!s.frameQualified()) {
            reasons.add("coordinate frame unqualified; raw coordinate-derived numbers retained without physical comparison claim");
            q.forEach(v -> { ((ObjectNode)v).put("status", UNKNOWN_INCONCLUSIVE.name()); ((ArrayNode)v.get("reasons")).add("coordinate frame unqualified"); });
        }
        return result;
    }
    private static void pairs(SystemStateView s, RuleRequest r, JsonNode op, Map<String,List<AtomReference>> groups,
                              Map<AtomReference,JsonNode> radii, ObjectNode q, ObjectNode coverage, JsonNode sources) {
        var first = group(groups, op, "firstGroupId"); var second = group(groups, op, "secondGroupId");
        // Count the complete explicit product without allocating its potentially quadratic rows.
        boolean knownHydrogenScope = !text(op, "hydrogenScope").equals("HEAVY_ONLY")
                || java.util.stream.Stream.concat(first.stream(), second.stream()).allMatch(a -> {
                    var element = s.atoms().get(a).getElement(); return element != null && element != Element.UNKNOWN;
                });
        var firstSet = new TreeSet<>(first); var secondSet = new TreeSet<>(second);
        var intersection = new TreeSet<>(firstSet); intersection.retainAll(secondSet);
        long self = intersection.size();
        long fullNonself = (long) first.size() * second.size() - self;
        if (text(op, "hydrogenScope").equals("HEAVY_ONLY")) {
            firstSet.removeIf(a -> s.atoms().get(a).getElement() == Element.H);
            secondSet.removeIf(a -> s.atoms().get(a).getElement() == Element.H);
        }
        intersection = new TreeSet<>(firstSet); intersection.retainAll(secondSet);
        long filteredNonself = (long) firstSet.size() * secondSet.size() - intersection.size();
        long hydrogen = fullNonself - filteredNonself;
        long requested = filteredNonself - (long) intersection.size() * (intersection.size() - 1) / 2;
        var candidates = new TreeMap<String,List<AtomReference>>(); var memberships = new TreeMap<String,ArrayNode>();
        var union = new TreeSet<>(firstSet); union.addAll(secondSet); var ordered = List.copyOf(union);
        outer: for (int i = 0; i < ordered.size(); i++) for (int j = i + 1; j < ordered.size(); j++) {
            var a = ordered.get(i); var b = ordered.get(j);
            boolean forward = firstSet.contains(a) && secondSet.contains(b);
            boolean reverse = firstSet.contains(b) && secondSet.contains(a);
            if (!forward && !reverse) continue;
            if (candidates.size() >= r.maximumCandidates()) break outer;
            var pair = List.of(a, b); String key = String.format(java.util.Locale.ROOT, "%010d", candidates.size());
            candidates.put(key, pair); var member = JSON.createArrayNode();
            if (forward) member.add(node(Map.of("firstGroupId", text(op, "firstGroupId"), "firstAtom", a, "secondGroupId", text(op, "secondGroupId"), "secondAtom", b)));
            if (reverse) member.add(node(Map.of("firstGroupId", text(op, "firstGroupId"), "firstAtom", b, "secondGroupId", text(op, "secondGroupId"), "secondAtom", a)));
            memberships.put(key, member);
        }
        coverage.put("requestedPairs", requested); coverage.put("omittedSelfPairs", self); coverage.put("omittedHydrogenPairs", hydrogen);
        boolean budgetExceeded = requested > r.maximumCandidates();
        boolean complete = !budgetExceeded && knownHydrogenScope; coverage.put("completeEnumeration", complete); coverage.put("budgetExceeded", budgetExceeded);
        var rows = JSON.createArrayNode(); var ties = JSON.createArrayNode(); double minimum = Double.POSITIVE_INFINITY;
        boolean allRadii = true; boolean topologyKnown = s.graph().structure().getConnectivityMetadata().provenance() == ConnectivityProvenance.EXPLICIT;
        String policy = op.has("topologyPolicy") ? text(op, "topologyPolicy") : "RETAIN_ALL";
        for (var entry : candidates.entrySet()) {
            if (rows.size() >= r.maximumCandidates()) break;
            var a = entry.getValue().get(0); var b = entry.getValue().get(1); double d = point(s, a).distance(point(s, b));
            var row = rows.addObject(); row.set("firstAtom", node(a)); row.set("secondAtom", node(b)); row.put("distanceAngstrom", finite(d));
            Double ra = radius(radii.get(a)), rb = radius(radii.get(b)); boolean assigned = ra != null && rb != null; allRadii &= assigned;
            row.set("firstRadius", ra == null ? NullNode.instance : node(finite(ra))); row.set("secondRadius", rb == null ? NullNode.instance : node(finite(rb)));
            row.set("gapAngstrom", assigned ? node(finite(d - ra - rb)) : NullNode.instance);
            row.set("overlapAngstrom", assigned ? node(finite(ra + rb - d)) : NullNode.instance);
            row.put("radiusStatus", assigned ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
            row.put("topologyStatus", policy.equals("RETAIN_ALL") ? NOT_EVALUATED.name() : topologyKnown ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
            var flags = row.putArray("topologyReasons");
            if (!policy.equals("RETAIN_ALL")) {
                if (!topologyKnown) flags.add("authoritative complete connectivity unavailable");
                else if (bonded(s, a, b)) flags.add("BONDED");
                else if (policy.equals("FLAG_BONDED_AND_1_3") && neighbors(s, a).stream().anyMatch(n -> bonded(s, n, b))) flags.add("ONE_THREE");
                else flags.add("NO_EXCLUSION_IN_REQUESTED_TOPOLOGY_SCOPE");
            }
            row.set("groupMemberships", memberships.get(entry.getKey()));
            if (d < minimum) { minimum = d; ties.removeAll(); ties.add(node(entry.getValue())); }
            else if (Double.compare(d, minimum) == 0) ties.add(node(entry.getValue()));
        }
        coverage.put("evaluatedPairs", rows.size());
        coverage.put("radiusCoverage", rows.isEmpty() ? NOT_EVALUATED.name() : allRadii && complete ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
        coverage.put("topologyCoverage", policy.equals("RETAIN_ALL") ? NOT_EVALUATED.name() : topologyKnown && complete ? SUPPORTED_PRESENT.name() : UNKNOWN_INCONCLUSIVE.name());
        if (text(op, "kind").equals("PAIR_MATRIX")) quantity(q, "pairs", rows, "ANGSTROM", complete ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, complete ? "" : knownHydrogenScope ? "pair budget incomplete" : "heavy-atom identity coverage incomplete; unknown-element rows retained");
        else {
            boolean available = Double.isFinite(minimum);
            quantity(q, "completeMinimum", complete && available ? minimum : null, "ANGSTROM", complete && available ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, complete && available ? "" : "no exhaustive nonempty minimum");
            quantity(q, "observedMinimum", available ? minimum : null, "ANGSTROM", available ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, available ? "" : "no evaluated pair");
            quantity(q, "tiedPairs", ties, "NONE", complete ? SUPPORTED_PRESENT : UNKNOWN_INCONCLUSIVE, sources, complete ? "" : "ties only within evaluated scope");
        }
    }
    private static Set<AtomReference> neighbors(SystemStateView s, AtomReference a) {
        var out = new HashSet<AtomReference>();
        for (var b : s.graph().structure().bonds()) { if (b.atom1().equals(a)) out.add(b.atom2()); if (b.atom2().equals(a)) out.add(b.atom1()); }
        return out;
    }
    private static boolean bonded(SystemStateView s, AtomReference a, AtomReference b) { return neighbors(s, a).contains(b); }
    private static Double radius(JsonNode assignment) { return assignment != null && assignment.path("status").asText().equals(SUPPORTED_PRESENT.name()) ? decimal(assignment.get("radius")) : null; }
    private static Plane3D.FitDiagnostics fit(SystemStateView s, List<AtomReference> atoms) { return Plane3D.fitWithDiagnostics(atoms.stream().map(a -> point(s, a)).toList()); }
    private static double gap(Plane3D.FitDiagnostics d) { var e = d.covarianceEigenvalues(); return (e.get(1) - e.get(0)) / e.get(2); }
    private static boolean unique(Plane3D.FitDiagnostics d, RuleManifest m) { return d.covarianceEigenvalues().get(2) > 0 && Double.isFinite(gap(d)) && gap(d) > Double.parseDouble(m.parameters().get("planeNormalRelativeGapTolerance").value()); }
    private static void planeQuantities(ObjectNode q, Plane3D.FitDiagnostics d, JsonNode sources) {
        quantity(q, "centroidAngstrom", List.of(d.plane().centroid().x(), d.plane().centroid().y(), d.plane().centroid().z()).stream().map(ContinuousGeometryRules::finite).toList(), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
        quantity(q, "algorithmNormal", vector(d.plane().normal()), "DIMENSIONLESS", SUPPORTED_PRESENT, sources, "raw algorithm normal; see uniqueness coverage");
        quantity(q, "covarianceEigenvaluesAngstrom2", d.covarianceEigenvalues().stream().map(ContinuousGeometryRules::finite).toList(), "ANGSTROM2", SUPPORTED_PRESENT, sources, "");
        quantity(q, "rmsDistanceAngstrom", d.rmsDistance(), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
        quantity(q, "maximumAbsoluteDistanceAngstrom", d.maximumAbsoluteDistance(), "ANGSTROM", SUPPORTED_PRESENT, sources, "");
        quantity(q, "smallestEigenvalueGapRatio", gap(d), "DIMENSIONLESS", SUPPORTED_PRESENT, sources, "numerical policy only");
    }
    private static void quantity(ObjectNode q, String name, Object value, String unit, EvidenceInterpretation.Status status, JsonNode sources, String reason) {
        var item = q.putObject(name); item.put("status", status.name()); item.put("unit", unit);
        item.set("value", value == null ? NullNode.instance : node(value instanceof Double d ? finite(d) : value));
        var reasons = item.putArray("reasons"); if (!reason.isEmpty()) reasons.add(reason); item.set("sourceReferences", sources);
    }
    private static List<String> vector(Vector3D v) { return List.of(finite(v.x()), finite(v.y()), finite(v.z())); }
    private static Point3D point(SystemStateView s, AtomReference a) { return s.atoms().get(a).getPosition(); }
    private static List<AtomReference> group(Map<String,List<AtomReference>> groups, JsonNode op, String field) { var g = groups.get(text(op, field)); if (g == null) throw new IllegalArgumentException("unknown group"); return g; }
    private static JsonNode pin(EvidenceEnvelope e) { return node(Map.of("reference", e.reference(), "payloadSha256", e.payloadSha256())); }
    private static JsonNode node(Object value) { return JSON.valueToTree(value); }
    private static AtomReference atom(JsonNode n) throws Exception { fields(n, "chainId", "residueNumber", "insertionCode", "atomName"); var ref = JSON.treeToValue(n, AtomReference.class); if (!node(ref).equals(n)) throw new IllegalArgumentException("canonical atom reference required"); return ref; }
    private static List<AtomReference> atoms(JsonNode n) throws Exception { if (n == null || !n.isArray()) throw new IllegalArgumentException("atom array required"); var out = new ArrayList<AtomReference>(); for (var a : n) out.add(atom(a)); return List.copyOf(out); }
    private static String text(JsonNode n, String field) { if (n == null || !n.path(field).isTextual() || n.get(field).asText().isBlank()) throw new IllegalArgumentException("text required: " + field); return n.get(field).asText(); }
    private static void fields(JsonNode n, String... expected) { if (n == null || !n.isObject()) throw new IllegalArgumentException("object required"); var actual = new HashSet<String>(); n.fieldNames().forEachRemaining(actual::add); if (!actual.equals(Set.of(expected))) throw new IllegalArgumentException("exact fields required: " + Set.of(expected)); }
    private static void equal(JsonNode n, String key, String expected) { if (!text(n, key).equals(expected)) throw new IllegalArgumentException("invalid " + key); }
    private static void strings(JsonNode n) { if (n == null || !n.isArray()) throw new IllegalArgumentException("string array required"); for (var v : n) if (!v.isTextual() || v.asText().isBlank()) throw new IllegalArgumentException("nonblank string required"); }
    private static void references(JsonNode n) throws Exception { if (n == null || !n.isArray() || n.isEmpty()) throw new IllegalArgumentException("attributed sources required"); for (var v : n) { fields(v, "kind", "namespace", "id", "version"); JSON.treeToValue(v, ScientificReference.class); } }
    private static double decimal(JsonNode n) { if (n == null || !n.isTextual()) throw new IllegalArgumentException("decimal string required"); double d = Double.parseDouble(n.asText()); if (!Double.isFinite(d) || !Double.toString(d).equals(n.asText())) throw new IllegalArgumentException("canonical finite decimal required"); return d; }
    private static String finite(double d) { if (!Double.isFinite(d)) throw new IllegalArgumentException("nonfinite arithmetic"); return Double.toString(d); }
}
