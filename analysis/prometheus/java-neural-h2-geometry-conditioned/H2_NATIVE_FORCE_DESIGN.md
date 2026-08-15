# Native Nuclear-Force Design

The Java estimator evaluates

`F_R = -(<dH/dR> + 2*(<O_R E_L> - <O_R><E_L>))`.

`dH/dR` includes all four electron-nuclear derivatives for nuclei fixed at
`z=+-R/2` and the `-1/R^2` nuclear-repulsion derivative. `O_R` is the analytic
logarithmic derivative of the complete geometry-conditioned state, including
the encoder response. The estimator invokes one shared state/geometry bundle
per sampled configuration.

The force, Hellmann-Feynman term, Pulay/response term, energy derivative, and
force-estimator variance are accumulated in one bounded streaming pass. Force
variance uses sufficient moments; samples are neither retained nor reevaluated.
Units are Hartree/bohr and positive force increases R.

