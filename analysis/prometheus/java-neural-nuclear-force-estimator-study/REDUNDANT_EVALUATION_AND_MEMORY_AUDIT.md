# Redundant-Evaluation and Streaming Audit

- Batch size remained bounded at 512 configurations.
- Electron-coordinate clouds were not retained.
- Exact 3-IQR diagnostics retained only scalar force contributions and weights
  for the current geometry, then released them before advancing.
- Direct HF/Pulay and bare-HF diagnostics shared one state trace. The standalone
  baseline replay was deliberately separate and visibly counted.
- AC-ZV used one state evaluation per configuration.
- AC-ZVZB used one state/local-energy evaluation per configuration; its linear
  dependence on sampled mean energy allowed clipped diagnostics without a
  second state pass.
- SWCT used five genuinely distinct state/local-energy evaluations per
  configuration: center, warped plus/minus, and fixed-coordinate plus/minus for
  the preregistered Eq. 15 decomposition.
- Correlated finite difference used two paired state/local-energy evaluations
  per configuration.
- Parameter response used exactly `(2*20+3)*72,000 = 3,096,000` state
  evaluations per geometry, matching the locked accounting.
- No estimator invoked wavefunction optimization, changed parameters, or
  generated reusable training points.

The current SWCT capability uses a locked central numerical derivative because
the Java state graph provides second electronic derivatives but not the mixed
third derivative needed for analytic `d(laplacian)/dR`. This limitation is
explicit in every SWCT result and is not hidden as autodiff.

