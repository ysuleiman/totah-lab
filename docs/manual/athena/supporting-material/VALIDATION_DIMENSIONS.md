# Dimensional chemical-state validation (V01 / V02 / V05)

The user-reviewed [API and semantics contract](../../../../software/qualification/validation-dimensions-contract-20261005/DESIGN.txt)
is the definition authority. This is an opt-in OCL validation adapter, not a new
chemistry engine or replacement of historical validation. No force field is assigned.

## Supporting material and scientific reasoning

Pinned implementation: OpenChemLib 2026.7.2. The [inspection record](../../../../software/qualification/validation-dimensions-contract-20261005/OCL_INSPECTION.json)
records the installed JAR SHA256 and extraction method (`javap -c -p`, no downloads).
Exact method evidence: `ExtendedMolecule.validate()` and `StereoMolecule.validate()`
bytecode excerpts in the same directory. The former checks close coordinates, valence,
then total formal charge; the latter invokes it before checking ESR/stereo problems
and tetrahedral drawing geometry. Thus its charge exception does not establish either
invalid stereo or successful execution of the later stereo checks.

The [pre-fix witnesses](../../../../software/qualification/v01-validation-characterization-20261005/results-with-causes.json)
preserve methylamine/methylammonium, pyridine/pyridinium and acetamide results and
original causes. Neutrality is a separate proposition, not a valence or stereo model.
The user explicitly approved that distinction and subsequently the exact additive API.
No empirical distribution, new radius, threshold calibration, literature inference,
charge generation or graph repair supports or is introduced by this correction.

ADOPT existing OCL occupied/max-valence, H consistency and public ESR/parity/problem
predicates. MODIFY the combined validator into separately attributed dimensions.
REJECT catching the charge exception as evidence of stereo success; reject neutralization,
counterions or source graph edits to satisfy a validator. UNSUPPORTED/UNKNOWN: mapper-
unsupported representations and charged tetrahedral drawing checks whose remaining
OCL branch is not independently established. These cannot become positive certificates.

## Contract and coverage

`MolecularValidationService` exposes TOPOLOGY_VALENCE, SUPPLIED_H_STATE,
STEREOCHEMISTRY, NET_NEUTRALITY and OCL_COORDINATE_COMPATIBILITY. The result is immutable,
retains net formal charge and attributed BackendEvidence with original combined causes.
H consistency tests supplied constraints; zero annotation retains its historical
unspecified meaning. It does not certify complete protonation or place donor H.

NET_NEUTRALITY evaluates **COMPONENT_NET_NEUTRAL**:

| Policy | Charge | Assessment |
|---|---|---|
| OBSERVE_ONLY | any | NOT_EVALUATED; numeric charge retained |
| REQUIRE_COMPONENT_NEUTRAL | 0 | SUPPORTED_PRESENT |
| REQUIRE_COMPONENT_NEUTRAL | nonzero | ABSENT_FALSE |

No neutrality result changes the other dimensions. Charge refers to the supplied
component, not an inferred salt parent. All source atoms are summed with overflow
rejection; no missing charge is invented. For other dimensions, failures and unknowns
retain their own meanings; NONE/zero/unavailable input is not an exhaustive negative.

The coordinate guard is OCL's existing average-bond-length-squared/16 check, not an
Athena favorable-contact or clash threshold. Missing coordinates remain UNKNOWN.
Where the original combined validator actually succeeds on a coordinate-free graph,
the boolean adapter preserves that historical chemical-validation behavior while
retaining UNKNOWN coordinate coverage. It does not certify the missing geometry.

## Implementation and compatibility

- `athena/design/backend/MolecularValidationService`: additive public dimensional result.
- `athena-openchemlib/.../OclValidationDimensions`: package-private OCL adapter.
- `OclMolecularBackend.forChemicalStateValidation()`: opt-in sanitizer/stereo path,
  identity `2026.7.2/athena-validation-dimensions/1`.
- `SystemGraphValidation` opt-in constructor: dimensional checks and
  `system-graph-validation/2`, with policy-bound method reference.
- `SystemQualificationPipeline`: takes that instance reference; legacy reference bytes unchanged.

Default constructors retain historical behavior. No production caller is automatically
migrated. Corrected sanitizer retains existing graph-change guards and policy checks;
its supported qualification uses the fail-on-meaningful-change policy. Source inputs
are immutable. The dimensional API does not claim a universal stereochemical validator.
No existing interaction threshold or perception family is widened.

[Checkpoint and exact tests](../../../../software/qualification/validation-dimensions-20261005/CHECKPOINT.txt)
include consumer comparisons, source pins, synthetic charged-invalid witnesses,
independent replay and immutable historical source copies. This is bounded implementation
qualification; no current-policy scientific Research Gate receipt or validity date is
fabricated. Broader salt, stereo and chemistry domains remain open in the catalog.
