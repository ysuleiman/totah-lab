# Random-walk oracle decorrelation follow-up

## Final decision

`MALA_QUALIFICATION_FAILED`

The single authorized oracle intervention succeeded. Increasing retained-state
spacing from 2 to 8 sweeps reduced estimated autocorrelation time from 7.8007 to
3.8479 and raised normalized ESS from 0.1282 to 0.2599. The unchanged 0.20 gate
passed. Acceptance (0.4547), Rhat (1.0205), sticking (0), tail concentration,
deterministic provenance, and bounded memory also passed.

The already locked MALA qualification was then opened without changing its
configuration. Its acceptance was healthy at 0.5752, Rhat was 1.0321, retained
sticking was zero, and both tail-concentration gates passed. However, estimated
autocorrelation time was 13.1642, giving ESS 77.79 from 1,024 retained samples,
or normalized ESS 0.0760. This failed the unchanged 0.20 gate.

Execution stopped before the frozen Step 3 energy/force assessment. No MALA
step, spacing, adaptation, state, optimizer, force estimator, or scientific
threshold was changed after observation.

## Interpretation

The original importance-weight degeneracy is resolved, and the random-walk
oracle demonstrates that direct `|Psi|2` sampling can produce a statistically
qualified bounded H2O sample under the frozen state when retained states are
spaced adequately.

The current MALA configuration is less statistically efficient than the
qualified random-walk oracle for this state. Its acceptance alone was not a
sufficient health indicator. This result does not establish that MALA as a
model class is invalid, but the preregistered production candidate did not
qualify and cannot be used for Step 3.

The recorded large local-energy means/variances remain outside a physics
decision because the MALA gate stopped the assessment. No wavefunction failure
is classified in this unit.
