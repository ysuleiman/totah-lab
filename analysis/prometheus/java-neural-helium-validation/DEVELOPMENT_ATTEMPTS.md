# Helium gate development attempts

Failed development attempts are retained separately from validated evidence.

## Attempt 1 — computationally oversized diagnostic

- Training set: 12,000 deterministic six-dimensional configurations
- Optimizer: 140 finite-difference Adam iterations, three seeds
- Outcome: failed validation after approximately 197.5 seconds
- Disposition: not accepted and not frozen as scientific validation evidence
- Finding: the diagnostic loop was unnecessarily expensive for ansatz debugging.

## Attempt 2 — cusp-violating neural invariant

- Correlated energy: `-2.895817802175623 Ha`
- Energy error: `0.007906574858496995 Ha`
- Electron-electron cusp error: `2.5561e-05`
- Electron-nuclear cusp: `-1.965551370031182`
- Electron-nuclear cusp error: `0.03444862996881803` (failed locked `0.01` gate)
- Outcome: rejected
- Root cause: direct dependence of a neural feature on `r1+r2` introduced a nonzero first radial derivative at electron-nucleus coalescence.
- Correction: replace that feature input with the cusp-safe invariant `r1^2/(1+r1)+r2^2/(1+r2)`. No gate was relaxed.

The corrected formulation was rerun from all three initial seeds and passed every gate.
