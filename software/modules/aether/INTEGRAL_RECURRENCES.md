# Cartesian s/p integral protocol

Aether's new Cartesian path uses the Hermite Gaussian expansion framework of
[McMurchie and Davidson, J. Comput. Phys. 26, 218–231 (1978)](https://doi.org/10.1016/0021-9991(78)90092-X).
The following equations document the actual implementation in `CartesianIntegrals`.
No separate per-axis Coulomb formulas, d/f AO support, screening or approximation
thresholds are introduced. The legacy s-only path remains in place to preserve
its validated numerical behavior and receipt bytes.

For primitive exponents a,b, p=a+b, Q=A-B and mu=ab/p, each axis uses Hermite
coefficients with E(0,0,0)=exp(-mu Q²). Out-of-range t is zero. Raising the left
power uses `E(i-1,j,t-1)/(2p) - b Q E(i-1,j,t)/p + (t+1)E(i-1,j,t+1)`;
raising the right uses `E(i,j-1,t-1)/(2p) + a Q E(i,j-1,t)/p + (t+1)E(i,j-1,t+1)`.
Three-dimensional overlap is `E_x(0) E_y(0) E_z(0) (pi/p)^(3/2)`.

Coulomb derivatives start at `R^n_000=(-2p)^n F_n(p|PC|²)` and raise x by
`R^n_tuv=(t-1)R^(n+1)_(t-2)uv + PC_x R^(n+1)_(t-1)uv`, with analogous y/z
recurrences. Attraction sums `-Z 2pi/p E_t E_u E_v R^0_tuv`.
ERI combines two pair expansions, uses rho=pq/(p+q), a ket-derivative sign
`(-1)^(x+y+z)`, and prefactor `2 pi^(5/2)/(p q sqrt(p+q))`.

Kinetic energy is evaluated by applying the second derivative to the ket polynomial:
`b(2L_b+3)S - 2b² sum_axis S(b+2_axis) - 0.5 sum_axis l_b(l_b-1) S(b-2_axis)`.
These shifted overlaps keep the original primitive normalization. Higher polynomial
powers here are internal recurrence intermediates, not supported d/f basis functions.

## Normalization and contraction reuse

`PrimitiveGaussian` retains its existing meaning and API: a normalized s radial
envelope. `CartesianAngularMomentum` adds only S, PX, PY and PZ. For p, the existing
radial normalization is multiplied by sqrt(4a); S remains unchanged. The angular
label is attached to the homogeneous contraction via an additive constructor.
This avoids changing record components or replacing the existing primitive API.

`ContractedGaussian(List<GaussianTerm>)` still constructs S exactly as before.
The new constructor accepts angular momentum; its norm comes from the same shared
ordered contraction sum using the Cartesian overlap recurrence. Operator integrals
reuse the existing two-function contraction sum and four-function Neumaier sum.
The four-function logarithmic fallback now explicitly retains the sign of a primitive
ERI, necessary for signed p-function integrals; the old positive s primitive case is
unchanged. Nonfinite arithmetic fails closed.

## Boys functions and supported order

The maximum Coulomb order for four p AOs is 4. `BoysFunction.value` accepts orders
0 through 4 only. Order zero delegates to the frozen BoysF0 implementation.
For n>0 and T<16 it uses the positive-term identity
`F_n(T)=exp(-T)/(2n+1) * sum_k T^k / (n+3/2)_k`, with relative term cutoff 1e-16
and a hard limit of 256 terms. At T=0 this yields 1/(2n+1) without division by T.
For T>=16, upward recurrence from F0 is stable for the four supported orders:
`F_(n+1)=((2n+1)F_n-exp(-T))/(2T)`. No silent zero-argument quotient is used.

Forty-four independent 80-digit mpmath incomplete-gamma references cover all four
new orders at zero, near zero, small/moderate T, both sides of T=16, and large T
through 10,000. Analytic p self-overlap, kinetic and centered nuclear-attraction
checks accompany the all-entry libcint comparisons.

## Basis and receipt provenance

`Sto3gBasis` loads pinned PySCF 2.10.0 C/N/O STO-3G parameters, extracted from
[the versioned upstream basis data](https://github.com/pyscf/pyscf/blob/v2.10.0/pyscf/gto/basis/sto-3g.dat).
The new CSV SHA-256 is
`e7de8394ca8ff2fba0ee8750a890bfff98147d23faae1450261494c5f7ea1546`.
Hydrogen delegates to the unchanged Sto3gHydrogen loader and resource. C/N/O each
produce five functions: core s, valence s, px, py, pz. Every contraction has three
primitives. Nuclear order is preserved. S, P and Cl elements remain unsupported.

The canonical basis identity adds an explicit angular marker only for p functions;
all old S identities retain their exact bytes. Runtime receipts for a basis containing
p select an explicit `s/p-Cartesian` protocol and append the recurrence/Boys version.
Legacy public protocol constants and all-s receipt values remain unchanged. Context
checks compute the expected protocol from the actual ordered basis. Thus changing
px to py, S to p, AO order, exponents, coefficients or geometry changes provenance.

QuantumSystem now admits H/C/N/O nuclear charges. Electron counting sums nuclear
charges before subtracting molecular charge. The SCF scope check validates the
complete bundled basis as an order-independent collection, allowing explicit AO
permutations. J/K contractions, Fock assembly, Lowdin solve, occupied-density formula,
RHF energy equation and SCF convergence control flow are unchanged.
