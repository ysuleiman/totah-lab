# Completed GPU-60 ingestion and sulfur-force characterization

All 60 results passed nested checksum, geometry, SCF, array-shape, component-sum, and force-sign validation. They are ingested as a homogeneous GPU provenance partition; no fitting method was selected.

## What the points establish

- Energy span: 210.112 kcal/mol.
- Global force-component RMS: 24.717411 kcal/mol/A.
- Sulfur-local force-component RMS: 29.239762 kcal/mol/A.
- S-C range: 1.7470–2.0792 A; S-H range: 1.3112–1.6961 A; C-S-H angle range: 88.20–104.29 degrees.
- Phi/Psi coverage: -180.00–179.99 / -179.89–180.00 degrees.
- D3/global RMS fraction: 0.0132; D3/sulfur-local RMS fraction: 0.0158.

The force field problem is multidimensional: sulfur-local forces change across minimum, torsion, local bond/angle perturbation, and higher-strain geometry. These targets support testing locality and model capacity, but do not by themselves select a fitting method. Baseline residuals on this exact homogeneous set and a frozen split are required before choosing linear corrections, kernels, neural models, or parameter refits.

The batch is deliberately broad rather than near-equilibrium-only. Its 210.112 kcal/mol energy span includes strongly strained force-cloud candidates: sulfur-local per-point RMS ranges from 0.504 to 66.985 kcal/mol/A. Median sulfur-local RMS differs across MIN01/MIN02/MIN04 (21.364/15.789/25.833 kcal/mol/A), while the aggregate is 29.240 kcal/mol/A. Sulfur-local magnitude correlates strongly with total energy rank (`rho=0.903`) but weakly with S-C distance (`-0.020`), S-H distance (`0.125`), or C-S-H angle (`0.046`) individually. This is direct evidence against treating the sulfur problem as a single-coordinate correction.

D3 contributes only about 1.58% of sulfur-local gradient RMS, so the observed structure is overwhelmingly electronic PBE rather than dispersion-driven. The dataset should remain stratified by minimum, perturbation family, and strain/energy regime during any later method comparison. Equal-weight fitting across all 60 would allow the high-force tail to dominate, but the appropriate weighting and model class are deliberately not chosen here.
