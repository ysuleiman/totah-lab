# TSL-RSH instance-typing pilot result

This is the sealed result of the preregistered ten-amplitude instance-typing pilot. It used the existing 56 authoritative QM labels and performed no new QM, MD, C4 construction, periodicity change, or charge/LJ/SCEE/SCNB change.

## Execution and integrity

- Optimizer: frozen L-BFGS-B configuration; termination `STOP: TOTAL NO. OF F,G EVALUATIONS EXCEEDS LIMIT` after 110 function evaluations and 6 iterations.
- `PILOT_CONVERGED = false`.
- Parent identity, one-four integrity, symmetry ties, C1 frozen parameters, charges, LJ, bonds/angles/impropers, SCEE/SCNB, and serialized read-back invariants all pass.
- Final topology SHA-256: `408ea000989032ae5a65360bbdc4f0fbefb6172ed4724d001b2f5aa76789b047`.

## Thermal-region comparison

Errors are in kcal/mol. Delta is pilot minus C1; negative is improvement.

| Axis | Band | N | C1 RMSE | Pilot RMSE | C1 MAE | Pilot MAE | C1 max | Pilot max | C1 signed mean | Pilot signed mean |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| CHI | <=1 | 4 | 0.403603 | 0.386435 | 0.358567 | 0.344106 | 0.565488 | 0.532801 | -0.195474 | -0.181014 |
| CHI | <=5 | 24 | 0.783062 | 0.783477 | 0.673887 | 0.674663 | 1.536912 | 1.537961 | 0.297641 | 0.316926 |
| CHI | <=10 | 24 | 0.783062 | 0.783477 | 0.673887 | 0.674663 | 1.536912 | 1.537961 | 0.297641 | 0.316926 |
| PHI | <=1 | 1 | 0.000000 | 0.000000 | 0.000000 | 0.000000 | 0.000000 | 0.000000 | 0.000000 | 0.000000 |
| PHI | <=5 | 3 | 0.578783 | 0.546511 | 0.447441 | 0.431261 | 0.899258 | 0.818775 | 0.152065 | 0.114590 |
| PHI | <=10 | 6 | 0.636015 | 0.595474 | 0.527574 | 0.500899 | 1.032171 | 0.938316 | 0.116236 | 0.084798 |
| PSI | <=1 | 2 | 0.010900 | 0.014674 | 0.007708 | 0.010376 | 0.015415 | 0.020753 | -0.007708 | 0.010376 |
| PSI | <=5 | 4 | 0.272980 | 0.295554 | 0.162307 | 0.188905 | 0.537107 | 0.566133 | -0.113954 | -0.094161 |
| PSI | <=10 | 5 | 0.315907 | 0.360164 | 0.219492 | 0.260519 | 0.537107 | 0.566133 | -0.001516 | 0.034066 |

The equal-axis thermal objective decreases from `0.3724997680759783` to `0.36604785784715066`, but CHI <=10 degrades by `+0.0004150710281776` and PSI <=10 degrades by `+0.0442561718942386`; only PHI improves (`-0.0405413915865923`). The frozen cross-surface gate therefore fails.

## Identifiability and attribution

The final sensitivity matrix has rank 10/10, singular values `[8.9360715390, 8.2341184571, 1.5255609698, 0.5761078052, 0.3160739502, 0.2643754362, 0.0630529731, 0.0430299896, 0.0256674469, 0.0085724783]`, and condition number `1042.4140215402394`. There are no numerical null directions, but all ten parameters are classified weakly identifiable because of conditioning and/or strong correlations.

Linearized final-Jacobian split attribution (profile-change RMS, kcal/mol): `LOCAL_TYPE_17 = 0.0190520119`, `LOCAL_TYPE_12 = 0.0`, and `LOCAL_TYPE_2 = 0.0284773835`. All five `LOCAL_TYPE_12` subclasses remain at their lower bound of zero. No subclass reaches the upper bound.

## Frozen gates and decision

Topology, symmetry, serialization, one-four, and minimum-topology gates pass. Thermal, cross-surface, whole-profile, barrier, closure, and unsampled-domain gates fail. The closure metric remains unavailable because the frozen full-domain minimization at PSI -180 did not converge; no value was fabricated.

The residual PHI/PSI one-four electrostatic problem and multidimensional coupling remain. The required classification is `INSTANCE_TYPING_NOT_SUPPORTED`. New multidimensional QM may be scientifically required next, but this run does not authorize or execute it.
