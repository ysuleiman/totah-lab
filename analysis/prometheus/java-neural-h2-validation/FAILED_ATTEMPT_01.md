# H2 failed execution attempt 01

Classification: `NOT_ACCEPTED_OPTIMIZER_CONVERGENCE_GATE_FAILED`

The first complete nine-point execution used a maximum of 70 optimizer iterations. The first four geometries reached that ceiling and were therefore not classified as converged. The complete gate failed even though the observed curve-level diagnostics were:

- RMSE: `0.008382448558561570 Ha`
- Maximum error: `0.01265584087309035 Ha`
- Predicted equilibrium: `1.443767062862391 bohr`
- Well-depth error: `0.009695648018853475 Ha`
- R=6 energy error from the reference: `0.002057575512636745 Ha`
- Continuation objective-evaluation saving: `0.2115384615384616`
- Seed-energy spread: `0.001456218843117529 Ha`
- Redundant state evaluations: `0`

No acceptance threshold was changed. The next attempt increases only the maximum allowed optimizer iterations so the convergence criterion can be evaluated rather than truncated by the execution ceiling.
