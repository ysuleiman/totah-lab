# Aether M15 protocol — v2 validation baseline

SCREENING_ONLY. Restricted total (doubled closed-shell) density, atomic units,
normalized Cartesian s/p/d def2-SVP on H/C/N/O/P/S/Cl. No HF exchange or dispersion.
All existing source, receipts and evidence are protected by the M15 frozen manifest.
New APIs are additive; existing APIs and old implementations are not edited.
The v1 water pilot is retained separately. Independent Libxc boundary tests
corrected the exchange spin-scaling cutoff in v2. The implementation manifest
was frozen after these tests and before the complete SCF measurement suite.

## Functional equations and primary sources

PBE: Perdew, Burke and Ernzerhof, Phys. Rev. Lett. **77**, 3865 (1996),
[DOI](https://doi.org/10.1103/PhysRevLett.77.3865), equations 7, 8 and 14.
PW92: Perdew and Wang, Phys. Rev. B **45**, 13244 (1992),
[DOI](https://doi.org/10.1103/PhysRevB.45.13244), unpolarized parametrization.
The independent numerical oracle is PySCF 2.10.0 / Libxc 7.0.0, IDs 101 and 130.
The precise numerical convention is checked against Libxc's versioned
[PBE exchange parameters](https://gitlab.com/libxc/libxc/-/blob/7.0.0/src/gga_x_pbe.c),
[PBE correlation definition](https://gitlab.com/libxc/libxc/-/blob/7.0.0/maple/gga_exc/gga_c_pbe.mpl),
and [modified PW92 parameters](https://gitlab.com/libxc/libxc/-/blob/7.0.0/maple/lda_exc/lda_c_pw.mpl).
Libxc/PySCF are validation-only, never Aether runtime dependencies.

Let n=rho, sigma=grad(n) dot grad(n), kF=(3*pi^2*n)^(1/3),
s^2=sigma/(4*kF^2*n^2), kS^2=4*kF/pi, t^2=sigma/(4*kS^2*n^2).
Exchange energy density is -Cx*n^(4/3)*[1+kappa-kappa/(1+mu*s^2/kappa)],
Cx=3/4*(3/pi)^(1/3), kappa=0.804, mu=0.2195149727645171.
Correlation energy density is n*(epsilon_c+H), with
H=gamma*log(1+(beta/gamma)*t^2*(1+A*t^2)/(1+A*t^2+A^2*t^4)),
A=(beta/gamma)/expm1(-epsilon_c/gamma), beta=0.06672455060314922,
gamma=(1-ln(2))/pi^2.
For rs=(3/(4*pi*n))^(1/3), epsilon_c=-2*a*(1+alpha*rs)*log1p(1/q),
q=2*a*(b1*sqrt(rs)+b2*rs+b3*rs^(3/2)+b4*rs^2).
Parameters: a=0.0310907 (modified PW92 used by standard PBE), alpha=0.21370,
b1=7.5957, b2=3.5876, b3=1.6382, b4=0.49294.
The PW92 coefficient a is not replaced by PBE gamma; these are distinct constants.

Analytic chain-rule differentiation in n and sigma gives vrho=d(e)/dn and
vsigma=d(e)/d(sigma), where e is energy per volume, not per electron.
log1p/expm1 avoid cancellation. The pointwise Libxc oracle sets its gradient-magnitude threshold
to 1e-100 (sigma floor 1e-200) to test the analytic zero-gradient limit; the default Libxc gradient
magnitude floor (1e-20) otherwise perturbs ultralow-density zero-gradient tests.
Molecular PySCF references retain defaults and quantify their residual difference. No finite differences in production.
The explicit Libxc-compatible low-density convention returns zero exchange for
n<=2e-15 (the per-spin exchange cutoff 1e-15 after spin scaling) and zero correlation for n<1e-12 (the distinct ID 101/130 defaults). This is a versioned numerical tail policy, not an integral screening
threshold; integrated electron count retains these points. Tail-point counts
record points below the larger correlation cutoff. Invalid/nonfinite densities or gradients fail closed.

## AO and GGA potential

d/dx [x^l*y^m*z^n*exp(-a*r^2)] =
(l*x^(l-1)-2*a*x^(l+1))*y^m*z^n*exp(-a*r^2).
Existing primitive/contraction normalization and AO evaluation are reused.
No division by coordinate is used, so nodal planes and coincident centers work.
For symmetric P, grad(rho)=2*sum_ij Pij*phi_j*grad(phi_i).
Vxc_ij=sum_g w_g [vrho*phi_i*phi_j +
2*vsigma*grad(rho) dot (grad(phi_i)*phi_j+phi_i*grad(phi_j))].

F=Hcore+J+Vxc; Eelec=sum(P*Hcore)+0.5*sum(P*J)+Exc.
The frozen ScfCycles, DIIS, overlap/orthogonalization, eigensolver and density
construction govern every transition. Core guess, 1e-12 energy and 1e-10 density
thresholds and 128-iteration cap remain unchanged. No K allocation or contraction.
The new J-only reader shares the frozen checksummed, canonical ERI file format.
Pair symmetry contributes multiplicity 2 for off-diagonal density pairs.

## Execution and validation

Default grid remains 120 radial Gauss-Legendre points mapped r=u/(1-u),
590 angular Lebedev points (order 41), three Becke cubic iterations,
no radius adjustment and no pruning. Exact GridPointSource is reused.
Grid ladder: 40x110, 80x302, 120x590, 160x974 on H2O, CH4, H2S.
Rotation: angle .513 radians about normalized (1,2,3).
Translation: (.31,-1.27,.44) bohr. AO and density evidence uses normalized
Cartesian AOs in both Aether and PySCF, including the diagonal scaling of d AOs.

Block size 256; eight workers; at most sixteen in-flight blocks. Pointwise
and block reductions have deterministic ordering. No whole-grid AO or derivative
array exists in production. Timing sums of worker stages are overlapping elapsed-work durations,
not CPU-time counters or additive wall-clock decompositions. Total SCF wall time and process RSS are
reported separately. Java benchmark heap cap: 512 MiB; large jobs run serially.

Before SCF: validate analytic AO derivatives and rho gradients at arbitrary and
actual quadrature points, plus local XC values/derivatives and complete Vxc for
externally supplied densities. Absolute intermediate tolerance 1e-8, with
1e-10 relative allowance for high-magnitude AO/local-functional values;
energy tolerance 1e-8 hartree. Local functional derivative relative errors use
the sum of absolute exchange/correlation derivatives as conditioning scale:
these terms cancel at zero gradient and can each exceed 1e11 in dilute tails.
A relative error against their near-zero sum would measure cancellation, not
the accuracy of either derivative. Molecular matrix comparisons stay absolute. Independent SCF density/orbital/matrix differences
are quantified; no altered convergence criteria to force a pass.
Same-grid implementation error, ladder quadrature differences and fixed-lab-grid
rotation residuals are separate quantities. Grid convergence is evidence, not a
production-accuracy claim. No dispersion follows without authorization.

## Oracle convergence note

The initial PySCF chlorobenzene core-guess run exhausted 128 iterations. Its
failure log is preserved. An external MINAO atomic-density guess is used to
seek an independent stationary reference at the same grid and thresholds.
This does not alter Aether: its initial guess remains the frozen core guess,
and its convergence still has to pass independently. Reference JSON records
the external guess; iteration trajectories need not agree between packages.

The MINAO/incremental-J chlorobenzene attempt also exhausted 128 cycles.
A full packed Libcint Coulomb recomputation on every oracle cycle converged
within the same cap and energy/orbital-gradient thresholds. Both failed logs
are preserved. No Aether equation, initial guess or SCF criterion changed.
This reference-only packed tensor is not used by Aether's runtime.
