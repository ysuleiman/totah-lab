# Aether: STO-3G s/p RHF for H/C/N/O/P/S/Cl

Aether reuses Gaia's immutable `Point3D`. Runtime dependencies are Gaia and,
from Milestone 6, the repository's existing Commons Math 3.6.1 numerical backend.
The implementation supports normalized Cartesian s/p contractions for H/C/N/O/P/S/Cl,
dimensionless overlap plus kinetic and nuclear-attraction integrals in hartree, ordered immutable matrices and deterministic SHA-256
receipts. Existing module APIs are unchanged.

```java
var hydrogen = Sto3gHydrogen.load(); // throws IOException
var basis = List.of(
        hydrogen.atBohr(new Point3D(0, 0, 0)),
        hydrogen.atBohr(new Point3D(1.4, 0, 0)));
var overlap = OverlapMatrix.compute(basis);
double crossOverlap = overlap.get(0, 1);
var receipt = overlap.receipt();
```

Imports: `java.util.List`, `totah.lab.gaia.geometry.Point3D`,
`totah.lab.aether.basis.Sto3gHydrogen`, `totah.lab.aether.matrix.OverlapMatrix`.
Use `PrimitiveGaussian` and `GaussianTerm` to supply custom normalized s
primitives to `ContractedGaussian`. All coordinates passed here are **bohr**;
Gaia molecular structures must be explicitly converted by callers if necessary.

Validated examples (Java 21, absolute comparison tolerance 2e-13):

| Example | Aether | Independent PySCF 2.10.0/libcint |
| --- | --- | --- |
| Normalized primitive, alpha=1, self | 1 | 1.0000000000000002 |
| Two alpha=1 primitives, separation 1.4 bohr | 0.37531109885139957 | 0.37531109885139963 |
| Normalized STO-3G H contraction, self | 1.0 | 1.0000000000000004 |
| STO-3G H2 at 1.4 bohr, off diagonal | 0.659318206134864 | 0.65931820613486425 |

H2 overlap matrix, rounded to 15 decimal places:

```text
[1.000000000000000  0.659318206134864]
[0.659318206134864  1.000000000000000]
```

Milestone 1: twenty JUnit 5 invocations passed, including 14 independently generated matrix
entries in five reference cases. Maximum reference error: 4.440892098500626e-16.
Two separate Java 21 processes also reproduced the H2 receipt hash
`3f57c97401fa3b1bcc9e620245f4771842f3d35ff316fca0b23343cfa40c4dc9`.
The fixture generation calls `int1e_ovlp` only, using PySCF/libcint; it does not
use Aether or reproduce its overlap formula. The unequal-exponent oracle uses
an He label solely to assign a second custom basis, not to validate helium chemistry.

Run from the repository root with Maven using Java 21:

```sh
mvn -f software/modules/pom.xml -pl aether -am test \
  -Dtest='Aether*Test' -Dsurefire.failIfNoSpecifiedTests=false
```

On this host Maven otherwise selects Homebrew Java 26; set
`JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home` for
this command. The focused command builds Gaia but selects only Aether tests.

For independent fixture regeneration, install `pyscf==2.10.0` in a disposable
Python environment and run `scripts/generate_overlap_reference.py`. The saved
manifest records Python, package/platform versions, source/fixture/generator
SHA-256 hashes and tolerance. Oracle generation is explicit and is never part
of the Maven test lifecycle. Review regenerated fixtures and update pinned test
hashes only after checking provenance and numerical changes.

Basis policy: bundle immutable, hash-verified H STO-3G parameters extracted
programmatically from the versioned PySCF 2.10.0 distribution (EMSL data dated
2014-09-15). Each resource records source, version, element, shell, exponents,
coefficients and a SHA-256 sidecar. Coefficients multiply normalized primitives;
Aether normalizes the contraction, matching the oracle convention. This is a
small internal properties format, not a general basis-format parser. The original milestone covered hydrogen only; Milestone 9 below adds the
separately pinned C/N/O shells and Cartesian p functions.
Original H–Ne basis reference: Hehre, Stewart and Pople, J. Chem. Phys. 51,
2657 (1969), as cited by the upstream data header.

Reference sources: [versioned basis data](https://github.com/pyscf/pyscf/blob/v2.10.0/pyscf/gto/basis/sto-3g.dat),
[PySCF integral documentation](https://pyscf.org/user/gto.html).

Runtime receipts remain `SCREENING_ONLY` with an explicit reason. Analytic and
fixture validation is capability evidence, not automatic validation of arbitrary
inputs. Invalid input throws `IllegalArgumentException`; nonfinite integral
arithmetic throws `ArithmeticException` with `NUMERICAL_FAILURE`; resource I/O
throws checked `IOException`. No failed matrix is returned. Large-separation
exponential underflow to zero is valid decay. Milestone 7 adds supplied-state electronic and total RHF energy evaluation.
Milestone 8 adds plain RHF SCF. Application integration remains unimplemented.

Repository design documents: `AETHER_V1_SCOPE.md`,
`AETHER_MODULE_ARCHITECTURE.md`, `AETHER_V1_MATHEMATICAL_CONTRACT.md`,
`AETHER_NUMERICAL_BACKEND_DECISION.md`, `AETHER_VALIDATION_MATRIX.csv`.
Current stopping point: Milestone 10 elemental validation. S/P/Cl support is added,
but chlorobenzene core-guess SCF does not converge; the milestone gate remains unmet.

## Milestone 2: kinetic integrals

```java
var kinetic = KineticMatrix.compute(basis); // Same ordered basis and bohr geometry as S
var kineticReceipt = kinetic.receipt();      // SCREENING_ONLY, units in protocol
```

Import `totah.lab.aether.matrix.KineticMatrix`; scalar overloads are available as
`totah.lab.aether.integral.SKinetic.between`. Existing public APIs are unchanged.
Gaussian normalization, contraction sums, pair quantities and matrix/hash
construction are reused through internal helpers. No dependency was added.

H2 kinetic matrix at 1.4 bohr (hartree):

```text
[0.7600318835666087   0.23645465597967405]
[0.23645465597967405  0.7600318835666087 ]
```

Validation: 19 kinetic test invocations plus all 20 overlap regressions passed
on Java 21. All 22 kinetic entries from seven PySCF 2.10.0/libcint cases agree
within 2e-13 hartree; maximum absolute error is 7.771561172376096e-16 hartree.
Cases include primitive self/separated/unequal/negative values, contracted H,
H2 at 1.4 bohr and signed contractions. Analytic, invariance, immutable storage,
ordering, numerical-failure and operator-specific identity tests also pass.
Two independent Java 21 processes produce byte-identical full UTF-8 receipts
under different locales; the test checks against the local receipt as well.

Kinetic receipt hash:
`cf80c3c693451abcedb128ae007f1dc9d733962bec90bba5ac7a4bee7512dbe7`.
SHA-256 of the full UTF-8 receipt representation:
`784309f421fbde437bf0c4d8932591c61f9e450f7fa3bade1981a8e7a14bd90a`.
The distinction matters: the receipt hash covers canonical identity components,
whereas the latter hashes the Java 21 record's full displayed representation.

Run `scripts/generate_kinetic_reference.py` in a disposable Python environment
with `pyscf==2.10.0` to regenerate only the kinetic fixture and its manifest.
It verifies agreement with the existing bundled H STO-3G coefficients, invokes
[libcint's kinetic operator through PySCF](https://pyscf.org/_modules/pyscf/gto/moleintor.html)
(`int1e_kin`), and records the full inputs, package versions and SHA-256 hashes.
It never runs Aether, evaluates SCF, or changes frozen overlap/basis resources.
The generator and fixtures are validation-only and are not runtime dependencies.

This milestone supplies kinetic operator integrals only. All computed matrices
remain SCREENING_ONLY; no total electronic-energy, HF, binding or interaction-energy
capability is implied. Work stops after kinetic validation.

## Milestone 3: nuclear attraction

```java
var nuclei = List.of(
        new NuclearCenter(new Point3D(0, 0, 0), Element.H.getAtomicNumber()),
        new NuclearCenter(new Point3D(1.4, 0, 0), Element.H.getAtomicNumber()));
var attraction = NuclearAttractionMatrix.compute(basis, nuclei);
var attractionReceipt = attraction.receipt(); // SCREENING_ONLY
```

Additional imports: `totah.lab.aether.model.NuclearCenter`,
`totah.lab.aether.matrix.NuclearAttractionMatrix`, `totah.lab.gaia.chemistry.Element`.
Basis and nuclear coordinates are both **bohr**; V entries are **hartree**.
The basis list in this example is the same H2 STO-3G basis used for S and T.

```text
H2 V at 1.4 bohr:
[-1.8804408924734457  -1.1948346203692894]
[-1.1948346203692894  -1.8804408924734457]
```

Java 21 validation: **91 tests passed** (19 nuclear, 33 Boys F0, 39 existing S/T).
All 30 libcint V entries agree within 2e-13 hartree; maximum absolute error is
3.2862601528904634e-14 hartree. Boys F0 agrees with 26 independently generated
80-digit mpmath incomplete-gamma references across zero, near zero, small,
moderate and large arguments, including both branch boundaries. F0 uses a
Taylor series near zero rather than a quotient involving sqrt(t).

The full V receipt bytes agree between two fresh Java 21 processes (en_US and
tr_TR), and with the in-process receipt. Its nuclearCentersHash prevents reuse
across different nuclear charges/positions with an otherwise identical basis.
Existing public APIs and S/T hashes are unchanged; no runtime dependency added.

Regenerate only the nuclear and Boys fixtures with
`scripts/generate_nuclear_reference.py` in the disposable oracle environment
(`pyscf==2.10.0`, `mpmath==1.3.0`). The generator reuses frozen kinetic case
specifications; it does not modify the S/T fixtures or bundled basis. The new
manifest records complete nuclear inputs, versions, tolerances and hashes.
The V oracle is [PySCF/libcint int1e_nuc and int1e_rinv](https://pyscf.org/_modules/pyscf/gto/moleintor.html).

All computed matrices remain SCREENING_ONLY. A NuclearCenter is a positive
mathematical Coulomb source; charge-scaling checks do not authorize additional
chemical basis sets. Invalid input, unrepresentable geometry or nonfinite
arithmetic returns no matrix. No nuclear repulsion, ERI, J/K, Fock, SCF, HF
energy, DFT or application integration is implemented. Nuclear-attraction validation
is complete; Milestone 3.5 below composes the existing one-electron matrices.
See `AETHER_MILESTONE_3_REPORT.md` at the repository root for the full report.

## Milestone 3.5: core Hamiltonian

```java
var system = new QuantumSystem(nuclei, 0, 1); // H2: molecular charge 0, multiplicity 1
var calculator = new CoreHamiltonianCalculator(system, basis);
var core = calculator.calculate(calculator.bind(kinetic), calculator.bind(attraction));
var coreReceipt = core.receipt(); // SCREENING_ONLY; contains the original T and V receipts
```

Additional imports: `totah.lab.aether.model.QuantumSystem` and
`totah.lab.aether.matrix.CoreHamiltonianCalculator`. `calculator.calculate()`
also computes T and V through their existing implementations before checked addition.
The returned type is `totah.lab.aether.matrix.CoreHamiltonianMatrix`, with receipt
`totah.lab.aether.provenance.CoreHamiltonianReceipt`.

The immutable system records ordered hydrogen nuclei in bohr, molecular charge,
and multiplicity. These last two fields distinguish provenance, without adding
an approximation or wavefunction calculation. Existing T/V receipts did not
carry electronic-state identity: binding explicitly associates a reusable matrix
with this context after checking its operator-relevant inputs. Private constructors
prevent unchecked creation of the calculator's typed `KineticInput` and
`NuclearAttractionInput` bindings. Cross-system bindings fail before addition,
including different charge or multiplicity even when T/V values are identical.

Bindings require identical ordered basis hashes, including centers, primitive
exponents, coefficients and primitive ordering; V must also match the ordered
nuclear charges and geometry. Exact operator implementation/protocol checks fix
bohr/hartree units and the existing normalization and numerical conventions.
Matrices of equal dimensions alone cannot pass. Hcore reuses shared matrix
storage and canonical hashing; its calculation hash commits to the system and
both source receipts. All 17 existing public APIs remain unchanged.

```text
H2 Hcore at 1.4 bohr (hartree):
[-1.120409008906837   -0.9583799643896154]
[-0.9583799643896154  -1.120409008906837 ]
```

Java 21: **111 tests passed**, including 20 core tests and all 91 existing tests.
Fresh PySCF 2.10.0/libcint evaluations of `int1e_kin + int1e_nuc` supply 22
reference entries across seven cases, with maximum absolute error
`3.2862601528904634e-14` hartree (tolerance `2e-13`). Separate Java 21 processes
under en_US and tr_TR produce byte-identical full receipts. A pre-change snapshot
of 45 complete S/T/V receipts remains byte-identical; frozen fixtures are unchanged.

Regenerate only the new oracle with `scripts/generate_core_reference.py` in the
disposable PySCF environment. It reuses input specifications, evaluates fresh
integrals, verifies bundled STO-3G coefficients, and records complete inputs and
hashes in `src/test/resources/totah/lab/aether/reference/core-manifest.json`.
See [Milestone 3.5 validation report](MILESTONE_3_5_REPORT.md) for exact evidence.

All results remain **SCREENING_ONLY**. Hcore is a one-electron operator matrix,
not a total electronic energy. Core validation is complete; Milestone 4 below
adds only the electron-repulsion integrals.

## Milestone 4: electron-repulsion integrals

```java
var eri = new ElectronRepulsionCalculator(system, basis).calculate();
double coulombIntegral = eri.get(0, 0, 1, 1); // (00|11), chemists' ordering, hartree
var eriReceipt = eri.receipt();              // SCREENING_ONLY
var counters = eri.performanceCounters();   // elapsedNanos is outside the receipt
```

Import `totah.lab.aether.matrix.ElectronRepulsionCalculator`. The returned immutable
`ElectronRepulsionTensor` exposes indexed retrieval and `uniqueQuartetCount()`,
with no public four-dimensional array. Scalar normalized ssss overloads are in
`totah.lab.aether.integral.ElectronRepulsionIntegral.between`.

For `n` basis functions, unordered AO pairs number `m = n(n+1)/2`; only
`m(m+1)/2` quartets are computed and stored. All eight ERI permutations map to
one stored value. H2 has six stored quartets:

| Quartet (zero-based indices) | Aether, hartree |
| --- | ---: |
| (00\|00) | 0.7746059439198977 |
| (10\|00) | 0.4441076580319601 |
| (10\|10) | 0.29702854027693143 |
| (11\|00) | 0.5696759256037445 |
| (11\|10) | 0.4441076580319601 |
| (11\|11) | 0.7746059439198977 |

The primitive expression reuses normalized Gaussian-pair overlaps, product
centers and Boys F0. Four-function contractions reuse the existing normalizations
and use ordered Neumaier compensated summation. No screening or cache is added.
Each unique contracted quartet uses the full ordered primitive expansion;
H2 evaluates `6 * 3^4 = 486` primitive quartets. Counters record evaluations,
unique storage, zero cache hits, and elapsed nanoseconds including hashing.
Timing never contributes to deterministic receipt bytes.

System/basis identities and receipt hashing reuse the existing internal helpers.
ERIs depend numerically on the basis, while their receipts also retain explicit
QuantumSystem identity to distinguish provenance. Units are bohr and hartree.
Nonfinite intermediate arithmetic fails with `NUMERICAL_FAILURE`; unrepresentable
input geometry returns no tensor. The existing normalization-domain checks and
Boys accuracy protocol remain unchanged.

Java 21 validation: **139 tests passed** (28 ERI, 111 existing). Fifteen analytic
and numerical stress cases accompany all-eightfold symmetry, complete-system
translation/rotation, explicit basis remapping, stability, provenance, immutable
storage, counters, deterministic replay and independent reference checks.
The 370 individual entries from 10 fresh PySCF 2.10.0/libcint `int2e` cases agree
within `2e-13` hartree; maximum absolute error is `3.197442310920451e-14` hartree.
The oracle includes all 16 H2 entries and 256 entries from four distinct centers.

Two separate Java 21 JVMs reproduce identical full receipts. All 59 frozen
S/T/V/Hcore receipts and 21 existing top-level public APIs remain unchanged.
Generate only the new validation fixtures using `scripts/generate_eri_reference.py`
in the disposable PySCF environment. Complete oracle inputs, AO ordering, versions
and hashes are saved in `src/test/resources/totah/lab/aether/reference/eri-manifest.json`.
See [Milestone 4 validation report](MILESTONE_4_REPORT.md) for full evidence.

All results remain **SCREENING_ONLY**. ERI validation is complete. Milestone 5
below adds supplied-density J/K. Milestone 6 adds Fock assembly and a one-shot
orbital solve. Milestone 7 adds supplied-state RHF energy and Milestone 8 adds
plain RHF SCF. DFT and application integration remain unimplemented.

## Milestone 5: supplied-density J/K

The frozen real, spin-summed closed-shell convention is
`P_mu_nu = 2 sum_occupied C_mu_i C_nu_i`. Stored P already includes the factor
of two. Both contractions consume it directly:

```text
J_mu_nu = sum_lambda_sigma P_lambda_sigma (mu nu|lambda sigma)
K_mu_nu = sum_lambda_sigma P_lambda_sigma (mu lambda|nu sigma)
Future convention only: F = Hcore + J - 0.5 K
```

Milestone 6 below implements this Fock convention. Input P entries are dimensionless and
J/K entries are hartree. The sum includes both ordered off-diagonal density
entries; there is no additional factor of two or implicit division of P.

```java
// Externally supplied symmetric density, in row-major order; no orbital calculation.
var density = DensityMatrix.fromRowMajor(system, basis, List.of(1.1, -0.27, -0.27, 0.4));
var jk = JkCalculator.calculate(density, eri);
var coulomb = jk.coulomb();  // CoulombMatrix
var exchange = jk.exchange(); // ExchangeMatrix
var receipt = jk.receipt(); // JkReceipt, SCREENING_ONLY; commits to both outputs and inputs
var counters = jk.performanceCounters(); // elapsed time is excluded from receipt/hash
```

Imports: `totah.lab.aether.matrix.DensityMatrix` and
`totah.lab.aether.matrix.JkCalculator`. The example uses the existing H2 basis,
singlet QuantumSystem and ERI tensor. Density input requires exactly `n*n` finite
row-major values and exact symmetry; it never averages mismatched triangles.
It is immutable and retains the ordered basis, system and convention. Arbitrary
symmetric matrices, including zero and indefinite matrices, are intentionally
allowed; trace, positivity, idempotency and occupations are not inferred.

J/K checks identical QuantumSystem and ordered basis/geometry hashes, plus the
ERI implementation/protocol, before contraction. It reads the existing packed
tensor directly without any full `n^4` expansion. Shared matrix storage and
hashing mirror the upper output triangle. The baseline uses ordered binary64
sums, with no screening or density shortcuts. Nonfinite arithmetic returns no
result. Every existing production source, public API and receipt remains unchanged.

Validation: **159 tests passed**, including 20 J/K checks and all 139 existing
tests. Eight supplied density cases yield 56 J and 56 K external reference entries.
Maximum absolute errors are `8.271161533457416e-15` hartree for J and
`5.551115123125783e-15` for K, against a `5e-13` tolerance. Tests cover zero,
arbitrary signed densities, scaling, additivity, symmetry, basis remapping,
provenance rejection, immutable input/output, overflow, packed access, counters
and deterministic receipts across Java 21 JVMs. All 69 frozen S/T/V/Hcore/ERI
receipts remain unchanged.

`scripts/generate_jk_reference.py` uses fresh PySCF/libcint `scf.hf.get_jk` for
every supplied density. Only that external validation script uses PySCF RHF to
obtain the H2 density fixture; Aether performs no SCF iteration or density update.
The density and reference CSVs, full inputs and hashes are committed under
`src/test/resources/totah/lab/aether/reference/`. See the
[Milestone 5 validation report](MILESTONE_5_REPORT.md) for matrices and counter evidence.

All results remain **SCREENING_ONLY**. J/K validation is complete.

## Milestone 6: Fock assembly and one-shot orbital solve

```java
var result = OneShotRhfCalculator.solve(density, overlap, core, jk);
var fock = result.fock();
var overlapSpectrum = result.overlapEigenvalues();
var x = result.orthogonalization();
var orthogonalFock = result.orthogonalFock();
var coefficients = result.coefficients(); // AO rows, ascending-energy orbital columns
var orbitalEnergies = result.energies();  // hartree; not a total energy
var receipt = result.receipt();           // SCREENING_ONLY
```

Import `totah.lab.aether.matrix.OneShotRhfCalculator`. All six typed intermediate
and orbital evidence objects share the complete one-shot receipt, preserving
system, ordered basis/geometry, supplied density and original S/Hcore/J/K lineage.
The supplied density is retained unchanged. No new density or occupations are built.

The explicit sequence is `F=(Hcore+J)-0.5*K`, diagonalize S, construct
`X=U diag(s^-1/2) U^T`, form `Fprime=X^T F X`, solve its symmetric eigenproblem,
and back-transform `C=X Cprime`. Commons Math 3.6.1's general symmetric solver
was already used in the repository and passed the new reference checks; it is
reused directly without an Athena dependency. The repository audit, pinned
artifact hash and decision are recorded in the [Milestone 6 report](MILESTONE_6_REPORT.md).

S must be exactly symmetric. F/transformation asymmetry beyond
`1e-12 * max(1,maxAbs(matrix))` fails; roundoff within that tolerance is explicitly
averaged. Overlap eigenvalues at or below `max(1e-12,1e-10*smax)` fail closed:
no clipping, rank reduction or regularization. Finite eigenpairs, normalization,
`X^T S X`, `C^T S C` and scaled residuals are checked before any result is returned.

Orbital energies are sorted ascending. Each AO coefficient column is sign-fixed
by making its largest-magnitude component positive, with the first index winning
exact ties. External column comparisons allow sign changes. For exact degeneracy,
the pinned backend's order is retained; arbitrary eigenbasis rotations describe
the same degenerate subspace and are not treated as external sign-only matches.

Validation: **188 tests passed** (29 new, 159 unchanged). The 264 fresh PySCF/SciPy
entries cover all F, overlap eigenvalues, X, Fprime, energies and C entries for
the eight frozen supplied densities. Maximum Fock error is `2.375877272697835e-14`
hartree, orbital-energy error `7.882583474838611e-14` hartree, generalized residual
`4.884981308350689e-15`, and orthonormality error `4.551914400963142e-15`.
H2 and H4 receipts are byte-identical across fresh Java 21 JVMs with different locales.
All 77 frozen S/T/V/Hcore/ERI/J/K receipts and 30 existing top-level APIs are unchanged.

Regenerate only the new reference evidence with `scripts/generate_one_shot_reference.py`.
It reuses the exact Milestone 5 densities, evaluates fresh integrals/J/K and a
PySCF generalized eigensolve, and separately computes explicit Lowdin evidence
using SciPy. It does not call an SCF kernel. Timing counters are available through
`result.performanceCounters()` and are excluded from receipts and hashes.

All evidence remains **SCREENING_ONLY**. Milestone 7 below adds separately invoked
density construction and supplied-state energy evaluation.


## Milestone 7: occupied density and one-shot RHF energy

`OccupiedDensityCalculator.build(system, overlap, result.coefficients(), result.energies())`
returns immutable density and occupation evidence. It reuses `DensityMatrix` and the
existing canonical basis/system/receipt hashes. For a singlet with an even electron
count, the first `Ne/2` ascending-energy columns are occupied twice. Zero and full
occupation are supported; impossible occupations and open-shell systems fail closed.
For exact degeneracies the existing solver column order is retained; no alternative
occupation rule or degeneracy splitting is introduced.

Construction checks identical C/energy/S/system lineage, finite ascending orbital
energies, finite coefficients, `C^T S C = I`, `Tr(P S) = Ne` and the doubled-density
identity **`P S P = 2 P`**. The validation threshold is an absolute `1e-10`;
numerically inadequate evidence is rejected without regularization. Both triangles
of P agree exactly. The result receipt binds the MO source, occupation, constructed
density, numerical protocol and mathematical diagnostics.

```java
var built = OccupiedDensityCalculator.build(system, overlap,
        result.coefficients(), result.energies());
var suppliedEnergy = RhfEnergyCalculator.evaluate(density, core, result.fock());
var nuclear = NuclearRepulsion.calculate(system); // hartree; coordinates in bohr

// The constructed density requires a fresh Fock assembly for that exact P.
var builtJk = JkCalculator.calculate(built.density(), eri);
var builtState = OneShotRhfCalculator.solve(built.density(), overlap, core, builtJk);
var builtEnergy = RhfEnergyCalculator.evaluate(built, core, builtState.fock());
```

New imports are `totah.lab.aether.matrix.OccupiedDensityCalculator`,
`totah.lab.aether.matrix.NuclearRepulsion` and `totah.lab.aether.matrix.RhfEnergyCalculator`.
The energy evaluator requires an exact match between P's hash and the density hash
inside F's receipt, plus the exact Hcore receipt and system/basis/protocol context.
Even a one-ULP density change fails this check. Passing the typed constructed-density
result retains its MO/occupation lineage. Passing a plain external `DensityMatrix`
records external density origin without asserting orbital construction or physical
admissibility. The Fock source's MO evidence is retained in its own role.

Nuclear repulsion sums `ZA ZB / RAB` over ordered `A<B` pairs. It has no AO-basis
dependency, binds the system and ordered nuclear geometry, and rejects coincident
nuclei or nonfinite arithmetic. Electronic energy is `0.5 sum P (Hcore+F)`; total
energy adds nuclear repulsion. The evaluator always reports
`SUPPLIED_STATE_NO_SCF_PERFORMED` and **SCREENING_ONLY**, including when an external
reference was independently converged. It never infers convergence from an energy
or from a small density difference.

Validation: **217 tests pass**, including 29 new checks. Fresh PySCF 2.10.0/libcint
references cover ten supplied states: the eight previous external densities plus
new independently converged H2 and H4 references. All 76 constructed density
entries and 70 occupation/trace/energy scalars are compared separately. PySCF's own
`make_rdm1`, `energy_elec`, `energy_tot` and `energy_nuc` provide the oracle. Only
reference generation runs external RHF kernels; Java contains no SCF loop.

All 85 earlier full receipts remain unchanged. New complete receipts reproduce
byte for byte across Java 21 JVMs with different locales. No existing production
source, public API, fixture or Maven dependency changed. See
[Milestone 7 validation report](MILESTONE_7_REPORT.md) and regenerate the new oracle
with `scripts/generate_rhf_state_reference.py` in the pinned Python environment.

Milestone 8 below adds the separately authorized plain SCF loop. No DIIS, damping,
mixing, DFT, geometry optimization, interaction energy, Athena or METTL7 integration
is implemented.


## Milestone 8: deterministic plain RHF SCF

```java
var scf = RhfScfCalculator.solve(system, basis); // throws IOException for basis-resource I/O
var termination = scf.status();                 // algorithm termination, not scientific validation
var evidenceStatus = scf.scientificStatus();    // always SCREENING_ONLY
var converged = scf.convergedState();           // empty unless CONVERGED
var trajectory = scf.iterations();              // immutable complete iteration evidence
var receipt = scf.receipt();                    // no timings
var performance = scf.performanceCounters();
```

Import `totah.lab.aether.matrix.RhfScfCalculator`; the immutable result is
`RhfScfResult`. The supplied basis must be the bundled STO-3G H basis with exactly
one function at each nucleus; explicit basis permutations are supported. The system
must have a feasible singlet/even-electron occupation. Invalid supported-domain
requests return `UNSUPPORTED_SYSTEM`. Coincident nuclei, rank-deficient overlap and
numerical errors return `NUMERICAL_FAILURE` with a reason and any completed history.
Missing/corrupt basis-resource I/O retains checked `IOException`; invalid iteration
cap configuration throws `IllegalArgumentException`.

The only initial guess diagonalizes Hcore in the overlap metric and doubly occupies
the lowest `Ne/2` orbitals. It reuses the existing one-shot solve with exact zero
J/K; this makes its Fock matrix exactly Hcore. The guess is excluded from the
iteration count. Each subsequent iteration evaluates `E(P[n], F(P[n]))`, solves
F[n], and constructs P[n+1]. Both frozen tests must pass:

- `abs(E[n]-E[n-1]) <= 1e-12` hartree.
- `max_mu_nu abs(P[n+1]-P[n]) <= 1e-10` (dimensionless AO entries).

The first delta energy is explicitly unavailable and cannot pass. Default maximum:
128 iterations. The overload accepting a positive integer cap changes only that cap
and binds it into the receipt. No energy-monotonicity test is used. There is no DIIS,
damping, density mixing, level shift, alternate guess or hidden acceleration.

Each typed iteration retains its input/output density constructions, one-shot
orbitals/Fock, energy, both convergence metrics and criterion flags. The receipt
binds all source and result hashes and every iteration. The converged state exposes
P[n], F(P[n]) and C[n] together; its P[n+1] differs by no more than the frozen density
threshold. It never substitutes the new density into an energy computed with the
old Fock. A capped run retains inspectable history but has no `convergedState()`.
The existing one-shot receipts retain their operation-local no-SCF labels unchanged;
the outer SCF receipt alone asserts algorithmic convergence. All evidence remains
**SCREENING_ONLY**.

Validation: **237 tests pass** (20 new, 217 existing). H2 at 1.4 bohr converges in
2 iterations to `-1.1167143250625706` hartree. The existing H4 benchmark converges in
59 iterations to `-1.4167346115740314` hartree. Fresh plain PySCF 2.10.0 references
use tighter energy/density thresholds, the exact geometries/basis/charges and no
DIIS, damping or level shift. All 94 energy, density, Fock, orbital-energy,
occupied-space projector, core-guess and status entries pass at `1e-8` tolerance.
Maximum total-energy error: `4.529709940470639e-14` hartree; density error:
`3.0127489392128837e-10`; orbital-energy error: `1.1128431509632719e-11` hartree.
A fixed-point density residual is a stopping criterion, not an upper bound on the
distance to a tighter external solution.

Converged and capped H2/H4 receipts are byte-identical across fresh Java 21 JVMs
and locales. Tests also cover both independent stopping criteria, immutable
trajectories, invalid systems, numerical failures, basis permutation, translation
and rotation. All 115 frozen lower-level full receipts remain unchanged.

The performance counters report dimension, completed iteration count, J/K time,
overlap eigendecomposition plus Fock transformation/eigendecomposition time, total
SCF time and final metrics. Phase timings include the core guess and are excluded
from hashes. Integrals and packed ERIs are computed once per run; this milestone
adds no optimization or screening. See [Milestone 8 report](MILESTONE_8_REPORT.md)
and `scripts/generate_scf_reference.py` for full reference and replay provenance.

Milestone 9 below expands chemistry through the integral machinery while retaining
the same plain-SCF equations, guess and convergence policy.


## Milestone 9: H/C/N/O and Cartesian p orbitals

```java
var basisLibrary = Sto3gBasis.load(); // checked IOException; pinned resources
var basis = basisLibrary.forSystem(system); // H/C/N/O nuclei in bohr
var result = RhfScfCalculator.solve(system, basis);
```

Import `totah.lab.aether.basis.Sto3gBasis`. The existing system, matrix and RHF APIs
are reused. Each H contributes one s function; each C/N/O contributes core s,
valence s, px, py, pz. The new `CartesianAngularMomentum` enum and additive
`ContractedGaussian(terms, angularMomentum)` constructor attach a homogeneous
Cartesian polynomial to the existing radial primitives. The original constructor
and primitive record API are unchanged. `CartesianIntegrals` implements one
McMurchie–Davidson recurrence framework for the new overlap, kinetic, attraction
and ERI path; `BoysFunction` adds only orders 1–4, reusing frozen F0.

The [integral protocol](INTEGRAL_RECURRENCES.md) documents normalization, recurrence
formulas, kinetic intermediate powers, stable Boys evaluation, basis source and
angular provenance. Pure-s inputs retain the exact legacy arithmetic and protocol.
P-containing bases have explicitly versioned angular protocol metadata. Electron
counting uses the sum of nuclear charges. RHF equations and iteration policy are
unchanged: core guess, `|deltaE|<=1e-12`, `max|Pnext-P|<=1e-10`, cap 128, no DIIS,
damping, mixing, level shift or extra guess.

Validation: **256 tests pass**, including all 237 previous tests. Fresh PySCF 2.10.0
references cover CH4, NH3, H2O, CO, N2 and ethene. All six plain-RHF runs converge;
14,374 reference entries include all 10,752 unique ERIs. Maximum errors are
`4.440892098500626e-16` for S, `1.4210854715202004e-14` hartree for T,
`1.1191048088221578e-13` hartree for V, `1.2323475573339238e-14` hartree for ERI
and `7.105427357601002e-13` hartree for total RHF energy.

Rotation tests use a dense three-dimensional rotation and explicit AO p-block
transformation, checking S/T/V, density, Fock, orbital energies and total RHF energy
for water, CO and ethene. An independent test transforms all water pppp ERIs as a
rank-four tensor. Primitive eightfold symmetries, normalization, analytic kinetic
and attraction cases, translation, basis permutation and 44 high-precision Boys
values also pass. Full receipts reproduce byte for byte across fresh Java 21 JVMs
for all six molecules. All 119 frozen prior receipts, including H2/H4 SCF states,
remain unchanged.

See [Milestone 9 validation and scaling report](MILESTONE_9_REPORT.md). Regenerate
only the new reference fixtures with `scripts/generate_cno_reference.py` in the
pinned Python environment; Maven does not generate oracles. All evidence remains
**SCREENING_ONLY**. Work stops here. The next milestone is
`S_P_CL_ELEMENT_EXPANSION`, pending authorization. No d/f AOs, DFT, gradients,
geometry optimization, interaction-energy workflow, Athena or METTL7 integration
is implemented.


## Milestone 10: S/P/Cl validation (convergence gate unmet)

`Sto3gBasis` now loads pinned P/S/Cl shells through the same shared loader. Each
new element contributes nine Cartesian functions: 1s, 2s, 3s, 2px/py/pz, 3px/py/pz.
`QuantumSystem` accepts their nuclear charges and reuses its existing molecular
charge/electron-count handling. No integral recurrence, normalization, contraction,
J/K, Fock, eigensolver, density/energy equation or SCF policy changed. See
[SPCl basis provenance](SPCL_BASIS_PROVENANCE.md) for source hashes and extraction.

The **267-test suite passes**, including explicit checks of capped results. Fresh
PySCF/libcint references cover ten fixed chemistry models. Eight Aether core-guess
runs converge: H2S, CH3SH, dimethyl sulfide, PH3, HCl, CH3Cl, trimethylsulfonium (+1)
and methylamine. Trimethylsulfonium has 42 electrons and 21 occupied orbitals;
odd-electron/open-shell and mismatched-charge evidence fail closed.

**Required chlorobenzene and optional ethyl–methyl sulfide do not converge from
the frozen core guess.** They return MAX_ITERATIONS at 128 with no converged state.
Independent plain-PySCF core-guess audits show the same oscillation. MINAO is used
only by the external oracle to obtain converged reference densities for checking
those two systems' integrals and supplied-state energies; no alternate Aether guess
or acceleration was introduced.

All 109,204 reference checks pass, including 61,911 deterministic ERI entries and
all one-electron/J/K/Fock entries at supplied reference densities. The eight
converged Aether states have maximum total-energy error 1.4210854715202004e-12
hartree. This maximum excludes the two capped runs; it is not evidence that they
converged. Prior H/C/N/O receipts remain frozen. Cross-JVM replay covers PH3, HCl
and the charged sulfonium model, alongside all existing replay tests.

See [Milestone 10 validation report](MILESTONE_10_REPORT.md) for the complete outcome,
errors, fixed-geometry provenance and scaling observations. Every result remains
**SCREENING_ONLY**. These are chemistry-validation fragments, not METTL7 binding
calculations. The milestone has not fully passed, so interaction-energy benchmarks
are not started. No DFT, DIIS, geometry optimization, gradients, METTL7 integration
or protein-fragment extraction was implemented.


## Explicit DIIS policy — Milestone 10.2 validated

`RhfScfCalculator.solve(system, basis)` and its cap overload remain plain Roothaan
and preserve historical receipts. Overloads accepting `ScfPolicy.PLAIN` or
`ScfPolicy.DIIS` return `RhfScfRun`; the plain-policy wrapper retains the original
`RhfScfResult`. DIIS remains an explicit choice.

The current version-2 DIIS protocol starts extrapolation at iteration **32** and
retains eight chronological histories. The initial 31 steps are plain updates,
with early termination allowed when both frozen criteria pass. Equations, core
guess, energy/density thresholds (1e-12 hartree / 1e-10), cap 128, conditioning and
fallback rules are unchanged.

The [Milestone 10.2 report](MILESTONE_10_2_REPORT.md) diagnoses the original N2
symmetry-broken RHF saddle and documents the general delayed-start fix. All 18
benchmark systems and 289 tests pass, including separate-JVM replay. This is
benchmark validation, not a guarantee of locating the global RHF minimum.
[Milestone 10.1 evidence](MILESTONE_10_1_REPORT.md) remains historical; new receipts
explicitly identify the changed protocol.

Physical Fock matrices and energies always use the current density through the
validated kernels. Extrapolated Fock operators only generate the next density.
`RhfScfRun.DiisUpdate` separately records histories, coefficients, conditioning,
rejections, fallback decisions, orbital/occupation hashes and output density.
All evidence remains `SCREENING_ONLY`. No interaction-energy workflow is added.

## Fixed-geometry interaction and counterpoise — Milestone 11

`InteractionEnergyCalculator.calculate(FragmentPair)` evaluates five RHF states:
the complex, each fragment in its own basis, and each fragment with the other
fragment's basis present as ghosts. It exposes the uncorrected interaction
`(E_AB - E_A) - E_B` and the Boys–Bernardi counterpoise value
`(E_AB - E_A_ghostB) - E_B_ghostA` separately in hartree. Fragment geometries and
charges are retained; no deformation energy is included. Each fragment must be
closed shell, and fragment identifiers must be distinct.

`GhostBasis` builds complete pinned STO-3G shells on real centers first and then
ordered ghost centers. Ghosts contribute no nuclear charge or electrons. The
real `QuantumSystem` remains the source of electron count, nuclear attraction
and nuclear repulsion. The explicit `RhfScfCalculator.solve(GhostBasis)` entry
point uses the frozen version-2 DIIS protocol, with the existing core guess,
1e-12 hartree energy threshold, 1e-10 maximum density change and cap of 128.
Native SCF entry points retain their existing basis checks and receipts.

Interaction evidence binds fragment identities, all five system/basis/order
contexts, numerical protocols and full SCF receipt hashes. Each value is absent
unless all three of its required components converge. Failed calculations remain
available as typed evidence. Timings are retained in component performance
counters and excluded from deterministic receipts.

See [Milestone 11 validation](MILESTONE_11_REPORT.md) for independent PySCF
comparisons and replay evidence. These fixed STO-3G models validate the
calculation machinery. RHF omits dispersion, and these interaction energies are
not complete binding energies. All results remain `SCREENING_ONLY`.

## LDA Kohn–Sham foundation — Milestone 12

`KohnShamCalculator.solve(system, basis, gridDefinition, functional)` adds pure
restricted LDA with an explicit atom-centered grid. `LdaFunctional.EXCHANGE`
provides Dirac exchange; `EXCHANGE_PZ81` adds original unpolarized PZ81
correlation. `GridDefinition` specifies radial and Lebedev point counts; the
versioned rule uses mapped Gauss–Legendre radial quadrature and Becke partitioning
without radius adjustment or pruning. There is no implicit production grid.

`MolecularGrid`, `AoGrid`, `XcIntegration`, `XcPotentialMatrix`, `KsFockMatrix`,
`KsOrbitals` and `KohnShamResult` retain typed, immutable scientific evidence.
KS uses `F=Hcore+J+Vxc` and the appropriate pure-DFT energy equation. The shared
`ScfCycles` driver reuses the existing deterministic convergence/DIIS transitions,
eigensolver and occupied-density construction. RHF public APIs remain unchanged.

See the [numerical protocol and primary sources](DFT_FOUNDATION_PROTOCOL.md) and
[Milestone 12 validation report](MILESTONE_12_REPORT.md) for independent PySCF
intermediates, grid convergence, quantified rotation errors and replay evidence.
All results remain `SCREENING_ONLY`.

**STO-3G DFT IS NOT A PRODUCTION INTERACTION-ENERGY METHOD.** Do not use these
calculations to rank inhibitors. No DFT interaction workflow, GGA, hybrid,
dispersion, Athena, METTL7, docking, ML or protein-fragment integration is added.

## def2-SVP and Cartesian polarization — Milestone 13

`Def2SvpBasis.load().forSystem(system)` constructs pinned H/C/N/O/P/S/Cl
bases with normalized Cartesian s, p and d functions. The existing recurrence,
AO evaluator and RHF/LDA calculators accept these complete bases. Cartesian d
shells contain six functions; external comparisons explicitly account for
libcint's different Cartesian normalization.

`BasisFamily.DEF2_SVP` selects the expanded basis in the existing ghost-basis and
counterpoise APIs. Existing overloads retain STO-3G defaults. `BasisPerformance`
provides observational setup counters separately from deterministic receipts.

See the [basis and polarization protocol](BASIS_POLARIZATION_PROTOCOL.md) and
[Milestone 13 report](MILESTONE_13_REPORT.md) for the current validation status,
independent references, grid residuals and scaling evidence. Results remain
`SCREENING_ONLY`; neither basis's interaction values are final binding energies.
