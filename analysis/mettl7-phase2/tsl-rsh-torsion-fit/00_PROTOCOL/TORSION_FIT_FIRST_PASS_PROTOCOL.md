# Preregistered torsional fitting first-pass protocol

## Boundary

Raw CHI/PHI/PSI archives are immutable. This first pass performs integrity
verification, topology mapping, frozen-geometry Sander diagnostics, design
construction, and identifiability analysis only. It performs no fit.

## Reference energies and units

For each independent surface, `Delta E(theta) = E(theta) - min(E)`.
The single conversion constant is `1 Eh = 627.509474 kcal/mol`. The
frozen-QM-geometry residual `Delta E_QM - Delta E_MM` is diagnostic and is not
the final production objective.

## Production MM-relaxed objective (preregistered, not executed here)

At every parameter iteration and every authoritative grid point:

1. start from that point's authoritative QM-optimized geometry;
2. apply an Amber dihedral restraint to the selected axis only, centered at the
   authoritative grid angle, with `rk2=rk3=500 kcal/mol/rad^2` and a `+/-0.5
   degree` flat region;
3. minimize every remaining coordinate with Sander, using the immutable baseline
   topology plus only the candidate proper-torsion changes;
4. use the converged physical MM energy after subtracting the explicit restraint
   energy; fail the objective if minimization, connectivity, chirality, atom
   order, or the `0.5 degree` realization gate fails;
5. independently reference each relaxed MM surface to its own minimum;
6. compare it with the correspondingly referenced QM surface.

This follows established relaxed-profile practice and prevents a torsion term
from being accepted merely because it compensates on frozen QM coordinates.
No unconstrained MD is included. Before execution, the exact Sander restraint
file and minimization controls require a numerical convention/serialization
test and a restart-determinism test.

## Weighting and model selection

Both equal-point and equal-surface objectives will be reported. Equal-surface
weighting is the preregistered primary policy so the 24/18/14 point counts do not
silently change scientific importance. All authoritative points remain reported.
An OpenFF/BespokeFit-style energy sensitivity analysis (flat through 1 kcal/mol,
attenuated through 10 kcal/mol, zero above 10 kcal/mol) will be reported only as
a secondary comparison; it cannot remove points from unweighted validation.

Candidate complexity proceeds from existing periodicities to chemically
justified additions. A more complex model must improve structured angular
holdout behavior, critical-point topology, and conditioning—not only training
RMSE. Periodicities, phases, 1-4 scalings, charges, LJ, bonds, angles, and
impropers remain frozen unless separately authorized.

## Predictive check

Model selection will use a preregistered structured angular holdout, not random
points: every fourth available ordered grid cell per surface, with four rotations
of the starting offset. The model is refit on the remaining cells for each
rotation, and all rotations are reported. Missing PHI/PSI cells remain missing.

## Acceptance status

Literature establishes relaxed-profile objectives and a 1 kcal/mol energy scale,
but does not supply a universal publication acceptance gate for this molecule.
The proposed numerical gates are recorded separately for review and are not yet
locked. No final optimization may begin until they are explicitly approved.
