# TSL-RSH torsion scientific evidence

This directory is the authoritative local collection point for the CHI, PHI,
and PSI TorsionDrive records.

## Preserved records

- `TSL_RSH_TORSIONDRIVE_BACKUP-20260825T144336Z-1-001.zip`: original Colab
  CHI backup, preserved byte-for-byte.
- `TSL_RSH_PHI_RUNPOD_RESULTS.tar.gz`: sealed, locally verified PHI RunPod
  result archive.
- `TSL_RSH_PSI_RUNPOD_RESULTS.tar.gz`: sealed PSI RunPod result archive,
  independently extracted and verified locally after canonical convergence.

`TORSION_SCIENTIFIC_EVIDENCE_SHA256SUMS` records all three immutable archive
identities.

## Publication and reproducibility layer

Raw evidence is limited to the three archives above; it is never rewritten.
Derived, reproducible audit artifacts are:

- `TORSION_PUBLICATION_REPRODUCIBILITY_MANIFEST.json`: protocol, execution,
  lineage, archive, count, and surface identities;
- `TORSION_SURFACE_CONSISTENCY.csv`: actual CHI/PHI/PSI surface summary;
- `TORSION_CHECKSUM_AUDIT.json`: archive and nested-checksum verification;
- `TORSION_CROSS_AXIS_CONTAMINATION_AUDIT.json`: independent axis isolation;
- `PSI_WRAPPER_LABEL_DEFECT_AND_PROVENANCE_AUDIT.md`: permanent correction
  record without altering the historical receipt;
- `TORSIONDRIVE_METHODS_AND_REPRODUCIBILITY.md`: methods-ready record;
- `PUBLICATION_ARTIFACT_SHA256SUMS`: identities of the derived publication
  layer.

Run `audit_torsion_publication_record.py --verify-only` to fail closed on any
raw-evidence or provenance mismatch. No torsional fitting is authorized by this
index alone; all three provenance, contamination, checksum, and wrapper-test
gates must pass first.
