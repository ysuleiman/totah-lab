package totah.lab.daedalus.system;

import totah.lab.athena.system.rules.RuleManifest.Source;
import totah.lab.athena.system.rules.research.*;
import totah.lab.mnemosyne.EvidenceExchange;
import totah.lab.mnemosyne.ScientificReference;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Opt-in local caller binding. Pins must come from application-controlled custody, not request fields. */
final class PinnedInvocationTimeAuthority implements ResearchTimeAuthority {
    private final Source policy;
    private final ScientificReference issuer;
    private final String contextSha256;
    private final Instant invocationTime;

    PinnedInvocationTimeAuthority(RuleReviewPolicyV2 policyDocument, Source policy,
            ScientificReference issuer, String contextSha256, Clock invocationClock) throws IOException {
        this.policy = Objects.requireNonNull(policy);
        this.issuer = Objects.requireNonNull(issuer);
        this.contextSha256 = Objects.requireNonNull(contextSha256);
        this.invocationTime = Objects.requireNonNull(invocationClock).instant();
        if (!EvidenceExchange.sha256(ResearchDocuments.encode(policyDocument)).equals(policy.sha256())
                || !policyDocument.authorizedContextIssuers().contains(issuer)
                || !contextSha256.matches("[0-9a-f]{64}")) {
            throw new IOException("untrusted invocation policy/issuer/context pin");
        }
    }

    Instant invocationTime() { return invocationTime; }

    @Override public void verifyCurrent(RulePolicyContext context, Instant evaluatedAt) throws IOException {
        if (!policy.equals(context.policy()) || !issuer.equals(context.issuer())
                || !invocationTime.equals(evaluatedAt) || !invocationTime.equals(context.asOf())
                || !contextSha256.equals(EvidenceExchange.sha256(ResearchDocuments.encode(context)))) {
            throw new IOException("context does not match trusted invocation binding");
        }
    }
}
