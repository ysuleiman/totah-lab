# TSL-RSH read-only coupling diagnosis

No QM, MD, fitting, topology mutation, or minimization was run. All 56 authoritative C1 points and persisted C3 endpoints were used.

## Decision

Dominant diagnosis: **SHARED_TORSION_PARAMETERIZATION**. The C1 local clones each act on multiple physical quartets (counts: {'LOCAL_TYPE_1': 2, 'LOCAL_TYPE_2': 4, 'LOCAL_TYPE_7': 2, 'LOCAL_TYPE_12': 7, 'LOCAL_TYPE_17': 2, 'LOCAL_TYPE_30': 1}). The C3 PHI n=3 continuation acts on all 9 mapped PHI quartets; its fixed-geometry instance counterfactuals show non-equivalent contributions across those quartets. The independently verified Amber phase and 1-4 invariants rule out a sign/topology explanation.

Secondary evidence supports multidimensional torsional response and incomplete QM coverage. Component correlations are reported as diagnostics only and are not interpreted causally. C1 remains the appropriate frozen Hamiltonian in the sampled thermal region.

## C3 amplification

```json
{
  "C3B_PHI_N3": {
    "phi_le10_direct_rms_kcal_mol": 5.077568809735924,
    "phi_le10_relaxation_mediated_rms_kcal_mol": 1.9275198231419053,
    "phi_le10_total_response_rms_kcal_mol": 4.780373602388986,
    "amplification_total_over_direct": 0.9414689946146895,
    "collective_direct_effect_over_single_fitted_amplitude": 6.258552722547686
  },
  "C3C_CHI_N2_PHI_N3": {
    "phi_le10_direct_rms_kcal_mol": 5.046903497008595,
    "phi_le10_relaxation_mediated_rms_kcal_mol": 1.941262220093087,
    "phi_le10_total_response_rms_kcal_mol": 4.742130213492304,
    "amplification_total_over_direct": 0.9396118265988371,
    "collective_direct_effect_over_single_fitted_amplitude": 5.77362545073017
  }
}
```

## Limits

PHI +15..+90 and the PSI seam/flank remain unlabeled. Existing 1-D scans cannot uniquely separate shared-typing, true multidimensional coupling, and nonbonded relaxation. No interpolation was performed.
