# Native Ligand Preparation TODO

## Purpose

The current docking-preparation pipeline prepares receptor PDBQT files and
extracts bound non-polymer components, but it does not yet prepare those
components as docking-ready ligands.

The first target case is PDB `1A4W`:

```text
TYS I:363 -> modified receptor residue -> keep in receptor
QWE H:373 -> bound inhibitor           -> extract as ligand
```

With optional online CCD lookup, `QWE` has the following BioJava evidence:

```text
available   = true
polymeric   = false
residueType = peptideLike
polymerType = otherPolymer
```

This identifies `QWE`, but identification alone does not produce a valid ligand
PDBQT.

## Constraint

Ligand preparation must be implemented natively in Java. Do not invoke Meeko,
Open Babel, RDKit, or another external preparation executable.

Meeko/Open Babel behavior may be used as scientific reference material or for
manually curated comparison fixtures, but the production pipeline must not
depend on external tools.

## Version 1 Scope

Version 1 should prepare only ligands that have a complete CCD entry and whose
deposited atoms can be reconciled unambiguously with that entry.

Version 1 should:

- preserve deposited atom and coordinate ordering;
- construct bonds from CCD definitions rather than distance guessing;
- validate all deposited heavy atoms against the CCD;
- retain the CCD chemical state rather than enumerate pH-dependent states;
- add missing hydrogens from explicit graph chemistry;
- assign ligand Gasteiger charges;
- assign graph-based AutoDock4 atom types;
- identify rotatable bonds;
- construct a deterministic ligand torsion tree;
- write ligand PDBQT;
- fail clearly for incomplete or ambiguous chemistry.

Version 1 should not:

- infer arbitrary ligand chemistry from PDB coordinates alone;
- silently invent missing heavy atoms;
- perform tautomer enumeration;
- perform general pH-dependent protonation-state enumeration;
- treat CCD identification as proof of Amber support;
- reuse protein residue-name rules for ligand typing;
- produce a PDBQT when graph validation fails.

## Existing Code That Can Be Reused

### High reuse

- `StructureIO` atom and coordinate loading.
- `ResidueClassificationEvidence`.
- Reduced and optional cached online CCD lookup.
- Pipeline context, stage, report, and error-handling conventions.
- PDBQT atom-line formatting concepts.
- Existing AutoDock type enumeration.

### Partial reuse

- `GasteigerModel` is a starting point, but it needs formal-charge,
  hybridization, and bond-order information.
- `TorsionTree` and `TorsionBranch` provide a starting representation, but
  their mutability and invariants should be reviewed.
- `FlexPDBQTWriter` demonstrates recursive `BRANCH` output, but its
  `BEGIN_RES`/`END_RES` format is specific to flexible receptor residues.
- Receptor hydrogen geometry utilities may be reusable after separating them
  from amino-acid assumptions.

### Not directly reusable

- `ReceptorHydrogenator`, which is amino-acid-specific.
- Amber residue-template charge assignment.
- `AD4AtomTypingStage`, which uses protein atom names and residue states.
- `FlexTorsionTreeBuilder`, which uses amino-acid chi-bond tables.
- Distance-derived topology as a source of ligand bond order or aromaticity.

## Required Chemical Graph

The current relevant bond representations are:

```java
Topology.Edge(int indexA, int indexB, double length)
BondTemplate(String atom1, String atom2)
```

Neither carries enough ligand chemistry. Ligand preparation needs bond order and
aromaticity in addition to connectivity.

Avoid a ligand-specific duplicate if a suitable generic bond model is added.
Prefer a chemistry-level representation such as:

```java
public enum BondOrder {
    SINGLE,
    DOUBLE,
    TRIPLE,
    AROMATIC
}

public record ChemicalBond(
        int atomIndexA,
        int atomIndexB,
        BondOrder order,
        boolean aromatic
) {}
```

Do not change the components of the public `Topology.Edge` record without
explicit approval. Changing record components would break its constructor and
public API.

A possible graph boundary is:

```java
public record MolecularGraph(
        List<Atom> atoms,
        List<ChemicalBond> bonds,
        List<AtomChemicalProperties> atomProperties
) {}
```

Required per-atom chemical properties include:

- formal charge;
- aromaticity;
- hybridization or equivalent valence state;
- CCD atom identifier;
- leaving-atom status where relevant;
- deposited atom index.

The final package and module ownership must be decided before implementation.
The generic graph should preferably live in `chemistry` or `domain-model`, not
under a ligand-only pipeline package.

## Proposed Native Pipeline

```text
Extracted ligand residue
        |
        v
CCD atom and bond reconciliation
        |
        v
Validated molecular graph
        |
        v
Hydrogen completion
        |
        v
Formal-charge validation
        |
        v
Ligand Gasteiger charges
        |
        v
Ligand AutoDock4 typing
        |
        v
Rotatable-bond classification
        |
        v
Ligand torsion tree
        |
        v
Ligand PDBQT
```

## Milestone 1: CCD Graph Construction

Suggested components:

```text
CcdLigandGraphBuilder
CcdAtomReconciler
LigandGraphValidationReport
```

Tasks:

- Read CCD atoms and bonds through BioJava.
- Match deposited atom names to CCD atom identifiers.
- Preserve deposited atom ordering.
- Preserve deposited coordinates.
- Transfer CCD bond orders, aromatic flags, and formal charges.
- Detect duplicate atom names.
- Detect missing and extra heavy atoms.
- Define behavior for existing deposited hydrogens.
- Reject ambiguous alternate-name mappings.
- Verify that every chemical bond endpoint maps to a deposited or intentionally
  generated atom.
- Record whether evidence came from reduced CCD, cache, or online lookup when
  source tracking is available.

Acceptance test:

```text
1A4W QWE -> complete validated graph with stable atom ordering
```

## Milestone 2: Hydrogen Completion

Suggested components:

```text
LigandHydrogenator
LigandValenceValidator
LigandHydrogenationReport
```

Tasks:

- Compute bond-order sums.
- Validate element-specific valence and formal charge.
- Determine missing hydrogen counts.
- Add hydrogens using deterministic geometry.
- Preserve existing valid hydrogens.
- Reject unsupported valence states.
- Avoid changing the CCD chemical state in Version 1.
- Ensure generated atom names and ordering are deterministic.

Required tests:

- neutral carbon;
- aromatic carbon;
- neutral and charged nitrogen;
- hydroxyl and carbonyl oxygen;
- thiol, thioether, and sulfonate sulfur;
- halogens;
- already hydrogenated ligand;
- invalid over-valent atom.

## Milestone 3: Ligand Charges

Suggested components:

```text
LigandChargeSystem
LigandChargeAssignmentStage
LigandChargeAssignmentReport
```

Tasks:

- Extend charge input with formal atom charges.
- Use bond order and hybridization in parameter selection.
- Initialize charges from formal charges rather than all-neutral atoms.
- Preserve the CCD total formal charge after normalization.
- Fail on unsupported elements unless an explicit policy exists.
- Keep receptor Amber charges unchanged and authoritative for receptors.

The receptor `ChargeAssignmentStage` should not be overloaded with ligand
branches. Ligand charging should have a separate stage and report.

## Milestone 4: Ligand AutoDock4 Typing

Suggested components:

```text
LigandAd4AtomTyper
LigandAd4TypingStage
LigandAd4TypingReport
```

Typing must use graph chemistry, not protein residue names.

Cases requiring explicit rules and tests:

- aliphatic versus aromatic carbon;
- donor versus non-donor hydrogen;
- acceptor versus non-acceptor nitrogen;
- protonated nitrogen;
- amide nitrogen;
- aromatic nitrogen;
- acceptor oxygen;
- carboxylate oxygen;
- sulfur states;
- phosphate and sulfonate groups;
- halogens;
- supported metals and unsupported elements.

## Milestone 5: Rotatable Bonds and Torsion Tree

Suggested components:

```text
LigandRingDetector
RotatableBondClassifier
LigandTorsionTreeBuilder
LigandTorsionReport
```

A bond is not rotatable when it is:

- not a single bond;
- part of a ring;
- resonance-restricted, including amide-like bonds;
- terminal under the selected AutoDock policy;
- explicitly configured as rigid;
- unsuitable because of atom or bond chemistry.

Tasks:

- detect rings deterministically;
- classify rotatable bonds;
- divide the molecular graph into rigid fragments;
- choose a deterministic root fragment;
- create branches without duplicating or losing atoms;
- preserve deposited atom order within stable traversal rules;
- compute `TORSDOF`;
- validate that every ligand atom appears exactly once in the tree.

Review `TorsionTree` and `TorsionBranch` for:

- defensive copies;
- immutable public views;
- duplicate atom rejection;
- branch endpoint validation;
- full atom-coverage validation.

## Milestone 6: Ligand PDBQT Export

Create a dedicated:

```text
LigandPdbqtWriter
LigandPdbqtExportStage
LigandPdbqtExportReport
```

Required output structure:

```text
ROOT
ATOM ...
ENDROOT
BRANCH parent child
...
ENDBRANCH parent child
TORSDOF n
```

Do not emit flexible-receptor `BEGIN_RES` or `END_RES` records.

Exporter validation must require:

- finite coordinates;
- finite partial charges;
- supported AutoDock4 type for every atom;
- unique serial numbers;
- valid branch serial references;
- complete atom coverage;
- `TORSDOF` consistent with active rotatable bonds.

## Pipeline Integration

Do not insert ligand chemistry into receptor stages. Add a separate ligand
pipeline or explicit ligand sub-pipeline.

Possible context keys:

```text
EXTRACTED_LIGANDS
SELECTED_LIGAND
LIGAND_MOLECULAR_GRAPH
LIGAND_HYDROGENATION_REPORT
LIGAND_CHARGE_ASSIGNMENT_REPORT
LIGAND_AD4_ATOM_TYPING_REPORT
LIGAND_TORSION_TREE
LIGAND_PDBQT
LIGAND_PDBQT_PATH
```

Before adding keys, consider replacing loosely typed context output with one
typed `LigandPreparationResult`.

The first implementation should require explicit ligand selection if multiple
ligands were extracted. Do not silently merge independent ligand residues into
one molecule.

## Scientific Invariants

- Preserve deposited heavy-atom ordering.
- Preserve deposited coordinates unless an explicit reconstruction step is
  requested.
- Treat CCD bond definitions as authoritative for CCD-backed Version 1.
- Never infer bond order from distance alone.
- Never use Amber protein templates for general ligands.
- Keep receptor Amber charges as the receptor source of truth.
- Preserve total ligand formal charge.
- Do not classify every CCD peptide-like component as a receptor residue.
- Require explicit support for covalently attached ligands and cofactors.
- Fail rather than emit a superficially valid but chemically incomplete PDBQT.

## Test Strategy

### Unit tests

- CCD atom reconciliation.
- Bond-order and aromaticity transfer.
- Valence calculation.
- Hydrogen counts and geometry.
- Charge conservation.
- AD4 type rules.
- Ring detection.
- Rotatable-bond classification.
- Root selection.
- Torsion-tree coverage.
- PDBQT record and serial formatting.

### Integration fixtures

Include at least:

- `1A4W` / `QWE`;
- rigid aromatic ligand;
- flexible aliphatic ligand;
- amide-containing ligand;
- charged ligand;
- heteroaromatic ligand;
- ring-rich ligand;
- ligand containing sulfur;
- halogenated ligand;
- ligand with existing hydrogens;
- incomplete CCD/deposited atom mismatch;
- unsupported element;
- multiple extracted ligands.

### `QWE` acceptance criteria

- Online or cached CCD evidence is available.
- CCD atom mapping is unambiguous.
- Deposited heavy-atom coordinates and ordering are preserved.
- All CCD-required heavy atoms are present, or preparation stops with a precise
  missing-atom report.
- Formal charge matches the CCD entry.
- Every prepared atom has a finite partial charge.
- Every prepared atom has a legal AutoDock4 type.
- Every atom appears exactly once in the torsion tree.
- PDBQT contains valid `ROOT`, branch records, and `TORSDOF`.
- Cleanup continues to exclude `QWE` from receptor preparation.

## Open Decisions

- Should the generic molecular graph live in `chemistry` or `domain-model`?
- Is there an existing bond class on another branch that already stores order
  and aromaticity and should be reused?
- Should `Topology` remain geometry-only or gain a separate chemical-topology
  companion?
- Which CCD atom-name aliases are accepted?
- How should missing deposited heavy atoms be handled after Version 1?
- Which ligand formal-charge states are supported initially?
- What exact rotatable-bond policy should be treated as authoritative?
- How should deterministic root selection be specified?
- Should multiple CCD components connected by PDB `LINK` be supported?
- How are covalent receptor-ligand complexes represented and rejected or
  prepared?
- What comparison tolerances define acceptable charge and PDBQT parity?

## Estimated Effort

For a defensible CCD-backed native Java implementation:

```text
CCD graph and reconciliation      1-2 weeks
Hydrogen completion               1-2 weeks
Charge and AD4 typing             1-2 weeks
Rotatable bonds and PDBQT         1-2 weeks
Scientific test panel/hardening   2-3 weeks
```

Expected total: approximately 6-10 engineer-weeks.

Arbitrary PDB-only ligand chemistry perception is explicitly outside this
estimate and should be planned as a separate project.

## Suggested First Work Session

1. Decide generic graph package ownership.
2. Confirm whether an existing chemical bond model can be reused.
3. Add `BondOrder` only if no suitable type exists.
4. Implement CCD atom-name reconciliation for `QWE`.
5. Build a graph without changing atom order or coordinates.
6. Add missing/extra-heavy-atom validation.
7. Stop after producing and testing the validated graph; do not combine graph,
   hydrogenation, charging, typing, torsions, and export in one change.
