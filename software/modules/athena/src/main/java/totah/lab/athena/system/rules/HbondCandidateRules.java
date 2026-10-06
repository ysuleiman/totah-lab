package totah.lab.athena.system.rules;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import totah.lab.athena.system.*;
import totah.lab.mnemosyne.*;
import java.util.*;
import static totah.lab.mnemosyne.EvidenceInterpretation.Status.*;

/** Opt-in I02 candidate definition; historical hydrogen-bond implementations remain independent. */
final class HbondCandidateRules {
    static final String ID = "ATHENA.HBOND.EXPLICIT_H_DIRECTIONAL_CANDIDATE";
    static final ObjectMapper JSON = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private HbondCandidateRules() { }

    static void validate(RuleManifest m) {
        if (!Set.of("athena-rule/2", "athena-rule/3").contains(m.schema()) || !m.ruleId().equals(ID)
                || !m.version().equals("1.0.0") || !m.implementationVersion().equals("1")
                || !m.profile().equals("ATHENA_EXPLICIT_H_DIRECTIONAL_CANDIDATE_V1")
                || m.family() != RuleManifest.Family.INTERACTION || !m.requiredCapabilities().isEmpty())
            throw new IllegalArgumentException("explicit-H candidate manifest contract");
        try (var input = HbondCandidateRules.class.getResourceAsStream("hbond-candidate-v1/" + ID + ".rule.json")) {
            if (input == null) throw new IllegalArgumentException("missing pinned candidate definition");
            var pinned = JSON.readTree(input);
            if (!node(m.parameters()).equals(pinned.get("parameters"))
                    || !node(m.negativeCoverage()).equals(pinned.get("negativeCoverage")))
                throw new IllegalArgumentException("unreviewed candidate parameters/domain/coverage");
        } catch (java.io.IOException e) { throw new IllegalArgumentException("cannot read candidate definition", e); }
    }
    static JsonNode parameter(RuleManifest m, String name) throws java.io.IOException {
        return JSON.readTree(m.parameters().get(name).value());
    }
    static JsonNode node(Object value) {
        try { return JSON.readTree(SystemStateView.bytes(value)); }
        catch (java.io.IOException e) { throw new IllegalArgumentException("cannot encode candidate attribution", e); }
    }
    static ScientificReference method(RuleManifest m, boolean evaluate) {
        return new ScientificReference(ScientificReference.Kind.METHOD, "athena.hbond-candidate",
                m.key() + (evaluate ? "/evaluate" : "/collect"), RuleRegistry.digest(m));
    }
    static SystemGraphAnalyzer analyzer(RuleManifest m, RuleRequest r, boolean evaluate) {
        validate(m);
        return new SystemGraphAnalyzer() {
            public ScientificReference method() { return HbondCandidateRules.method(m, evaluate); }
            public Set<SystemGraphCertificate.Capability> requires() { return Set.of(); }
            public Set<SystemGraphCertificate.Capability> qualifies() { return Set.of(); }
            public Set<String> evidenceTypes() { return Set.of("athena:group-identities", "athena:rule-measurements", "athena:system-state"); }
            public List<Finding> analyze(SystemStateView s, List<EvidenceEnvelope> inputs, Map<String,String> config) throws Exception {
                if (!s.binding().equals(r.state()) || !m.key().equals(r.manifestKey())
                        || !RuleRegistry.digest(m).equals(r.manifestSha256()) || !r.atoms().isEmpty()
                        || r.first().isEmpty() || r.second().isEmpty())
                    throw new IllegalArgumentException("explicit complete directional partner scopes and bound request required");
                var report = HbondCandidateEnumeration.build(s, m, r, inputs);
                if (evaluate) {
                    var prior = inputs.stream().filter(e -> e.evidenceType().equals("athena:rule-measurements")).toList();
                    if (prior.size() != 1 || !prior.getFirst().method().equals(HbondCandidateRules.method(m, false))
                            || !report.equals(JSON.readTree(prior.getFirst().readPayload())))
                        throw new IllegalArgumentException("candidate measurements/state/provenance mismatch");
                }
                var status = EvidenceInterpretation.Status.valueOf(report.path("assessment").asText());
                if (!evaluate) status = SUPPORTED_PRESENT;
                else if (m.retired() || (!m.schema().equals("athena-rule/3") && m.qualification() != SystemGraphCertificate.Status.QUALIFIED)) status = NOT_EVALUATED;
                return List.of(new Finding(evaluate ? "evaluate" : "collect", List.of(RuleAnalyzers.subjects(s, r)), status,
                        Map.of("payload", report.toString()), List.of("Directional structural candidates only; per-class scope and completeness retained"), m.limitations()));
            }
        };
    }
}
