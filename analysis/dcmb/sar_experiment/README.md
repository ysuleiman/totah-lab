# DCMB mechanism/SAR experiment

This directory is a self-contained, resumable four-state WT docking experiment.
It uses the accepted homologous catalytic pockets and preserves SAM explicitly.
It does not use the earlier protein-only DCMB poses for mechanistic conclusions.

Run from the repository root:

```bash
python3 analysis/dcmb/sar_experiment/build_inputs.py
python3 analysis/dcmb/sar_experiment/run_docking.py
MPLCONFIGDIR=/private/tmp/dcmb-sar-mpl \
  analysis/dcmb/selectivity_validation/.conda-md/bin/python \
  analysis/dcmb/sar_experiment/analyze.py
```

Generated raw jobs are skipped when both their PDBQT and log already exist. Exact
input hashes, boxes, software versions, seeds, and preparation assumptions are
recorded in JSON manifests.
