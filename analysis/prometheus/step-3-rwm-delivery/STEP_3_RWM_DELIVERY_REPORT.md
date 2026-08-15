# Step 3 H2O delivery result using qualified direct-|Psi|2 RWM

## Decision

`STEP_3_MULTI_NUCLEAR_VALIDATION_FAILED`

Dominant blocker: `GENERAL_FERMIONIC_WAVEFUNCTION_ACCURACY`.

This is the requested H2O scientific result. The qualified random-walk
Metropolis sampler passed statistical-health requirements at all three frozen
geometries. MALA was not used and did not block execution.

## Energy result

| Geometry | Prometheus (Ha) | Reference (Ha) | Error (Ha) | SE (Ha) |
|---|---:|---:|---:|---:|
| EQ | 148.946182 | -76.438884 | +225.385066 | 8.653705 |
| COMPRESSED | 126.976607 | -76.423221 | +203.399827 | 5.129719 |
| STRETCHED | 153.182225 | -76.420776 | +229.603001 | 7.394130 |

Energy RMSE is `219.763097 Ha`, versus the frozen `0.010 Ha` gate. The errors
are vastly larger than their sampling uncertainties, so this conclusion does
not depend on borderline Monte Carlo noise.

## Force result

| Geometry | component RMSE (Ha/bohr) | max error | max walker SE |
|---|---:|---:|---:|
| EQ | 13.733355 | 23.145971 | 5.462424 |
| COMPRESSED | 10.649948 | 15.838558 | 3.778556 |
| STRETCHED | 15.757900 | 28.564208 | 5.246260 |

Aggregate force-component RMSE is `13.544222 Ha/bohr`; the frozen gate is
`0.010 Ha/bohr`. Maximum component error is `28.564208 Ha/bohr`; the gate is
`0.025 Ha/bohr`. Translational force cancellation remained near machine
precision, so canonical ordering and internal action/reaction consistency did
not cause the failure.

## Sampling delivery metrics

All geometries passed the normalized ESS gate: EQ `0.260`, COMPRESSED `0.444`,
and STRETCHED `0.370`. ESS throughput was approximately 16.68, 22.35, and 14.99
effective samples per sampling second. Sampling consumed 68.1%, 67.4%, and
79.0% of sampling-plus-primary-observable wall time. No sampler replacement is
recommended on the delivery path.

## Interpretation

### Observed

- Direct-|Psi|2 RWM provides statistically qualified samples at every frozen
  geometry.
- Frozen Prometheus energies have the wrong magnitude and sign relative to the
  authoritative H2O reference.
- Cartesian forces disagree by many hartree/bohr.
- Both energy and force uncertainty gates fail by orders of magnitude.

### Supported inference

Because the energy itself fails catastrophically under a qualified sampler,
the dominant blocker lies upstream of the force estimator: the frozen general
Slater–Jastrow state and its frozen optimized parameters do not represent the
ten-electron H2O ground state adequately. Force failure follows but cannot be
assigned solely to SWCT.

### Not established

- That the general molecular Hamiltonian is incorrect.
- That analytic differential SWCT is intrinsically defective.
- That RWM sampling caused the scientific discrepancy.
- Which specific wavefunction extension will resolve H2O.

The first execution omitted per-component force serialization. It is preserved
as `step3-rwm-raw.csv`. The deterministic execution was repeated solely to emit
the complete 27-component force evidence in `step3-rwm-complete-raw.csv`; all
energies and aggregate force metrics reproduced exactly.

Step 3 is finished and failed on scientific model accuracy, not sampling.
