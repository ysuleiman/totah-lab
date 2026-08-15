# Shared-Geometry H2 Implementation Record

Prometheus now owns a Java-native geometry-conditioned H2 state. A cubic
Chebyshev encoder maps bond length to the five parameters of the already
validated cusp-safe correlated H2 ansatz. Twenty shared coefficients describe
the full nine-geometry curve; there are no per-geometry parameters.

Electronic first derivatives, the electronic 6D Laplacian, shared-parameter
derivatives, and the logarithmic geometry derivative are produced from one
seven-dimensional `SecondOrderJet` graph per configuration. The electronic
Laplacian explicitly excludes the geometry axis. The optimizer combines
separately normalized SR statistics from each geometry with equal weight and
retains only bounded sufficient statistics.

Independent finite differences validate all twenty parameter derivatives and
the geometry derivative. Cold-state equivalence to the fixed-geometry H2 state,
electron exchange symmetry, and nuclear interchange symmetry are permanent
regression tests.

