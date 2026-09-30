# Aether LDA Kohn–Sham foundation, protocol version 1

All evidence is **SCREENING_ONLY**.

**STO-3G DFT IS NOT A PRODUCTION INTERACTION-ENERGY METHOD.** These calculations
must not be used to rank inhibitors. This protocol validates numerical DFT
infrastructure, not medicinal-chemistry accuracy.

## Quadrature

`GridDefinition(radialPoints, angularPoints)` is mandatory. There is no implicit
production grid. Every atom uses the same chosen unpruned rule, in supplied atom,
radial-node and angular-node order. Coordinates are bohr; weights are bohr cubed.

The radial rule is Gauss–Legendre on `0 < u < 1`, mapped by
`r = u/(1-u)` with a fixed scale of one bohr. Its volume factor is
`r² dr/du = r²/(1-u)²`. The radial table contains nodes and weights for 40, 80,
120 and 160 points. Gaussian quadrature and its polynomial exactness are
documented in [NIST DLMF §3.5(v)](https://dlmf.nist.gov/3.5#v). Tests check all
radial moments through degree `2*n-1`, plus analytic three-dimensional Gaussian
integrals after the radial mapping.

Angular rules are Lebedev–Laikov: 110 points/order 17, 302/order 29,
590/order 41 and 974/order 53. The tabulated angular weights sum to one, so the
volume weight includes `4*pi`. Tables are generated with PySCF 2.10.0's
`LebedevGrid.MakeAngularGrid`, whose lineage is Laikov → van Wuellen → Knizia →
PySCF. The requested attribution is V. I. Lebedev and D. N. Laikov,
“A quadrature formula for the sphere of the 131st algebraic order of accuracy,”
Doklady Mathematics **59**(3), 477–481 (1999). See the
[PySCF implementation and original attribution](https://pyscf.org/_modules/pyscf/dft/LebedevGrid.html).
Tests check positive weights, unit radii, and axial and mixed-even angular
moments through the declared orders. Numerical tables are hash pinned; no
third-party grid-generator source is compiled into Aether.

The atomic partition uses Becke's three repeated cubic smoothings, with
`mu_ab=(distance_to_a-distance_to_b)/R_ab`, `f(mu)=1.5*mu-0.5*mu³`,
`s_ab=(1-f(f(f(mu_ab))))/2`, `p_a=product_b(s_ab)`, and
`partition_a=p_a/sum_b(p_b)`. There is **no atomic-radius adjustment** and
**no pruning**. See A. D. Becke, J. Chem. Phys. **88**, 2547 (1988),
[doi:10.1063/1.454033](https://doi.org/10.1063/1.454033), and the independent
[PySCF partition implementation](https://pyscf.org/_modules/pyscf/dft/gen_grid.html).
The triangle inequality bounds `mu` by ±1; only binary64 overshoot within
1e-10 is clamped. Larger violations and singular/nonfinite partitions fail.
Coincident distinct nuclei are rejected before division by interatomic distance.

Resource SHA-256 values:

| Resource | SHA-256 |
|---|---|
| `grid/radial.csv` | `ff8aec876d18f02104fd1cb9a6903233b531b03fa3aea92213315bc4d9825da4` |
| `grid/lebedev.csv` | `cfa887a478d09f2b0c7e3ea68a8eae5640477f024345951f1cdc1e5d9227b7d3` |

## AO values, density and XC derivative

`AoGrid` evaluates each existing normalized contraction at every point. It reuses
`ContractedGaussian.normalization()` and
`CartesianAngularMomentum.normalization(PrimitiveGaussian)`, with the existing
s/px/py/pz polynomials and ordered primitive sum. It introduces no element-specific
formula, normalization convention or exponent changes.

The density is the frozen doubled closed-shell convention:
`P_mu_nu = 2 sum_occ C_mu_i C_nu_i`, and
`rho_g = sum_mu_nu AO_gmu P_mu_nu AO_gnu`. Consequently,
`Ngrid=sum_g w_g rho_g`, `Exc=sum_g w_g rho_g epsilon_xc(rho_g)`, and
`Vxc_mu_nu=sum_g w_g v_xc(rho_g) AO_gmu AO_gnu`.
Here `v_xc=d(rho*epsilon_xc)/d rho`, not simply `epsilon_xc`.

There is no density-screening threshold. Nonfinite values fail. A negative
computed density is accepted only within the explicitly recorded rounding bound
`1e-14*sum(abs(AO_mu*P_mu_nu*AO_nu))`; it is clamped to zero and the number of
such points is retained. Larger negative densities fail. Zero density has zero
XC energy and potential. All matrix output is exactly symmetric by upper-triangle
evaluation and mirroring through the existing matrix infrastructure.

## Dirac exchange — validated before correlation

With total, spin-unpolarized density in atomic units,
`Cx=(3/4)*(3/pi)^(1/3)`, `epsilon_x=-Cx*rho^(1/3)` and
`v_x=(4/3)*epsilon_x`. This is `LDA_X`, Libxc id 1, with no adjustable alpha.
The primary reference is P. A. M. Dirac, Proc. Cambridge Philos. Soc. **26**,
376 (1930), [doi:10.1017/S0305004100016108](https://doi.org/10.1017/S0305004100016108),
as identified by the [Libxc functional catalogue](https://libxc.gitlab.io/functionals/).
Tests cover analytic constants and limiting behavior, finite-difference scalar
derivatives, an integrated matrix derivative, and independent Libxc/PySCF
intermediates for all eight molecules.

## Original Perdew–Zunger 1981 correlation

Correlation was added only after the exchange gate passed. The selected form is
the original unpolarized `LDA_C_PZ`, Libxc id 9, together with `LDA_X`.
The primary reference is J. P. Perdew and A. Zunger, Phys. Rev. B **23**, 5048
(1981), [doi:10.1103/PhysRevB.23.5048](https://doi.org/10.1103/PhysRevB.23.5048).
The constants below are the original fit, also exposed in the
[Libxc implementation](https://sources.debian.org/src/libxc/5.2.3-3.1/src/lda_c_pz.c).

Let `rs=(3/(4*pi*rho))^(1/3)`. For `rs<1`,
`epsilon_c=A*ln(rs)+B+C*rs*ln(rs)+D*rs`.
Otherwise `epsilon_c=gamma/(1+beta1*sqrt(rs)+beta2*rs)`.

| Constant | Value |
|---|---:|
| A | 0.0311 |
| B | -0.048 |
| C | 0.0020 |
| D | -0.0116 |
| gamma | -0.1423 |
| beta1 | 1.0529 |
| beta2 | 0.3334 |

The potential uses `v_c=epsilon_c-(rs/3)*d(epsilon_c)/d rs` with an analytic
derivative of each branch. Ordered division avoids squaring a large denominator
in the low-density tail. Scalar references cover both branches and points on
both sides of `rs=1`; tests also cover zero and extreme positive densities.
Original PZ81's rounded parameters have a small discontinuity at the join
(approximately 3.2e-5 hartree per electron). This is not the modified PZ fit;
Aether does not smooth the join or silently replace its constants. Consequently
grid convergence need not be monotonic. The benchmark comparisons use the same
original functional in Libxc 7.0.0.

## Pure LDA KS operator and self-consistency

`F_KS=Hcore+J+Vxc`,
`Eelec=sum(P*Hcore)+0.5*sum(P*J)+Exc`, and `Etot=Eelec+Enuc`.
There is no HF exchange contribution. The existing paired J/K evaluator is
reused to obtain J; its K output is discarded and enters neither operator nor
energy. No duplicate Coulomb contraction was introduced to avoid that incidental
work in this foundation milestone.

`ScfCycles` is the shared package-private state-transition driver for RHF/DIIS
and KS. It owns iteration ordering, the two convergence decisions, Pulay history,
and occupied-density updates. Only the physical Fock/energy evaluations and
their typed evidence differ. The existing plain-RHF entry points remain intact.
Both methods use the validated Lowdin orthogonalization, Commons Math 3.6.1
eigensolver and deterministic orbital sign rule. No new dependency was added.

The initial density comes from Hcore in the overlap metric. DIIS starts at
iteration 32 with history 8, using the frozen commutator and conditioning/fallback
policy. Energy tolerance is 1e-12 hartree, maximum absolute density change is
1e-10, and the default cap is 128. Both criteria must pass. The density residual
is against occupied orbitals of the **physical** `F(P)`, not an extrapolated Fock
operator. No damping, level shifting, alternate occupation or convergence
threshold was introduced. Status remains explicit: CONVERGED, MAX_ITERATIONS,
NUMERICAL_FAILURE or UNSUPPORTED_SYSTEM. The final iterate is not relabeled
converged on failure.

## Provenance and scope

Grid identities bind the complete system, explicit rule, pinned tables and all
ordered coordinates/weights. AO identities additionally bind basis content and
ordering. XC identities bind the density, grid/AO identities, functional protocol,
sampled density, integrated electron count, energy, potential matrix and rounding
count. Fock and orbital evidence bind these identities to Hcore, J and overlap.
The KS receipt contains the initial-state identity and all typed iterations,
including density/Fock/XC/orbital/energy hashes and DIIS coefficients/events.
Timing is excluded from receipts. Large numerical arrays use bounded blocks of
1,024 canonical numbers and the existing `ContentHash` SHA-256 implementation.

The current KS solver accepts complete native STO-3G s/p bases for real centers
and closed-shell systems. Existing RHF ghost and counterpoise workflows remain
separate and unchanged. This milestone adds no DFT interaction-energy workflow,
GGA, hybrid, dispersion, gradients, docking, ML, Athena or METTL7 integration.
