MNEMOSYNE — SCIENTIFIC EVIDENCE FOUNDATION, FIRST SLICE

Mnemosyne preserves immutable scientific observations and their provenance,
reviews, assessments, and historical relationships. It does not determine
scientific truth.

It must not become a dumping ground for scientific classes. Detailed results,
protocol implementations, chemistry, physical models, domain acceptance rules,
execution and reuse remain with their existing authorities. Mnemosyne supplies
the small common semantic layer and references those results.

Ownership and class justification
---------------------------------
ScientificReference: existing Athena references require an upward dependency and
lack namespace/kind. Existing structural and quantum identities have different
scientific scopes. This reference preserves external IDs and distinguishes axes;
it does not replace or canonicalize domain identity.
Observation: existing Athena qualification requires DesignState; Prometheus
records require computational domain objects. Neither can own this graph-free
scalar observation. Small nested Scalar, Uncertainty and Provenance values avoid
importing domain payloads into the common layer.
Review: existing source review includes design qualification. The common review
must cite an observation independently of molecular state and other reviewers.
Assessment: existing Athena Evaluation requires a successful design attempt.
The common envelope instead references a proposition and criterion; it supplies
no hypothesis language, inference or scoring algorithm.
EvidenceHistory: neither existing registry can own cross-domain observation ID
conflicts and independent review revisions without reversing dependencies.
This is a bounded immutable in-memory working set, NOT a persistent registry,
database, event-sourcing system or alternate domain authority.

Adapters remain in Athena and Prometheus. Athena's nested Binding links the
shared observation/review to an unchanged domain SourceDecision; it is a
historical linkage, not a live authorization capability. Call bind again against
the appropriate current history when making a new decision. Existing frozen
snapshots are not silently rewritten after a retraction.

Semantics
---------
All references include kind, namespace, ID and version. Byte artifact, source,
subject/state, method/specification, actual run, observation, review and assessment
are separate kinds. IDs for observations and real activities are supplied by the
caller. The library does not authenticate that two alleged runs actually occurred.
A content/specification hash cannot automatically identify a run or replicate.

Observation availability is PRESENT, UNAVAILABLE or FAILED_INVALID; it is not a
truth/acceptance status. Scalar retains the original numeric text and unit.
Only the narrow scalar vocabulary needed by these adapters is implemented.
Unknown uncertainty has an explicit reason; reported intervals and named errors
are separate types. No confidence level, conversion or statistical estimate is
inferred from a point value. Larger domain payloads remain outside the module.

Reviews identify reviewer, process, policy, scope, reasons, time and optional
qualification references. ACCEPTED and REJECTED decisions can coexist. Assessment
is an attributed assertion, not a computation of truth. The bounded history checks
its observation/review/context linkage and requires an admissible present
observation for support, contradiction or unresolved findings. FAILED_INVALID
can record an unusable evaluation. NOT_MEASURED has neither an observation nor
review reference; it records criterion coverage without inventing a measurement.

Append returns a new working set. Exact re-import returns the existing working
set; changed content under the same kind/namespace/ID/version fails closed.
Different observation IDs and activity IDs retain independent equal-value records.
ReviewChange withdraws or supersedes an existing review without deleting it.
knownAt/effectiveAt queries distinguish late-known changes from prior admissibility.
assessmentsAsOf returns historical assertions; a later withdrawal does not erase
or silently reinterpret them. Supersession is deliberately narrow: a later review
by the same reviewer of the same observation and scope. No generalized revision
language or cycle-solving engine is provided.

No production library dependency beyond the JDK. Athena and Prometheus depend
on this module, never the reverse. The parent compiler's existing annotation
processor configuration is inherited; it does not introduce domain runtime types.
No new hashing algorithm, canonical chemistry, unit framework, storage format,
source importer or executor is introduced.
