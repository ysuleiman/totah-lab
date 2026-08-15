# Analytic Differential SWCT Derivation and Provenance

## Primary source

Sorella and Capriotti, *Algorithmic differentiation and the calculation of
forces by quantum Monte Carlo*, J. Chem. Phys. 133, 234111 (2010), DOI
`10.1063/1.3516208`, archived as
`reference/prometheus-nuclear-force-literature/sorella_capriotti_2010_ad_forces.pdf`,
SHA-256 `1fd8011bb759043625fbf5588c85cccc73840fd889755731a5dfb1ec29ad9e63`.

- Eq. 10 defines the electronic warp `r_i -> r_i + dR_a omega_a(r_i)`.
- Eq. 11 defines normalized inverse-fourth-power weights.
- Eqs. 12-13 define the Jacobian-reweighted local-energy expectation.
- Eq. 14 gives the differential force estimator.
- Eqs. 15-16 split total derivatives into explicit nuclear and electronic
  directional terms, including one-half the warp divergence from `J^(1/2)`.

The implemented estimator identity is also cross-checked against Qian et al.,
J. Chem. Phys. 157, 164104 (2022), DOI `10.1063/5.0112344`, archived SHA-256
`f8461334477fc994c94204653c715354be743e3d9ba7e75c488596c0bc514e08`,
especially Eqs. 12-15.

## Frozen centered-H2 direction

For the existing scalar bond coordinate R, nuclei move by `-dR/2` and `+dR/2`.
Each electron has frozen SWCT velocity

`v_i,z = omega_+(r_i) - 1/2`, `v_i,x=v_i,y=0`,

with `omega_+` and its divergence exactly as in
`HydrogenMoleculeSpaceWarp`. Along the path, the total directional derivative
is

`D = partial_R + sum_i v_i,z partial_(z_i)`.

For `psi`, electronic Laplacian `Lpsi`, and potential `V`,

`E_L = -1/2 Lpsi/psi + V`,

`D E_L = -1/2 [(D Lpsi) psi - Lpsi (D psi)]/psi^2 + D V`.

The logarithmic/Jacobian response is

`D log(J^(1/2)|psi|) = Dpsi/psi + 1/2 sum_i div(v_i)`.

Substitution into Sorella-Capriotti Eq. 14 gives exactly the frozen estimator's
sample decomposition. The bare derivative is the same expression with electron
velocity zero, retained only for the existing reported decomposition.

## Derivative-order audit

`D Lpsi = D sum_j partial_(x_j)^2 psi` is a nuclear/electronic directional
derivative of a spatial second derivative: a selected mixed third derivative of
the state. A dense third-order tensor is unnecessary. Each graph node carries:

1. primal value, six spatial first derivatives, six spatial Hessian diagonals;
2. one directional tangent of the same compact spatial jet.

This forward directional-over-spatial-second-order construction computes the
single JVP required by SWCT. Storage and arithmetic remain linear in electronic
coordinate dimension per node. Forward-over-reverse and reverse-over-forward
were rejected for this small single-output/single-direction owned graph because
they require graph retention/tape machinery not otherwise needed. Explicit
symbolic kernels were rejected except for the Coulomb potential and SWCT
weight/divergence, whose compact analytic forms already define the estimator.
