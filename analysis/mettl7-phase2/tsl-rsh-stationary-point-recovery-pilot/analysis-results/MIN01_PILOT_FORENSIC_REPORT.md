# MIN01 A100 pilot forensic result

The pilot archive is intact and the preregistered stop was correct. No optimization or
qualification Hessian ran.

## Curvature in a common coordinate

The coordinate is the actual symmetric XYZ displacement reconstructed as
`d=(R_plus-R_minus)/2`, with `q=sqrt(sum_i m_i |d_i|^2)` and `v=d/q`. Curvatures below
are in hartree/(amu bohr²).

| source | full scale | half scale |
|---|---:|---:|
| persisted total Hessian, `vᵀHv` | -1.2346561583240487e-3 | -1.2346561582699288e-3 |
| A100 total energy second difference | +2.6874767298961223e-4 | +2.6870697448585964e-4 |
| A100 total-gradient secant | +2.6483623046405700e-4 | +2.6475599254932850e-4 |

Energy and gradient curvature signs agree. Their magnitudes differ by 1.455% at full
scale and 1.470% at half scale. Full/half energy curvature differs by only 0.0152%, and
full/half gradient curvature differs by 0.0303%. The positive direct curvature is resolved.

The actual XYZ direction has mass-weighted cosine `1.0` with the stored mode and maximum
component difference `1.14e-11`; it is the same direction. Axis permutation, atom-major
flattening, bohr/angstrom conversion and Hessian composition all reproduce exactly.

## Component isolation

At full scale:

| component | Hessian curvature | gradient-secant curvature |
|---|---:|---:|
| D3(BJ) | +5.227940904961175e-6 | +5.227913162869854e-6 |
| PBE | -1.2398840992290092e-3 | +2.596083173011871e-4 |
| total | -1.2346561583240487e-3 | +2.648362304640570e-4 |

D3 agrees to `2.77e-11` hartree/(amu bohr²). The defect is isolated to PBE derivatives.
The historical producer called `mf.Hessian().kernel()` without enabling RKS grid response;
PySCF 2.14.0 defaults `Hessian.grid_response` to false. The A100 runner similarly called
`nuc_grad_method().kernel()` without enabling RKS gradient grid response, whose PySCF default
is also false. These incomplete atom-centered-grid derivatives are not mutually consistent
with the directly evaluated PBE energy surface.

There is also a secondary mass convention defect: `harmonic_analysis` used isotope-average
masses, after which the pilot renormalized and displaced using PySCF's default isotope masses.
It changes scale slightly but cannot change the curvature sign.

## Origin gradient

- norm: `3.863471476022649e-4` hartree/bohr;
- projection on actual displacement direction: `-8.045151459029919e-6`
  hartree/(bohr sqrt(amu));
- energy central slope: `+2.5762261635867548e-5` hartree/(bohr sqrt(amu)).

The stored gradient predicts the opposite one-sided direction from the observed energies.
Therefore the one-sided lowering cannot be attributed reliably to the stored nonzero gradient.
MIN01 passes the old numerical gradient convergence limits, but its physical stationary-point
identity is not trustworthy under the derivative implementation used.

## Decision

`HESSIAN_MODE_CONSTRUCTION_DEFECT = true` because the constructed mode came from an
incomplete PBE Hessian rather than the curvature of the frozen numerical energy protocol.
Stop before optimization. Implement and qualify grid-response-complete PBE gradients and
Hessians, use one explicit mass convention throughout, then repeat only the MIN01 directional
consistency probe.
