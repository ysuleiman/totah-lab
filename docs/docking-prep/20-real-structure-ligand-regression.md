# Real-Structure Ligand Regression

The initial ligand-preparation release is protected by both synthetic
capability tests and deposited-coordinate regression fixtures.

## Offline deposited fixture

`4E1J-glycerol-panel.pdb` contains one deposited ALA residue and two deposited
GOL instances extracted without coordinate changes from RCSB PDB `4E1J`.
BioJava's bundled reduced CCD contains the complete GOL atom, bond, formal
charge, and reference-coordinate definition, so this test remains fully
offline.

GOL is deliberately used in two different ways:

- as a chemistry-engine fixture, it validates a real deposited connected
  molecule with six heavy atoms, eight missing hydrogens, heteroatoms, and
  rotatable bonds;
- as a selection-policy fixture, it validates that a technically preparable
  crystallization additive is not automatically selected as a docking ligand.

This separation prevents chemistry capability from being confused with
operational ligand selection.

## Assertions

The real GOL preparation test verifies:

- all six deposited heavy-atom names remain in their original order;
- all deposited heavy-atom coordinates remain exactly unchanged;
- eight CCD-defined hydrogens are added, producing fourteen atoms;
- every coordinate and partial charge is finite;
- partial charges preserve the CCD formal charge;
- every atom receives an AD4 type;
- the torsion tree has at least one degree of freedom;
- repeated preparation produces byte-identical PDBQT;
- each prepared atom is serialized exactly once;
- `ROOT`, `ENDROOT`, `BRANCH`, `ENDBRANCH`, and `TORSDOF` counts are
  internally consistent.

A derived negative case removes deposited `O3` and verifies the stable
`MISSING_HEAVY_ATOMS` rejection with the missing CCD atom named in the error.

The two deposited GOL instances additionally verify ambiguous multi-candidate
selection followed by `EXCLUDED_BY_POLICY` for explicit GOL selection.

## Online deposited fixture

The gated `1A4W/QWE` test remains the drug-like, aromatic, flexible acceptance
fixture. It exercises online CCD download/cache, heavy-atom reconciliation,
hydrogenation, charge assignment, AD4 typing, torsion construction, cleanup,
selection orchestration, and PDBQT output.

Online tests require:

```text
RUN_ONLINE_CCD_TESTS=true
```

They are intentionally separate from the deterministic default suite.

## Honest boundary

This panel establishes the Version 1 contract for deposited, single-residue,
free, connected ligands with complete CCD definitions and currently supported
elements and valence states. It does not establish universal CCD support.

Phosphates, metals, covalent ligands, multi-residue ligands, missing-heavy-atom
reconstruction, and protonation/tautomer enumeration remain explicit future
work.
