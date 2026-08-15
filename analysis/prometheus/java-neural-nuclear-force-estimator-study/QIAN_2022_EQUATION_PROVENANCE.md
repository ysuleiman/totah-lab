# Qian et al. 2022 Equation Provenance

Authoritative local artifact:
`reference/prometheus-nuclear-force-literature/qian_2022_interatomic_force_nnvvmc.pdf`

SHA-256:
`f8461334477fc994c94204653c715354be743e3d9ba7e75c488596c0bc514e08`

Publication: Y. Qian, W. Fu, W. Ren, and J. Chen, *Interatomic force
from neural network based variational quantum Monte Carlo*, J. Chem. Phys.
157, 164104 (2022), DOI
[10.1063/5.0112344](https://doi.org/10.1063/5.0112344).

## Implementable definitions

- Eq. 5: bare nucleus-nucleus plus nucleus-electron Hellmann-Feynman force.
- Eqs. 6–8: AC-ZVZB estimator, local energy, and sampled mean energy.
- Eqs. 9–10: minimal AC auxiliary function
  `psi_tilde_A = Q_A psi_T`, with
  `Q_A = Z_A sum_i (r_i-R_A)/|r_i-R_A|`.
- Eq. 11: AC-ZV force component.
- Eqs. 12–13: space-warp transformation and normalized displacement weights.
  The paper follows Filippi–Umrigar with fixed `f(r)=r^-4`.
- Eq. 14: SWCT force as the total derivative of local energy plus the
  energy-centered derivative of `log|J^(1/2) psi_T|`.
- Eq. 15: decomposition of the SWCT local-energy derivative into bare and warp
  parts.
- Eq. 16: no-SWCT estimator.

The paper reports a 3-IQR clipping diagnostic for extreme force samples and
notes that its FermiNet implementation samples plain `psi_T^2` rather than a
traditional reweighting scheme. Prometheus will report raw and identically
predeclared 3-IQR-clipped statistics separately. Raw statistics remain primary;
clipping may not convert a failed unbiased gate into a pass.

## Mapping to Prometheus

The frozen Prometheus direct estimator evaluates the normalized variational
energy derivative through analytic `dH/dR` and the centered logarithmic-state
response. Its expectation is the no-SWCT variational derivative family, but its
sample-level decomposition is not declared identical to Qian Eq. 16 until an
equation-by-equation numerical identity test passes.

