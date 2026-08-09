# Hermes

Hermes owns molecular file I/O and remote data access for Totah Lab. It does
not perform protein or ligand preparation; those scientific decisions belong
to Hephaestus. Cross-format molecular domain objects belong to Gaia.

## File I/O architecture

File code is organized by format. Models live at the format-package root and
are shared by its readers and writers; behavioral classes live below
`reader`, `writer`, `validation`, or `internal`.

```text
totah.lab.hermes.file
├── api             shared I/O contracts
├── chemcomp        chemical-component provider support
├── mmcif
│   └── reader      bound non-polymer occurrences and experimental geometry
├── fasta
│   ├── reader
│   └── writer
├── pdb
│   ├── reader
│   ├── writer
│   └── internal
├── pdbqt
│   ├── reader
│   ├── writer
│   ├── validation
│   ├── vina
│   ├── meeko
│   └── internal
├── sdf
│   ├── reader
│   └── writer
└── pocket
    └── reader
```

PDBQT uses `PdbqtAtom` as its canonical atom representation. Prepared-protein
indices are carried separately by `PdbqtAtomReference` and are never written
to a file. Vina and Meeko results are PDBQT interpretations and therefore live
inside the PDBQT package.

Format-specific parsers and formatters remain separate. In particular, PDB
and PDBQT fixed-column rules must not be silently treated as identical.

FASTA, PDB, PDBQT, and V2000 SDF have paired readers and writers. Their
format models (`FastaRecord`, `PdbqtFile`, and `SdfLigand`) live above those
operations so neither side owns the shared representation. Pocket readers
consume fpocket and P2Rank tool output; Hermes does not currently produce
those external result formats.

## Scientific invariants

I/O changes must preserve:

- atom, residue, chain, model, and pose ordering;
- Amber-derived partial charges;
- AutoDock4 atom types;
- coordinates, precision, fixed-column placement, and record ordering;
- PDBQT torsion trees and branch serial references;
- compatibility with Meeko, Open Babel, and Vina behavior.

Behavior that appears scientifically incorrect is characterized and reported,
not changed as part of a structural refactor.

## Remote access

RCSB, UniProt, Biohub, structure resolution, and shared HTTP support are a
separate architectural boundary. They are intentionally not coupled to the
file-package organization and should be refactored independently.

Provider clients remain in their provider packages. The `http` package owns
only reusable transport, retry, request-building, and endpoint-loading
mechanics. Default service URLs are defined in
`src/main/resources/hermes-endpoints.properties`; injectable client
constructors remain the runtime and test override mechanism.

## Bound components and CCD chemistry

Experimental non-polymer coordinates are read from structure mmCIF files by
`file/mmcif`; they are not CCD data. Component inventory and classification
live under `component`, while `ccd` owns only acquisition of authoritative CCD
chemistry and idealized reference files. The component-inventory API keeps
bound coordinates, CCD CIF paths, and `_ideal.sdf` paths distinct. Idealized
coordinates must never replace the experimental bound geometry.
