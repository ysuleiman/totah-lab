package totah.lab.athena.system.rules.research;

import totah.lab.athena.system.rules.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import static totah.lab.athena.system.rules.research.RuleResearchDossier.Applicability.*;

/** Eligibility only. This class cannot issue a qualification certificate or validated rule handle. */
public final class ScientificRuleResearchGate {
    private static final Set<String> SYSTEMS=Set.of("RDKit/FDef","OpenFF/SMIRNOFF","PLIP","ProLIF",
            "MolProbity/Probe/wwPDB","RING","Athena/OCL");
    private static final Set<String> CHANGES=Set.of("domain","perception","measurement","references/distributions",
            "classification/thresholds","negative coverage","implementation version");

    public ResearchEligibility evaluate(RuleManifest manifest,RuleReviewPolicy policy,RulePolicyContext context,
            ResearchArtifactReader artifacts,Instant evaluatedAt,QualificationMode mode)throws IOException {
        return evaluateShared(manifest,ReviewPolicyAccess.of(policy),context,artifacts,evaluatedAt,mode);
    }
    public ResearchEligibility evaluate(RuleManifest manifest,RuleReviewPolicyV2 policy,RulePolicyContext context,
            ResearchArtifactReader artifacts,Instant evaluatedAt,QualificationMode mode)throws IOException {
        return evaluateShared(manifest,ReviewPolicyAccess.of(policy),context,artifacts,evaluatedAt,mode);
    }
    private ResearchEligibility evaluateShared(RuleManifest manifest,ReviewPolicyAccess policy,RulePolicyContext context,
            ResearchArtifactReader artifacts,Instant evaluatedAt,QualificationMode mode)throws IOException {
        Objects.requireNonNull(artifacts);Objects.requireNonNull(evaluatedAt);Objects.requireNonNull(mode);
        if(!manifest.schema().equals("athena-rule/3"))throw new IllegalArgumentException("historical policy is not current research eligibility");
        var binding=manifest.research();var reasons=new TreeSet<String>();var checked=new TreeSet<String>();
        if(policy.v2()!=binding.schema().equals("athena-rule-research-binding/2"))reasons.add("MIXED_RESEARCH_VERSIONS");
        var definition=ResearchCodec.definition(manifest);
        if(!definition.equals(binding.definitionSha256()))reasons.add("DEFINITION_DIGEST_MISMATCH");
        if(!ResearchCodec.digest(policy.document()).equals(binding.reviewPolicy().sha256()))reasons.add("POLICY_BINDING_MISMATCH");
        if(!context.policy().equals(binding.reviewPolicy()))reasons.add("CONTEXT_POLICY_MISMATCH");
        if(!context.asOf().equals(evaluatedAt))reasons.add("CONTEXT_TIME_MISMATCH");
        if(!policy.authorizedContextIssuers().contains(context.issuer()))reasons.add("UNAUTHORIZED_CONTEXT_ISSUER");
        if(policy.applicableRulePrefixes().stream().noneMatch(manifest.ruleId()::startsWith))reasons.add("RULE_OUTSIDE_POLICY");
        if(!new HashSet<>(policy.requiredSystems()).containsAll(SYSTEMS))reasons.add("POLICY_SYSTEM_COVERAGE_MISSING");
        if(policy.v2() && !new HashSet<>(policy.implementationChanges()).containsAll(Set.of("implementation identity/version","source/artifact pins","fixtures/tests","execution/context/receipt binding")))reasons.add("POLICY_IMPLEMENTATION_INVALIDATION_COVERAGE_MISSING");
        var requiredChanges=policy.v2()?Set.of("definition","domain","perception","measurement","references/distributions","classification/thresholds","negative coverage"):CHANGES;
        if(!new HashSet<>(policy.invalidatingChangeKinds()).containsAll(requiredChanges))reasons.add("POLICY_INVALIDATION_COVERAGE_MISSING");
        if(policy.requiredQualificationChecks().isEmpty())reasons.add("IMPLEMENTATION_CHECK_POLICY_MISSING");
        if(!new HashSet<>(policy.requiredAuditCategories()).equals(Set.of("IMPLEMENTATION","LITERATURE","DATASET")))reasons.add("UNSUPPORTED_AUDIT_CATEGORY_POLICY");
        if(new HashSet<>(policy.requiredSystems()).size()!=policy.requiredSystems().size()
                ||new HashSet<>(policy.requiredQualificationChecks()).size()!=policy.requiredQualificationChecks().size())reasons.add("DUPLICATE_POLICY_IDENTITY");
        // Verify original admitted bytes, not just objects supplied by the caller.
        byte[] policyBytes=ResearchCodec.read(binding.reviewPolicy(),artifacts,checked);
        if(!Arrays.equals(policyBytes,ResearchCodec.bytes(policy.document())))reasons.add("NONCANONICAL_OR_DIFFERENT_POLICY");
        ResearchCodec.verifySources(context,artifacts,checked);
        byte[] domainBytes=ResearchCodec.read(binding.domain(),artifacts,checked);
        byte[] dossierBytes=ResearchCodec.read(binding.dossier(),artifacts,checked);
        RuleDomain domain=null;RuleResearchDossier dossier=null;
        try { domain=ResearchCodec.decode(domainBytes,RuleDomain.class); }
        catch(IOException invalid){reasons.add("INVALID_DOMAIN");}
        try { dossier=ResearchCodec.decode(dossierBytes,RuleResearchDossier.class); }
        catch(IOException invalid){reasons.add("INVALID_DOSSIER");}
        if(domain!=null){
            if(!Arrays.equals(domainBytes,ResearchCodec.bytes(domain)))reasons.add("NONCANONICAL_DOMAIN");
            if(domain.entityKinds().isEmpty()||domain.chemistryClauses().isEmpty())reasons.add("INCOMPLETE_DOMAIN");
            if(!domain.negativeCoverageVersion().equals(manifest.negativeCoverage().version()))reasons.add("NEGATIVE_COVERAGE_DOMAIN_MISMATCH");
            ResearchCodec.verifySources(domain,artifacts,checked);
        }
        if(dossier!=null){
            if(policy.v2()!=dossier.schema().equals("athena-rule-research-dossier/2"))reasons.add("MIXED_RESEARCH_VERSIONS");
            if(!Arrays.equals(dossierBytes,ResearchCodec.bytes(dossier)))reasons.add("NONCANONICAL_DOSSIER");
            if(!dossier.ruleKey().equals(manifest.key())||!dossier.definitionSha256().equals(definition)
                    ||!dossier.domain().equals(binding.domain()))reasons.add("DOSSIER_DEFINITION_DOMAIN_MISMATCH");
            if(!dossier.unresolvedRequirements().isEmpty())reasons.add("UNRESOLVED_RESEARCH_REQUIREMENTS");
            var review=dossier.review();
            if(!policy.authorizedReviewers().contains(review.reviewer()))reasons.add("UNAUTHORIZED_REVIEWER");
            if(!review.policy().equals(binding.reviewPolicy())||!review.approvedDefinitionSha256().equals(definition)
                    ||!review.approvedDomainSha256().equals(binding.domain().sha256()))reasons.add("REVIEW_BINDING_MISMATCH");
            if(review.reviewedAt().isAfter(evaluatedAt))reasons.add("REVIEW_IN_FUTURE");
            if(!review.validUntil().isAfter(review.reviewedAt()))reasons.add("INVALID_REVIEW_INTERVAL");
            if(!evaluatedAt.isBefore(review.validUntil()))reasons.add("REVIEW_EXPIRED");
            var systems=new HashSet<String>();
            for(var audit:dossier.implementationAudits()){
                if(!systems.add(audit.system()))reasons.add("DUPLICATE_SYSTEM_AUDIT");
                audit(audit,reasons);
            }
            if(!systems.containsAll(policy.requiredSystems()))reasons.add("MISSING_SYSTEM_AUDIT");
            if(dossier.sources().isEmpty()||dossier.literatureAudits().isEmpty())reasons.add("MISSING_PRIMARY_PROVENANCE");
            dossier.literatureAudits().forEach(a->audit(a,reasons));
            if(dossier.datasetAudits().isEmpty())reasons.add("MISSING_DATASET_APPLICABILITY");
            for(var data:dossier.datasetAudits()){
                if(data.applicability()==UNINSPECTED)reasons.add("UNINSPECTED_DATASET_APPLICABILITY");
                if(data.sourcePins().isEmpty())reasons.add("MISSING_DATASET_PROVENANCE");
                if(data.applicability()==APPLICABLE&&data.analysisStatus()!=RuleResearchDossier.AnalysisStatus.ANALYZED)
                    reasons.add("EMPIRICAL_CALIBRATION_INCOMPLETE");
            }
            if(dossier.decisions().isEmpty())reasons.add("MISSING_CRITERION_DECISIONS");
            var decisions=new HashSet<String>();
            for(var decision:dossier.decisions()){
                if(!decisions.add(decision.id()))reasons.add("DUPLICATE_CRITERION_DECISION");
                if(!decision.domainSha256().equals(binding.domain().sha256()))reasons.add("DECISION_DOMAIN_MISMATCH");
                if(decision.sourcePins().isEmpty())reasons.add("MISSING_DECISION_PROVENANCE");
            }
            ResearchCodec.verifySources(dossier,artifacts,checked);
        }
        var cyclic=Set.of(binding.dossier().sha256(),binding.reviewPolicy().sha256());
        for(var source:manifest.scientificSources())if(cyclic.contains(source.sha256()))reasons.add("CYCLIC_SCIENTIFIC_SOURCE");
        for(var source:manifest.referenceArtifacts())if(cyclic.contains(source.sha256()))reasons.add("CYCLIC_REFERENCE_SOURCE");
        for(var source:manifest.scientificSources())ResearchCodec.read(source,artifacts,checked);
        for(var source:manifest.referenceArtifacts())ResearchCodec.read(source,artifacts,checked);
        for(var invalidation:context.invalidations())if(!invalidation.effectiveAt().isAfter(evaluatedAt)
                &&(checked.contains(invalidation.targetSha256())||invalidation.targetSha256().equals(definition)
                ||invalidation.targetSha256().equals(RuleRegistry.digest(manifest))))reasons.add("EFFECTIVE_INVALIDATION");
        return new ResearchEligibility("athena-rule-research-eligibility/1",RuleRegistry.digest(manifest),binding.dossier().sha256(),
                binding.domain().sha256(),binding.reviewPolicy().sha256(),ResearchCodec.digest(context),evaluatedAt,mode,
                reasons.isEmpty(),List.copyOf(reasons),List.copyOf(checked));
    }
    private static void audit(RuleResearchDossier.Audit audit,Set<String> reasons){
        if(audit.applicability()==UNINSPECTED)reasons.add("UNINSPECTED_REQUIRED_AUDIT");
        if(audit.inspectedArtifacts().isEmpty())reasons.add("MISSING_AUDIT_PROVENANCE");
        if(audit.applicability()==APPLICABLE&&audit.entryPoints().isEmpty())reasons.add("MISSING_IMPLEMENTATION_ENTRY_POINTS");
    }
}
