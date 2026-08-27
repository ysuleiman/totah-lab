# Invalidated C2 result

The C2 evidence originally committed as
`137a09b1ce004643348504dd9036773ddb1a8c47` is preserved here solely for
provenance. It is scientifically invalid and must not be used to infer that
the registered C2 Fourier extensions fail.

The production topology builder appended continuation terms with
`ignore_end=false`. In Amber/ParmEd, a continuation term must use
`ignore_end=true`; otherwise it defines the quartet's end-group 1–4
electrostatic and Lennard-Jones interaction again. Consequently, the old C2
Hamiltonians differed from C1 even when every new Fourier amplitude was zero.

The sealed C1 result, source force field, and raw QM evidence were not affected.
