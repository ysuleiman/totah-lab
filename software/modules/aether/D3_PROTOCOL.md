# M16 PBE-D3(BJ) protocol — validated PASS

SCREENING_ONLY. This is a separate geometry-only energy term, not a change to PBE,
its density, orbitals, SCF, or electronic quadrature. No gradients are implemented.

## Frozen initial method

Nonperiodic, pairwise D3 with rational Becke–Johnson damping; PBE parameters
s6=1, s8=0.7875, a1=0.4289, a2=4.4407. Optional ATM three-body dispersion is
NOT_IMPLEMENTED (s9=0 in the independent oracle). It is not reported as a computed zero.

For every real atom, CN_i=sum_j 1/(1+exp[-16((Rcov_i+Rcov_j)/r_ij-1)]), inclusive
40-bohr coordination cutoff. Rcov is 4/3 times the Pyykko–Atsumi covalent radius.
Reference weights are exp[-4(CN-CNref)^2], normalized per atom. In complete
underflow, the highest reference CN receives unit weight, matching upstream.
C6 is the weighted reference-pair interpolation. C8=3*C6*q_i*q_j, where
q_i=sqrt(0.5*sqrt(Z_i)*<r4>/<r2>_i).

For unique i>j at distances <=60 bohr:
E_ij=-s6*C6/(r^6+R0^6)-s8*C8/(r^8+R0^8),
R0=a1*sqrt(C8/C6)+a2. No additional magnitude screening. All sums have fixed
atom/reference order. Coincident or nonfinite real centers fail closed.

Atomic data are programmatically extracted from simple-dftd3 v1.2.1, retaining
source/payload SHA-256 and upstream LGPL license notices in the resource directory.
No table is manually transcribed. Unit conversion matches mctc-lib CODATA2018:
bohr=(h/(2*pi))/(m_e*c*alpha), using h=6.62607015e-34,
m_e=9.1093837015e-31, c=299792458, alpha=7.2973525693e-3 in SI units.

Primary sources:
- [D3 original definition](https://doi.org/10.1063/1.3382344)
- [BJ damping and PBE parameters](https://doi.org/10.1002/jcc.21759)
- [Pinned implementation/data](https://github.com/dftd3/simple-dftd3/tree/v1.2.1)
- [Covalent radii](https://doi.org/10.1002/chem.200800987)
- [Pinned unit conversion](https://github.com/grimme-lab/mctc-lib/tree/v0.4.0)

## Interaction and ghost convention

Delta_D3 = D3(real AB) - D3(real A) - D3(real B). Coordination numbers are
recomputed in each real system; Delta_D3 is not merely a sum of intermolecular
pairs. Ghost basis centers never contribute nuclei, CN, or dispersion pairs.

Uncorrected Eint(PBE-D3) = E_PBE(AB) - E_PBE(A own) - E_PBE(B own) + Delta_D3.
If a CP electronic calculation is available, Eint_CP(PBE-D3) =
E_PBE(AB) - E_PBE(A with ghost B) - E_PBE(B with ghost A) + Delta_D3.
The geometry-only correction is added exactly once and has no separate BSSE correction.

The existing M15 PbeScf entry point continues to reject ghost-augmented bases.
The user-approved additive solve(GhostBasis, Options, Consumer) overload validates
the explicit ghost context and uses the same SCF implementation. Its real
QuantumSystem determines nuclear charges, electrons, occupation, nuclear repulsion
and atom-centered grid; the augmented list supplies only AO functions. Ghost
identity is additionally bound into the electronic receipt. Existing non-ghost
protocol strings and equations are unchanged.

PbeD3Interaction reports own-basis interactions and rejects ghost-basis inputs.
PbeD3Counterpoise validates the complete donor basis for each physical monomer and
reports electronic CP and physical-atom Delta_D3 separately. Ghost atoms never
enter the D3 calculation. No CP claim is made for an own-basis result.

## Independent oracle

Compiled simple-dftd3 1.2.1, explicit parameters and cutoffs above. Pairwise output
stores half of a pair in each symmetric slot; comparison uses twice one triangular
entry. Independent C6/C8 are recovered from separate undamped s6-only and s8-only
oracle evaluations, not from Aether's interpolation. This isolates reference
interpolation from rational damping. Validation and higher-level method errors
must be reported separately. No claim of protein binding energy is permitted.
