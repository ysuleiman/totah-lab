# MIN01 A100 qualification closure

The immutable historical component-parity gate remains **failed**. The observed maximum gradient-component difference, `3.6847275325449513e-6 Ha/bohr`, exceeds its locked `1e-6 Ha/bohr` limit. This result is neither weakened nor relabelled.

A separate scientific-equivalence gate passes. The CPU/GPU differences are `5.168203642824665e-10 Ha` in energy, `7.537830768521607e-7 Ha/bohr` RMS (`~0.000894 kcal/mol/A`), `3.6847275325449513e-6 Ha/bohr` maximum (`~0.00437 kcal/mol/A`), and `1.0836018364572061e-6 Ha/bohr` sulfur-local RMS (`~0.00129 kcal/mol/A`). The maximum is over 1,700 times smaller than the locked sulfur-local force requirement of `7.5 kcal/mol/A`; the RMS is over 9,000 times smaller than the observed READOUT sulfur-local error of `8.431185 kcal/mol/A`. It is negligible relative to the scientific force-field problem.

Two no-D3 GPU results reproduce their gradients to about `1e-12 Ha/bohr`. D3 implementation, parameters, units, coordinate conversion, and sign were excluded by the forensic audit. The remaining difference is CPU/GPU PBE analytic-gradient numerical/grid implementation behavior. No protocol change or implementation modification is scientifically justified.

New labels are authorized only under one homogeneous frozen GPU protocol: PySCF 2.14.0, GPU4PySCF 1.8.0, PBE/def2-SVP, def2-SVP-JKFIT density fitting, grid level 2 with GPU-native NWChem pruning/original Becke/Treutler-Ahlrichs/Treutler radii adjustment, SCF `1e-8`, maximum 160 cycles, MINAO, explicit D3(BJ) through simple-dftd3 1.5.0 with effective `s9=0` and the frozen parameter database. Historical CPU labels remain immutable and provenance-separated; mixed CPU/GPU training is not yet authorized.

`HISTORICAL_COMPONENT_PARITY_GATE_PASS = false`  
`GPU_QM_SCIENTIFIC_EQUIVALENCE_PASS = true`  
`A100_QM_CAMPAIGN_AUTHORIZED = true`
