# Research authority and executable qualification /2

The [approved contract](../../../../software/qualification/research-separation-v2-contract-20261006/DESIGN.txt) and [verbatim implementation approval](../../../../software/qualification/research-separation-v2-20261006/APPROVAL.txt) define this milestone. Its [checkpoint](../../../../software/qualification/research-separation-v2-20261006/CHECKPOINT.txt) records tests and preservation. This is governance infrastructure; it activates no scientific rule or real authority.

## Definition and supporting rationale

`RuleReviewPolicyV2` separates scientific reviewers, implementation reviewers and execution-context issuers. Scientific identities cannot also occupy executor roles in one policy. Scientific approval does not authorize an implementation report, and executor authority cannot approve a definition. Per-review `reviewedAt` and `validUntil` remain mandatory. No lifetime, automatic renewal or backdated expiry is introduced.

`ResearchBinding` and `RuleResearchDossier` retain Java component signatures. Their `/1` schemas require projection `/1`; `/2` schemas require projection `/2`. Policy/binding/dossier/implementation-report versions must agree. The manifest remains `athena-rule/3` and evidence formats are unchanged.

Scientific projection `/2` retains the reviewed rule fields and domain digest, excluding only `implementationId`, `implementationVersion` and `referenceArtifacts`. Exact fields are listed in the approved contract. Scientific sources, parameters, domain, perception, measurement requirements and negative coverage remain bound. Implementation-only revisions do not erase an unchanged scientific review. No semantic equivalence is inferred. Material scientific information must not be moved into implementation-reference artifacts to evade scientific review.

`RuleImplementationQualificationV2.manifestSha256` binds the full executable manifest. An old report or receipt cannot be reused after any full-manifest change. A fresh verified report may qualify against an unchanged, unexpired scientific dossier. Scientific changes invalidate old scientific approval. Artifact/context invalidation can prevent current execution without rewriting historical review evidence.

## Reuse and operation

Internal policy/report adapters normalize metadata for the existing checks while retaining original versioned documents for hashes and serialization. `ScientificRuleResearchGate`, `RuleQualification`, registry verification and `CurrentRuleExecution` reuse the existing canonical codec, admission, time authority, state binding and persistence. Public pipeline signatures and `ResearchExecutionInputs` remain unchanged. No second codec, evidence store or qualification engine is introduced.

`NOT_APPLICABLE` remains valid with attributed rationale/source inspection. Applicable but unanalyzed datasets fail. Neither an inventory entry nor research eligibility alone produces qualification. Current-policy qualification requires authorized scientific review, valid eligibility, implementation checks and valid receipt/execution binding.

## Supporting-material decisions and limits

The pinned `/1` source and characterization establish the previous coupling: implementation fields in the scientific digest and one shared reviewer list. ADOPT existing canonicalization, explicit expiry, provenance checks and preservation. MODIFY only the opt-in projection and authority split. REJECT automatic scientific authority for an executor, universal expiry, implicit upgrades and stale implementation report reuse. No empirical dataset or chemistry literature can establish a universal governance lifetime; none is claimed or acquired.

Historical `/1` codecs, document bytes, projections, authority semantics, expiry and replay remain unchanged. New tests use explicitly synthetic authorities and reviews, never the real approved agent identities. No real policy, scientific approval, expiry or production receipt is issued in this checkpoint. This infrastructure does not assert chemical truth or close the 96-capability program.
