# Hephaestus

Hephaestus prepares molecular structures for docking. Gaia owns immutable
molecular objects, Hephaestus owns preparation and molecular validation, and
Hermes owns file parsing, PDBQT serialization, and PDBQT document validation.

## Implemented

- Rigid receptor preparation from PDB or mmCIF
- Ligand preparation from SDF (V2000, explicit hydrogens, 3D
  coordinates): topology from the parsed bond table, hydrogenation,
  Gasteiger charges, AD4 typing, torsion tree, validated PDBQT export
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
prepare-ligand
validate-pdbqt
validate-flex-pdbqt
version
help
```

Run `hephaestus --help` or `hephaestus <command> --help` for the current
command contract.

## Standalone CLI

`mvn package` builds a runnable jar (maven-shade; no extra runtime
dependencies to install):

```bash
java -jar target/hephaestus-1.0-SNAPSHOT.jar prepare-ligand \
    --input ligand.sdf --output ligand.pdbqt
java -jar target/hephaestus-1.0-SNAPSHOT.jar prepare-receptor \
    --input receptor.pdb --output receptor.pdbqt
```

## Planned

The flexibility domain model and flexible serializer exist, but flexible
receptor preparation is not exposed as an end-to-end CLI command yet.
Prepared-state loading, inspect, and generic conversion are also
not active CLI capabilities. Ligand preparation is available on both the
Java API and the CLI; protonation-state enumeration, tautomer
enumeration, and conformer generation are not implemented for ligands,
and requesting them fails explicitly.

## Build

```bash
mvn clean test
```
