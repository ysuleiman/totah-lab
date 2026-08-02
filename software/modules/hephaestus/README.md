# Hephaestus

Hephaestus prepares molecular structures for docking. Gaia owns immutable
molecular objects, Hephaestus owns preparation and molecular validation, and
Hermes owns file parsing, PDBQT serialization, and PDBQT document validation.

## Implemented

- Rigid receptor preparation from PDB or mmCIF
- Prepared-protein validation through the Java client
- Rigid receptor PDBQT writing
- Rigid PDBQT file validation
- Rigid/flexible PDBQT pair validation
- CCD-backed preparation of deposited, single-residue, connected free ligands
- Native real-structure regression for deposited GOL
- Gated native online regression for `1A4W/QWE`

The receptor regression suite prepares `1CRN` and `1UBQ` through the complete
native pipeline and compares every retained heavy atom with the curated Open
Babel reference frame. The comparison intentionally excludes waters removed by
the default cleanup policy and does not require charge or hydrogen-text parity.

The ligand capability boundary is explicit. Protonation-state enumeration,
tautomer enumeration, conformer generation, missing-heavy-atom reconstruction,
covalent ligands, and multi-residue ligands are not implemented. Default
options do not claim these capabilities, and explicitly requesting one fails
instead of silently doing nothing.

The active CLI commands are generated from the command registry:

```text
prepare-receptor
validate-pdbqt
validate-flex-pdbqt
version
help
```

Run `hephaestus --help` or `hephaestus <command> --help` for the current
command contract.

## Planned

The flexibility domain model and flexible serializer exist, but flexible
receptor preparation is not exposed as an end-to-end CLI command yet. Ligand
preparation is available through the Java API but is not yet an active CLI
capability. Prepared-state loading, inspect, and generic conversion are also
not active CLI capabilities.

## Build

```bash
mvn clean test
```
