# Native Nuclear-Force Validation Report

The native-force gate did not pass under the locked protocol.

| R (bohr) | Analytic force | Model finite difference | Trusted reference | Analytic/FD error | Reference error |
|---:|---:|---:|---:|---:|---:|
| 1.0 | 0.311980 | 0.322922 | 0.362196 | 0.010942 | 0.050217 |
| 1.4 | -0.016934 | 0.000560 | 0.009120 | 0.017494 | 0.026055 |
| 3.0 | -0.073316 | -0.059866 | -0.060871 | 0.013450 | 0.012445 |

The locked analytic/model finite-difference tolerance was 0.01 Ha/bohr and the
trusted-reference tolerance was 0.03 Ha/bohr. All three analytic/FD comparisons
missed narrowly or materially; R=1.0 also failed the trusted-reference gate.
No force result is eligible for downstream force-field evidence.

This is not classified as a fundamental correctness defect: independent unit
tests validate the analytic Hamiltonian derivative, force sign, units, and
response decomposition on a controlled geometry-conditioned state. The
one-shot scientific result is nevertheless a force-gate failure and is frozen.

