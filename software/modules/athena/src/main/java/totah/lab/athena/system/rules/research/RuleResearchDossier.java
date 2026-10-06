package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.mnemosyne.ScientificReference;
import java.time.Instant;
import java.util.*;

public record RuleResearchDossier(String schema, String id, String version, String ruleKey,
        String definitionSha256, String projectionVersion, Source domain, List<Source> sources,
        List<Audit> implementationAudits, List<Audit> literatureAudits, List<DatasetAudit> datasetAudits,
        List<Decision> decisions, List<String> unresolvedRequirements, Review review) {
    public enum Applicability { APPLICABLE, NOT_APPLICABLE, NO_RULE_IN_INSPECTED_SCOPE, UNINSPECTED }
    public enum AnalysisStatus { NOT_ACQUIRED, ACQUIRED_NOT_ANALYZED, ANALYZED }
    public enum Disposition { ADOPT, MODIFY, REJECT, UNSUPPORTED }
    public record Audit(String system, String version, Applicability applicability, List<Source> inspectedArtifacts,
            List<String> entryPoints, String perception, String measurements, String classification,
            List<String> exclusions, List<String> limitations) {
        public Audit { ResearchCodec.text(system); ResearchCodec.text(version); Objects.requireNonNull(applicability);
            inspectedArtifacts=List.copyOf(inspectedArtifacts); entryPoints=ResearchCodec.strings(entryPoints);
            ResearchCodec.text(perception); ResearchCodec.text(measurements); ResearchCodec.text(classification);
            exclusions=ResearchCodec.strings(exclusions); limitations=ResearchCodec.strings(limitations); }
    }
    public record DatasetAudit(String id, Applicability applicability, List<Source> sourcePins, String population,
            String releaseOrQuery, String inclusionExclusion, String redundancyPolicy, String preparation,
            AnalysisStatus analysisStatus, List<String> limitations) {
        public DatasetAudit { ResearchCodec.text(id); Objects.requireNonNull(applicability); sourcePins=List.copyOf(sourcePins);
            ResearchCodec.text(population); ResearchCodec.text(releaseOrQuery); ResearchCodec.text(inclusionExclusion);
            ResearchCodec.text(redundancyPolicy); ResearchCodec.text(preparation); Objects.requireNonNull(analysisStatus);
            limitations=ResearchCodec.strings(limitations); }
    }
    public record Decision(String id, String criterion, String domainSha256, Disposition disposition,
                           String rationale, List<Source> sourcePins, String resultingDefinition) {
        public Decision { ResearchCodec.text(id); ResearchCodec.text(criterion); ResearchCodec.hash(domainSha256);
            Objects.requireNonNull(disposition); ResearchCodec.text(rationale); sourcePins=List.copyOf(sourcePins); ResearchCodec.text(resultingDefinition); }
    }
    public record Review(ScientificReference reviewer, Source decisionSource, Instant reviewedAt, Instant validUntil,
                         Source policy, String approvedDefinitionSha256, String approvedDomainSha256) {
        public Review { Objects.requireNonNull(reviewer); Objects.requireNonNull(decisionSource); Objects.requireNonNull(reviewedAt);
            Objects.requireNonNull(validUntil); Objects.requireNonNull(policy); ResearchCodec.hash(approvedDefinitionSha256); ResearchCodec.hash(approvedDomainSha256); }
    }
    public RuleResearchDossier { if(!Set.of("athena-rule-research-dossier/1","athena-rule-research-dossier/2").contains(schema))throw new IllegalArgumentException("unsupported research schema"); ResearchCodec.text(id); ResearchCodec.text(version);
        ResearchCodec.text(ruleKey); ResearchCodec.hash(definitionSha256); ResearchCodec.schema(projectionVersion,schema.endsWith("/2")?"athena-rule-definition-projection/2":"athena-rule-definition-projection/1");
        Objects.requireNonNull(domain); sources=List.copyOf(sources); implementationAudits=List.copyOf(implementationAudits);
        literatureAudits=List.copyOf(literatureAudits); datasetAudits=List.copyOf(datasetAudits); decisions=List.copyOf(decisions);
        unresolvedRequirements=ResearchCodec.strings(unresolvedRequirements); Objects.requireNonNull(review); }
}
