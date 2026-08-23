# Scientific software correctness re-audit V2

## Scope

This audit independently reproduced the findings against `main` at
`e4679165ac6b9f077548c0d4ae0860ded4c7c726`. It did not run QM, retrain a
model, change a scientific threshold, or modify an immutable scientific label.
The prior atlas invalidation remains in force.

## Finding register

Each row records the required `FINDING`, `REPRODUCTION_TEST`, `CONFIRMED`,
`ROOT_CAUSE`, `FIX`, `ADVERSARIAL_TEST`, `AFFECTED_ARTIFACTS`,
`SCIENTIFIC_RESULTS_POTENTIALLY_AFFECTED`, and `RECOMPUTATION_REQUIRED` fields.

| # | FINDING / CONFIRMED | REPRODUCTION_TEST | ROOT_CAUSE | FIX / ADVERSARIAL_TEST | AFFECTED_ARTIFACTS / SCIENTIFIC_RESULTS_POTENTIALLY_AFFECTED / RECOMPUTATION_REQUIRED |
|---|---|---|---|---|---|
| 1 | Amber fixed strings received numeric `D→E`; **CONFIRMED=true** | Real fixed-width fixture contains `CD1`, `HD11`, `SD`, atom names/types and `D`-exponent charges. Pre-fix produces `CE1`, `HE11`, `SE`. | One tokenizer normalized all section types. | Separate numeric and character tokenization. `AmberPrmtopReaderTest.parsesFortranDExponentWithoutTransformingFixedWidthIdentifiers`. | No production caller or generated artifact references `AmberPrmtopReader`; affected-artifact inventory is empty. Future topology identities/charges could be affected. **RECOMPUTATION_REQUIRED=false**. |
| 2 | AC-ZVZB pass-1 failures left phantom finite zero samples, biased statistics and could overrun `tails`; **CONFIRMED=true** | Source-level reproduction plus beginning/middle/end invalid-sample tests. A finite zero survived while the mask was false; `finiteCount` underallocated the tail buffer. | Java primitive arrays default to `0.0`; validity had two inconsistent representations. | All sample buffers initialize to NaN; numerical arrays are authoritative; count mismatches fail closed. Hand-computable finite means/counts and tail construction are tested in both estimator suites. | Three archived H2O AC-ZVZB/DERIV result files were inventoried. All declare `nonfiniteCount=0` for every component, so their stored numbers did not traverse the defect. Failed-sample runs would be affected. **RECOMPUTATION_REQUIRED=false for inventoried files; true for any artifact with nonfinite samples**. |
| 3 | Deleted manifest files bypassed the force-cloud checksum loop; **CONFIRMED=true** | Manifest entry points to an absent file. Pre-fix continued because hashing was conditional on `isRegularFile`. | Absence and success shared the same branch. | Missing entries now throw before checksum comparison. `TslRshForceCloudQmRunnerTest.deletedManifestEntryFailsClosedInsteadOfBypassingChecksum`. | No immutable GPU-60 label changed; the committed GPU-60 SHA manifest independently verifies. Campaign input qualification was potentially affected. **RECOMPUTATION_REQUIRED=false**. |
| 4 | Missing/non-numeric preflight energy became `0.0`; **CONFIRMED=true** | `{}` and a textual `energy_hartree` both returned Jackson's primitive default pre-fix. | `path(...).asDouble()` omitted existence/type/finite checks. | `requiredFiniteDouble` rejects missing, non-number and nonfinite nodes. Dedicated preflight test includes valid negative control. | Existing authoritative MIN02 artifact contains a numeric finite energy; no corrected label is produced. Reuse decisions from malformed files were potentially affected. **RECOMPUTATION_REQUIRED=false for current artifact**. |
| 5 | Generation-2 numeric regex rejected negative exponents; **CONFIRMED=true** | `1.25E-12` truncated/failed pre-fix. | Character class allowed `+` but not exponent `-`. | Grammar now accepts signed decimal mantissas and signed exponents. Regression covers positive and negative mantissas with negative exponents. | Generation-2 postprocessor outputs containing negative-exponent notation were potentially affected. No immutable QM labels use this parser. **RECOMPUTATION_REQUIRED=unknown for external historical generation-2 outputs**. |
| 6 | NaN gradient/force pairs passed `force=-gradient`; **CONFIRMED=true** | NaN plus NaN makes every `>` comparison false. | Difference-only validation lacked operand finiteness and accepted NaN tolerance. | Both fields and tolerance must be finite. `nonfiniteForceOrGradientNeverPassesNegativeGradientInvariant`. | Any generated evidence containing NaNs could be falsely accepted. Current qualified force artifacts are finite. **RECOMPUTATION_REQUIRED=false for inventoried current evidence**. |
| 7 | Pathak-Wagner extrapolation passed total length as finite count; **CONFIRMED=true** | A panel containing NaN samples caused mean/variance denominators to disagree with values consumed. | Extrapolation bypassed the panel's actual finite count. | Counts are derived from values for every epsilon and extrapolated sample; mismatches fail closed. Invalid-position estimator tests exercise the same authority rule. | Archived PW panel has zero nonfinite samples in every component, so stored values are unchanged. Failed-sample extrapolations are affected. **RECOMPUTATION_REQUIRED=false for archived panel**. |
| 8 | SR residual-history size had no producer invariant and the diagnostic expected the wrong topology; **CONFIRMED=true** | Production structured SR emits one residual per direct solve; diagnostic required `solverIterations+1`. | Iterative-PCG history semantics survived after production moved to a direct structured solve. | Result constructor requires exactly one entry per direct solve; diagnostic uses the same invariant. Optimizer test asserts it. | SR diagnostic execution could stop falsely; optimizer state and energies are unaffected. **RECOMPUTATION_REQUIRED=false**. |
| 9 | Canonical store manifest paths were OS-dependent; **CONFIRMED=true** | A Windows-style `quantum\record.json` could not match portable manifest key `quantum/record.json`. | `Path.toString()` was used as a canonical identity. | Relative paths normalize separators to `/`; regression asserts the Windows representation. | Cross-platform evidence loading, not scientific values, was affected. **RECOMPUTATION_REQUIRED=false**. |
| 10 | Strategy evidence derivation omitted charge/multiplicity matching; **CONFIRMED=true (derivation branch only)** | Neutral-singlet Hessian was offered as a source for a charged-doublet derived target. Ordinary reuse already checked both fields. | Derivation filters used molecule/geometry/protocol but omitted electronic state. | Derivation now requires exact formal charge and multiplicity. Adversarial charged-doublet test expects `GENERATE_NEW`. | Affected planning/reuse decisions; no QM result changed. **RECOMPUTATION_REQUIRED=false; plans should be regenerated where mixed electronic states exist**. |
| 11 | PCG `solution()` exposed its mutable internal array; **CONFIRMED=true** | Mutating an accessor return changed later reads pre-fix. | Record constructor cloned once but generated accessor returned the field. | Accessor clones on every call; mutation regression added. The analogous diagnostic solver result was also hardened during the suspicious-pattern sweep. | Downstream numerical diagnostics could be mutated in memory; persisted current evidence was not shown affected. **RECOMPUTATION_REQUIRED=false**. |
| 12 | Quantum scientific identity omitted `requiredOutputs`; **CONFIRMED=true** | Two specifications differing only in requested outputs collided when solver observables were held constant. | Identity hashed observables and gates but not the specification output contract. | Required outputs now participate in identity; collision regression added. | Registry reuse identities for requests with different output contracts were potentially affected. **RECOMPUTATION_REQUIRED=true for reused evidence whose requests differed only by required outputs; otherwise false**. |
| 13 | Force-estimator finite-count and zero/norm guards were incomplete; **CONFIRMED=true** | Count/value mismatch and overflowing log-gradient norm were accepted into downstream arithmetic. | Callers supplied counts separately; squared norm could overflow after individually finite inputs. | Count equality is verified before statistics/tails; nodal squared norm must remain finite; invalid buffers are NaN. Tests cover invalid positions and count disagreement. | Same estimator artifacts as findings 2/7; current zero-nonfinite artifacts unchanged. **RECOMPUTATION_REQUIRED=false for inventoried files**. |
| 14 | Four-body sulfur angle-pair topology was not representable by the generic implementation; **CONFIRMED=true** | Locked protocol and preserved Python preflight independently define `P_l(cos 9-10-26) P_m(cos 11-10-26)`, while Java computed `9-10-11` and `9-10-26`. | One `ANGLE_PAIR` topology was incorrectly assumed for two preregistered motifs. | After provenance recovery, explicit `ANGLE_PAIR_SHARED_FOURTH` was added and analytically derived; test geometry gives the hand-computable first feature `0.5`. Existing shared-first behavior remains explicit. | No production four-body model was trained or frozen, so no model artifact is affected. **RECOMPUTATION_REQUIRED=false**. |
| 15 | Canonical force production did not require checkpoint verification; **CONFIRMED=true** | Estimator dispatch accepted a context containing only declared checksum strings. | Verification was an optional driver-side call, not a type-level pipeline prerequisite. | Unverified pipeline overloads fail closed. The production overload accepts only an externally unforgeable `Verified` context created by dataset plus checkpoint/payload verification. Regression proves unverified dispatch cannot invoke an estimator. | Driver-generated artifacts already persisted verification, but alternative callers could emit ambiguous evidence. **RECOMPUTATION_REQUIRED=false for driver artifacts; unknown for external callers**. |
| 16 | Force validation checked vector length but not Cartesian identity uniqueness/range; **CONFIRMED=true** | A same-length list with a duplicated `(nucleus,axis)` passed pre-fix. | Cardinality was treated as identity completeness. | Both validation paths require every in-range pair exactly once. Duplicate/missing adversarial test added. | Malformed externally constructed results could receive finite/physical diagnostics. Current canonical estimator ordering is complete. **RECOMPUTATION_REQUIRED=false**. |
| 17 | The z-coordinate diagnostic claimed generic molecular planarity; **CONFIRMED=true** | A molecule planar in the yz plane was not represented by the z-only criterion, and out-of-plane force meant z-force. | Orientation was hard-coded. | Plane normal is derived from molecular coordinates; all atoms are tested by perpendicular distance; force is projected onto that normal. Rotated-plane test verifies a hand-computable maximum of 3.0. | Diagnostic metadata, not force values, is affected. **RECOMPUTATION_REQUIRED=true for previously persisted planarity/out-of-plane diagnostics if interpreted scientifically**. |

## Affected-artifact inventory

- Amber reader: no production call site and no generated scientific artifact found.
- Estimator paths inspected:
  - `artifacts/prometheus/h2o/ferminet/forces/5137a8d88f97583a4180f9635a8ca595aef5e37765c7155836487e8f0155a3dc/AC_ZVZB/nuclear-force-result.json`
  - `.../AC_ZVZB_DERIV/nuclear-force-result.json`
  - `.../AC_ZVZB_DERIV_PW_PANEL/nuclear-force-result.json`
  Every component reports zero nonfinite samples. These files are not rewritten.
- GPU-60 immutable labels and their manifest were not changed.

## Suspicious-pattern sweep

The complete main-source tree was scanned for primitive-zero missing states,
unchecked Jackson numeric coercion, detached finite masks, NaN comparisons,
shared string/numeric transforms, conditional file guards, mutable array
accessors, incomplete identities, omitted electronic state, regex number
parsers, broad runtime catches, and length-only validation.

Confirmed additional hardening from the sweep:

- `TslRshForceCloudQmRunner` now validates result numeric node types and
  finiteness before scientific comparisons.
- `SolverConvergencePcg.Result` now clones its solution accessor as well as its
  constructor input.

Reviewed broad runtime catches in `FermiNetRuntimeSampling` and
`FermiNetVmcParallel` close/rethrow or transport/rethrow the original failure;
they do not convert implementation failures into samples. Legacy archive
ingestion catches record explicit ingestion errors rather than accepting
scientific evidence silently. Jackson sites using explicit NaN/sentinel
defaults are downstream-validated or diagnostic; remaining terse legacy
parsers remain a maintainability risk and are not silently reclassified as
confirmed scientific corruption.

## Test evidence

- Complete Prometheus suite: **463 run, 461 passed, 0 failed/errors, 2
  skipped** (`BUILD SUCCESS`).
- Atlas validation-integrity suite: **4 run, 4 passed, 0 failed, 0
  skipped**. The overflow warnings are intentionally induced by the absurd
  held-out-label mutation control; the invariance assertions pass.
- Combined: **467 run, 465 passed, 0 failed/errors, 2 skipped**.
- Scientific qualification registry: **8 `RUN_AND_PASS`; 2
  `NOT_RUN_MISSING_REQUIRED_ARTIFACT`**. The two tests not run are
  `LockedFermiNetH2oEnergyQualificationTest.measureMatchedBeforeAndAfterNeuralVmc`
  and `LockedFermiNetH2oCampaignTest.runLockedPretrainingCampaign`. Their
  absence is explicit in `SCIENTIFIC_QUALIFICATION_STATUS.json`; the green
  Maven result is not presented as qualification for them.

## Unresolved risks

1. Historical evidence generated by unknown external callers of the old
   unverified force-pipeline overload cannot be inventoried from this repository.
2. External generation-2 outputs using negative exponent notation require an
   external artifact inventory before recomputation can be decided.
3. Existing quantum registry records should be checked before reuse when two
   requests could differ only by their required-output contract.
4. No new QM or model training was performed, so this audit does not establish
   scientific model accuracy.
