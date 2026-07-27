# Residue Classification Design Note

## Why This Came Up

The current domain model uses `Residue` as a structural container: a group of
atoms with a residue name, chain id, residue number, insertion code, and atom
list. That matches how PDB files group both protein residues and HETATM groups.

During the `1A4W` review, this became scientifically important:

- `TYS I:363` is stored in the PDB as `HETATM`, but it is a modified amino acid
  in the peptide chain. It belongs in receptor preparation and needs an explicit
  protein-compatible Amber template.
- `QWE H:373` is also stored as `HETATM`, but it is RWJ-50215, the bound
  thrombin inhibitor. It is not a protein residue and should be extracted from
  receptor preparation and prepared as a ligand.

So the important distinction is:

```text
Residue object != protein residue
```

In this codebase, `Residue` currently means "PDB atom group", not "amino acid".
That is fine as a raw model, but docking preparation needs an explicit
scientific classification before chemistry stages run.

## Current Implementation

`StructureCleanupStage` now applies a coarse receptor-content policy:

- standard amino acids stay in `PROTEIN_RESIDUES`;
- supported modified amino acids such as `MSE` and `TYS` stay in
  `PROTEIN_RESIDUES`;
- waters are removed by default;
- monoatomic metals and known ions are removed or retained by policy;
- unknown multi-atom non-polymer groups are extracted as bound ligands.

Cleanup publishes the typed result:

```java
ContextKeys.STRUCTURE_CLEANUP_RESULT
```

`ResidueClassifier` performs reusable identity classification. The cleanup
stage separately applies workflow disposition and emits immutable
`ClassifiedResidue` categories in `StructureCleanupResult`.

The older `ContextKeys.EXTRACTED_LIGANDS` handoff remains populated for
backward compatibility while downstream consumers migrate.

`Residue` remains the raw structural grouping. Docking-prep meaning lives in a
classification wrapper rather than the core model.

Proposed enum:

```java
public enum ResidueRole {
    STANDARD_AMINO_ACID,
    MODIFIED_AMINO_ACID,
    WATER,
    METAL_OR_ION,
    COFACTOR,
    LIGAND,
    UNKNOWN
}
```

Proposed wrapper:

```java
public record ClassifiedResidue(
        Residue residue,
        ResidueRole role,
        String reason
) {}
```

Example classification for `1A4W`:

```text
TYS I:363 -> MODIFIED_AMINO_ACID -> keep in receptor
QWE H:373 -> LIGAND              -> extract from receptor
HOH ...   -> WATER               -> remove by default
```

## Cleanup Output

```java
public record StructureCleanupResult(
        List<ClassifiedResidue> receptorResidues,
        List<ClassifiedResidue> extractedLigands,
        List<ClassifiedResidue> removedWaters,
        List<ClassifiedResidue> removedMetals,
        List<ClassifiedResidue> keptSpecialResidues
) {}
```

For backward compatibility during migration, `StructureCleanupStage` still
publishes `ContextKeys.PROTEIN_RESIDUES` and `ContextKeys.EXTRACTED_LIGANDS`.
New consumers should use the typed result.

## Remaining Migration

1. Migrate downstream ligand selection to `StructureCleanupResult`.
2. Remove `ContextKeys.EXTRACTED_LIGANDS` only as a separately approved public
   API migration.
3. Decide later whether cofactors/glycans/covalent adducts need dedicated roles
   and policies beyond the current "allowed special residue" mechanism.

## Scientific Rule To Preserve

Do not classify by `ATOM` vs `HETATM` alone. PDB record type is useful evidence,
but it is not sufficient:

- modified amino acids may appear as `HETATM` and still belong to the receptor;
- bound small molecules may appear as residue-like groups and still need ligand
  preparation;
- cofactors and covalent adducts need explicit policy, not silent guessing.

The classifier should use residue name, known modified-residue policy, water and
ion tables, and eventually PDB metadata such as `MODRES`, `HET`, `HETNAM`,
`LINK`, and chain/polymer membership where available.
