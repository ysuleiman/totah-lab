# Focused BA/DCMB/2,4-isomer validation

This checkpoint is restricted to fixed WT METTL7A+SAM and WT METTL7B+SAM,
benzylamine, both DCMB enantiomers, and both 2,4-isomer enantiomers. It does not
run the top-100 campaign or regenerate/minimize SAM or productive TSL states.

```bash
python3 analysis/dcmb/focused_validation/run_docking.py
python3 analysis/dcmb/focused_validation/analyze.py
```

Both commands are resumable. Exact hashes, thresholds, seeds, and definitions are
recorded in the generated manifests.
