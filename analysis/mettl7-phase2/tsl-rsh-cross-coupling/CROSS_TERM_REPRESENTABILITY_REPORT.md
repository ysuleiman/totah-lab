# TSL-RSH cross-coupling representability report

This controlled experiment used the immutable 39/11/10 GPU-60 split and the
frozen additive result at commit `6d139cfb94130a660b8916fca280caa846883af2`.
Discovery and model construction read only the 39 training labels. Fourteen
translation/rotation-invariant coordinates were defined, including periodic
sin/cos semantics for all torsions. Generalized residual forces were obtained
from `Q = pinv(J.T) DeltaF`, not Cartesian component correlations. Synthetic
finite-difference and force-recovery tests passed before interpretation.

No candidate pair survived the predetermined one-standard-error CV selection.
The best mean candidate, CHI-ETA2, improved mean training CV loss by only 3.8%,
less than its sampling uncertainty. Consequently the frozen minimal model has
zero physical cross terms and one energy-reference nuisance coefficient. This
is an explicit data-limited negative result, not a fitted claim that coupling is
absent.

| Validation metric | Frozen baseline | Frozen additive | Cross candidate |
|---|---:|---:|---:|
| Energy RMS, kcal/mol | 17.815601 | 12.934837 | 12.934837 |
| Relative-energy RMS, kcal/mol | 16.418720 | 9.977987 | 9.977987 |
| Global force-component RMS, kcal/mol/A | 13.221407 | 13.715164 | 13.715164 |
| Sulfur-local force-component RMS, kcal/mol/A | 20.640276 | 22.784244 | 22.784244 |

The cross candidate cannot repair the frozen additive model's negative local
C-S-H harmonic curvature because no evidence-supported physical cross term was
selected. `PHYSICAL_STABILITY_PASS` is therefore false. The model-class decision
is **INCONCLUSIVE_DATA_LIMITED**: the 39-point training set does
not resolve a defensible low-order pairwise extension, and arbitrary extra terms
are prohibited.

Validation was opened once after artifact/receipt verification. A post-open
reporting defect double-shifted additive energies in the first generated JSON;
it was corrected without reopening QM labels from the prior immutable additive
residual artifact and the frozen scalar cross coefficient. Forces were never
affected. Stress results remain separate. No QM, neural model, threshold change,
or validation-driven model choice occurred.
