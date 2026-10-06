# First real policy preparation and local caller binding

The [R01 preservation checkpoint](../../../../software/qualification/activation-dependency-closure-20261006/CHECKPOINT.txt)
commits existing accepted dependency bytes. The [R02 checkpoint](../../../../software/qualification/activation-clean-source-20261006/CHECKPOINT.txt)
qualifies the bounded execution closure from committed sources and empty build outputs; it is not whole-foundation closure.

The [first review package](../../../../software/qualification/first-real-rule-package-20261006/REVIEW_REQUEST.txt)
selects existing `ATHENA.SULF.SS_CONNECTIVITY`, not biological disulfide chemistry. Its original definition,
negative-coverage semantics and implementation remain unchanged. Domain and scientific-projection digests
are prepared using projection `/2`. Existing supporting sources and rejected alternatives are linked.
The policy draft uses the approved human scientific and Codex implementation/context identities.
It is not installed in a registry. Mandatory human review, decision-source attribution and explicit
reviewedAt/validUntil remain absent. No scientific review, expiry or production receipt is manufactured.

`PinnedInvocationTimeAuthority` is an opt-in package-private Daedalus adapter for the existing
`ResearchTimeAuthority` callback. A local caller supplies the pinned policy, allowed context issuer,
canonical context digest and trusted invocation clock. The adapter captures that invocation time once;
request/context timestamps must match it and the complete pinned context must match. Policy digest and
issuer membership are checked at construction. Scientific review authority does not authorize context issuance.
This introduces no universal age window, automatic renewal, new schema or public API.

Trust is application-controlled custody of those pins and the invocation clock. Passing request-controlled
values into every constructor argument would not authenticate anything and is outside qualification.
The adapter does not provide remote identity verification, signatures or a web authentication system.
No production caller is wired automatically. Existing pipeline tests establish how callback failure
prevents evaluation while preserving admitted evidence; focused adapter tests check altered issuer,
policy, context, invalidation list and time. Independent-JVM replay is deterministic.

Admission still precedes interpretation. This callback neither supplies scientific review nor issues
qualification receipts. Before activation, the human must review the exact package and validity interval,
and implementation qualification must bind the final complete manifest after that review. An incomplete
review package remains unreadable as a valid dossier and cannot silently activate a rule.
