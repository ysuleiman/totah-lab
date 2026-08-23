# Scientific result completeness contract

This contract is part of the scientific-software correctness re-audit. It does
not authorize QM, fitting, training, or reconstruction of historical state.

## Publication rule

A calculation or fit may be published or qualified as
`REPRODUCIBLE_COMPLETE` only after every mandatory artifact exists, every
recorded SHA-256 verifies, cross-file ordering agrees, and a read-back
reconstructs the numerical result. A metric without the state that generated it
is incomplete. The fail-closed classifications are:

- `INCOMPLETE_MISSING_MODEL_STATE`
- `INCOMPLETE_MISSING_DERIVATIVES`
- `INCOMPLETE_MISSING_COMPONENT_DECOMPOSITION`
- `INCOMPLETE_MISSING_OPTIMIZER_STATE`
- `INCOMPLETE_MISSING_PROVENANCE`

`ScientificResultCompletenessValidator.requireComplete` is the generic
publication gate. `FitArtifactWriter.persistSuccessful` is the fit-specific
gate and returns no success receipt until an atomic bundle has been written and
read back with verified checksums. Delta exposes this through
`DeltaModelTrainer.persistSuccessfulFit`; future Amber/RESP/vdW/ML fit paths
must use the same seam or an adapter with exactly the same mandatory state.

## Identity ordering decision (B3)

`constraints`, `requiredOutputs`, and `acceptanceGates` are unordered sets of
independent scientific clauses. Presentation order and duplicate presentation
are not scientific content. `CalculationSpecification` therefore stores unique
clauses in lexicographic order, and both its checksum and
`QuantumScientificIdentity` consume canonical order. A genuinely procedural
constraint sequence must be represented by an explicitly ordered field, not by
relying on list encounter order.

## Mandatory QM bundle

Every QM point persists geometry and atom order; charge and multiplicity;
method, basis, grid, dispersion and SCF configurations; electronic energy and
gradient; dispersion energy and gradient; total energy and gradient; force;
convergence diagnostics; software and relevant hardware/runtime identity; and
input/output checksums. If electronic and dispersion components were computed,
preserving totals alone is prohibited.

## Mandatory fit bundle

`FitArtifact` contains model family/version, exact basis definition/order,
parameter names/units, initial/final vectors, frozen values, bounds,
regularization, objective and weights, train/validation IDs, normalization,
optimizer/configuration/state, seed, convergence, iteration history,
predictions, residuals, final metrics, dataset checksums and code commit. The
writer separately mirrors the basis order, parameter names/vectors, split,
optimizer state, predictions and residuals and binds all files through
`SHA256SUMS` plus `ARTIFACT_SHA256`. Names, basis entries, units and both vectors
must have identical lengths. For a stateless closed-form fit, optimizer state
must explicitly say `NOT_APPLICABLE`; absence is not equivalent.

ML fits additionally require architecture and parent-model identities,
checkpoint, trainable/frozen masks, optimizer and scheduler states,
normalization tensors, seeds, selected epoch/step and selection criterion.
Derived force fields additionally require atom typing/order mapping, charges,
all bonded/nonbonded/cross terms, fitted corrections, per-parameter provenance,
and a runnable final topology/parameter file.

## Historical minimum findings

1. The best-Amber result is `CANDIDATE_A_B_C_COMBINED`. Its metrics survive but
   its 26 coefficients do not. Exact basis order recovered from
   `analyze_residual_representability.py` is: 9 fixed-equilibrium quadratic
   terms (S-C, S-H, then angles 9-10-11, 9-10-26, 9-10-37, 11-10-26,
   11-10-37, 26-10-37, 10-26-56); 5 cubic terms for S-C, S-H and the first
   three listed junction angles; the same 5 quartic terms; 4 couplings
   `(S-C)*(angle 9-10-26)`, `(S-C)*(angle 11-10-26)`,
   `(S-H)*(angle 10-26-56)`, `(angle 9-10-26)*(angle 11-10-26)`; and 3
   Urey-Bradley squared-distance terms for atom pairs 11-26, 26-37 and 10-56
   (one-based). It is `NON_REPRODUCIBLE_PARAMETER_STATE_MISSING`.
2. MIN01's CPU no-D3 energy `-1477.8579032334885 Ha` is recoverable from the
   archived scalar log/reference. No authoritative standalone 56x3 CPU no-D3
   gradient or checksum survives. Subtracting a separately reconstructed D3
   gradient from the historical total would create a derived array, not recover
   the exact original analytic-gradient output, so gradient reconstruction is
   not exact.
3. The historical CPU 60-point force cloud stores total energy/gradient/force,
   but lacks per-point electronic energy, electronic gradient, D3 energy and D3
   gradient artifacts. In contrast, the later homogeneous A100 GPU-60 bundle
   does preserve all four electronic/D3 energy-gradient components. These two
   datasets must not be conflated.

The full recovery assessment is in
`HISTORICAL_SCIENTIFIC_RESULT_COMPLETENESS_INVENTORY.csv`.

## Explicit specification block

`FourBodyBasis.Kind.ANGLE_PAIR` remains `SPECIFICATION_BLOCKED`. No tuple or
chemical motif is changed by this persistence work. The separate
`ANGLE_PAIR_SHARED_FOURTH` implementation does not resolve the intended motif
of the original generic kind.
