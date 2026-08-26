# TSL-RSH torsion-drive methods and reproducibility record

## Scientific method

CHI, PHI, and PSI were evaluated as independent one-dimensional relaxed scans
on a 15-degree periodic grid (`-180, -165, ..., 165` degrees). Three sealed
verified minima supplied multistart seeds. At each candidate, the selected
dihedral was constrained and all remaining coordinates were optimized. The
canonical wavefront used a `1e-5 Eh` energy-decrease reactivation threshold and
a `0.05 Eh` propagation energy limit. Round results were reduced
deterministically after a barrier; no same-round result could influence another
candidate in that round.

Electronic structure used PBE-D3(BJ)/def2-SVP with def2-SVP-JKFIT density
fitting, level-5 quadrature, grid-response-complete analytic gradients, SCF
tolerance `1e-8`, at most 160 SCF cycles, and MINAO initialization. The molecule
had charge 0 and multiplicity 1. Dispersion used simple-dftd3 1.5.0 with
`s6=1.0`, `s8=0.7875`, `s9=0`, `a1=0.4289`, `a2=4.4407`, `alp=14`, and ATM
disabled. Candidate optimization used geomeTRIC 1.1.1, with at most 300
iterations and the frozen campaign convergence settings.

## Computational implementation

The wavefront controller assigned deterministic candidate IDs from torsion,
source identity, and target grid value. Candidates persisted geometry, energy,
gradient components, optimization history, geometry-gate results, receipts, and
nested checksum manifests before becoming reusable. Periodicity, duplicate
suppression, interrupted-candidate recovery, candidate failure isolation, and
round barriers were covered by the offline/package tests.

Frozen software versions were PySCF 2.14.0, GPU4PySCF 1.8.0, CuPy 13.4.1,
cuTensor 2.2.0, simple-dftd3 1.5.0, and geomeTRIC 1.1.1.

## Hardware and execution

- CHI: Google Colab, one NVIDIA A100-SXM4-40GB (embedded runtime evidence).
- PHI: RunPod Secure Cloud, two NVIDIA A100-SXM4-80GB (operator-verified).
- PSI: RunPod Secure Cloud, two NVIDIA A100-SXM4-80GB (operator-verified).

Exact PHI/PSI completion runtimes are not present in the immutable archives and
are recorded as unavailable. Operator estimates are not substituted for missing
scientific metadata. PHI and PSI launch timestamps are preserved in their logs.

## Quality control and provenance

Raw archives are preserved byte-for-byte. The offline publication audit verifies
their fixed SHA-256 identities, ZIP readability, every PHI/PSI root checksum
(5,352 and 9,051 respectively), all 225 CHI candidate manifests, state checksum
lineage, finite energies, task hashes, torsion atoms, constraints, axis-specific
paths, and absence of cross-axis candidate trees.

CHI's historical controller reactivated cells for microscopic improvements. Its
canonical surface was reconstructed without QM from all 225 paid candidate
records; canonical convergence occurs at round 10, uses 67 candidates, and
contains all 24 cells. The original backup remains immutable.

PSI has a documented wrapper-label/status defect. The raw erroneous receipt is
preserved; positive archive evidence proves that all scientific work was PSI.
See `PSI_WRAPPER_LABEL_DEFECT_AND_PROVENANCE_AUDIT.md`.

## Reproduction and data boundaries

`TORSION_PUBLICATION_REPRODUCIBILITY_MANIFEST.json` is the machine-readable
entry point. `TORSION_SURFACE_CONSISTENCY.csv` contains surface-level results;
`TORSION_CHECKSUM_AUDIT.json` and
`TORSION_CROSS_AXIS_CONTAMINATION_AUDIT.json` record integrity decisions. Raw
archives must not be edited. Derived audit artifacts can be regenerated with:

```text
python3 audit_torsion_publication_record.py
python3 test_torsion_publication_audit.py
```

No torsion parameters have been fitted in this work. Any later fit must cite the
exact surface/archive identities and preserve the raw-versus-derived boundary.
No credentials or infrastructure secrets are included.
