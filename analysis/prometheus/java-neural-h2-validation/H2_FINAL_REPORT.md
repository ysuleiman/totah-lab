# Prometheus H2 Multi-Geometry Gate — Final Report

Primary classification: `H2_MULTI_GEOMETRY_GATE_FAILED`

The complete preregistered nine-point Born–Oppenheimer curve was executed. This is a valid negative result. No acceptance criterion was relaxed, and H2 is not frozen as a passed physics gate.

## Curve result

- Curve RMSE: `0.01070812611002042 Ha` — PASS (`<=0.015`)
- Maximum absolute error: `0.01800745152588923 Ha` — PASS (`<=0.025`)
- Predicted equilibrium distance: `1.460780986432386 bohr` — PASS
- Well-depth error: `0.01252620943285199 Ha` — PASS
- Near-dissociation R=6 error from reference: `0.002072783503470843 Ha` — PASS
- Potential curve smoothness and force signs around the minimum: PASS
- Integration-size stability at R=1.4/3.0/6.0: PASS

## Physics audits

- Electron–nuclear cusp maximum errors: `1.51e-05` to `4.64e-05` — PASS
- Electron–electron cusp errors: `2.05e-06` to `8.82e-05` — PASS
- Electron-exchange symmetry: exact within reported arithmetic — PASS
- Nuclear-interchange symmetry: maximum `2.78e-17` — PASS
- Maximum independent 6D gradient error: `4.27e-09` — PASS
- Maximum full 6D Laplacian error: `3.27e-08` — PASS
- Virial behavior at R=1.4: PASS
- Three-seed energy spread: `0.002696871567059889 Ha` — PASS
- Deterministic replay energy and parameter difference: exactly zero — PASS

## Decisive failures

1. The R=0.8, 1.0, and 1.2 optimizations reached the 120-iteration ceiling and did not meet the locked convergence-status requirement.
2. Local-energy variance at R=1.0 was `0.1262303253824847 Ha^2`, exceeding the locked `0.10 Ha^2` gate.
3. Local-energy variance at R=1.2 was `0.1141997095278766 Ha^2`, also exceeding the gate.

Excellent equilibrium, stretched-bond, cusp, and dissociation behavior cannot compensate for these failures.

## Continuation experiment

Continuation reduced aggregate objective evaluations by `21.153846%`, passing the preregistered `>=20%` performance target. Warm/cold final-energy differences were `0.003142 Ha` at R=1.6, `0.000874 Ha` at R=3.0, and `0.001033 Ha` at R=6.0, all inside the existing integration-stability scale.

Continuation was especially effective after the equilibrium region: warm runs required 23, 36, and 19 iterations at R=1.6, 3.0, and 6.0. It did not rescue the compressed-region convergence problem. This supports continuation as a useful execution strategy but not as a substitute for representational adequacy.

## Reuse instrumentation

For every objective evaluation, the wavefunction value, Cartesian gradient, Laplacian, local energy, and parameter-gradient evidence were produced from one shared state-evaluation bundle per electronic configuration. Recorded redundant state evaluations were exactly zero at all nine geometries.

## Scientific interpretation

The current cusp-safe covalent neural state captures the overall H2 potential curve, molecular symmetries, explicit electron correlation, and separated-atom behavior surprisingly well. It is not yet a fully validated molecular neural-QM model because its compressed/near-equilibrium optimization is not reliably converged and the local-energy variance remains too high at two points.

No LiH or geometry-conditioned wavefunction work is authorized by this failed gate. The next action requires a design review, not silent extension or threshold changes.
