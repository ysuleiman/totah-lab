# Analytic Differential SWCT Final Report

Classification: `ANALYTIC_DIFFERENTIAL_SWCT_EQUIVALENT_AND_FASTER`

- R=1.0 oracle: PASS; max base error 1.41813962e-05
- R=1.4 oracle: PASS; max base error 7.47526628e-06
- R=3.0 oracle: PASS; max base error 6.80565268e-07
- R=1.0: force difference 7.93797031e-07 Ha/bohr; traversal 360000 -> 72000; median speedup 1.870x; equivalent=true
- R=1.4: force difference 1.76661527e-07 Ha/bohr; traversal 360000 -> 72000; median speedup 2.123x; equivalent=true
- R=3.0: force difference 5.29835775e-09 Ha/bohr; traversal 360000 -> 72000; median speedup 1.701x; equivalent=true

The frozen numerical estimator is the scientific reference. No H2 state, estimator definition, sample, or historical evidence was changed. Nuclear antisymmetry and zero transverse components follow exactly from the centered scalar bond-coordinate construction; this experiment does not claim a general molecular vector-force implementation.
