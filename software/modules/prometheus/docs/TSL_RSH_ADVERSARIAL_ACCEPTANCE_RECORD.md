# TSL-RSH Adversarial Acceptance — Execution Record

Date: 2026-08-23. Companion to `TSL_RSH_ADVERSARIAL_TEST_SUITE.md` (the spec; its
invariants were not weakened or reinterpreted).

## Rescan — second fix round (uncommitted, on top of `52742249d`)

Re-run of the full suite against the current working tree: **81 tests, 81 pass,
0 fail** (`mvn test -Dtest='Adversarial*'`). All six prior post-fix failures are
closed:

- **B1**: `CanonicalEvidenceStore` now verifies the manifest against disk
  (payload-missing named per file, path-escape rejected) instead of counting
  directory contents.
- **B3**: `CalculationSpecification` now stores constraints/requiredOutputs/
  acceptanceGates as unique lexicographically sorted lists with a written
  identity contract, and `QuantumScientificIdentity` canonicalizes the same
  way — reordered inputs produce identical identity, content changes still
  change it.
- **B8**: `TrainingTarget` and `FermiNetKfacState` block accessors no longer
  leak internal arrays.
- **A3 residual**: the preflight gradient path now goes through package-private
  `readFiniteGradient` with a per-token finiteness check; pinned by the new
  `execution/AdversarialGradientFinitenessAcceptanceTest` (NaN/±Inf at
  first/middle/last, wrong count, finite control).
- **E1/E2 unblocked**: the second fix round added the persistence contract
  (`persistence/FitArtifact` + `persistence/FitArtifactWriter`, exposed via
  `DeltaModelTrainer.persistSuccessfulFit`): atomic staged write, per-component
  checksums, mirror verification, read-back-verified receipt, SUCCESS-only
  publication gate. New `persistence/AdversarialFitPersistenceAcceptanceTest`
  (5 tests) executes the Layer-E oracles against it: coefficient round-trip
  exactness, FAILED/stateless fits unpublishable, tampered mirror/metadata
  rejected, deleted component fails by name, split ids round-trip and a
  published fit directory is immutable.

Remaining open item: **C6 ANGLE_PAIR** stays SPECIFICATION_BLOCKED (motif
convention still unwritten; tuple deliberately untouched). The "no independent
oracle" list in the suite doc is unchanged.

## Methodology

- **Post-fix run**: working tree (= commit `52742249d` "Fix second-round
  scientific audit defects"). `mvn test -Dtest='Adversarial*'` →
  **74 tests, 68 pass, 6 fail**.
- **Pre-fix run**: commit `e4679165a` via a `git worktree` at `/tmp/totah-prefix`
  (main tree untouched; worktree removed after the run). 23 new test files were
  copied in; 4 classes reference test seams that only exist post-fix and cannot
  compile at e4679165a — they were shelved and their pre-fix status is recorded
  from direct code inspection during the audit (marked *inspection*). Executed:
  **54 tests, 39 pass, 15 fail, 2 errors** (the 2 errors are E5's fixture being
  untracked and therefore absent from the worktree — not behavioral).
- All test files are new; no production code was modified by this work. Tests
  were written without inspecting the concurrent fix-round's test files.

## Corrections and audit conclusions mandated by the acceptance task

- **requiredOutputs correction**: the pre-fix `QuantumScientificIdentity` did
  omit `requiredOutputs` (the original audit finding was correct for the code
  audited); the post-fix code appends them (`QuantumScientificIdentity.java:30`).
  Record updated accordingly.
- **Canonical-ordering audit**: `CalculationSpecification.constraints /
  requiredOutputs / acceptanceGates` are ordered `List`s hashed with
  `CanonicalHashing.sequence` (documented "ordered sequence" — no sorting).
  `QuantumScientificIdentity` appends constraints/outputs/gates in encounter
  order; only `observables` is sorted. Consequence: the mandated invariant
  "identical scientific requests with reordered set-like inputs produce
  identical identity" is **violated by both pre- and post-fix code** for the
  three list fields (B3 equality tests fail on both). Sensitivity ("removing or
  changing a gate changes identity") holds post-fix, and held only partially
  pre-fix (requiredOutputs changes were invisible pre-fix).
- **FourBodyBasis tuple**: verified unchanged. The fix round added the
  `ANGLE_PAIR_SHARED_FOURTH` kind but did not alter the `ANGLE_PAIR` tuple
  (∠(0,1,2), ∠(0,1,3)); the motif-convention question stays open and C6's
  angle-pair value oracle stays SPECIFICATION_BLOCKED.

## BLOCKING tier

| TEST | Executable test (class#method anchor) | Pre-fix | Post-fix |
|---|---|---|---|
| A1 | execution/AdversarialQmTruthAcceptanceTest (missing `energy_hartree` → IOException, `requiredFiniteDouble` + public reader) | FAIL (inspection: `asDouble()`→0.0 at old ForceCampaignPreflightRunner:170) | **PASS** |
| A2 | same class (non-numeric `"N/A"` energy) | FAIL (inspection: same defect) | **PASS** |
| A3 | same class (1e400 energy; NaN gradient token at first/middle/last via public reader) + execution/quantum/AdversarialForceSignAcceptanceTest (NaN/Inf sign gate) | energy: FAIL (inspection); sign gate: **FAIL (executed)**; public-reader gradient: PASS both | **PASS** |
| A10 | same class (manifest-listed geometry deleted → verifyFrozenInputs throws) | FAIL (inspection: `isRegularFile &&` short-circuit, old line 223) | **PASS** |
| B1 | store/AdversarialStoreIntegrityAcceptanceTest#deletedManifestListedPayloadFailsLoadNamingTheMissingFile | FAIL (inspection: count-check message names no file) | **FAIL** — load does refuse (count mismatch vs manifest) but the error names no payload and does not distinguish missing from modified; message contract unmet |
| B4 | planning/AdversarialReuseElectronicStateTest#differentElectronicStateIsNeitherReusedNorDerived | **FAIL (executed)** | **PASS** |
| C1 | ingest/authoritative/AdversarialPrmtopAcceptanceTest#c1_atomNamesContainingDRoundTripByteExact | **FAIL (executed)** | **PASS** |
| C6 | potential/delta/basis/AdversarialBasisOracleTest — torsion exact-oracle | PASS (executed) | **PASS**; ANGLE_PAIR value oracle: **SPECIFICATION_BLOCKED** (no written motif convention; tuple intentionally unchanged) |
| C8 | neural/ferminet/force/AdversarialSampleStatisticsAcceptanceTest (failed sample absent, mean exact over survivors, no AIOOBE) | FAIL (inspection: `continue` left primitive 0.0; tails() array over-run) | **PASS** |
| C9 | same class (single validity channel; 0.0 is data, NaN is not) | FAIL (inspection: dual mask/array channels disagreed) | **PASS** |
| C10 | same class (bad sample at first/middle/last → identical statistics) | FAIL (inspection) | **PASS** |
| D2 | execution/AdversarialQmTruthAcceptanceTest (holdout split deleted; re-sealed-but-emptied split) | PASS (inspection, incidental: the holdout seal throws on the missing/mismatched split file — the general deletion gate A10 is what was broken) | **PASS** |

**BLOCKING tier verdict: all green post-fix except B1 (detection works; error
message contract fails) and the C6 angle-pair oracle (SPECIFICATION_BLOCKED).**

## HIGH_VALUE tier

| TEST | Executable test | Pre-fix | Post-fix |
|---|---|---|---|
| A4 | execution/quantum/AdversarialForceSignAcceptanceTest (asymmetric non-zero fixture, both directions) | PASS (executed) | PASS |
| A5 | execution/AdversarialQmTruthAcceptanceTest (self-contained FD units oracle + LengthUnit constant pin) | PASS (inspection: constant unchanged) | PASS — note: no production FD check consumes this oracle yet, by design |
| A6 | identity/AdversarialAtomOrderingAcceptanceTest | PASS (executed) | PASS |
| A7/A8 | planning/AdversarialElectronicStateAcceptanceTest | PASS (executed; EvidencePlanner's state filter pre-existed) | PASS |
| B3 | planning/AdversarialIdentityCanonicalizationTest + execution/quantum/AdversarialQuantumIdentityTest | equality: **FAIL**; sensitivity: **FAIL** (requiredOutputs invisible) | equality: **FAIL** (ordered lists — open deviation from the mandated invariant); sensitivity: PASS |
| B6 | neural/ferminet/runtime/AdversarialCheckpointProvenanceAcceptanceTest | seam absent pre-fix (no `verifyCheckpoint` API; not compilable) | PASS (4/4: absent checksum, altered payload, foreign-verified claim, honest control) |
| B8 | numerics/AdversarialPcgResultImmutabilityAcceptanceTest; potential/delta/training/AdversarialTrainingTargetImmutabilityAcceptanceTest; neural/ferminet/runtime/AdversarialKfacStateImmutabilityAcceptanceTest; evidence/AdversarialEvidenceImmutabilityAcceptanceTest | PCG: **FAIL (executed)**; TrainingTarget: **FAIL**; KfacState: **FAIL ×2**; QuantumEvidence: PASS | PCG: PASS; TrainingTarget: **FAIL**; KfacState: **FAIL ×2**; QuantumEvidence: PASS — three accessor leaks remain |
| C5 | potential/delta/basis/AdversarialBasisOracleTest (all-nonzero coefficients, mid-switch point, exact force-sum-zero) | PASS (executed) | PASS |
| C11 | potential/delta/training/AdversarialLeakageAcceptanceTest | PASS (executed) — no leakage seam exists: trainer is an authorization gate, no centering/scaling/PCA in the fitting path; strongest applicable sub-oracles implemented (labeled C11-SUB-a..d) | PASS |
| D1 | validation/AdversarialValidationGateAcceptanceTest (absurd metrics fail and name the metric) | PASS (executed) | PASS |
| D3 | potential/delta/training/AdversarialFitSensitivityTest (teacher-recovery OLS oracle through production basis/model; +0.5 label moves prediction by δ·leverage) | PASS (executed) — caveat: no production fitter maps dataset→parameters; the fit step is a documented hand-computed oracle over production features | PASS |
| D9/D10 | potential/hybrid/AdversarialInvarianceAcceptanceTest (t=(7.3,−2.1,11.0); R=Rot((1,2,3)/√14, 37°); covariance, conservation to round-off) | PASS (executed) | PASS |
| D11 | potential/delta/training/AdversarialFitSensitivityTest (+13.7 Ha shift; c₀ absorbs it, forces unchanged to solver tolerance) | PASS (executed) | PASS |

## REGRESSION / NICE_TO_HAVE (executed with the same runs)

- C2: FAIL pre-fix (shared fixture's D-names corrupted the file-level
  assertions), PASS post-fix. C3: FAIL pre-fix (type `SD`→`SE`), PASS post-fix.
  C4, C7: PASS both.
- B2: PASS post-fix (not executed pre-fix — same shelved class as B1).
- D4, D6: **FAIL pre-fix → PASS post-fix** (component-identity rejection added
  in the fix round). D5, D7: PASS both.
- D8: **FAIL pre-fix → PASS post-fix** (planarity check hardened in the fix
  round; fixture upgraded to 4-atom because 3 atoms are trivially coplanar).
- D12: PASS both (O/H swap yields ΔE=0.36 Ha; production does not reject, so the
  "different E" branch of the oracle applies).
- E3, E4: PASS both — checkpoint resume is bit-identical to uninterrupted
  execution; truncation/tail-clip/flipped-byte/trailing-byte all refused.
- E5: PASS post-fix against the real frozen cloud
  `analysis/mettl7-phase2/execution-unit-05O/force-cloud-qm/` (partition
  disjoint/union-complete, sidecar hashes match, seal binding verified via
  `frozen_artifacts.*_sha256`; tamper sub-case detected). Pre-fix worktree:
  not executable (fixture is untracked; 2 errors were fixture-absence).
  Naming note: the seal field is not literally `split_manifest_sha256`; the
  equivalent binding was asserted.
- E1, E2: **SPECIFICATION_BLOCKED** — no persistence seam exists for a fitted
  delta model: `DeltaModelParameters` has no writer, `DeltaModelTrainer` has no
  fit method, `DeltaTrainingDataset` is unreferenced in `src/main`. The missing
  artifact is the persistence contract itself.

## Open deviations requiring a decision (not silently accepted)

Resolved by the second fix round (see "Rescan" above): B3 canonical ordering,
B1 message contract, B8 residual leaks, A3 private gradient path.

Still open:

1. **C6 ANGLE_PAIR** — SPECIFICATION_BLOCKED pending a written motif convention;
   the tuple remains deliberately unchanged.
2. **E1/E2 were unblocked by the new FitArtifact seam and now pass** — but the
   seam covers artifact persistence only; nothing yet constructs a FitArtifact
   from an actual delta-model training run (no production fitter exists). A
   future successful fit must wire its coefficients/decomposition/optimizer
   state into this boundary before reporting success.
