package totah.lab.athena.system.rules;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import totah.lab.athena.system.*;
import totah.lab.gaia.structure.AtomReference;
import totah.lab.mnemosyne.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Selected source peptide geometry; raw measurements and named operational screens stay separate. */
final class PeptideGeometryRules {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String GROUP = "athena:group-identities";
    private static final String PLAN = "athena:continuous-geometry-plan";
    private static final String MEASUREMENT = "athena:rule-measurements";
    private PeptideGeometryRules() { }

    private static boolean npi(RuleManifest m) { return Set.of("ATHENA.INT.N_PI_STAR.ADJACENT_BACKBONE_CANDIDATE", "ATHENA.INT.N_PI_STAR.CYCLIC_DIPEPTIDE_CANDIDATE").contains(m.ruleId()); }
    static void validate(RuleManifest manifest) {
        boolean npi = npi(manifest);
        if (!Set.of("athena-rule/2", "athena-rule/3").contains(manifest.schema())
                || !(npi || manifest.ruleId().equals("ATHENA.VAL.PEPTIDE_OMEGA.SOURCE_ALPHA_LINK"))
                || !manifest.profile().equals(npi ? "ATHENA_N_PI_BACKBONE_V1" : "ATHENA_PEPTIDE_OMEGA_V1")
                || !manifest.implementationVersion().equals("1")
                || !manifest.requiredCapabilities().isEmpty()
                || !manifest.negativeCoverage().requirements().equals(npi
                    ? List.of("EXACT_SOURCE_ROLE_TUPLE", "ALL_CORRESPONDENCES_AGREE", "FINITE_COMMON_FRAME_DISTANCE_ANGLE")
                    : List.of("EXACT_SOURCE_ROLE_TUPLE", "ALL_CORRESPONDENCES_AGREE", "FINITE_COMMON_FRAME_TORSION"))
                || manifest.family() != (npi ? RuleManifest.Family.INTERACTION : RuleManifest.Family.VALIDATOR)
                || !manifest.parameters().keySet().equals((npi ? Set.of("sourceManifest", "geometryManifest", "maximumDistanceAngstrom", "minimumAngleDegrees", "maximumAngleDegrees") : Set.of("sourceManifest", "geometryManifest", "cisTransToleranceDegrees"))))
            throw new IllegalArgumentException("peptide omega contract");
        if (npi) {
            parameter(manifest, "maximumDistanceAngstrom", "angstrom", 3.2);
            parameter(manifest, "minimumAngleDegrees", "degree", 99.0);
            parameter(manifest, "maximumAngleDegrees", "degree", 119.0);
        } else parameter(manifest, "cisTransToleranceDegrees", "degree", 30.0);
        try {
            var group = source(manifest, "sourceManifest");
            var geometry = source(manifest, "geometryManifest");
            FunctionalGroupRules.validate(group); ContinuousGeometryRules.validate(geometry);
            if (!group.ruleId().equals(npi ? (manifest.ruleId().contains("CYCLIC_DIPEPTIDE") ? "ATHENA.GROUP.CYCLIC_DIPEPTIDE.ADJACENT_AMIDES.SOURCE_CONNECTIVITY" : "ATHENA.GROUP.ADJACENT_ALPHA_BACKBONE_AMIDES.SOURCE_CONNECTIVITY") : "ATHENA.GROUP.ALPHA_PEPTIDE_LINK.SOURCE_CONNECTIVITY")
                    || !group.implementationVersion().equals("4") || !geometry.implementationVersion().equals("3"))
                throw new IllegalArgumentException("source role/geometry version required");
        } catch (Exception error) { throw new IllegalArgumentException("invalid dependency manifest", error); }
    }

    static SystemGraphAnalyzer analyzer(RuleManifest manifest, RuleRequest request, boolean evaluate) {
        validate(manifest);
        return new SystemGraphAnalyzer() {
            public ScientificReference method() { return reference(manifest, evaluate); }
            public Set<SystemGraphCertificate.Capability> requires() { return Set.of(); }
            public Set<SystemGraphCertificate.Capability> qualifies() { return Set.of(); }
            public Set<String> evidenceTypes() { return Set.of(GROUP, PLAN, MEASUREMENT, "athena:system-state"); }
            public List<Finding> analyze(SystemStateView state, List<EvidenceEnvelope> inputs, Map<String,String> config) throws Exception {
                if (!state.binding().equals(request.state()) || !manifest.key().equals(request.manifestKey())
                        || !RuleRegistry.digest(manifest).equals(request.manifestSha256())
                        || request.atoms().size() != (npi(manifest) ? 5 : 4) || new HashSet<>(request.atoms()).size() != request.atoms().size()
                        || !request.first().isEmpty() || !request.second().isEmpty())
                    throw new IllegalArgumentException("exact ordered source-role tuple required");
                var groupEnvelope = one(inputs, GROUP); var planEnvelope = one(inputs, PLAN);
                var group = source(manifest, "sourceManifest"); var geometry = source(manifest, "geometryManifest");
                var groupRequest = request(state, group, List.of(), request);
                var verified = RuleAnalyzers.evaluator(group, groupRequest).analyze(state, List.of(groupEnvelope), Map.of()).getFirst();
                var groupReport = JSON.readTree(verified.measurements().get("payload"));
                var plan = JSON.readTree(planEnvelope.readPayload());
                verifyPlan(plan, request.atoms(), npi(manifest));
                var geometryRequest = request(state, geometry, request.atoms().stream().sorted().toList(), request);
                var raw = RuleAnalyzers.collector(geometry, geometryRequest).analyze(state, List.of(planEnvelope), Map.of()).getFirst();
                String payload = raw.measurements().get("payload");
                var values = new TreeMap<String,String>(); values.put("payload", payload);
                values.put("sourceGroupSha256", groupEnvelope.payloadSha256());
                values.put("sourceGroupReference", text(groupEnvelope.reference()));
                values.put("sourceRoleDefinitionDigest", groupReport.path("definitionDigest").asText());
                values.put("manifestDigest", RuleRegistry.digest(manifest));
                if (!evaluate) return List.of(finding(state, SUPPORTED_PRESENT, values, "raw peptide geometry collected; no classification"));
                var measurement = one(inputs, MEASUREMENT);
                if (!measurement.method().equals(reference(manifest, false)) || !JSON.readTree(measurement.readPayload()).equals(JSON.readTree(payload)))
                    throw new IllegalArgumentException("peptide geometry measurement content/provenance mismatch");
                values.put("measurementSha256", measurement.payloadSha256());
                boolean rolePresent = matchesRoles(state, groupReport, request.atoms(), npi(manifest) ? List.of("O1", "C2", "O2", "CA2", "N3") : List.of("CA1", "C1", "N2", "CA2"));
                var operations = JSON.readTree(payload).path("operations");
                var status = UNKNOWN_INCONCLUSIVE;
                if (npi(manifest)) {
                    var distance = available(operations.get(0).path("quantities").path("distanceAngstrom"));
                    var angle = available(operations.get(1).path("quantities").path("angleDegrees"));
                    values.put("proposition", "ADJACENT_BACKBONE_N_PI_GEOMETRIC_CANDIDATE");
                    if (rolePresent && distance != null && angle != null) {
                        values.put("distanceAngstrom", Double.toString(distance)); values.put("angleDegrees", Double.toString(angle));
                        status = distance <= number(manifest, "maximumDistanceAngstrom")
                                && angle >= number(manifest, "minimumAngleDegrees") && angle <= number(manifest, "maximumAngleDegrees")
                                ? SUPPORTED_PRESENT : ABSENT_FALSE;
                    }
                } else {
                    var omega = available(operations.get(0).path("quantities").path("torsionDegrees"));
                    if (rolePresent && omega != null) {
                        double cis = Math.abs(omega), trans = Math.abs(180.0 - Math.abs(omega));
                        double deviation = Math.min(cis, trans), tolerance = number(manifest, "cisTransToleranceDegrees");
                        String conformation = cis <= tolerance ? "CIS_WINDOW" : trans <= tolerance ? "TRANS_WINDOW" : "TWISTED_OUTSIDE_WINDOWS";
                        values.put("omegaDegrees", Double.toString(omega)); values.put("nearestIdealDeviationDegrees", Double.toString(deviation));
                        values.put("conformation", conformation);
                        values.put("proposition", "OMEGA_OUTSIDE_CIS_TRANS_30_DEGREE_WINDOWS");
                        status = deviation > tolerance ? SUPPORTED_PRESENT : ABSENT_FALSE;
                    } else values.put("conformation", "UNKNOWN_INCONCLUSIVE");
                }
                if (manifest.retired() || (!manifest.schema().equals("athena-rule/3")
                        && manifest.qualification() != SystemGraphCertificate.Status.QUALIFIED)) status = NOT_EVALUATED;
                return List.of(finding(state, status, values, "selected source peptide geometric proposition only; no biological invalidity, orbital donation or energetic claim"));
            }
            private Finding finding(SystemStateView state, EvidenceInterpretation.Status status, Map<String,String> values, String reason) {
                return new Finding(evaluate ? "evaluate" : "collect", List.of(RuleAnalyzers.subjects(state, request)), status, values, List.of(reason), manifest.limitations());
            }
        };
    }

    private static boolean matchesRoles(SystemStateView state, JsonNode report, List<AtomReference> tuple, List<String> roleNames) {
        if (!report.path("assessment").asText().equals(SUPPORTED_PRESENT.name())) return false;
        var component = state.components().stream().filter(c -> JSON.valueToTree(c.identity()).equals(report.path("componentReference"))).findFirst().orElseThrow();
        for (var correspondence : component.correspondenceAlternatives()) {
            boolean found = false;
            for (var occurrence : report.path("occurrences")) for (var roles : occurrence.path("roleCorrespondenceAlternatives")) {
                var mapped = new ArrayList<AtomReference>();
                for (String role : roleNames) {
                    if (roles.path(role).size() != 1) throw new IllegalArgumentException("ambiguous peptide role");
                    mapped.add(correspondence.get(roles.path(role).get(0).asText()));
                }
                if (mapped.equals(tuple)) found = true;
            }
            // Do not select a convenient atom correspondence as the sole source truth.
            if (!found) return false;
        }
        return !component.correspondenceAlternatives().isEmpty();
    }
    private static void parameter(RuleManifest m, String name, String unit, double value) {
        var p = m.parameters().get(name);
        if (!p.unit().equals(unit) || Double.parseDouble(p.value()) != value) throw new IllegalArgumentException("unreviewed peptide geometry criterion");
    }
    private static double number(RuleManifest m, String name) { return Double.parseDouble(m.parameters().get(name).value()); }
    private static Double available(JsonNode quantity) {
        if (!quantity.path("status").asText().equals(SUPPORTED_PRESENT.name()) || !quantity.path("value").isTextual()) return null;
        double value = Double.parseDouble(quantity.path("value").asText()); return Double.isFinite(value) ? value : null;
    }
    private static void verifyPlan(JsonNode plan, List<AtomReference> atoms, boolean npi) {
        var ops = plan.path("operations");
        if (!plan.path("radiusAssignmentReference").isNull()) throw new IllegalArgumentException("no radii needed for this explicit peptide geometry");
        if (!npi) {
            if (ops.size() != 1 || !plan.path("groups").isEmpty() || !tuple(ops.get(0), "DIHEDRAL", atoms))
                throw new IllegalArgumentException("one exact source omega tuple required");
        } else {
            if (ops.size() != 3 || plan.path("groups").size() != 1
                    || !tuple(ops.get(0), "DISTANCE", atoms.subList(0,2))
                    || !tuple(ops.get(1), "ANGLE", atoms.subList(0,3))
                    || (!ops.get(2).path("kind").asText().equals("POINT_PLANE") || !ops.get(2).path("atom").equals(JSON.valueToTree(atoms.get(1)))))
                throw new IllegalArgumentException("exact backbone distance/angle/acceptor-plane operations required");
            var group = plan.path("groups").get(0);
            if (!group.path("id").equals(ops.get(2).path("planeGroupId"))
                    || !group.path("atoms").equals(JSON.valueToTree(atoms.subList(2,5).stream().sorted().toList())))
                throw new IllegalArgumentException("acceptor plane must use exact O2/CA2/N3 substituents");
        }
    }
    private static boolean tuple(JsonNode operation, String kind, List<AtomReference> atoms) {
        return operation.path("kind").asText().equals(kind) && operation.path("atoms").equals(JSON.valueToTree(atoms));
    }
    private static RuleRequest request(SystemStateView state, RuleManifest manifest, List<AtomReference> atoms, RuleRequest bounds) {
        return new RuleRequest(state.binding(), manifest.key(), RuleRegistry.digest(manifest), atoms, List.of(), List.of(), bounds.radiusAngstrom(), bounds.maximumHops(), bounds.maximumNodes(), bounds.maximumCandidates());
    }
    private static ScientificReference reference(RuleManifest manifest, boolean evaluate) {
        return new ScientificReference(ScientificReference.Kind.METHOD, "athena.peptide-geometry", manifest.key() + (evaluate ? "/evaluate" : "/collect"), RuleRegistry.digest(manifest));
    }
    private static RuleManifest source(RuleManifest manifest, String key) throws Exception {
        return RuleRegistry.decode(manifest.parameters().get(key).value().getBytes(StandardCharsets.UTF_8));
    }
    private static EvidenceEnvelope one(List<EvidenceEnvelope> inputs, String type) throws Exception {
        var selected = inputs.stream().filter(e -> e.evidenceType().equals(type)).toList();
        if (selected.size() != 1) throw new IllegalArgumentException("one explicitly selected " + type + " required");
        var envelope = selected.getFirst();
        if (!EvidenceExchange.sha256(envelope.readPayload()).equals(envelope.payloadSha256())) throw new IllegalArgumentException("artifact digest mismatch");
        return envelope;
    }
    private static String text(Object value) { return new String(SystemStateView.bytes(value), StandardCharsets.UTF_8); }
}
