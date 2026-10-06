package totah.lab.daedalus.system;

import org.junit.jupiter.api.Test;
import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.*;
import java.io.IOException;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PinnedInvocationTimeAuthorityTest {
    private static final Path PACKAGE = Path.of(System.getProperty("athena.reviewPackage"));
    private record Fixture(RuleReviewPolicyV2 policy, Source policyPin, RulePolicyContext context,
                           Instant time, String contextHash) {
        PinnedInvocationTimeAuthority authority() throws IOException {
            return new PinnedInvocationTimeAuthority(policy,policyPin,context.issuer(),contextHash,Clock.fixed(time,ZoneOffset.UTC));
        }
    }
    private static Fixture fixture() throws Exception {
        byte[] bytes=Files.readAllBytes(PACKAGE.resolve("POLICY_DRAFT.json"));
        var policy=ResearchDocuments.decode(bytes,RuleReviewPolicyV2.class);
        var pin=new Source("inactive-review-package",EvidenceExchange.sha256(bytes),"inactive real-authority policy draft");
        // Execution-time evidence is within Codex's authority, never a scientific review.
        var now=Instant.now();var evidence=new Source("engineering-only-context",EvidenceExchange.sha256("engineering-only".getBytes(java.nio.charset.StandardCharsets.UTF_8)),"no scientific approval");
        var context=new RulePolicyContext("athena-rule-policy-context/1",pin,List.of(),now,policy.authorizedContextIssuers().getFirst(),evidence);
        return new Fixture(policy,pin,context,now,EvidenceExchange.sha256(ResearchDocuments.encode(context)));
    }
    public static void main(String[] args) throws Exception {
        var f=fixture();var at=Instant.parse("2026-10-06T00:00:00Z"); // replay fixture only, not scientific approval time
        var c=new RulePolicyContext(f.context.schema(),f.policyPin,List.of(),at,f.context.issuer(),f.context.contextSource());
        var authority=new PinnedInvocationTimeAuthority(f.policy,f.policyPin,c.issuer(),EvidenceExchange.sha256(ResearchDocuments.encode(c)),Clock.fixed(at,ZoneOffset.UTC));
        authority.verifyCurrent(c,at);
        assertThrows(IOException.class,()->authority.verifyCurrent(c,at.plusSeconds(1)));
        Files.write(Path.of(args[0]),ResearchDocuments.encode(Map.of("context",c,"policyPin",f.policyPin,"valid","PASS","wrongTime","REJECTED","productionReceipt","NOT_ISSUED")));
    }
    @Test void realRolePolicyAndTrustedCapturedInvocationPass() throws Exception {
        var f=fixture();var a=f.authority();assertEquals(f.time,a.invocationTime());a.verifyCurrent(f.context,f.time);
        assertEquals("codex-local-qualification",f.context.issuer().id());
        assertEquals("project-user",f.policy.authorizedScientificReviewers().getFirst().id());
    }
    @Test void callerSuppliedEqualTimesCannotReplaceTrustedTime() throws Exception {
        var f=fixture();var c=f.context;var other=new RulePolicyContext(c.schema(),c.policy(),c.invalidations(),c.asOf().minusSeconds(1),c.issuer(),c.contextSource());
        assertThrows(IOException.class,()->f.authority().verifyCurrent(other,other.asOf()));
    }
    @Test void changedContextWithSameIssuerAndTimeFails() throws Exception {
        var f=fixture();var c=f.context;var changed=new Source("changed",c.contextSource().sha256(),"changed custody");
        var other=new RulePolicyContext(c.schema(),c.policy(),c.invalidations(),c.asOf(),c.issuer(),changed);
        assertThrows(IOException.class,()->f.authority().verifyCurrent(other,f.time));
    }
    @Test void scientificAuthorityCannotIssueExecutionContext() throws Exception {
        var f=fixture();assertThrows(IOException.class,()->new PinnedInvocationTimeAuthority(f.policy,f.policyPin,f.policy.authorizedScientificReviewers().getFirst(),f.contextHash,Clock.fixed(f.time,ZoneOffset.UTC)));
    }
    @Test void policyPinTamperFails() throws Exception {
        var f=fixture();assertThrows(IOException.class,()->new PinnedInvocationTimeAuthority(f.policy,new Source("tampered","0".repeat(64),"tampered"),f.context.issuer(),f.contextHash,Clock.fixed(f.time,ZoneOffset.UTC)));
    }
    @Test void invalidationCannotBeRemovedOrInjected() throws Exception {
        var f=fixture();var c=f.context;var invalidation=new RulePolicyContext.Invalidation(f.contextHash,f.time,"engineering tamper",c.contextSource());
        var other=new RulePolicyContext(c.schema(),c.policy(),List.of(invalidation),c.asOf(),c.issuer(),c.contextSource());
        assertThrows(IOException.class,()->f.authority().verifyCurrent(other,f.time));
    }
    @Test void realPackageCannotBecomeReviewOrActivationImplicitly() throws Exception {
        assertThrows(IOException.class,()->ResearchDocuments.decode(Files.readAllBytes(PACKAGE.resolve("DOSSIER_REVIEW_MATERIAL.json")),RuleResearchDossier.class));
        assertThrows(Exception.class,()->ResearchDocuments.decode(Files.readAllBytes(PACKAGE.resolve("MANIFEST_UNBOUND.json")),totah.lab.athena.system.rules.RuleManifest.class));
        assertFalse(Files.exists(PACKAGE.resolve("RECEIPT.json")));
    }
    @Test void domainIsCanonicalExistingContract() throws Exception {
        byte[] bytes=Files.readAllBytes(PACKAGE.resolve("DOMAIN.json"));assertArrayEquals(bytes,ResearchDocuments.encode(ResearchDocuments.decode(bytes,RuleDomain.class)));
    }
}
