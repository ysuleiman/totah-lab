# SWCT Symmetry and Relative-Memory Audit

## Nuclear antisymmetry

The frozen coordinate is the centered scalar bond length
`R = z_B - z_A`. Therefore the reported conjugate force maps exactly to
`F_A,z = -F_B,z`; their sum is constructed as IEEE-754 `+0.0` at every radius.
No independent nuclear degree of freedom or estimator term was introduced.

## Transverse reflection

The regression audit evaluates the fused bundle under independent x and y
reflections of both electrons. Wavefunction value, Laplacian, total and bare
directional wavefunction derivatives, and both directional Laplacian
derivatives match bitwise. This establishes zero odd transverse leakage for the
centered axial representation. It does not claim a general molecular vector
force implementation.

## Relative memory gate

Peak-observed heap is a JVM sampling lower bound. Comparing the maximum observed
value over three replays at each radius:

| R (bohr) | Numerical max (bytes) | Analytic max (bytes) | Ratio | Gate |
|---:|---:|---:|---:|---:|
| 1.0 | 307280328 | 307280328 | 1.000 | PASS |
| 1.4 | 445783448 | 445783448 | 1.000 | PASS |
| 3.0 | 351419864 | 306345544 | 0.872 | PASS |

All values are below 1 GiB and no analytic maximum exceeds 1.5 times the
corresponding numerical maximum.
