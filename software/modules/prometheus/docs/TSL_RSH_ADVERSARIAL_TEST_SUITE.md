# TSL-RSH Force-Field Pipeline — Adversarial Scientific Test Suite

Designed independently of any concurrent defect fixes. Each test is specified so a
scientifically wrong implementation fails even when it is internally consistent
(self-consistent wrong code passes its own unit tests; these tests use external or
hand-computed oracles).

Conventions:

- `PRE_FIX_CODE_EXPECTED_TO_FAIL` refers to the code as audited on 2026-08-23,
  before the concurrent fix round. It documents which tests are new-coverage vs.
  regression locks.
- Fixtures are minimal and hand-computable. Coordinates are in bohr unless noted.
  All "important equation terms" are nonzero in at least one fixture (no symmetric
  cancellations, no zero force components in sign tests, no 0/π torsion angles
  except where the exact angle is the oracle).
- No new QM is run. All QM-side fixtures are fabricated text artifacts
  (result.json, gradient files, prmtop sections, CSVs) with numbers chosen by hand.

---

## Layer A — QM truth generation

Targets: `PyscfEnergyGradientResultReader`, `TslRshForceCloudQmRunner`
(`qualify`, `campaign`, `execute`, `verifyFrozenInputs`),
`ForceCampaignPreflightRunner.authoritativeEnergyGradientAlias`.

### A1 — Missing `energy_hartree`

- TEST_ID: A1
- SCIENTIFIC_INVARIANT: An evidence record's energy must be a value the QM backend
  actually produced. Absent is not zero.
- FAILURE_MODE: `ObjectNode.path("energy_hartree").asDouble()` returns `0.0` for a
  missing node; 0.0 hartree is registered as the authoritative energy of a real
  molecule (confirmed defect in `ForceCampaignPreflightRunner.java:170`).
- MINIMAL_FIXTURE: a MIN02-shaped directory with `result.json` containing every
  field except `energy_hartree`, plus a well-formed
  `final_gradient_hartree_per_bohr.txt` of the right length.
- EXPECTED_RESULT: `IOException` (or explicit rejection) naming the missing field;
  nothing is registered.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: existing reader tests always include the
  field; Jackson's silent default makes the omission invisible to any test that
  only checks the happy path.

### A2 — Non-numeric `energy_hartree`

- TEST_ID: A2
- SCIENTIFIC_INVARIANT: as A1 — unparseable is not zero.
- FAILURE_MODE: `"energy_hartree": "converged"` or `"N/A"` also yields `0.0` via
  `asDouble()`.
- MINIMAL_FIXTURE: as A1 with `"energy_hartree": "N/A"`.
- EXPECTED_RESULT: rejection naming the field and offending value.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: type confusion is invisible to tests that
  construct fixtures through the same Jackson writer as production.

### A3 — NaN/Inf in energy or gradient

- TEST_ID: A3
- SCIENTIFIC_INVARIANT: Registered energies/gradients are finite real numbers.
- FAILURE_MODE: `Double.parseDouble("NaN")` and `parseDouble("Infinity")` succeed,
  so a gradient text file containing `NaN` tokens passes parsing and the length
  check in `ForceCampaignPreflightRunner.java:157-162`, and NaN propagates into
  the frozen registry. Also `forceIsNegativeGradient` treats NaN as "consistent"
  (`Math.abs(NaN) > t` is false).
- MINIMAL_FIXTURE: (a) gradient file with token `NaN` at the first, middle, and
  last position (three sub-cases); (b) result.json with `1e400` (parses to
  `Infinity`); (c) a force/gradient pair where force contains NaN, presented to
  the sign-consistency gate.
- EXPECTED_RESULT: every variant rejected with a finiteness error; no evidence
  registered; the sign gate never accepts NaN.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: finiteness is checked on some paths
  (`requiredFiniteNumber` in the qualify path) but not on the preflight alias
  path; tests exercising only the strict path conclude "finiteness is enforced".

### A4 — Force sign error

- TEST_ID: A4
- SCIENTIFIC_INVARIANT: F = −∇E, componentwise, in the artifact's stated units.
- FAILURE_MODE: a sign flip is invisible to magnitude checks and to any test whose
  fixture has symmetric or zero components.
- MINIMAL_FIXTURE: 2-atom heteronuclear molecule, hand-written gradient
  `[[0.13, -0.27, 0.41], [-0.11, 0.29, -0.37]]` (no zero components, not
  antisymmetric, so a row swap is also visible). Expected force is the exact
  negation. Second fixture: same numbers with force already negated (wrong).
- EXPECTED_RESULT: the correct-sign fixture passes the consistency gate exactly;
  the flipped fixture fails. Both assertions, not just one.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false (gate exists) — regression lock, and the
  fixture discipline (no zeros, no antisymmetry) is the actual contribution.
- WHY_EXISTING_TESTS_COULD_MISS_IT: fixtures built from H2 have F0 = −F1 and
  often a single nonzero axis; a row-swap *and* sign-flip bug can cancel in such
  fixtures.

### A5 — Wrong units (hartree/Å vs hartree/bohr)

- TEST_ID: A5
- SCIENTIFIC_INVARIANT: A gradient labeled `hartree_per_bohr` must be consistent
  with energy differences under bohr displacements: g ≈ ΔE/Δr with r in bohr.
- FAILURE_MODE: an Å-unit gradient is 1.8897× too large; tolerance-based
  reproduction against a trusted reference catches it only for the one qualified
  geometry — every other snapshot has no reference.
- MINIMAL_FIXTURE: fabricated 1-coordinate model: E(r) = E0 + g·r with
  g = 0.173 hartree/bohr; provide E at r and r±h (h = 1e-3 bohr) consistent with
  the bohr-unit gradient, and a second "gradient" file scaled by 1.8897261254
  (the Å impostor).
- EXPECTED_RESULT: the consistency check |ΔE/2h − g| < tol accepts the bohr
  gradient and rejects the Å gradient by ~89% relative error — far outside any
  finite-difference tolerance.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: n/a (new oracle; no such FD-consistency check
  exists in the pipeline today — that is the point).
- WHY_EXISTING_TESTS_COULD_MISS_IT: all current checks compare against values
  produced by the same unit convention; a convention shared by producer and
  checker is invisible.

### A6 — Wrong atom ordering

- TEST_ID: A6
- SCIENTIFIC_INVARIANT: Gradient row i belongs to the atom in row i of
  `ATOM_ORDER.csv`; permuted rows are a different (wrong) force field.
- FAILURE_MODE: a reader that sorts, deduplicates, or zips against a differently
  ordered list silently reassigns forces between atoms.
- MINIMAL_FIXTURE: 3-atom molecule with distinct elements (e.g. C, N, H) and
  gradient rows with distinct magnitudes (0.05, 0.11, 0.23 along x, others zero);
  a second fixture with rows 0 and 1 swapped.
- EXPECTED_RESULT: the pipeline either rejects the swapped fixture (ordering
  contract violated) or maps forces per the declared atom map; under no path may
  atom C end up with atom N's force while labeled C.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown (ordering contract is asserted per-file
  by checksum, but no test permutes rows against a fixed ATOM_ORDER).
- WHY_EXISTING_TESTS_COULD_MISS_IT: fixtures generated and read by the same code
  share any ordering bug.

### A7/A8 — Charge and multiplicity mismatch

- TEST_ID: A7, A8
- SCIENTIFIC_INVARIANT: Evidence computed at (charge, multiplicity) ≠ the
  requirement's is different science and must neither satisfy the requirement nor
  share an identity with it.
- FAILURE_MODE: identity/reuse logic that omits charge or multiplicity treats a
  cation doublet's energy as the neutral singlet's.
- MINIMAL_FIXTURE: two `CalculationSpecification`s identical except
  `formalCharge` 0 vs +1 (A7), `multiplicity` 1 vs 3 (A8); an artifact metadata
  fixture claiming charge +1 offered against a charge-0 requirement.
- EXPECTED_RESULT: distinct identities; reuse/derivation refused; the refusal is
  loud (reason recorded), not a silent empty match.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: partially — identity includes both fields
  (verified), but `StrategyEvidenceMatcher`'s DERIVE_FROM_EXISTING filter omits
  the charge/multiplicity match (audit finding), so A7/A8 fail on the reuse path.
- WHY_EXISTING_TESTS_COULD_MISS_IT: all existing reuse tests vary protocol or
  geometry, never electronic state.

### A9 — Requested-output mismatch

- TEST_ID: A9
- SCIENTIFIC_INVARIANT: Evidence delivers the observables it was required to
  deliver; a gradient-only artifact does not satisfy a HESSIAN requirement.
- FAILURE_MODE: matching by molecule/protocol only, ignoring required outputs.
- MINIMAL_FIXTURE: requirement with `requiredOutputs = [ENERGY, GRADIENT,
  HESSIAN]`; artifact record with energy+gradient only.
- EXPECTED_RESULT: requirement unresolved (missing-evidence plan lists Hessian),
  never "satisfied by reuse".
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false (identity covers requiredOutputs) —
  regression lock at the matcher level.
- WHY_EXISTING_TESTS_COULD_MISS_IT: matcher tests pair requirements with
  fully-populated evidence.

### A10 — Deleted frozen input

- TEST_ID: A10
- SCIENTIFIC_INVARIANT: Every entry of a checksum manifest names a file that
  exists and matches. Absence is tampering, not innocence.
- FAILURE_MODE: `Files.isRegularFile(p) && !sha.equals(expected)` short-circuits
  to pass when the file is gone (confirmed defect,
  `TslRshForceCloudQmRunner.java:223`).
- MINIMAL_FIXTURE: force-cloud tree whose SHA256SUMS lists N files; delete one
  that is not otherwise opened by the runner (e.g. a geometry file not in the
  first manifest row — deletion of manifest/status/seal is caught incidentally by
  later reads, which is exactly why existing runs never exposed this).
- EXPECTED_RESULT: `verifyFrozenInputs` throws before any calculation launches.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: integration tests run against intact trees;
  the only "tamper" test (if any) modifies content, which the current condition
  does catch.

### A11 — Changed geometry under the same nominal identifier

- TEST_ID: A11
- SCIENTIFIC_INVARIANT: Snapshot identity is content, not label. `S001` with
  different coordinates is a different snapshot.
- FAILURE_MODE: any path that keys on snapshot id without the geometry hash would
  reuse stale results or mislabel new ones.
- MINIMAL_FIXTURE: manifest row `S001,...,<shaA>` with geometry file content
  hashing to shaB (one coordinate changed by 0.001 bohr).
- EXPECTED_RESULT: `execute()` aborts on the checksum gate
  (`TslRshForceCloudQmRunner.java:107`); the frozen dataset artifact's
  `geometry_sha256` never silently tracks new content under an old id.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false — regression lock on a gate that is the
  last line of defense for A10's fix.
- WHY_EXISTING_TESTS_COULD_MISS_IT: n/a — cheap lock, keep forever.

---

## Layer B — Evidence / provenance

Targets: `CanonicalEvidenceStore`, `GeneratedEvidenceRegistry`,
`QuantumScientificIdentity`, `StrategyEvidenceMatcher`, `EvidenceBundle`,
checkpoint loaders.

### B1 — Checksum-listed store file deleted

- TEST_ID: B1
- SCIENTIFIC_INVARIANT: as A10, applied to the canonical evidence store.
- MINIMAL_FIXTURE: a compiled store generation; delete `quantum/<hash>.json`.
- EXPECTED_RESULT: load/verify fails naming the missing payload; the failure
  message distinguishes "missing" from "modified".
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown (verifyChecksum's null-expected path is
  only exercised on this platform's separator) — must pass on all platforms;
  include a path-separator-normalization assertion (audit: manifest keys use `/`,
  lookup uses platform separator).
- WHY_EXISTING_TESTS_COULD_MISS_IT: store tests write-then-read in one JVM on
  one OS.

### B2 — Checksum-listed file modified

- TEST_ID: B2
- SCIENTIFIC_INVARIANT: one flipped bit anywhere in a payload invalidates the
  store.
- MINIMAL_FIXTURE: flip one byte deep in a classical evidence JSON (not the hash
  field, not whitespace-only — change a digit in a charge value).
- EXPECTED_RESULT: checksum mismatch, load refused, no partial store surfaced.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false — regression lock.
- WHY_EXISTING_TESTS_COULD_MISS_IT: —

### B3 — Request ordering must not change identity; content must

- TEST_ID: B3
- SCIENTIFIC_INVARIANT: Scientific identity is a function of scientific content,
  not of Java collection iteration order. Two requests with the same constraints,
  required outputs, and gates listed in different orders are the *same* request;
  two requests differing in any of them are *different*.
- FAILURE_MODE: `QuantumScientificIdentity.calculate` appends `constraints`,
  `requiredOutputs`, and `acceptanceGates` in encounter order (only `observables`
  is sorted). Same science in different list order → different hash → duplicate
  execution and registry forks; worse, a "canonical" id that isn't canonical
  defeats every downstream equality guard.
- MINIMAL_FIXTURE: spec X with constraints [c1, c2] and gates [g1, g2]; spec Y
  identical but [c2, c1], [g2, g1]; spec Z identical to X but dropping g2.
- EXPECTED_RESULT: id(X) == id(Y); id(X) != id(Z). Both directions asserted.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: likely true for the equality direction
  (unsorted lists).
- WHY_EXISTING_TESTS_COULD_MISS_IT: identity tests build specs once, in one
  order, so order-dependence is unobservable.

### B4 — Charge/multiplicity omitted from a reuse decision

- TEST_ID: B4
- SCIENTIFIC_INVARIANT: as A7/A8, at the planner level: DERIVE_FROM_EXISTING may
  only draw from a source at the same electronic state.
- FAILURE_MODE: `StrategyEvidenceMatcher.java:92-98` filter omits the
  `sameSubject` charge/multiplicity check (audit finding); in exact-protocol mode
  a force-constant derivation from a different charge state is accepted.
- MINIMAL_FIXTURE: requirement (molecule M, charge 0, mult 1, protocol P, type
  HESSIAN); existing accepted evidence (M, charge +1, mult 2, protocol P, type
  HESSIAN).
- EXPECTED_RESULT: decision is not DERIVE/REUSE; requirement lands in the
  missing-evidence plan.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: planner tests never vary electronic state
  between source and requirement.

### B5 — Derivative/numerics configuration in identity

- TEST_ID: B5
- SCIENTIFIC_INVARIANT: Two results computed with different numerical contracts
  (finite-difference step, solver mode, convergence tolerance that changes the
  bits) are different evidence.
- FAILURE_MODE: identity covers solverMode but not (e.g.) FD step size or worker
  version knobs that change the numbers; stale results are reused after a
  numerics change.
- MINIMAL_FIXTURE: two execution requests identical except FD step h = 1e-3 vs
  1e-4 (or backend version 2.14.0 vs 2.15.0, whichever knob the request model
  exposes).
- EXPECTED_RESULT: distinct identities, or — if a knob is deliberately excluded —
  the provenance record carries it and reuse is gated on it. The test fails if
  *neither* holds.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: suspect true for FD step.
- WHY_EXISTING_TESTS_COULD_MISS_IT: identity tests toggle scientific fields
  (charge, basis), never numerics knobs.

### B6 — Unverified checkpoint represented as verified

- TEST_ID: B6
- SCIENTIFIC_INVARIANT: "Verified" is a conclusion of a check, not a string in a
  file. A self-asserting artifact is unverified.
- FAILURE_MODE: a status/flag field read from the artifact itself
  (`"verified": true`) is trusted without recomputing the check it claims.
- MINIMAL_FIXTURE: checkpoint directory with the status JSON claiming verified
  but (a) payload checksum absent, (b) payload one byte altered, (c) status file
  copied verbatim from a genuinely verified different checkpoint.
- EXPECTED_RESULT: all three load as unverified (or refuse to load); case (c)
  must not pass via a valid-looking but mismatched checksum.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown — no adversarial checkpoint fixture
  exists.
- WHY_EXISTING_TESTS_COULD_MISS_IT: round-trip tests write-then-read their own
  status, so the claim and the check are always consistent.

### B7 — Stale cache across configuration change

- TEST_ID: B7
- SCIENTIFIC_INVARIANT: Changing any input that changes the science invalidates
  every cached artifact derived from the old inputs.
- FAILURE_MODE: registry keyed on a too-narrow checksum hits stale entries after
  a basis-set / geometry / protocol change.
- MINIMAL_FIXTURE: run the lifecycle with spec S; then spec S′ = S with basis
  changed; assert cache miss and fresh execution; then S again and assert hit.
- EXPECTED_RESULT: hit iff the full specification checksum matches.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false (registry is checksum-keyed) — lock.
- WHY_EXISTING_TESTS_COULD_MISS_IT: —

### B8 — Evidence objects are immutable through every accessor

- TEST_ID: B8
- SCIENTIFIC_INVARIANT: A registered evidence record cannot be altered by any
  holder, accidentally or otherwise; a second reader sees the first reader's
  world.
- FAILURE_MODE: record accessors returning internal arrays/lists
  (`GeneratedEvidenceEntry` payloads, `PreconditionedConjugateGradientSolver.
  Result.solution()`, `DeltaTrainingDataset.TrainingTarget.residualForces()`,
  `FermiNetKfacState` block arrays — all confirmed to leak internals).
- MINIMAL_FIXTURE: fetch evidence; attempt `gradientList.add(...)` (expect
  `UnsupportedOperationException`); where an array is returned, mutate element 0,
  re-fetch, compare.
- EXPECTED_RESULT: mutation either throws or is invisible to subsequent readers.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true for the array-returning records above.
- WHY_EXISTING_TESTS_COULD_MISS_IT: immutability is conventionally reviewed, not
  tested; tests only read.

---

## Layer C — Potential construction / fitting

Targets: `AmberPrmtopReader`, `TwoBodyBasis`/`ThreeBodyBasis`/`FourBodyBasis`,
`LinearDeltaModel`, `DeltaModelTrainer`, force estimators' sample statistics.

### C1 — Amber atom names containing `D`

- TEST_ID: C1
- SCIENTIFIC_INVARIANT: Atom identity strings pass through the reader byte-exact.
  `CD1`, `HD11`, `SD`, `CD`, `OD2` are names, not numbers.
- FAILURE_MODE: Fortran D→E exponent normalization applied to string sections
  (confirmed defect, `AmberPrmtopReader.java:70`): `CD1`→`CE1`, `SD`→`SE`.
- MINIMAL_FIXTURE: hand-written prmtop fragment, NATOM=4, ATOM_NAME =
  `CD1 HD11SD  OD2` (fixed 4-char fields), AMBER_ATOM_TYPE = `CT H1 SD O2`
  wait — use distinct types `CT HC SD OS`; CHARGE with plain E notation.
- EXPECTED_RESULT: names and types returned exactly as written.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: the existing reader test uses only `S1`,
  `H56` — no D anywhere.

### C2 — Fortran D exponents in numeric sections

- TEST_ID: C2
- SCIENTIFIC_INVARIANT: `0.123456D+00` in CHARGE is the number 0.123456.
- MINIMAL_FIXTURE: CHARGE section mixing `1.0D+00`, `-2.5D-01`, `3.0E+00` in one
  line.
- EXPECTED_RESULT: parsed values 1.0, −0.25, 3.0 (before the 18.2223 unscale) —
  and C1 still passes on the same file (D→E must apply to numerics only).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false — regression lock guarding the C1 fix
  from over-correcting (deleting the replacement instead of scoping it).
- WHY_EXISTING_TESTS_COULD_MISS_IT: the C1 fix is the risk.

### C3 — Atom-type vs atom-name preservation

- TEST_ID: C3
- SCIENTIFIC_INVARIANT: name and type are separate channels; neither is derived
  from, swapped with, or deduplicated against the other.
- MINIMAL_FIXTURE: 3 atoms where name≠type for all (`CD1/CT`, `CD2/CT`,
  `SD/SD`): two atoms share a type, two names share a prefix; one name contains
  D, one type contains D.
- EXPECTED_RESULT: per-atom (name, type) pairs exactly as authored, in file
  order; duplicate type values preserved (no set semantics).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true (via the C1 defect).
- WHY_EXISTING_TESTS_COULD_MISS_IT: single-channel fixtures can't detect a swap.

### C4 — Duplicate / missing atoms

- TEST_ID: C4
- SCIENTIFIC_INVARIANT: NATOM is the contract; shorter sections are corrupt;
  repeated names are legitimate (e.g. two `H` atoms) and must survive.
- MINIMAL_FIXTURE: (a) NATOM=3, ATOM_NAME with 2 entries → reject; (b) NATOM=3,
  names `H H O` → accepted with two distinct H entries in order.
- EXPECTED_RESULT: (a) `IOException`; (b) three atoms, no dedup.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false — lock.
- WHY_EXISTING_TESTS_COULD_MISS_IT: —

### C5 — Energy/gradient consistency by finite differences

- TEST_ID: C5
- SCIENTIFIC_INVARIANT: For any potential, F_i = −∂E/∂x_i. This is the oracle no
  amount of internal consistency can fake.
- FAILURE_MODE: a sign, factor, or chain-rule error in any basis gradient is
  invisible to value-only tests.
- MINIMAL_FIXTURE: `LinearDeltaModel` over a single two-body channel with
  coefficient vector (1, 0.5, −0.25, 0.125) (all Chebyshev terms nonzero), one
  pair at r mid-cutoff, h = 1e-5 bohr, central differences on every coordinate
  of both atoms. Repeat for one three-body triplet (angle ≈ 97°, no symmetry)
  and the C6 torsion.
- EXPECTED_RESULT: |analytic − FD| < 1e-6·scale for every component; also
  Σ_i F_i = 0 (translation) and torque-free under the C6 geometry.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false (FD tests exist for 2-/3-/4-body) — the
  delta here is all-nonzero coefficients plus global conservation laws, which
  catch scale errors that per-component FD can mask when terms cancel.
- WHY_EXISTING_TESTS_COULD_MISS_IT: coefficient vectors with zeros let a
  mis-wired term hide; symmetric geometries let sign errors cancel.

### C6 — Four-body basis on a hand-computed chain

- TEST_ID: C6
- SCIENTIFIC_INVARIANT: The torsion and coupled-angle features are the documented
  geometric invariants of the preregistered motif, evaluated on the documented
  atoms.
- FAILURE_MODE: wrong atom tuple in an angle (audit suspect: `ANGLE_PAIR`
  computes ∠(1,2,3) and ∠(1,2,4) — both at vertex 2 sharing arm 1 — while the
  chemically conventional flanking pair of a chain is ∠(1,2,3) and ∠(2,3,4));
  gradient tests can't catch this because AD differentiates the same wrong
  formula consistently.
- MINIMAL_FIXTURE (torsion, exact): A(0,0,0) B(1,0,0) C(1,1,0) D(1,1,1) bohr.
  Hand result: φ = atan2(−1, 0) = −π/2, features (sin kφ, cos kφ) k=1..3 =
  (−1, 0, 0, −1, 1, 0).
  MINIMAL_FIXTURE (angle pair, all terms nonzero): A(0,0,0), B(1,0,0),
  C(2,√3,0) → ∠ABC = 120° (cos = −1/2); D(0,1,0) → ∠ABD = 45° (cos = √2/2).
  With P1(x)=x, P2(x)=(3x²−1)/2: expected feature vector for the documented
  angle pair, in generation order (l=1,m=1),(1,2),(2,1),(2,2).
  The test must encode which tuple the *specification* says each Kind uses, then
  assert both the angles and the products.
- EXPECTED_RESULT: exact match to hand values (torsion to machine precision on
  the 0/±1 entries; angles to 1e-12), and gradient-vs-FD agreement on the same
  geometry.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true iff the spec says flanking-pair for
  ANGLE_PAIR — this test exists to make that decision executable; until the
  motif convention is written down, mark this test as blocking the question, not
  the code.
- WHY_EXISTING_TESTS_COULD_MISS_IT: the only existing coverage is AD-vs-FD,
  which is convention-blind.

### C7 — Permutation / atom-order consistency

- TEST_ID: C7
- SCIENTIFIC_INVARIANT: Reordering the input atoms (with the motif indices
  remapped accordingly) leaves feature values unchanged and permutes gradient
  rows accordingly.
- FAILURE_MODE: hidden dependence on absolute indices (e.g. gradient arrays
  indexed by motif position instead of atom id).
- MINIMAL_FIXTURE: the C6 chain plus a fifth decoy atom; evaluate with the chain
  at indices (0..3) and at (4,2,0,1) with a scrambled input array.
- EXPECTED_RESULT: identical values; gradients equal up to the permutation.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: tests always use the natural order.

### C8 — A failed sample must not become 0.0

- TEST_ID: C8
- SCIENTIFIC_INVARIANT: Missing data is absent, not zero. A primitive-double
  default is a data value.
- FAILURE_MODE: confirmed defect — `AcZvzbFermiNetForceEstimator.java:154-155`
  and `AcZvzbDerivFermiNetForceEstimator.java:172-173` `continue` on failed pass
  1, leaving `forceSamples[c][s] = 0.0` while `finite[c][s] = false`; statistics
  then include the phantom zero in the sum but not the count, and `tails()`
  over-runs its array.
- MINIMAL_FIXTURE: a stub state/coordinates source that throws for exactly one
  sample (k of n), returns hand-set constant force F≠0 for the rest. Expected
  mean is exactly F with count n−1; expected tails over n−1 entries.
- EXPECTED_RESULT: mean = F, no AIOOBE, no classification flip; explicitly
  assert the returned sample count is n−1.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: estimator tests use all-finite samples; the
  failure path was never exercised with statistics asserted.

### C9 — Mask and array must be a single source of truth

- TEST_ID: C9
- SCIENTIFIC_INVARIANT: There is exactly one validity channel. A consumer reading
  the numeric array and a consumer reading the mask agree on which samples exist.
- FAILURE_MODE: `ComponentStatistics.compute` filters by `Double.isFinite` while
  callers compute `finiteCount` from a separate boolean mask; any disagreement
  (NaN in array + mask true, or 0.0 in array + mask false) silently corrupts
  denominators.
- MINIMAL_FIXTURE: feed all four combinations of (array finite?, mask true?)
  through the public estimator path (not the private record) and assert the
  reported count and mean each time.
- EXPECTED_RESULT: count and statistics are identical whichever channel an
  implementation uses — i.e. the constructor rejects incoherent (array, mask)
  pairs, or one channel derives from the other.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true
- WHY_EXISTING_TESTS_COULD_MISS_IT: unit tests construct coherent pairs by hand;
  only the estimator's failure path produces incoherent ones.

### C10 — Non-finite sample at first / middle / last position

- TEST_ID: C10
- SCIENTIFIC_INVARIANT: Sample position carries no information; statistics are
  invariant to where the bad sample sits.
- MINIMAL_FIXTURE: the C8 stub failing at sample 0, n/2, n−1 (three runs).
- EXPECTED_RESULT: identical mean/variance/chain-SE/tails across the three runs;
  no index-out-of-bounds at the boundaries (first/last are where off-by-one
  allocation bugs live).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: true (crash and corruption both).
- WHY_EXISTING_TESTS_COULD_MISS_IT: single-position failure fixtures miss
  boundary overruns.

### C11 — Preprocessing leakage between train and validation

- TEST_ID: C11
- SCIENTIFIC_INVARIANT: Every statistic learned from data — centering, scaling,
  PCA/feature selection, secant caches, pretrained targets — is fit on training
  data only. Validation/holdout may only be transformed, never fit.
- FAILURE_MODE: pooled fitting leaks holdout information into the model;
  validation error is optimistically biased and the leak is invisible to
  aggregate metrics when train and holdout look alike.
- MINIMAL_FIXTURE (leak-amplifying): train targets cluster near 0; 20% of the
  data is "holdout" with targets near +1000. A pooled mean shifts by ≈ +200; a
  train-only mean shifts by 0. Assert the pipeline's train-fitted transform has
  mean ≈ 0 on train (not −200). Second fixture: flip one holdout label by 1e6 —
  the fitted model and its training-set predictions must be bit-identical before
  and after.
- EXPECTED_RESULT: both fixtures pass; the second is the sharper oracle
  (a model that cannot see holdout is provably unchanged by it).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown — no leakage oracle exists today.
- WHY_EXISTING_TESTS_COULD_MISS_IT: with i.i.d.-looking synthetic data, pooled
  and train-only statistics nearly coincide, so leakage hides.

---

## Layer D — Independent validation

Targets: validation/gate classes, metric assembly over (nucleus, axis)
components, invariance properties of the fitted hybrid potential.

### D1 — Absurd held-out values must fail validation

- TEST_ID: D1
- SCIENTIFIC_INVARIANT: Validation is a gate that can fail. A metric that passes
  any input is not a metric.
- MINIMAL_FIXTURE: replace held-out reference energies with 1e6 hartree and
  gradients with −1e4 (keeping shapes and checksums consistent at the fixture
  level).
- EXPECTED_RESULT: the qualification gate fails loudly; the report names the
  failed metric; nothing is promoted.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false — sanity lock proving the gate is wired.
- WHY_EXISTING_TESTS_COULD_MISS_IT: gate tests use near-miss values; a gate
  hardwired to "pass" or a metric accidentally comparing the model to itself
  only shows up at absurd inputs.

### D2 — Physically removed holdout must fail, not vanish

- TEST_ID: D2
- SCIENTIFIC_INVARIANT: A holdout that cannot be evaluated is a failed gate,
  never a vacuous pass ("no holdout records found" ⇒ error).
- MINIMAL_FIXTURE: delete the sealed holdout identities / labels after the seal
  is computed (two sub-cases: files gone; files present but empty).
- EXPECTED_RESULT: seal verification fails and the campaign/validation aborts;
  no path reports "0 holdout points, gate passed".
- PRE_FIX_CODE_EXPECTED_TO_FAIL: suspect true (the A10 short-circuit pattern
  suggests absence is treated as pass elsewhere too).
- WHY_EXISTING_TESTS_COULD_MISS_IT: absence is only distinguishable from
  innocence if a test removes something.

### D3 — Mutating a training label must change the model

- TEST_ID: D3
- SCIENTIFIC_INVARIANT: The fitted model is a function of the training data.
  If no label can change it, the labels were never used.
- FAILURE_MODE: hardcoded, cached-across-data, or mis-wired fitting that ignores
  its targets.
- MINIMAL_FIXTURE: fit on fixture F; refit on F′ = F with one force component
  perturbed by +0.5 hartree/bohr; compare predictions at that configuration.
- EXPECTED_RESULT: predictions differ measurably (exact bound depends on the
  ridge strength — assert > 0 at minimum, and directionally consistent); also
  assert any feature/secant cache keyed on geometry only was invalidated by the
  label change (or is provably label-independent).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: fit-quality tests only check error
  magnitudes, which a data-independent model can also pass on easy fixtures.

### D4/D5/D6 — Component map integrity: duplicate, missing, out-of-range

- TEST_ID: D4, D5, D6
- SCIENTIFIC_INVARIANT: A force result over N atoms has exactly 3N addressed
  components: each (nucleus, axis) ∈ {0..N−1}×{0,1,2} exactly once.
- MINIMAL_FIXTURE: N=2: (a) components [(0,x),(0,y),(0,z),(1,x),(1,y),(0,x)] —
  duplicate (0,x), missing (1,z); (b) only 5 components; (c) nucleus index 2
  (== N) and −1; (d) axis index 3.
- EXPECTED_RESULT: each rejected with a descriptive error before any math — no
  silent overwrite (duplicate), no implicit zero-fill (missing), no
  AIOOBE (out-of-range).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: assemblers are tested with well-formed
  maps; duplicate-key behavior depends on Map construction idiom (put vs
  putIfAbsent vs merge), which nobody asserts.

### D7 — NaN force component in the reference

- TEST_ID: D7
- SCIENTIFIC_INVARIANT: as A3 at the validation layer: a NaN reference component
  is a failed comparison, not a skipped one (a metric that silently drops NaN
  pairs can be gamed to any pass rate by poisoning inconvenient points).
- MINIMAL_FIXTURE: reference with exactly one NaN component, all others matched
  perfectly by the model.
- EXPECTED_RESULT: the metric reports failure/unevaluated-with-reason and the
  count of compared components is reported (3N−1 comparisons is not 3N).
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: filtering idiom `isFinite` in shared
  statistics code makes "drop" the default behavior everywhere.

### D8 — Planarity test must be rotation-free

- TEST_ID: D8
- SCIENTIFIC_INVARIANT: "Planar" is a geometric property; any check implemented
  as "z ≈ 0" is a coordinate-system property.
- MINIMAL_FIXTURE: a planar molecule in the XY plane, and the same molecule
  rotated by R = Rot(axis (1,2,3)/√14, 37°).
- EXPECTED_RESULT: identical planarity verdict and identical derived quantities
  for both orientations.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: fixtures are authored in the XY plane by
  habit.

### D9/D10 — Translation and rotation invariance

- TEST_ID: D9, D10
- SCIENTIFIC_INVARIANT: E(Rx + t) = E(x); F(Rx + t) = R·F(x) (forces are
  covariant, not invariant — asserting F′ = F under rotation is itself a bug).
- MINIMAL_FIXTURE: any fitted hybrid potential; t = (7.3, −2.1, 11.0) bohr;
  R as in D8 (non-axis-aligned so every component mixes; all force components
  nonzero in the base geometry).
- EXPECTED_RESULT: energies identical to machine precision; rotated forces match
  R·F to 1e-12; also ΣF = 0 and total torque = 0 in both frames.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown (local-environment potentials should
  pass; the torque/conservation assertions are new).
- WHY_EXISTING_TESTS_COULD_MISS_IT: axis-aligned rotations (90° about z) let a
  component-permutation bug pass.

### D11 — Energy-shift invariance

- TEST_ID: D11
- SCIENTIFIC_INVARIANT: Adding a constant C to every training energy changes no
  force and no force-fitting residual; the energy origin is arbitrary.
- FAILURE_MODE: any term coupling absolute energy scale into forces (e.g.
  energy-weighted force losses without centering) breaks this.
- MINIMAL_FIXTURE: fit on F; refit on F with all energies + 13.7 hartree.
- EXPECTED_RESULT: identical forces bit-for-bit (or to solver tolerance);
  predicted energies differ by exactly C if the model absorbs offsets, else
  identical loss.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: training fixtures are always near-zero-mean,
  where centering bugs are invisible.

### D12 — Permutation invariance for chemically identical atoms

- TEST_ID: D12
- SCIENTIFIC_INVARIANT: Exchanging two atoms of the same element and environment
  (the two H of water) leaves E unchanged and permutes F; exchanging different
  elements is a different molecule and must change E or be rejected.
- MINIMAL_FIXTURE: water at a bent geometry; swap H indices (with coordinates);
  then swap O and H coordinates (invalid chemistry).
- EXPECTED_RESULT: first swap: identical E, permuted F; second: different E or
  explicit rejection — never the same E.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.
- WHY_EXISTING_TESTS_COULD_MISS_IT: permutation invariance is assumed by the
  channel-sorting design but never asserted end-to-end.

---

## Ranking

### BLOCKING (wrong science ships silently if these are absent)

- A1, A2, A3 — fabricated/zero/NaN energies entering the frozen registry.
- A10 + B1 — deletion passing integrity checks (the two confirmed
  short-circuits).
- B4 — cross-electronic-state reuse in the planner.
- C1 — silent atom-identity corruption in authoritative topologies.
- C8, C9, C10 — phantom-zero sample statistics (confirmed defect + crash).
- D2 — removed holdout passing as vacuous success.
- C6 — until the ANGLE_PAIR motif convention is pinned by this test, the
  4-body basis has no oracle at all (see below).

### HIGH_VALUE

- A4, A5, A6 — sign/units/ordering oracles with asymmetric fixtures.
- A7/A8 — electronic-state identity.
- B3 — identity canonicalization (both directions).
- B6 — self-asserting checkpoints.
- B8 — mutation-through-accessor.
- C5 — FD consistency with all-nonzero coefficients + conservation laws.
- C11 — leak-amplifying preprocessing fixtures.
- D1, D3 — the two "is validation even connected" oracles.
- D9, D10, D11 — invariance suite with non-degenerate transforms.

### REGRESSION (locks on behavior currently correct; cheap, keep forever)

- A9, A11, B2, B7, C2, C3, C4, C7, D4–D7, D12.

### NICE_TO_HAVE

- B5 — numerics-knob identity (pending a policy decision on which knobs are
  scientific identity vs provenance).
- D8 — subsumed by D10 if the invariance suite runs on the planarity path.

---

## Layer E — Persistence / completeness of a successful fit

A fit that reports success but loses its artifacts is indistinguishable from no
fit. These requirements make "successful fit" mean "durable, complete,
reloadable result". Each artifact must exist, be checksummed, and round-trip.

### E1 — Coefficient vector survives the fit

- TEST_ID: E1
- SCIENTIFIC_INVARIANT: A successful fit persists its coefficient vector to
  durable storage before success is reported; reloading yields the identical
  vector (bit-exact or stated precision).
- FAILURE_MODE: coefficients live only in process memory; success is reported,
  the process exits, the model is gone.
- MINIMAL_FIXTURE: the C5 hand-computed training set; run the fit to success;
  kill nothing — simply reload from the persisted artifact and compare vectors.
- EXPECTED_RESULT: artifact exists, is covered by a checksum manifest, reloads
  to the same coefficients.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown — depends on whether a fit-persistence
  seam exists; if none exists, SPECIFICATION_BLOCKED (the persistence contract
  itself is the missing specification).

### E2 — Gradient/force decomposition survives with the fit

- TEST_ID: E2
- SCIENTIFIC_INVARIANT: The decomposition the fit claims (baseline vs delta,
  or per-term energy decomposition) is persisted with the same integrity as the
  coefficients — a coefficient vector without its decomposition cannot be
  evaluated or audited.
- EXPECTED_RESULT: reload yields both; evaluating the reloaded model reproduces
  the in-memory model's energy and forces on the C5 geometry.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown / SPECIFICATION_BLOCKED if no seam.

### E3 — Checkpoint resumes bit-identically

- TEST_ID: E3
- SCIENTIFIC_INVARIANT: A checkpoint contains everything needed to resume:
  parameters, optimizer state (moments / curvature blocks), RNG state, iteration
  counter. Resume-from-checkpoint equals uninterrupted execution.
- MINIMAL_FIXTURE: run optimizer N steps; checkpoint; run M more. Separately:
  run N+M uninterrupted. Compare final parameters bit-for-bit.
- EXPECTED_RESULT: identical parameters and optimizer state; a checkpoint
  missing any component fails this by construction.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false for FermiNet checkpoints (round-trip
  machinery exists) — the resume-equivalence assertion is the new content.

### E4 — Optimizer state is persisted whole, not partially

- TEST_ID: E4
- SCIENTIFIC_INVARIANT: Serialized optimizer state covers every state component
  the optimizer reads on the next step (SR/KFAC blocks, damping, step counts,
  Adam moments). A silent partial write changes post-resume trajectories.
- MINIMAL_FIXTURE: as E3, but additionally mutate-on-reload detection: reload
  the checkpoint twice, continue both, assert identical trajectories; then
  truncate one state component from the artifact and assert load refuses.
- EXPECTED_RESULT: deterministic resume; corrupt/partial state rejected.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: unknown.

### E5 — Split manifests are persisted, complete, and sealed

- TEST_ID: E5
- SCIENTIFIC_INVARIANT: A fit is only interpretable against its data partition.
  The train/holdout split manifest is part of the fit output: it exists, is
  checksummed, partitions exactly the intended snapshot set with no overlap, and
  the holdout seal binds the split to its checksum.
- FAILURE_MODE: split known only to the fitting process; later "holdout
  validation" runs against a re-derived (different) split — silent leakage.
- MINIMAL_FIXTURE: the TSL runner freeze output (60 snapshots, 45/15 split):
  assert `FORCE_FITTING_TRAINING_TARGETS.json`, `SEALED_HOLDOUT_IDENTITIES.json`,
  and the dataset manifest exist, parse, partition the 60 manifest ids exactly
  (disjoint, union-complete), and the seal's `split_manifest_sha256` matches the
  split file on disk. Sub-case: flip one id between train and holdout in the
  split file → seal verification must fail.
- EXPECTED_RESULT: as above.
- PRE_FIX_CODE_EXPECTED_TO_FAIL: false for the existence/partition assertions
  (freeze writes these today); the tamper sub-case is the new oracle.

---

## Identity-finding correction (2026-08-23, acceptance phase)

The Layer-B audit claim that `QuantumScientificIdentity` omits
`requiredOutputs` was correct for the pre-fix code and is **corrected for the
post-fix code**: `requiredOutputs` is now appended to the identity (verified in
the working tree). The remaining open question is canonical ordering:
`constraints`, `requiredOutputs`, and `acceptanceGates` are ordered lists
(`CanonicalHashing.sequence` is documented as an *ordered* injective
serialization), while `observables` is sorted before hashing. Test B3 encodes
the invariant as specified — reordered set-like inputs must yield identical
identity, and removing/changing a gate must change it — and records the
implementation's actual behavior rather than adapting the invariant to it.

---

## Invariants with no independent oracle currently possible

1. **Physical correctness of a brand-new force-cloud point.** The only QM-side
   oracle is reproduction of the trusted MIN01 reference (qualification gate).
   For the other 59 snapshots there is no independent check that the worker's
   numbers are *right* — only that they are consistent (sign, units via A5,
   finiteness). A worker with a systematically wrong but self-consistent
   Hamiltonian passes everything. Closing this needs an independent second
   backend or analytic limits (e.g. known bond-energy/force-constant ranges per
   motif), not more internal checks.
2. **The ANGLE_PAIR / ANGLE_PAIR_SHARED_FOURTH motif convention.** No design
   document in the repo states which atom tuples the preregistered motifs couple
   (audit found none; the test suite only checks AD-vs-FD consistency, which is
   convention-blind). C6 can encode a convention, but cannot derive one. This is
   a specification gap, not a test gap — it needs a written motif definition from
   the chemistry side.
3. **Holdout non-leakage as a process property.** Seals and hashes prove the
   *files* weren't read by the *sealed code path*; no test can prove no human or
   side channel informed parameter choices using holdout knowledge. Procedural,
   not technical.
4. **FermiNet-vs-worker agreement validates compatibility, not truth.** The two
   stacks can share a wrong sign/unit convention (both derived from the same
   documents). The A5 FD oracle covers units; an independent-implementation
   disagreement protocol is the only cover for shared-convention errors.
5. **Adequacy of the frozen protocol itself.** The protocol SHA pins *which*
   method/basis/dispersion was used; nothing can test that it is *sufficient*
   for the TSL-RSH science question. That judgment is external to the pipeline.
