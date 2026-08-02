# Native Preparation Migration

The supported molecular preparation implementation is moving from the legacy
Daedalus/domain-model packages to Gaia, Hephaestus, and Hermes:

| Legacy package | Native owner |
| --- | --- |
| `totah.lab.ligand.ccd` | `totah.lab.hephaestus.ligand.topology` |
| `totah.lab.ligand.hydrogen` | `totah.lab.hephaestus.ligand.hydrogen` |
| `totah.lab.ligand.charge` | `totah.lab.hephaestus.ligand.charge` |
| `totah.lab.ligand.typing` | `totah.lab.hephaestus.ligand.operation` |
| `totah.lab.ligand.torsion` | `totah.lab.hephaestus.ligand.flexibility` |
| legacy PDBQT writers | `totah.lab.hermes.file.writer.pdbqt` |

The legacy `totah.lab.ligand.LigandPreparer` entry point remains temporarily
for Daedalus source and binary compatibility, but is deprecated. New code must
use `DefaultLigandPreparer.standard(ChemCompProvider)` with Gaia `Ligand`
objects.

## Preserved acceptance boundary

The native suite now owns these regression contracts:

- deposited `4E1J/GOL` heavy-atom order and coordinates are preserved;
- missing CCD hydrogens are generated;
- charge and AD4 assignments are finite and complete;
- torsion serialization covers every atom exactly once;
- repeated ligand preparation emits byte-identical PDBQT;
- gated online `1A4W/QWE` preparation runs when
  `RUN_ONLINE_CCD_TESTS=true`;
- native receptor preparation for `1CRN` and `1UBQ` preserves the curated
  Open Babel heavy-atom frame while applying the configured water policy.

The compatibility comparison intentionally does not require identical
hydrogens, charges, B-factors, or whole-file text because the native receptor
pipeline uses Amber charges and its own explicit hydrogen policy.

## Explicitly unsupported ligand chemistry

The native public boundary returns stable `LigandUnsupportedReason` values for
expected chemistry failures. Protonation-state enumeration, tautomer
enumeration, conformer generation, missing-heavy-atom reconstruction, covalent
ligands, and multi-residue ligands remain unsupported. Default options no
longer claim those features, and explicit requests fail instead of being
silently ignored.
