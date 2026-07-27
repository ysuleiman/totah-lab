# Ligand Selection and Orchestration

`LigandPreparationOrchestrator` is the boundary between receptor cleanup and
ordinary free-ligand chemistry. It consumes `StructureCleanupResult` directly
and delegates the selected residue to `LigandPreparer`.

It is a ligand service under `totah.lab.ligand`; it is not a receptor pipeline
stage.

## Selection identity

`LigandSelection` identifies one deposited component using all residue identity
fields:

```text
component id + chain + residue number + insertion code
```

Component identifiers are matched case-insensitively. Chain identifiers,
residue numbers, and insertion codes must match exactly.

## Behavior

`prepareOnly(cleanupResult)` applies these rules:

- no extracted components returns `Optional.empty()`;
- one extracted CCD-confirmed `LIGAND` is prepared;
- multiple extracted components fail with `AMBIGUOUS_SELECTION` and require an
  explicit `LigandSelection`;
- an extracted fallback component whose role remains `UNKNOWN` is rejected
  with `UNSUPPORTED_CLASSIFICATION`.
- a component rejected by `LigandSelectionPolicy` fails with
  `EXCLUDED_BY_POLICY`.

The default `LigandSelectionPolicy` excludes `GOL` and `SO4` from ordinary
docking-ligand selection. These are technically useful chemistry fixtures but
normally represent a crystallization additive/solvent and sulfate ion. Callers
can provide an explicit exclusion set when their workflow has different
selection semantics.

`prepare(cleanupResult, selection)` also searches receptor, removed-water, and
removed-ion categories. This allows it to distinguish a nonexistent selection
from a component that cleanup intentionally did not extract.

Typed selection failures are:

- `AMBIGUOUS_SELECTION`;
- `SELECTION_NOT_FOUND`;
- `NOT_EXTRACTED_AS_LIGAND`;
- `UNSUPPORTED_CLASSIFICATION`;
- `EXCLUDED_BY_POLICY`.

Selection failures occur before CCD graph construction and therefore do not
masquerade as chemistry capability failures.

## Output

`SelectedLigandPreparation` contains:

- the exact `ClassifiedResidue` selected from cleanup;
- the complete `LigandPreparationResult`, including graph validation,
  hydrogenation, charges, AD4 types, torsion tree, and PDBQT text.

## Limitations

- Multiple extracted residues are treated as independent candidates. They are
  never merged into one ligand.
- Current structural loading does not yet expose receptor-to-ligand `LINK`
  relationships in the typed cleanup result. Covalently attached ligands
  therefore cannot yet be positively detected here and remain unsupported.
- An unknown name-only HET group is not silently prepared. Online CCD
  enrichment or an explicit upstream classification decision is required.
- Cofactors retained by special-residue policy cannot be selected through this
  ordinary free-ligand workflow.
- The orchestrator returns PDBQT text but does not choose an output filesystem
  location; persistence remains a caller responsibility.

## Validation

Offline tests cover zero, one, and multiple extracted components; explicit
residue selection; retained receptor, removed water/ion, unknown, and missing
selection failures; default/configured selection policy; and successful
delegation to ligand chemistry.

The gated online `1A4W` integration test now exercises the full path:

```text
TargetLoadStage
  -> StructureCleanupStage
  -> StructureCleanupResult
  -> LigandPreparationOrchestrator
  -> QWE PDBQT
```

The offline deposited GOL regression and the complete validation criteria are
documented in
[20-real-structure-ligand-regression.md](20-real-structure-ligand-regression.md).
